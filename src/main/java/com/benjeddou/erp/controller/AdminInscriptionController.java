package com.benjeddou.erp.controller;

import com.benjeddou.erp.model.*;
import com.benjeddou.erp.payload.response.MessageReponse;
import com.benjeddou.erp.repository.EntrepriseRepository;
import com.benjeddou.erp.repository.UtilisateurRepository;
import com.benjeddou.erp.service.EntrepriseService;
import com.benjeddou.erp.service.OtpService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

/**
 * AdminInscriptionController — Inscription d'un Administrateur Entreprise (SaaS Multi-Tenant).
 *
 * Flux :
 *  1. POST /api/admin/otp/envoyer    → Vérification email + envoi OTP
 *  2. POST /api/admin/otp/verifier   → Vérification du code OTP
 *  3. POST /api/admin/register       → Création compte ADMIN + base tenant dédiée
 *  4. GET  /api/admin/check-username → Vérif disponibilité username (temps réel)
 *  5. GET  /api/admin/check-email    → Vérif disponibilité email (temps réel)
 *
 * À la différence du ClientInscriptionController :
 *  - Rôle = Role.ADMIN (accès complet au dashboard ERP)
 *  - Création SYSTÉMATIQUE de la base tenant (erp_ent_XXXXX)
 *  - Si la création de la base échoue → 500 (pas de compte sans environnement)
 *  - Pas de KYC, pas de mode trial (l'admin gère son propre abonnement)
 *  - Retourne le schémaName dans la réponse pour confirmation à l'utilisateur
 */
@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/inscription-admin")
@Slf4j
public class AdminInscriptionController {

    @Autowired UtilisateurRepository utilisateurRepository; // master — check unicité globale
    @Autowired EntrepriseRepository  entrepriseRepository;  // master — routing tenants
    @Autowired PasswordEncoder        encoder;
    @Autowired OtpService             otpService;
    @Autowired EntrepriseService      entrepriseService;

    // Credentials root (master) — utilisés pour insérer dans le tenant si erp_user n'a pas encore les droits
    @org.springframework.beans.factory.annotation.Value("${spring.datasource.url}")
    private String masterDbUrl;

    @org.springframework.beans.factory.annotation.Value("${spring.datasource.username}")
    private String masterDbUser;

    @org.springframework.beans.factory.annotation.Value("${spring.datasource.password:}")
    private String masterDbPass;

    // ══════════════════════════════════════════════════════════════
    //  CHECK DISPONIBILITÉ
    // ══════════════════════════════════════════════════════════════

    @GetMapping("/check-username")
    public ResponseEntity<?> checkUsername(@RequestParam String username) {
        boolean inMaster = utilisateurRepository.findByNomUtilisateur(username)
                .map(u -> Boolean.TRUE.equals(u.getActif())).orElse(false);
        if (inMaster) return ResponseEntity.ok(Map.of("available", false));
        return ResponseEntity.ok(Map.of("available", !existsDansUnTenant("nom_utilisateur", username)));
    }

    @GetMapping("/check-email")
    public ResponseEntity<?> checkEmail(@RequestParam String email) {
        boolean inMaster = utilisateurRepository.findByEmail(email)
                .map(u -> Boolean.TRUE.equals(u.getActif())).orElse(false);
        if (inMaster) return ResponseEntity.ok(Map.of("available", false));
        return ResponseEntity.ok(Map.of("available", !existsDansUnTenant("email", email)));
    }

    // ══════════════════════════════════════════════════════════════
    //  ÉTAPE 1 : Envoi OTP
    // ══════════════════════════════════════════════════════════════

    @PostMapping("/otp/envoyer")
    public ResponseEntity<?> envoyerOtp(@RequestBody Map<String, String> payload) {
        String email  = payload.get("email");
        String prenom = payload.getOrDefault("prenom", "Administrateur");
        if (email == null || email.isBlank())
            return ResponseEntity.badRequest().body(new MessageReponse("Email requis."));
        if (utilisateurRepository.existsByEmail(email) || existsDansUnTenant("email", email))
            return ResponseEntity.badRequest()
                    .body(new MessageReponse("Cet email est déjà associé à un compte actif."));
        String code = otpService.genererEtEnvoyer(email, prenom);
        Map<String, Object> resp = new HashMap<>();
        resp.put("message", "Code OTP envoyé à " + email);
        resp.put("devCode", code);
        return ResponseEntity.ok(resp);
    }

    // ══════════════════════════════════════════════════════════════
    //  ÉTAPE 2 : Vérification OTP
    // ══════════════════════════════════════════════════════════════

