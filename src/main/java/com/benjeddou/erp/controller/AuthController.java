package com.benjeddou.erp.controller;

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
            // Enregistrer l'échec et incrémenter le compteur
            long secondesRestantes = auditService.enregistrerEchecEtVerifier(ip);
            int restantes = auditService.tentativesRestantes(ip);
            auditService.log(ActionAudit.LOGIN_ECHEC, ResultatAudit.ECHEC,
                "Identifiants incorrects pour: " + username + " (restantes: " + restantes + ")",
                null, username, ip, ua, "AUTH", null);
            Map<String, Object> erreur = new HashMap<>();
            if (secondesRestantes > 0) {
                erreur.put("message", "Compte temporairement bloqué. Réessayez dans " + (secondesRestantes / 60 + 1) + " minutes.");
                erreur.put("code", "RATE_LIMITED");
            } else {
                erreur.put("message", "Identifiants incorrects. Tentatives restantes : " + restantes + "/5");
                erreur.put("code", "BAD_CREDENTIALS");
                erreur.put("tentativesRestantes", restantes);

                // ── CAPTCHA requis dès 2 échecs (restantes <= 3 sur 5 max) ──
                // Le backend génère un nouveau CAPTCHA et le joint à la réponse.
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
        Optional<Utilisateur> userOpt = utilisateurRepository
                .findByNomUtilisateur(userDetails.getUsername());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new MessageReponse("Utilisateur introuvable."));
        }
        Utilisateur utilisateur = userOpt.get();

        // ── Auto-upgrade vers BCrypt (coût 12) pour une sécurité maximale ──
        if (utilisateur.getMotDePasse() != null && (!utilisateur.getMotDePasse().startsWith("$2a$12$") && !utilisateur.getMotDePasse().startsWith("$2b$12$"))) {
            try {
                utilisateur.setMotDePasse(encoder.encode(connexionRequest.getMotDePasse()));
                utilisateurRepository.save(utilisateur);
                log.info("🔒 Mot de passe mis à jour vers BCrypt (coût 12) pour : {}", utilisateur.getNomUtilisateur());
            } catch (Exception e) {
                log.warn("Auto-upgrade mot de passe non appliqué : {}", e.getMessage());
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
        utilisateurRepository.save(utilisateur);

        // Reset du compteur rate limiting (login réussi)
        auditService.resetCompteurIp(ip);

        // 9. Créer le refresh token (7 jours)
        RefreshToken refreshToken = refreshTokenService.creerRefreshToken(utilisateur.getId());

        // 10. Session unique + enregistrement de la connexion avec infos appareil
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
                connexionRequest.getDeviceFingerprint(),  // Empreinte numérique
                connexionRequest.getTypeReseau()          // Type de réseau : Wi-Fi / 4G / Ethernet
        );

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
        reponse.setRefreshToken(refreshToken.getToken());
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
        refreshToken.getUtilisateur().setTokenSession(newAccessToken);
        utilisateurRepository.save(refreshToken.getUtilisateur());

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

    @PostMapping("/forgot-password")
    public ResponseEntity<?> motDePasseOublie(@RequestParam String email) {
        java.util.Optional<Utilisateur> userOpt = utilisateurRepository.findByEmail(email);
        String prenom = userOpt.isPresent() ? userOpt.get().getPrenom() : "Utilisateur";
        String token = "mock-token-xyz";
        
        if (userOpt.isPresent()) {
            token = java.util.UUID.randomUUID().toString();
            Utilisateur user = userOpt.get();
            user.setTokenRecuperation(token);
            user.setExpirationTokenRecuperation(java.time.LocalDateTime.now().plusHours(2));
            utilisateurRepository.save(user);
        }
        
        try {
            org.springframework.mail.SimpleMailMessage message = new org.springframework.mail.SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(email);
            message.setSubject("Réinitialisation de votre mot de passe - BENJEDDOU ERP");
            message.setText("Bonjour " + prenom + ",\n\n"
                    + "Vous avez demandé la réinitialisation de votre mot de passe pour la plateforme BENJEDDOU ERP.\n"
                    + "Veuillez utiliser le lien suivant pour créer un nouveau mot de passe :\n"
                    + "http://localhost:4200/reset-password?token=" + token + "\n\n"
                    + "Si vous n'êtes pas à l'origine de cette demande, veuillez ignorer cet e-mail.\n\n"
                    + "Cordialement,\n"
                    + "L'équipe BENJEDDOU ERP");
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("=================================================");
            System.err.println("=== MODE DEV : IMPOSSIBLE D'ENVOYER L'EMAIL ===");
            System.err.println("Destinataire: " + email);
            System.err.println("Jeton généré: " + token);
            System.err.println("Lien de réinitialisation: http://localhost:4200/reset-password?token=" + token);
            System.err.println("Détail erreur SMTP: " + e.getMessage());
            System.err.println("=================================================");
            
            // Retourner quand même un statut succès en développement pour que l'interface affiche la réussite
            return ResponseEntity.ok(new MessageReponse("Mode Dev - Email simulé. Le lien de réinitialisation est visible dans la console du serveur backend."));
        }
        return ResponseEntity.ok(new MessageReponse("L'e-mail de réinitialisation a été envoyé avec succès à " + email + "."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> reinitialiserMotDePasse(@RequestParam String token, @RequestParam String motDePasse) {
        java.util.Optional<Utilisateur> userOpt = utilisateurRepository.findByTokenRecuperation(token);
        
        if (userOpt.isEmpty() || userOpt.get().getExpirationTokenRecuperation() == null 
                || userOpt.get().getExpirationTokenRecuperation().isBefore(java.time.LocalDateTime.now())) {
            return ResponseEntity.badRequest().body(new MessageReponse("Erreur: Le jeton de réinitialisation est invalide ou a expiré !"));
        }
        
        Utilisateur user = userOpt.get();
        user.setMotDePasse(encoder.encode(motDePasse));
        user.setTokenRecuperation(null);
        user.setExpirationTokenRecuperation(null);
        utilisateurRepository.save(user);
        
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

        user.setMotDePasse(encoder.encode(nouveauMotDePasse));
        user.setDoitChangerMotDePasse(false);
        utilisateurRepository.save(user);

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
}
