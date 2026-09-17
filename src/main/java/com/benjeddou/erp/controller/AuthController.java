package com.benjeddou.erp.controller;

import com.benjeddou.erp.config.TenantContextHolder;
import com.benjeddou.erp.model.AuditLog;
import com.benjeddou.erp.model.AuditLog.ActionAudit;
import com.benjeddou.erp.model.AuditLog.ResultatAudit;
import com.benjeddou.erp.service.SessionService;
import com.benjeddou.erp.model.RefreshToken;
import com.benjeddou.erp.model.Role;
import com.benjeddou.erp.model.StatutCompte;
import com.benjeddou.erp.model.Utilisateur;
import com.benjeddou.erp.payload.request.ConnexionRequest;
import com.benjeddou.erp.payload.request.InscriptionRequest;
import com.benjeddou.erp.payload.response.JwtReponse;
import com.benjeddou.erp.payload.response.MessageReponse;
import com.benjeddou.erp.repository.ConnexionLogRepository;
import com.benjeddou.erp.repository.UtilisateurRepository;
import com.benjeddou.erp.security.jwt.JwtUtils;
import com.benjeddou.erp.security.services.UserDetailsImpl;
import com.benjeddou.erp.service.AuditService;
import com.benjeddou.erp.service.CaptchaService;
import com.benjeddou.erp.service.RecaptchaService;
import com.benjeddou.erp.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UtilisateurRepository utilisateurRepository;


    @Autowired
    PasswordEncoder encoder;

    @Autowired
    com.benjeddou.erp.repository.EntrepriseRepository entrepriseRepository;

    @Autowired
    JwtUtils jwtUtils;

    @Autowired
    RefreshTokenService refreshTokenService;

    @Autowired
    AuditService auditService;

    @Autowired
    SessionService sessionService;

    @Autowired
    ConnexionLogRepository connexionLogRepository;

    @Autowired
    CaptchaService captchaService;

    @Autowired
    RecaptchaService recaptchaService;

    @Autowired
    private org.springframework.mail.javamail.JavaMailSender mailSender;

    @org.springframework.beans.factory.annotation.Value("${app.mail.from:ecoressourcesb2b@gmail.com}")
    private String mailFrom;

    // ── Credentials master (root) pour le fallback JDBC multi-tenant ──
    @org.springframework.beans.factory.annotation.Value("${spring.datasource.url}")
    private String masterDbUrl;

    @org.springframework.beans.factory.annotation.Value("${spring.datasource.username}")
    private String masterDbUser;

    @org.springframework.beans.factory.annotation.Value("${spring.datasource.password:}")
    private String masterDbPass;

    @org.springframework.beans.factory.annotation.Value("${app.frontend.url:http://localhost:4200}")
    private String frontendUrl;

    /**
     * GET /api/auth/captcha
     * Génère un CAPTCHA image local (PNG base64) + sessionId UUID.
     * Public — pas de JWT requis (couvert par /api/auth/** → permitAll).
     * Le CAPTCHA est requis après 2 tentatives de connexion échouées.
     */
    @GetMapping("/captcha")
    public ResponseEntity<Map<String, String>> getCaptcha() {
        CaptchaService.CaptchaResult result = captchaService.generateCaptcha();
        return ResponseEntity.ok(Map.of(
            "sessionId",    result.sessionId(),
            "imageBase64",  result.imageBase64()
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> authentifierUtilisateur(
            @Valid @RequestBody ConnexionRequest connexionRequest,
            HttpServletRequest request) {

        String ip = AuditService.extractIp(request);
        String ua = AuditService.extractUa(request);
        // L'identifiant peut etre un username, email ou telephone - resolution automatique
        String username = connexionRequest.getIdentifiant();

        // 0. Rate Limiting — bloquer si trop de tentatives échouées
        if (auditService.estBloquee(ip)) {
            auditService.log(ActionAudit.RATE_LIMIT_BLOQUE, ResultatAudit.BLOQUE,
                "IP bloquée après " + 5 + " tentatives échouées — tentative login: " + username,
                null, null, ip, ua, "AUTH", null);
            Map<String, Object> erreur = new HashMap<>();
            erreur.put("message", "Trop de tentatives de connexion. Réessayez dans 15 minutes.");
            erreur.put("code", "RATE_LIMITED");
            erreur.put("tentativesRestantes", 0);
            return ResponseEntity.status(429).body(erreur);
        }

        // 0b. Double CAPTCHA — requis si captchaSessionId présent dans la requête
        //     Le frontend envoie les deux CAPTCHAs après 2 échecs consécutifs :
        //       1. CAPTCHA image local (captchaSessionId + captchaCode)
        //       2. Google reCAPTCHA v2 (recaptchaToken)
        String captchaSessionId = connexionRequest.getCaptchaSessionId();
        String captchaCode      = connexionRequest.getCaptchaCode();
        String recaptchaToken   = connexionRequest.getRecaptchaToken();

        if (captchaSessionId != null && !captchaSessionId.isBlank()) {

            // ── Étape 1 : Valider le CAPTCHA image local ──────────────────────
            boolean captchaValide = captchaService.validateCaptcha(captchaSessionId, captchaCode);
            if (!captchaValide) {
                // Générer un nouveau CAPTCHA pour la prochaine tentative
                CaptchaService.CaptchaResult nouveauCaptcha = captchaService.generateCaptcha();
                Map<String, Object> erreur = new HashMap<>();
                erreur.put("message", "Code de vérification incorrect. Veuillez réessayer.");
                erreur.put("code", "CAPTCHA_INVALID");
                erreur.put("captchaRequired", true);
                erreur.put("captchaSessionId",  nouveauCaptcha.sessionId());
                erreur.put("captchaImageBase64", nouveauCaptcha.imageBase64());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(erreur);
            }

            // ── Étape 2 : Valider le token Google reCAPTCHA v2 ───────────────
            boolean recaptchaValide = recaptchaService.verifierToken(recaptchaToken);
            if (!recaptchaValide) {
                // Le code local était correct mais reCAPTCHA échoue : générer un nouveau CAPTCHA
                CaptchaService.CaptchaResult nouveauCaptcha = captchaService.generateCaptcha();
                Map<String, Object> erreur = new HashMap<>();
                erreur.put("message", "Vérification Google reCAPTCHA échouée. Veuillez cocher la case \"Je ne suis pas un robot\".");
                erreur.put("code", "RECAPTCHA_INVALID");
                erreur.put("captchaRequired", true);
                erreur.put("captchaSessionId",  nouveauCaptcha.sessionId());
                erreur.put("captchaImageBase64", nouveauCaptcha.imageBase64());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(erreur);
            }
        }

        // 1. Authentification Spring Security
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, connexionRequest.getMotDePasse()));
        } catch (Exception e) {
            // CRITIQUE : effacer le contexte tenant AVANT tout appel JPA (auditService)
            // Si loadUserByUsername a sett\u00e9 un tenant context et qu'une table JPA manque
            // dans cette base, l'exception se propage ici et cause un 500 non captur\u00e9.
            TenantContextHolder.clear();
            // Enregistrer l'\u00e9chec et incr\u00e9menter le compteur
            long secondesRestantes = auditService.enregistrerEchecEtVerifier(ip);
            int restantes = auditService.tentativesRestantes(ip);
            auditService.log(ActionAudit.LOGIN_ECHEC, ResultatAudit.ECHEC,
                "Identifiants incorrects pour: " + username + " (restantes: " + restantes + ")",
                null, username, ip, ua, "AUTH", null);
            Map<String, Object> erreur = new HashMap<>();
            if (secondesRestantes > 0) {
                erreur.put("message", "Compte temporairement bloqu\u00e9. R\u00e9essayez dans " + (secondesRestantes / 60 + 1) + " minutes.");
                erreur.put("code", "RATE_LIMITED");
            } else {
                erreur.put("message", "Identifiants incorrects. Tentatives restantes : " + restantes + "/5");
                erreur.put("code", "BAD_CREDENTIALS");
                erreur.put("tentativesRestantes", restantes);

                // \u2500\u2500 CAPTCHA requis d\u00e8s 2 \u00e9checs (restantes <= 3 sur 5 max) \u2500\u2500
                // Le backend g\u00e9n\u00e8re un nouveau CAPTCHA et le joint \u00e0 la r\u00e9ponse.
                if (restantes <= 3) {
                    CaptchaService.CaptchaResult captcha = captchaService.generateCaptcha();
                    erreur.put("captchaRequired",    true);
                    erreur.put("captchaSessionId",   captcha.sessionId());
                    erreur.put("captchaImageBase64", captcha.imageBase64());
                }
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(erreur);
        }

        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        // 2. Charger l'utilisateur depuis la DB pour acceder aux nouveaux champs
        // userDetails.getUsername() retourne le nom d'utilisateur resolu par Spring Security
        // IMPORTANT : Ce findByNomUtilisateur() utilise JPA (avec routage tenant).
        // Si la base tenant est ancienne et manque des colonnes, JPA lance une SQLException.
        // Dans ce cas, on reconstruit un Utilisateur minimal depuis UserDetailsImpl pour
        // ne pas bloquer le login — les colonnes manquantes seront ajoutées au prochain
        // redémarrage par DatabaseInitializer.appliquerMigrationsColonnesTousTenants().
        Utilisateur utilisateur;
        try {
            Optional<Utilisateur> userOpt = utilisateurRepository
                    .findByNomUtilisateur(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                // Tentative par email
                userOpt = utilisateurRepository.findByEmail(userDetails.getUsername());
            }
            if (userOpt.isEmpty()) {
                // Fallback : construire depuis UserDetailsImpl (login toujours possible)
                log.warn("[AUTH] Utilisateur '{}' introuvable via JPA → fallback UserDetailsImpl (colonnes manquantes ?)", userDetails.getUsername());
                utilisateur = new Utilisateur();
                utilisateur.setId(userDetails.getId());
                utilisateur.setNomUtilisateur(userDetails.getUsername());
                utilisateur.setEmail(userDetails.getEmail());
                utilisateur.setMotDePasse(userDetails.getPassword());
                utilisateur.setRole(userDetails.getAuthorities().stream()
                    .map(a -> { try { return Role.valueOf(a.getAuthority().replace("ROLE_", "")); } catch (Exception e) { return Role.ADMIN; } })
                    .findFirst().orElse(Role.ADMIN));
                utilisateur.setActif(true);
                utilisateur.setStatutCompte(StatutCompte.ACTIF);
                utilisateur.setModeTrial(false);
                utilisateur.setNbUtilisations(0);
                utilisateur.setNbUtilisationsMax(999);
                utilisateur.setDoitChangerMotDePasse(false);
                utilisateur.setEntrepriseSchema(TenantContextHolder.getCurrentTenant());
            } else {
                utilisateur = userOpt.get();
            }
        } catch (Exception jpaEx) {
            // JPA échoue (Unknown column, etc.) → fallback UserDetailsImpl
            log.warn("[AUTH] JPA findByNomUtilisateur échoué pour '{}' : {} → fallback UserDetailsImpl", userDetails.getUsername(), jpaEx.getMessage());
            utilisateur = new Utilisateur();
            utilisateur.setId(userDetails.getId());
            utilisateur.setNomUtilisateur(userDetails.getUsername());
            utilisateur.setEmail(userDetails.getEmail());
            utilisateur.setMotDePasse(userDetails.getPassword());
            utilisateur.setRole(userDetails.getAuthorities().stream()
                .map(a -> { try { return Role.valueOf(a.getAuthority().replace("ROLE_", "")); } catch (Exception e) { return Role.ADMIN; } })
                .findFirst().orElse(Role.ADMIN));
            utilisateur.setActif(true);
            utilisateur.setStatutCompte(StatutCompte.ACTIF);
            utilisateur.setModeTrial(false);
            utilisateur.setNbUtilisations(0);
            utilisateur.setNbUtilisationsMax(999);
            utilisateur.setDoitChangerMotDePasse(false);
            utilisateur.setEntrepriseSchema(TenantContextHolder.getCurrentTenant());
        }


        // ── Auto-upgrade vers BCrypt uniquement si le mot de passe stocké N'EST PAS BCrypt ──
        // ATTENTION : ne jamais re-encoder un hash BCrypt déjà valide (cost 10, 12, etc.).
        // Ré-encoder un hash BCrypt valide avec le mot de passe en clair productirait un
        // nouveau hash différent qui casserait la synchronisation master↔tenant.
        String hashActuel = utilisateur.getMotDePasse();
        boolean estBcrypt = hashActuel != null && (
            hashActuel.startsWith("$2a$") ||
            hashActuel.startsWith("$2b$") ||
            hashActuel.startsWith("$2y$")
        );
        if (!estBcrypt) {
            // Mot de passe en clair en DB → migrer vers BCrypt et synchroniser partout
            try {
                String nouveauHash = encoder.encode(connexionRequest.getMotDePasse());
                utilisateur.setMotDePasse(nouveauHash);
                // ═══════════════════════════════════════════════════════════════
                // SÉCURITÉ MULTI-TENANT : Ne jamais sauvegarder via JPA un admin
                // tenant en dehors de son contexte. Si l'utilisateur appartient à
                // un tenant → mise à jour JDBC directe dans la base tenant UNIQUEMENT.
                // Si master (entrepriseSchema null) → JPA master normal.
                // ═══════════════════════════════════════════════════════════════
                if (utilisateur.getEntrepriseSchema() != null && !utilisateur.getEntrepriseSchema().isBlank()) {
                    // Admin tenant → mise à jour JDBC dans le tenant uniquement (jamais en master)
                    syncMotDePasseTenant(utilisateur, nouveauHash);
                    log.info("\uD83D\uDD12 Mot de passe migré vers BCrypt (tenant JDBC) pour : {}", utilisateur.getNomUtilisateur());
                } else {
                    // Compte master (SuperAdmin, CLIENT trial) → save JPA master normal
                    utilisateurRepository.save(utilisateur);
                    syncMotDePasseTenant(utilisateur, nouveauHash);
                    log.info("\uD83D\uDD12 Mot de passe migré vers BCrypt (master JPA) pour : {}", utilisateur.getNomUtilisateur());
                }
            } catch (Exception e) {
                log.warn("Migration BCrypt non appliquée : {}", e.getMessage());
            }
        }
        StatutCompte statut = utilisateur.getStatutCompte();
        if (statut == null) statut = StatutCompte.ACTIF;

        if (statut == StatutCompte.REFUSE) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new MessageReponse(
                        "Votre compte a été refusé. Veuillez contacter l'administrateur."));
        }
        if (statut == StatutCompte.VERROUILLE) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new MessageReponse(
                        "COMPTE_VERROUILLE|Votre compte a été verrouillé suite à une connexion suspecte signalée. Veuillez contacter votre administrateur pour le déverrouiller."));
        }
        if (statut == StatutCompte.EN_ATTENTE) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new MessageReponse(
                        "Votre compte est en attente de validation. Vous recevrez un email de confirmation."));
        }

        // 4. Mode Trial — vérifier et incrémenter le compteur
        Boolean modeTrial = Boolean.TRUE.equals(utilisateur.getModeTrial());
        Integer nbMax    = utilisateur.getNbUtilisationsMax()  != null ? utilisateur.getNbUtilisationsMax()  : 30;
        Integer nbActuel = utilisateur.getNbUtilisations()     != null ? utilisateur.getNbUtilisations()     : 0;
        Integer utilisationsRestantes = null;

        if (modeTrial) {
            if (nbActuel >= nbMax) {
                return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                        .body(new MessageReponse(
                            "Votre période d'essai est terminée (" + nbMax + "/" + nbMax +
                            " utilisations). Veuillez activer votre abonnement."));
            }
            utilisateur.setNbUtilisations(nbActuel + 1);
            utilisationsRestantes = nbMax - (nbActuel + 1);
        }

        // 5. Générer le nouveau JWT (access token 15 min)
        String jwt = jwtUtils.generateJwtToken(authentication);

        // 6. Session unique : stocker le JWT (l'ancien est automatiquement invalidé)
        utilisateur.setTokenSession(jwt);

        // 7. Initialiser trialExpiresAt si premier login trial
        if (modeTrial && utilisateur.getTrialExpiresAt() == null) {
            utilisateur.setTrialExpiresAt(LocalDateTime.now().plusDays(30));
        }

        // 8. Sauvegarder (compteur + session)
        // ═══════════════════════════════════════════════════════════════
        // SÉCURITÉ MULTI-TENANT : S'assurer que le contexte tenant est
        // correctement positionné avant le save JPA.
        // Si l'utilisateur est un admin tenant et que le TenantContextHolder
        // a été effacé entre l'auth et ce point → le save JPA écrirait en
        // base MASTER (corruption du rôle).
        // On restaure systématiquement le contexte avant de sauvegarder.
        // ═══════════════════════════════════════════════════════════════
        if (utilisateur.getEntrepriseSchema() != null && !utilisateur.getEntrepriseSchema().isBlank()) {
            // Admin tenant : s'assurer que le TenantContextHolder pointe bien vers le bon tenant
            String tenantActuel = com.benjeddou.erp.config.TenantContextHolder.getCurrentTenant();
            if (tenantActuel == null || !tenantActuel.equals(utilisateur.getEntrepriseSchema())) {
                log.warn("[LOGIN-SAVE] TenantContext manquant ou incorrect (attendu={}, actuel={}) — restauration",
                         utilisateur.getEntrepriseSchema(), tenantActuel);
                com.benjeddou.erp.config.TenantContextHolder.setCurrentTenant(utilisateur.getEntrepriseSchema());
            }
        }
        utilisateurRepository.save(utilisateur);

        // Reset du compteur rate limiting (login réussi)
        auditService.resetCompteurIp(ip);

        // 9. Créer le refresh token (7 jours)
        // Encapsulé : une erreur DB (table manquante, DataSource non initialisé pour un nouveau
        // tenant) ne doit JAMAIS bloquer le login — l'utilisateur a déjà été authentifié.
        RefreshToken refreshToken = null;
        try {
            refreshToken = refreshTokenService.creerRefreshToken(utilisateur.getId());
        } catch (Exception e) {
            log.error("[LOGIN] Échec création refresh token pour '{}' (tenant={}): {} — {}",
                    utilisateur.getNomUtilisateur(),
                    TenantContextHolder.getCurrentTenant(),
                    e.getClass().getSimpleName(), e.getMessage());
            // Le login continue sans refresh token (l'accès token reste valide 15 min)
        }

        // 10. Session unique + enregistrement de la connexion avec infos appareil
        // Encapsulé : une erreur DB dans connexions_log ne doit pas bloquer le login.
        try {
            sessionService.ouvrirSession(
                    utilisateur,
                    jwt,
                    ip,
                    ua,
                    connexionRequest.getTypeAppareil(),
                    connexionRequest.getOs(),
                    connexionRequest.getNavigateur(),
                    connexionRequest.getResolution(),
                    connexionRequest.getLangue(),
                    connexionRequest.getFuseauHoraire(),
                    connexionRequest.getDeviceFingerprint(),
                    connexionRequest.getTypeReseau()
            );
        } catch (Exception e) {
            log.error("[LOGIN] Échec ouverture session pour '{}' (tenant={}): {} — {}",
                    utilisateur.getNomUtilisateur(),
                    TenantContextHolder.getCurrentTenant(),
                    e.getClass().getSimpleName(), e.getMessage());
            // Le login continue — la session est non tracée mais l'utilisateur est connecté
        }

        // 11. Construire la réponse
        List<String> roles = userDetails.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .collect(Collectors.toList());

        // Calculer les jours restants du trial
        Long joursTrialRestants = null;
        if (modeTrial && utilisateur.getTrialExpiresAt() != null) {
            joursTrialRestants = java.time.temporal.ChronoUnit.DAYS.between(
                LocalDateTime.now(), utilisateur.getTrialExpiresAt());
            if (joursTrialRestants < 0) joursTrialRestants = 0L;
        }

        JwtReponse reponse = new JwtReponse(
                jwt,
                userDetails.getId(),
                userDetails.getUsername(),
                userDetails.getEmail(),
                userDetails.getPrenom(),
                userDetails.getNom(),
                userDetails.getLanguePreferee(),
                roles,
                statut.name(),
                modeTrial,
                utilisationsRestantes,
                Boolean.TRUE.equals(utilisateur.getDoitChangerMotDePasse()));

        // Ajouter le refresh token et les infos trial à la réponse
        // refreshToken peut être null si la création a échoué (nouveau tenant, table manquante…)
        reponse.setRefreshToken(refreshToken != null ? refreshToken.getToken() : null);
        reponse.setExpiresIn(900);
        reponse.setTrialExpiresAt(utilisateur.getTrialExpiresAt() != null
                ? utilisateur.getTrialExpiresAt().toString() : null);
        reponse.setJoursTrialRestants(joursTrialRestants);

        // Audit log — login réussi
        auditService.log(ActionAudit.LOGIN_SUCCESS, ResultatAudit.SUCCES,
            "Connexion réussie" + (modeTrial ? " [TRIAL " + (nbActuel + 1) + "/" + nbMax + "]" : ""),
            utilisateur.getId(), utilisateur.getNomUtilisateur(), ip, ua, "AUTH", null);

        return ResponseEntity.ok(reponse);
    }

    // ═══════════════════════════════════════════════════════════
    // J3 — POST /api/auth/refresh
    // ═══════════════════════════════════════════════════════════
    @PostMapping("/refresh")
    public ResponseEntity<?> rafraichirToken(@RequestBody Map<String, String> body) {
        String refreshTokenStr = body.get("refreshToken");
        if (refreshTokenStr == null || refreshTokenStr.isBlank()) {
            return ResponseEntity.badRequest().body(new MessageReponse("Refresh token manquant."));
        }

        Optional<RefreshToken> tokenOpt = refreshTokenService.trouverParToken(refreshTokenStr);
        if (tokenOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new MessageReponse("Refresh token invalide ou introuvable."));
        }

        RefreshToken refreshToken;
        try {
            refreshToken = refreshTokenService.verifierValidite(tokenOpt.get());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new MessageReponse(e.getMessage()));
        }

        String newAccessToken = jwtUtils.generateJwtTokenFromUsername(
                refreshToken.getUtilisateur().getNomUtilisateur());

        // Mettre à jour le tokenSession en DB
        // SÉCURITÉ MULTI-TENANT : Restaurer le contexte tenant avant le save
        // pour éviter d'écraser un enregistrement tenant en base master.
        refreshToken.getUtilisateur().setTokenSession(newAccessToken);
        Utilisateur userARefraichir = refreshToken.getUtilisateur();
        if (userARefraichir.getEntrepriseSchema() != null && !userARefraichir.getEntrepriseSchema().isBlank()) {
            String tenantActuel = com.benjeddou.erp.config.TenantContextHolder.getCurrentTenant();
            if (tenantActuel == null || !tenantActuel.equals(userARefraichir.getEntrepriseSchema())) {
                com.benjeddou.erp.config.TenantContextHolder.setCurrentTenant(userARefraichir.getEntrepriseSchema());
            }
        }
        utilisateurRepository.save(userARefraichir);

        Map<String, Object> resp = new HashMap<>();
        resp.put("accessToken", newAccessToken);
        resp.put("refreshToken", refreshTokenStr);
        resp.put("expiresIn", 900);
        return ResponseEntity.ok(resp);
    }

    // ═══════════════════════════════════════════════════════════
    // J3 — POST /api/auth/logout
    // ═══════════════════════════════════════════════════════════
    @PostMapping("/logout")
    public ResponseEntity<?> deconnecter(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String jwt = authHeader.substring(7);
                String username = jwtUtils.getUserNameFromJwtToken(jwt);
                utilisateurRepository.findByNomUtilisateur(username).ifPresent(user -> {
                    user.setTokenSession(null);
                    utilisateurRepository.save(user);
                    refreshTokenService.revoquerTousLesTokens(user);
                });
            }
        } catch (Exception e) {
            // Logout silencieux même si le token est invalide
        }
        return ResponseEntity.ok(new MessageReponse("Déconnexion effectuée."));
    }

    @PostMapping("/register")
    public ResponseEntity<?> enregistrerUtilisateur(@Valid @RequestBody InscriptionRequest inscriptionRequest) {
        if (utilisateurRepository.existsByNomUtilisateur(inscriptionRequest.getNomUtilisateur())) {
            return ResponseEntity.badRequest()
                    .body(new MessageReponse("Erreur: Le nom d'utilisateur est déjà utilisé !"));
        }
        if (utilisateurRepository.existsByEmail(inscriptionRequest.getEmail())) {
            return ResponseEntity.badRequest()
                    .body(new MessageReponse("Erreur: L'email est déjà utilisé !"));
        }

        boolean modeTrial = Boolean.TRUE.equals(inscriptionRequest.getModeTrial());

        // Création du compte
        Utilisateur utilisateur = Utilisateur.builder()
                .nomUtilisateur(inscriptionRequest.getNomUtilisateur())
                .email(inscriptionRequest.getEmail())
                .motDePasse(encoder.encode(inscriptionRequest.getMotDePasse()))
                .prenom(inscriptionRequest.getPrenom())
                .nom(inscriptionRequest.getNom())
                .telephone(inscriptionRequest.getTelephone())
                .societe(inscriptionRequest.getSociete())
                .languePreferee("fr")
                .actif(true)
                .modeTrial(modeTrial)
                .nbUtilisations(0)
                .nbUtilisationsMax(30)
                .trialExpiresAt(modeTrial ? LocalDateTime.now().plusDays(30) : null)
                // En trial : le compte est directement ACTIF (accès immédiat limité à 30 connexions)
                // Sans trial : EN_ATTENTE de validation KYC puis abonnement
                .statutCompte(modeTrial ? StatutCompte.ACTIF : StatutCompte.EN_ATTENTE)
                .build();

        Set<String> strRoles = inscriptionRequest.getRoles();
        Role userRole = Role.CLIENT; // Par défaut CLIENT pour les inscriptions publiques

        if (strRoles != null && !strRoles.isEmpty()) {
            String roleStr = strRoles.iterator().next().toLowerCase();
            switch (roleStr) {
                case "admin"      -> userRole = Role.ADMIN;
                case "commercial" -> userRole = Role.COMMERCIAL;
                case "comptable"  -> userRole = Role.COMPTABLE;
                case "stock"      -> userRole = Role.STOCK;
                case "client"     -> userRole = Role.CLIENT;
                default           -> userRole = Role.CLIENT;
            }
        }

        utilisateur.setRole(userRole);
        utilisateurRepository.save(utilisateur);

        String message = modeTrial
            ? "Compte créé ! Vous bénéficiez de 30 connexions d'essai gratuites. Bonne découverte !"
            : "Compte créé ! Votre dossier est en attente de validation. Vous recevrez un email de confirmation.";

        return ResponseEntity.ok(new MessageReponse(message));
    }

    // ══════════════════════════════════════════════════════════════════════
    // POST /api/auth/forgot-password?email=xxx
    // Génère un token UUID, le sauvegarde en base (master ou tenant),
    // et envoie le lien par email.
    // Compatible avec TOUS les types de comptes (SuperAdmin, Admin tenant).
    // ══════════════════════════════════════════════════════════════════════
    @PostMapping("/forgot-password")
    public ResponseEntity<?> motDePasseOublie(@RequestParam String email) {

        // ── 1. Chercher l'utilisateur : master d'abord, puis tous les tenants ──
        java.util.Optional<Utilisateur> userOpt = utilisateurRepository.findByEmail(email);
        String tenantSchemaFound = null;

        if (userOpt.isEmpty()) {
            java.util.List<com.benjeddou.erp.model.Entreprise> entreprises =
                entrepriseRepository.findByStatut(com.benjeddou.erp.model.Entreprise.StatutEntreprise.ACTIVE);
            for (com.benjeddou.erp.model.Entreprise ent : entreprises) {
                if (ent.getDbUrl() == null || ent.getDbUrl().isBlank()) continue;
                try (Connection conn = connecterTenant(ent);
                     PreparedStatement ps = conn.prepareStatement(
                         "SELECT id, nom_utilisateur, email, prenom, nom "
                       + "FROM utilisateurs WHERE email = ? AND actif = TRUE LIMIT 1")) {
                    ps.setString(1, email);
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            Utilisateur u = new Utilisateur();
                            u.setId(rs.getLong("id"));
                            u.setNomUtilisateur(rs.getString("nom_utilisateur"));
                            u.setEmail(rs.getString("email"));
                            u.setPrenom(rs.getString("prenom"));
                            u.setNom(rs.getString("nom"));
                            u.setEntrepriseSchema(ent.getSchemaName());
                            userOpt = java.util.Optional.of(u);
                            tenantSchemaFound = ent.getSchemaName();
                            log.info("[FORGOT-PWD] Utilisateur '{}' trouvé dans tenant '{}'",
                                     u.getNomUtilisateur(), ent.getSchemaName());
                            break;
                        }
                    }
                } catch (Exception ex) {
                    log.warn("[FORGOT-PWD] Impossible de lire le tenant '{}': {}", ent.getSchemaName(), ex.getMessage());
                }
            }
        }

        // ── 2. Générer un vrai token UUID (jamais de mock) ──────────────────
        String prenom = userOpt.map(Utilisateur::getPrenom)
                               .filter(p -> p != null && !p.isBlank())
                               .orElse("Utilisateur");
        String token = java.util.UUID.randomUUID().toString();
        java.time.LocalDateTime expiration = java.time.LocalDateTime.now().plusHours(2);

        if (userOpt.isPresent()) {
            Utilisateur found = userOpt.get();
            if (tenantSchemaFound == null) {
                // SuperAdmin ou utilisateur master → JPA
                found.setTokenRecuperation(token);
                found.setExpirationTokenRecuperation(expiration);
                utilisateurRepository.save(found);
                log.info("[FORGOT-PWD] Token sauvegardé en master pour '{}'", found.getNomUtilisateur());
            } else {
                // Admin tenant → JDBC avec fallback root
                final String schema = tenantSchemaFound;
                final String emailFinal = email;
                final String tokenFinal = token;
                final java.time.LocalDateTime expFinal = expiration;
                entrepriseRepository.findBySchemaName(schema).ifPresent(ent -> {
                    try (Connection conn = connecterTenant(ent);
                         PreparedStatement ps = conn.prepareStatement(
                             "UPDATE utilisateurs "
                           + "SET token_recuperation = ?, expiration_token_recuperation = ? "
                           + "WHERE email = ?")) {
                        ps.setString(1, tokenFinal);
                        ps.setTimestamp(2, java.sql.Timestamp.valueOf(expFinal));
                        ps.setString(3, emailFinal);
                        int rows = ps.executeUpdate();
                        log.info("[FORGOT-PWD] Token sauvegardé dans tenant '{}' ({} ligne(s))", schema, rows);
                    } catch (Exception ex) {
                        log.error("[FORGOT-PWD] Échec sauvegarde token dans '{}': {}", schema, ex.getMessage());
                    }
                });
            }
        } else {
            // Email inconnu — réponse générique pour ne pas révéler l'existence du compte
            log.warn("[FORGOT-PWD] Email '{}' introuvable dans master et tous les tenants", email);
        }

        // ── 3. Envoyer l'email ───────────────────────────────────────────────
        String lien = frontendUrl + "/reset-password?token=" + token;
        try {
            org.springframework.mail.SimpleMailMessage msg = new org.springframework.mail.SimpleMailMessage();
            msg.setFrom(mailFrom);
            msg.setTo(email);
            msg.setSubject("Réinitialisation de votre mot de passe - BENJEDDOU ERP");
            msg.setText(
                "Bonjour " + prenom + ",\n\n"
              + "Vous avez demandé la réinitialisation de votre mot de passe pour la plateforme BENJEDDOU ERP.\n"
              + "Cliquez sur le lien ci-dessous pour créer un nouveau mot de passe (valable 2 heures) :\n\n"
              + lien + "\n\n"
              + "Si vous n'êtes pas à l'origine de cette demande, ignorez cet email.\n\n"
              + "Cordialement,\nL'équipe BENJEDDOU ERP"
            );
            mailSender.send(msg);
            log.info("[FORGOT-PWD] Email envoyé à '{}'", email);
        } catch (Exception e) {
            // Mode dev : SMTP non configuré → lien dans la console
            log.warn("[FORGOT-PWD] Envoi email impossible ({}): affichage console dev", e.getMessage());
            System.err.println("\n=================================================");
            System.err.println("=== FORGOT-PASSWORD : MODE DEV (SMTP OFF)    ===");
            System.err.println("  Email      : " + email);
            System.err.println("  Token UUID : " + token);
            System.err.println("  Lien reset : " + lien);
            System.err.println("=================================================\n");
        }
        // Réponse générique identique qu'on ait trouvé l'email ou non (sécurité)
        return ResponseEntity.ok(new MessageReponse(
            "Si un compte correspond à cet email, un lien de réinitialisation a été envoyé."
        ));
    }

    // ══════════════════════════════════════════════════════════════════════
    // POST /api/auth/reset-password?token=xxx&motDePasse=yyy
    // Valide le token, met à jour le mot de passe dans la bonne base
    // (master JPA ou tenant JDBC avec fallback root).
    // ══════════════════════════════════════════════════════════════════════
    @PostMapping("/reset-password")
    public ResponseEntity<?> reinitialiserMotDePasse(
            @RequestParam String token,
            @RequestParam String motDePasse) {

        // ── 1. Chercher le token : master d'abord, puis tous les tenants ────
        java.util.Optional<Utilisateur> userOpt = utilisateurRepository.findByTokenRecuperation(token);
        String tenantSchemaFound = null;

        if (userOpt.isEmpty()) {
            java.util.List<com.benjeddou.erp.model.Entreprise> entreprises =
                entrepriseRepository.findByStatut(com.benjeddou.erp.model.Entreprise.StatutEntreprise.ACTIVE);
            for (com.benjeddou.erp.model.Entreprise ent : entreprises) {
                if (ent.getDbUrl() == null || ent.getDbUrl().isBlank()) continue;
                try (Connection conn = connecterTenant(ent);
                     PreparedStatement ps = conn.prepareStatement(
                         "SELECT id, nom_utilisateur, email, prenom, expiration_token_recuperation "
                       + "FROM utilisateurs WHERE token_recuperation = ? LIMIT 1")) {
                    ps.setString(1, token);
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            Utilisateur u = new Utilisateur();
                            u.setId(rs.getLong("id"));
                            u.setNomUtilisateur(rs.getString("nom_utilisateur"));
                            u.setEmail(rs.getString("email"));
                            u.setPrenom(rs.getString("prenom"));
                            java.sql.Timestamp exp = rs.getTimestamp("expiration_token_recuperation");
                            u.setExpirationTokenRecuperation(exp != null ? exp.toLocalDateTime() : null);
                            u.setEntrepriseSchema(ent.getSchemaName());
                            userOpt = java.util.Optional.of(u);
                            tenantSchemaFound = ent.getSchemaName();
                            log.info("[RESET-PWD] Token trouvé dans tenant '{}' pour '{}'",
                                     ent.getSchemaName(), u.getNomUtilisateur());
                            break;
                        }
                    }
                } catch (Exception ex) {
                    log.warn("[RESET-PWD] Impossible de lire token dans tenant '{}': {}", ent.getSchemaName(), ex.getMessage());
                }
            }
        }

        // ── 2. Valider le token ─────────────────────────────────────────────
        if (userOpt.isEmpty()
                || userOpt.get().getExpirationTokenRecuperation() == null
                || userOpt.get().getExpirationTokenRecuperation().isBefore(java.time.LocalDateTime.now())) {
            log.warn("[RESET-PWD] Token invalide ou expiré : '{}'", token);
            return ResponseEntity.badRequest().body(
                new MessageReponse("Le lien de réinitialisation est invalide ou a expiré. Veuillez faire une nouvelle demande."));
        }

        // ── 3. Changer le mot de passe dans la bonne base ───────────────────
        Utilisateur user = userOpt.get();
        String nouveauHash = encoder.encode(motDePasse);

        if (tenantSchemaFound == null) {
            // SuperAdmin / utilisateur master → JPA + sync tenant
            user.setMotDePasse(nouveauHash);
            user.setTokenRecuperation(null);
            user.setExpirationTokenRecuperation(null);
            utilisateurRepository.save(user);
            syncMotDePasseTenant(user, nouveauHash);
            log.info("[RESET-PWD] Mot de passe mis à jour en master pour '{}'", user.getNomUtilisateur());
        } else {
            // Admin tenant → JDBC avec fallback root
            final String schema    = tenantSchemaFound;
            final String emailUser = user.getEmail();
            entrepriseRepository.findBySchemaName(schema).ifPresent(ent -> {
                try (Connection conn = connecterTenant(ent);
                     PreparedStatement ps = conn.prepareStatement(
                         "UPDATE utilisateurs "
                       + "SET mot_de_passe = ?, token_recuperation = NULL, expiration_token_recuperation = NULL "
                       + "WHERE email = ?")) {
                    ps.setString(1, nouveauHash);
                    ps.setString(2, emailUser);
                    int rows = ps.executeUpdate();
                    log.info("[RESET-PWD] Mot de passe mis à jour dans tenant '{}' ({} ligne(s))", schema, rows);
                } catch (Exception ex) {
                    log.error("[RESET-PWD] Échec mise à jour mot de passe dans '{}': {}", schema, ex.getMessage());
                }
            });
        }

        return ResponseEntity.ok(new MessageReponse("Votre mot de passe a été modifié avec succès !"));
    }

    // ── Changement obligatoire du mot de passe (1ère connexion) ──
    @PutMapping("/changer-mot-de-passe")
    public ResponseEntity<?> changerMotDePasse(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam String ancienMotDePasse,
            @RequestParam String nouveauMotDePasse) {

        // Extraire le JWT et retrouver l'utilisateur
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(new MessageReponse("Token manquant."));
        }
        String jwt = authHeader.substring(7);
        String nomUtilisateur;
        try {
            nomUtilisateur = jwtUtils.getUserNameFromJwtToken(jwt);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(new MessageReponse("Token invalide."));
        }

        Optional<Utilisateur> userOpt = utilisateurRepository.findByNomUtilisateur(nomUtilisateur);
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Utilisateur user = userOpt.get();

        // Vérifier l'ancien mot de passe
        if (!encoder.matches(ancienMotDePasse, user.getMotDePasse())) {
            return ResponseEntity.badRequest()
                .body(new MessageReponse("L'ancien mot de passe est incorrect."));
        }

        if (nouveauMotDePasse.length() < 6) {
            return ResponseEntity.badRequest()
                .body(new MessageReponse("Le nouveau mot de passe doit contenir au moins 6 caractères."));
        }

        String nouveauHash = encoder.encode(nouveauMotDePasse);
        user.setMotDePasse(nouveauHash);
        user.setDoitChangerMotDePasse(false);
        utilisateurRepository.save(user);
        // Propager le nouveau hash dans la base tenant
        syncMotDePasseTenant(user, nouveauHash);

        return ResponseEntity.ok(new MessageReponse("Mot de passe changé avec succès. Bienvenue sur BENJEDDOU ERP !"));
    }

    // ═══════════════════════════════════════════════════════════
    // INIT SUPERADMIN — Endpoint unique pour créer le compte
    // ⚠️  DÉVELOPPEMENT UNIQUEMENT — appeler une seule fois
    // POST /api/auth/init-superadmin?motDePasse=VotreMotDePasse
    // ═══════════════════════════════════════════════════════════
    @GetMapping("/init-superadmin")
    public ResponseEntity<?> initSuperAdmin(
            @RequestParam(defaultValue = "Superadmin@2026!") String motDePasse) {

        // Si le superadmin existe déjà → mettre à jour son mot de passe et son rôle
        Optional<Utilisateur> existing = utilisateurRepository.findByNomUtilisateur("superadmin");
        if (existing.isPresent()) {
            Utilisateur sa = existing.get();
            sa.setMotDePasse(encoder.encode(motDePasse));
            sa.setRole(Role.SUPERADMIN);
            sa.setStatutCompte(StatutCompte.ACTIF);
            sa.setActif(true);
            sa.setEntrepriseId(null);
            sa.setEntrepriseSchema(null);
            utilisateurRepository.save(sa);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message",  "✅ Compte superadmin MIS À JOUR !",
                "login",    "superadmin",
                "password", motDePasse,
                "role",     "SUPERADMIN"
            ));
        }

        // Sinon le créer
        Utilisateur superAdmin = Utilisateur.builder()
            .nomUtilisateur("superadmin")
            .email("superadmin@benjeddou.com")
            .motDePasse(encoder.encode(motDePasse))
            .prenom("Super")
            .nom("Admin")
            .role(Role.SUPERADMIN)
            .statutCompte(StatutCompte.ACTIF)
            .actif(true)
            .modeTrial(false)
            .doitChangerMotDePasse(false)
            .build();
        utilisateurRepository.save(superAdmin);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message",  "✅ Compte superadmin CRÉÉ avec succès !",
            "login",    "superadmin",
            "password", motDePasse,
            "role",     "SUPERADMIN"
        ));
    }

    // ══════════════════════════════════════════════════════════════
    // GET /api/auth/sync-credentials-all
    // ─────────────────────────────────────────────────────────────
    // OUTIL DE RÉPARATION DÉFINITIF — À appeler quand la plateforme
    // nécessite un réimport manuel des bases de données.
    //
    // Ce endpoint synchronise les hash BCrypt depuis la base MASTER
    // vers TOUTES les bases tenant pour TOUS les utilisateurs.
    //
    // Résultat : authentification fonctionnelle sans aucune suppression
    // ni réimportation de base de données.
    //
    // Appel : GET http://localhost:9090/api/auth/sync-credentials-all
    // ══════════════════════════════════════════════════════════════
    @GetMapping("/sync-credentials-all")
    public ResponseEntity<?> syncCredentialsAll() {
        Map<String, Object> rapport = new HashMap<>();
        int totalUtilisateurs = 0;
        int totalSyncs = 0;
        int totalErreurs = 0;
        StringBuilder details = new StringBuilder();

        try {
            // Récupérer tous les utilisateurs de la base master
            java.util.List<Utilisateur> tousMaster = utilisateurRepository.findAll();

            // Récupérer toutes les entreprises actives
            java.util.List<com.benjeddou.erp.model.Entreprise> entreprises =
                entrepriseRepository.findByStatut(com.benjeddou.erp.model.Entreprise.StatutEntreprise.ACTIVE);

            for (com.benjeddou.erp.model.Entreprise ent : entreprises) {
                String schema = ent.getSchemaName();
                String url    = ent.getDbUrl();
                String user   = ent.getDbUsername();
                String pass   = ent.getDbPassword() != null ? ent.getDbPassword() : "";

                if (url == null || url.isBlank()) continue;

                int syncsDansCetteBase = 0;
                for (Utilisateur masterUser : tousMaster) {
                    if (masterUser.getMotDePasse() == null) continue;
                    totalUtilisateurs++;
                    try {
                        // Vérifier si l'utilisateur existe dans cette base tenant
                        String sqlCheck = "SELECT mot_de_passe FROM utilisateurs WHERE nom_utilisateur = ? OR email = ? LIMIT 1";
                        try (Connection conn = java.sql.DriverManager.getConnection(url, user, pass);
                             java.sql.PreparedStatement psCheck = conn.prepareStatement(sqlCheck)) {
                            psCheck.setString(1, masterUser.getNomUtilisateur());
                            psCheck.setString(2, masterUser.getEmail());
                            try (java.sql.ResultSet rs = psCheck.executeQuery()) {
                                if (rs.next()) {
                                    String hashTenant = rs.getString("mot_de_passe");
                                    if (!masterUser.getMotDePasse().equals(hashTenant)) {
                                        // Hash désynchronisé → corriger
                                        String sqlUpdate = "UPDATE utilisateurs SET mot_de_passe = ?, actif = TRUE, statut_compte = 'ACTIF' WHERE nom_utilisateur = ? OR email = ?";
                                        try (java.sql.PreparedStatement psUpd = conn.prepareStatement(sqlUpdate)) {
                                            psUpd.setString(1, masterUser.getMotDePasse());
                                            psUpd.setString(2, masterUser.getNomUtilisateur());
                                            psUpd.setString(3, masterUser.getEmail());
                                            psUpd.executeUpdate();
                                            syncsDansCetteBase++;
                                            totalSyncs++;
                                            details.append("[SYNC] ").append(masterUser.getNomUtilisateur())
                                                   .append(" → ").append(schema).append("\n");
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception ex) {
                        totalErreurs++;
                        log.warn("[SYNC-ALL] Erreur pour '{}' dans '{}': {}", masterUser.getNomUtilisateur(), schema, ex.getMessage());
                    }
                }
                if (syncsDansCetteBase > 0) {
                    log.info("[SYNC-ALL] {} hash(es) synchronisé(s) dans '{}'", syncsDansCetteBase, schema);
                }
            }

            rapport.put("succes", true);
            rapport.put("message", "Synchronisation terminée. " + totalSyncs + " hash(es) resynchronisé(s) sur " + totalUtilisateurs + " vérifications.");
            rapport.put("totalVerifications", totalUtilisateurs);
            rapport.put("totalSynchronisations", totalSyncs);
            rapport.put("totalErreurs", totalErreurs);
            rapport.put("details", details.toString());
            log.info("[SYNC-ALL] Terminé : {} sync, {} erreurs sur {} utilisateurs vérifiés", totalSyncs, totalErreurs, totalUtilisateurs);

        } catch (Exception ex) {
            rapport.put("succes", false);
            rapport.put("message", "Erreur lors de la synchronisation : " + ex.getMessage());
            log.error("[SYNC-ALL] Erreur fatale : {}", ex.getMessage());
        }

        return ResponseEntity.ok(rapport);
    }

    // ══════════════════════════════════════════════════════════════
    // POST /api/auth/signaler-connexion — Signaler une connexion inconnue
    // Accessible sans authentification (lien dans l'email)
    // ══════════════════════════════════════════════════════════════
    @PostMapping("/signaler-connexion")
    public ResponseEntity<?> signalerConnexionInconnue(@RequestParam String token) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "succes", false,
                "message", "Token manquant ou invalide."
            ));
        }
        String resultat = sessionService.signalerConnexion(token);
        return switch (resultat) {
            case "OK" -> ResponseEntity.ok(Map.of(
                "succes", true,
                "message", "✅ Connexion suspecte signalée avec succès. Notre équipe de sécurité a été alertée."
            ));
            case "DEJA_SIGNALE" -> ResponseEntity.ok(Map.of(
                "succes", true,
                "message", "ℹ️ Cette connexion a déjà été signalée. Notre équipe traitera votre demande."
            ));
            default -> ResponseEntity.status(404).body(Map.of(
                "succes", false,
                "message", "❌ Lien de signalement invalide ou expiré."
            ));
        };
    }

    // ══════════════════════════════════════════════════════════════
    // GET /api/auth/test-lien-signalement?username=xxx
    // ⚠️  ENDPOINT DE TEST UNIQUEMENT — Retourne le lien signalement
    // sans avoir besoin d'aller dans la base de données
    // ══════════════════════════════════════════════════════════════
    @GetMapping("/test-lien-signalement")
    public ResponseEntity<?> getLienSignalement(@RequestParam String username) {
        return utilisateurRepository.findByNomUtilisateur(username)
            .map(user -> {
                var sessions = connexionLogRepository
                    .findByUtilisateurOrderByDateConnexionDesc(user);
                if (sessions.isEmpty()) {
                    return ResponseEntity.ok(Map.of(
                        "message", "Aucune session trouvée pour cet utilisateur.",
                        "conseil", "Faites d'abord une double connexion pour créer une session."
                    ));
                }
                // Prendre la session la plus récente
                var session = sessions.get(0);
                String token = session.getSignalementToken();
                String lien  = "http://localhost:4200/signaler-connexion?token=" + token;
                return ResponseEntity.ok(Map.of(
                    "username",       username,
                    "session_id",     session.getId(),
                    "statut",         session.getStatut().name(),
                    "date_connexion", session.getDateConnexion() != null
                                          ? session.getDateConnexion().toString() : "?",
                    "ip",             session.getAdresseIp() != null ? session.getAdresseIp() : "?",
                    "token",          token,
                    "lien_complet",   lien,
                    "instruction",    "Copiez le lien_complet dans votre navigateur pour tester le signalement."
                ));
            })
            .orElseGet(() -> ResponseEntity.status(404).body(Map.of(
                "message", "Utilisateur '" + username + "' introuvable."
            )));
    }

    // ══════════════════════════════════════════════════════════════════════
    // UTILITAIRE : Connexion JDBC à une base tenant avec fallback root
    // ──────────────────────────────────────────────────────────────────────
    // Stratégie identique à UserDetailsServiceImpl :
    //  1. Tenter avec les credentials dédiés (ent.getDbUsername/Password)
    //  2. Si échec → tenter avec le user/pass root de la base master
    // Cela garantit que les opérations fonctionnent même si le user dédié
    // n'a pas encore été créé ou si ses credentials sont incorrects.
    // ══════════════════════════════════════════════════════════════════════
    private Connection connecterTenant(com.benjeddou.erp.model.Entreprise ent) throws Exception {
        String url        = ent.getDbUrl();
        String dedUser    = ent.getDbUsername();
        String dedPass    = ent.getDbPassword() != null ? ent.getDbPassword() : "";
        // Essai 1 : credentials dédiés
        if (dedUser != null && !dedUser.isBlank()) {
            try {
                return DriverManager.getConnection(url, dedUser, dedPass);
            } catch (Exception ex) {
                log.debug("[TENANT-CONN] User dédié '{}' refusé pour '{}', tentative root : {}",
                          dedUser, ent.getSchemaName(), ex.getMessage());
            }
        }
        // Essai 2 : fallback root (master credentials)
        return DriverManager.getConnection(url, masterDbUser, masterDbPass);
    }

    /**
     * Propage le nouveau hash BCrypt dans la base MySQL du tenant de l'utilisateur.
     * Appelé systématiquement après tout changement de mot de passe (reset ou volontaire).
     *
     * Sans cette synchronisation, l'authentification multi-tenant échoue car :
     * - La base Master (benjeddou_erp) contient le nouveau hash BCrypt
     * - La base Tenant (erp_ent_XXXXX) contient encore l'ancien hash
     * - Spring Security lit le hash depuis la base Tenant → BAD_CREDENTIALS
     */
    private void syncMotDePasseTenant(Utilisateur user, String nouveauHash) {
        if (user.getEntrepriseSchema() == null || user.getEntrepriseSchema().isBlank()) {
            return; // SuperAdmin → base master uniquement, pas de tenant
        }
        entrepriseRepository.findBySchemaName(user.getEntrepriseSchema()).ifPresent(ent -> {
            String tenantUrl  = ent.getDbUrl();
            String tenantUser = ent.getDbUsername();
            String tenantPass = ent.getDbPassword() != null ? ent.getDbPassword() : "";
            if (tenantUrl == null || tenantUrl.isBlank()) return;
            try {
                String sql = "UPDATE utilisateurs SET mot_de_passe = ? WHERE nom_utilisateur = ? OR email = ?";
                try (Connection conn = DriverManager.getConnection(tenantUrl, tenantUser, tenantPass);
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, nouveauHash);
                    ps.setString(2, user.getNomUtilisateur());
                    ps.setString(3, user.getEmail());
                    int rows = ps.executeUpdate();
                    log.info("✓ Hash mot de passe synchronisé dans tenant '{}' pour '{}' ({} ligne(s))",
                        user.getEntrepriseSchema(), user.getNomUtilisateur(), rows);
                }
            } catch (Exception ex) {
                log.warn("⚠️  Sync mot de passe tenant ignorée pour '{}' : {}",
                    user.getNomUtilisateur(), ex.getMessage());
            }
        });
    }
}