    @PostMapping("/otp/verifier")
    public ResponseEntity<?> verifierOtp(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        String code  = payload.get("code");

        if (email == null || code == null) {
            return ResponseEntity.badRequest().body(new MessageReponse("Email et code requis."));
        }
        if (otpService.verifier(email, code)) {
            return ResponseEntity.ok(new MessageReponse("OTP valide. Email vérifié."));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new MessageReponse("Code OTP incorrect ou expiré."));
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  ÉTAPE 3 : Inscription ADMIN + Création base tenant
    // ══════════════════════════════════════════════════════════════

    @PostMapping("/register")
    public ResponseEntity<?> inscrireAdmin(@RequestBody Map<String, Object> payload) {

        // ── Extraction des champs ────────────────────────────────
        String nomUtilisateur = (String) payload.get("nomUtilisateur");
        String email          = (String) payload.get("email");
        String motDePasse     = (String) payload.get("motDePasse");
        String prenom         = (String) payload.get("prenom");
        String nom            = (String) payload.get("nom");
        String telephone      = (String) payload.get("telephone");
        String societe        = (String) payload.get("societe");       // NOM ENTREPRISE — Obligatoire
        String adresse        = (String) payload.get("adresse");
        String secteur        = (String) payload.get("secteur");       // Secteur d'activité
        String tailleEntreprise = (String) payload.get("tailleEntreprise");

        // ── Validation champs obligatoires ────────────────────────
        if (nomUtilisateur == null || nomUtilisateur.isBlank()) {
            return ResponseEntity.badRequest().body(new MessageReponse("Nom d'utilisateur requis."));
        }
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(new MessageReponse("Email requis."));
        }
        if (motDePasse == null || motDePasse.isBlank()) {
            return ResponseEntity.badRequest().body(new MessageReponse("Mot de passe requis."));
        }
        if (societe == null || societe.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new MessageReponse("Le nom de l'entreprise est obligatoire."));
        }

        // ── Vérification unicité globale (master + toutes bases tenant) ──
        boolean usernamePris = utilisateurRepository.findByNomUtilisateur(nomUtilisateur)
                .map(u -> Boolean.TRUE.equals(u.getActif())).orElse(false)
                || existsDansUnTenant("nom_utilisateur", nomUtilisateur);
        if (usernamePris)
            return ResponseEntity.badRequest().body(new MessageReponse("Ce nom d'utilisateur est déjà pris."));

        boolean emailPris = utilisateurRepository.findByEmail(email)
                .map(u -> Boolean.TRUE.equals(u.getActif())).orElse(false)
                || existsDansUnTenant("email", email);
        if (emailPris)
            return ResponseEntity.badRequest().body(new MessageReponse("Cet email est déjà utilisé."));

        String motDePasseHache = encoder.encode(motDePasse);

        // ── ETAPE A : Créer la base tenant (adminId=null — admin sera dans le tenant) ──
        Entreprise entreprise;
        try {
            entreprise = entrepriseService.creerEntreprise(societe, email, null);
            log.info("[AdminInscription] Base tenant créée : {} pour '{}'",
                     entreprise.getSchemaName(), societe);
        } catch (Exception ex) {
            log.error("[AdminInscription] ECHEC création tenant : {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageReponse("Erreur lors de la création de votre espace : " + ex.getMessage()));
        }

        String schemaName = entreprise.getSchemaName();

        // ── ETAPE B : Insérer l'admin dans erp_ent_XXXXX.utilisateurs via JDBC ──
        // L'admin N'EST PAS créé dans benjeddou_erp.utilisateurs (base master)
        try {
            insererAdminDansTenant(entreprise, nomUtilisateur, email, motDePasseHache,
                                   prenom, nom, telephone, societe, adresse);
            log.info("[AdminInscription] Admin '{}' inséré dans '{}' (base tenant, PAS en master)",
                     nomUtilisateur, schemaName);
        } catch (Exception ex) {
            log.error("[AdminInscription] ECHEC insertion admin dans '{}': {}", schemaName, ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageReponse("Espace créé mais erreur enregistrement compte : " + ex.getMessage()));
        }

        // ── Réponse succès ─────────────────────────────────────
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Inscription réussie ! Votre espace entreprise a été créé.");
        response.put("nomUtilisateur", nomUtilisateur);
        response.put("societe", societe);
        response.put("schemaName", schemaName);
        response.put("role", "ADMIN");
        return ResponseEntity.ok(response);
    }

