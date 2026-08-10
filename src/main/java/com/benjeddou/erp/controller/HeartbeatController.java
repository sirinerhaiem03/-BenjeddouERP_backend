package com.benjeddou.erp.controller;

import com.benjeddou.erp.model.StatutCompte;
import com.benjeddou.erp.model.Utilisateur;
import com.benjeddou.erp.repository.UtilisateurRepository;
import com.benjeddou.erp.security.jwt.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * HeartbeatController — J3 Sécurité (P5)
 *
 * Endpoint : POST /api/auth/heartbeat
 * Appelé par Angular toutes les 30 secondes pour :
 * 1. Vérifier que la session est toujours valide (tokenSession en DB)
 * 2. Contrôler l'expiration de la période d'essai (par date)
 * 3. Retourner l'état actuel (OK / TRIAL_EXPIRED / SESSION_INVALIDEE)
 *
 * IMPORTANT : La réponse JSON inclut TOUJOURS les champs 'code' ET 'status'
 * pour compatibilité avec le frontend Angular.
 */
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
public class HeartbeatController {

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @PostMapping("/heartbeat")
    public ResponseEntity<?> heartbeat(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        Map<String, Object> response = new HashMap<>();

        // 1. Extraire le JWT
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.put("code", "UNAUTHORIZED");
            response.put("status", "UNAUTHORIZED");
            response.put("message", "Token manquant.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String jwt = authHeader.substring(7);

        // 2. Extraire le username — même si le token est expiré
        //    Permet de détecter une double-connexion même après 15 min d'inactivité
        String username = jwtUtils.getUserNameFromExpiredOrValidToken(jwt);
        if (username == null) {
            response.put("code", "INVALID_TOKEN");
            response.put("status", "INVALID_TOKEN");
            response.put("message", "Token JWT invalide ou malformé.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        // 3. Charger l'utilisateur
        Optional<Utilisateur> userOpt = utilisateurRepository.findByNomUtilisateur(username);
        if (userOpt.isEmpty()) {
            response.put("code", "USER_NOT_FOUND");
            response.put("status", "USER_NOT_FOUND");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        Utilisateur user = userOpt.get();

        // 4. ⭐ VÉRIFICATION PRIORITAIRE : Comparer token_session en DB
        //    Si une autre session a été ouverte → token_session en DB ≠ jwt actuel
        String tokenEnDb = user.getTokenSession();
        if (tokenEnDb == null) {
            log.warn("[HEARTBEAT] tokenSession null en base pour '{}' — accès refusé", username);
            response.put("code", "SESSION_INVALIDEE");
            response.put("status", "SESSION_INVALIDEE");
            response.put("message", "Session invalide. Veuillez vous reconnecter.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        if (!tokenEnDb.equals(jwt)) {
            log.warn("[HEARTBEAT] 🔴 Double connexion détectée pour '{}' — session révoquée.", username);
            response.put("code", "SESSION_INVALIDEE");
            response.put("status", "SESSION_INVALIDEE");
            response.put("message", "Votre compte a été connecté depuis un autre appareil. Cette session a été fermée automatiquement.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        // 5. Vérifier si le JWT est expiré (seulement après avoir validé la session)
        //    Si expiré → c'est la BONNE session mais il faut un refresh
        if (!jwtUtils.validateJwtToken(jwt)) {
            response.put("code", "TOKEN_EXPIRED");
            response.put("status", "TOKEN_EXPIRED");
            response.put("message", "Access token expiré. Veuillez utiliser le refresh token.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        // 6. Vérifier le statut du compte
        if (!Boolean.TRUE.equals(user.getActif())) {
            log.warn("[HEARTBEAT] Compte désactivé : {}", username);
            response.put("code", "COMPTE_SUSPENDU");
            response.put("status", "COMPTE_SUSPENDU");
            response.put("message", "Votre compte a été désactivé. Contactez l'administrateur.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        if (user.getStatutCompte() == StatutCompte.REFUSE) {
            response.put("code", "COMPTE_REFUSE");
            response.put("status", "COMPTE_REFUSE");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        // 7. Vérification trial par date
        if (Boolean.TRUE.equals(user.getModeTrial()) && user.getTrialExpiresAt() != null) {
            if (LocalDateTime.now().isAfter(user.getTrialExpiresAt())) {
                response.put("code", "TRIAL_EXPIRED");
                response.put("status", "TRIAL_EXPIRED");
                response.put("message", "Votre période d'essai a expiré. Veuillez souscrire à un abonnement.");
                response.put("trialExpiresAt", user.getTrialExpiresAt().toString());
                return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(response);
            }

            long joursRestants = ChronoUnit.DAYS.between(LocalDateTime.now(), user.getTrialExpiresAt());
            response.put("joursTrialRestants", Math.max(0, joursRestants));
            response.put("trialExpiresAt", user.getTrialExpiresAt().toString());
        }

        // 8. Tout est OK ✅
        log.debug("[HEARTBEAT] ✅ Session active pour '{}'", username);
        response.put("code", "OK");
        response.put("status", "OK");
        response.put("sessionValide", true);
        response.put("username", username);
        response.put("modeTrial", user.getModeTrial());

        return ResponseEntity.ok(response);
    }
}
