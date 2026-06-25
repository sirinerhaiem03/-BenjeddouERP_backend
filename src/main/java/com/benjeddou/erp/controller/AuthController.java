package com.benjeddou.erp.controller;

import com.benjeddou.erp.model.ConnexionLog;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UtilisateurRepository utilisateurRepository;

    @Autowired
    ConnexionLogRepository connexionLogRepository;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    JwtUtils jwtUtils;

    @Autowired
    private org.springframework.mail.javamail.JavaMailSender mailSender;

    @org.springframework.beans.factory.annotation.Value("${app.mail.from:ecoressourcesb2b@gmail.com}")
    private String mailFrom;

    @PostMapping("/login")
    public ResponseEntity<?> authentifierUtilisateur(
            @Valid @RequestBody ConnexionRequest connexionRequest,
            HttpServletRequest request) {

        // 1. Authentification Spring Security
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        connexionRequest.getNomUtilisateur(),
                        connexionRequest.getMotDePasse()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        // 2. Charger l'utilisateur depuis la DB pour accéder aux nouveaux champs
        Optional<Utilisateur> userOpt = utilisateurRepository
                .findByNomUtilisateur(connexionRequest.getNomUtilisateur());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new MessageReponse("Utilisateur introuvable."));
        }
        Utilisateur utilisateur = userOpt.get();

        // 3. Vérifier le statut du compte
        StatutCompte statut = utilisateur.getStatutCompte();
        if (statut == null) statut = StatutCompte.ACTIF;

        if (statut == StatutCompte.REFUSE) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new MessageReponse(
                        "Votre compte a été refusé. Veuillez contacter l'administrateur."));
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

        // 5. Générer le nouveau JWT
        String jwt = jwtUtils.generateJwtToken(authentication);

        // 6. Session unique : stocker le JWT (l'ancien est automatiquement invalidé)
        utilisateur.setTokenSession(jwt);

        // 7. Sauvegarder (compteur + session)
        utilisateurRepository.save(utilisateur);

        // 8. Traçabilité : enregistrer la connexion
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
        // Normaliser l'adresse IPv6 loopback en IPv4
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) ip = "127.0.0.1";
        String ua = request.getHeader("User-Agent");
        connexionLogRepository.save(ConnexionLog.builder()
                .utilisateur(utilisateur)
                .adresseIp(ip)
                .userAgent(ua != null ? ua.substring(0, Math.min(ua.length(), 500)) : "inconnu")
                .succes(true)
                .build());

        // 9. Construire la réponse
        List<String> roles = userDetails.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .collect(Collectors.toList());

        return ResponseEntity.ok(new JwtReponse(
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
                utilisationsRestantes));
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
}