    // ════════════════════════════════════════════════════════════
    //  HELPER : Insérer l'admin dans la base TENANT via JDBC
    //  Stratégie : tente avec erp_user_XXXXX, si refusé → fallback root
    //  Le fallback root est nécessaire quand le GRANT n'a pas été appliqué
    //  (tables Aria corrompues dans MariaDB — FLUSH PRIVILEGES échoue)
    // ════════════════════════════════════════════════════════════
    private void insererAdminDansTenant(
            Entreprise entreprise,
            String nomUtilisateur, String email, String motDePasseHache,
            String prenom, String nom, String telephone,
            String societe, String adresse) throws Exception {

        String schemaName = entreprise.getSchemaName();

        // Construire l'URL root vers la base tenant (mysql sélectionne la base via USE)
        // jdbc:mysql://localhost:3306/benjeddou_erp?... → jdbc:mysql://localhost:3306/erp_ent_00003?...
        String rootUrl = masterDbUrl.replaceFirst("/benjeddou_erp", "/" + schemaName)
                                    .replaceFirst("/[^/?]+\\?", "/" + schemaName + "?");

        String sql = "INSERT INTO utilisateurs (" +
            "nom_utilisateur, email, mot_de_passe, prenom, nom, " +
            "telephone, societe, adresse, role, statut_compte, " +
            "actif, mode_trial, kyc_soumis, langue_preferee, " +
            "nb_utilisations, nb_utilisations_max, " +
            "doit_changer_mot_de_passe, entreprise_schema, date_creation" +
            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ADMIN', 'ACTIF', " +
            "TRUE, FALSE, FALSE, 'fr', 0, 999, FALSE, ?, NOW())";

        // Tentative 1 : connexion avec l'user dédié erp_user_XXXXX
        boolean inserted = false;
        String tenantUrl  = entreprise.getDbUrl();
        String tenantUser = entreprise.getDbUsername();
        String tenantPass = entreprise.getDbPassword() != null ? entreprise.getDbPassword() : "";

        if (tenantUrl != null && !tenantUrl.isBlank()) {
            try (Connection conn = DriverManager.getConnection(tenantUrl, tenantUser, tenantPass);
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                setAdminParams(ps, nomUtilisateur, email, motDePasseHache, prenom, nom,
                               telephone, societe, adresse, schemaName);
                int rows = ps.executeUpdate();
                if (rows == 1) {
                    inserted = true;
                    log.info("  ✓ Admin inséré dans '{}' via user dédié '{}'", schemaName, tenantUser);
                }
            } catch (Exception e) {
                log.warn("  ⚠ User dédié '{}' refusé ({}). Tentative root...", tenantUser, e.getMessage());
            }
        }

        // Tentative 2 (fallback root) — contourne le problème GRANT non appliqué (Aria corrompu)
        if (!inserted) {
            log.warn("  → Fallback ROOT pour insérer l'admin dans '{}'", schemaName);
            try (Connection conn = DriverManager.getConnection(rootUrl, masterDbUser, masterDbPass != null ? masterDbPass : "");
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                setAdminParams(ps, nomUtilisateur, email, motDePasseHache, prenom, nom,
                               telephone, societe, adresse, schemaName);
                int rows = ps.executeUpdate();
                if (rows != 1) throw new RuntimeException("INSERT root retourna " + rows + " lignes.");
                log.info("  ✓ Admin inséré dans '{}' via ROOT (GRANT Aria non encore actif)", schemaName);
            }
        }
    }

    /** Bind les paramètres PreparedStatement pour l'INSERT admin */
    private void setAdminParams(PreparedStatement ps,
            String nomUtilisateur, String email, String motDePasseHache,
            String prenom, String nom, String telephone,
            String societe, String adresse, String schemaName) throws Exception {
        ps.setString(1, nomUtilisateur);
        ps.setString(2, email);
        ps.setString(3, motDePasseHache);
        ps.setString(4, prenom    != null ? prenom    : "");
        ps.setString(5, nom       != null ? nom       : "");
        ps.setString(6, telephone);
        ps.setString(7, societe);
        ps.setString(8, adresse);
        ps.setString(9, schemaName); // entreprise_schema pour auto-routage auth
    }


    // ═════════════════════════════════════════════════════════════
    //  HELPER : Vérifier unicité dans toutes les bases tenant
    // ═════════════════════════════════════════════════════════════
    private boolean existsDansUnTenant(String colonne, String valeur) {
        if (!colonne.matches("[a-zA-Z_]+")) return false; // sécurité SQL injection
        List<Entreprise> entreprises = entrepriseRepository.findByStatut(Entreprise.StatutEntreprise.ACTIVE);
        for (Entreprise ent : entreprises) {
            String url  = ent.getDbUrl();
            String user = ent.getDbUsername();
            String pass = ent.getDbPassword() != null ? ent.getDbPassword() : "";
            if (url == null || url.isBlank()) continue;
            String sql = "SELECT 1 FROM utilisateurs WHERE `" + colonne + "` = ? AND actif = TRUE LIMIT 1";
            try (Connection conn = DriverManager.getConnection(url, user, pass);
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, valeur);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return true;
                }
            } catch (Exception e) {
                log.warn("[AdminInscription] Check unicité dans '{}': {}", ent.getSchemaName(), e.getMessage());
            }
        }
        return false;
    }
}
