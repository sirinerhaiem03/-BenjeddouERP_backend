package com.benjeddou.erp.controller;

import com.benjeddou.erp.model.Entreprise;
import com.benjeddou.erp.model.Role;
import com.benjeddou.erp.model.StatutCompte;
import com.benjeddou.erp.model.Utilisateur;
import com.benjeddou.erp.repository.EntrepriseRepository;
import com.benjeddou.erp.repository.UtilisateurRepository;
import com.benjeddou.erp.service.EntrepriseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * EntrepriseController — API REST de gestion des entreprises (tenants).
 * Accessible uniquement au SuperAdmin de la plateforme.
 *
 * Règle métier fondamentale : 1 SEUL admin par entreprise.
 */
@RestController
@RequestMapping("/api/entreprises")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class EntrepriseController {

    private final EntrepriseService      entrepriseService;
    private final EntrepriseRepository   entrepriseRepository;
    private final UtilisateurRepository  utilisateurRepository;
    private final PasswordEncoder        passwordEncoder;

    // ═══════════════════════════════════════════════════════════════════════
    // LISTER
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> listerEntreprises() {
        List<Entreprise> entreprises = entrepriseRepository.findAll();
        List<Map<String, Object>> result = entreprises.stream().map(this::buildResponse).toList();
        return ResponseEntity.ok(Map.of("entreprises", result, "total", result.size()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> getEntreprise(@PathVariable Long id) {
        return entrepriseRepository.findById(id)
            .map(e -> ResponseEntity.ok((Object) buildResponse(e)))
            .orElse(ResponseEntity.notFound().build());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CRÉER ENTREPRISE + ADMIN EN UNE SEULE OPÉRATION
    // Règle : 1 SEUL admin par entreprise
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * POST /api/entreprises/creer-avec-admin
     *
     * Crée en une seule opération atomique :
     * 1. L'admin de l'entreprise (role=ADMIN)
     * 2. La base MySQL dédiée  (erp_ent_XXXXX)
     * 3. L'utilisateur MySQL dédié avec mot de passe unique (erp_user_XXXXX)
     * 4. L'entrée entreprise dans la base master
     * 5. Le lien admin ↔ entreprise
     *
     * Body JSON attendu :
     * {
     *   "nomEntreprise"  : "Société XYZ SARL",
     *   "nomUtilisateur" : "admin_xyz",
     *   "email"          : "admin@xyz.tn",
     *   "motDePasse"     : "MotDePasse@123",
     *   "prenom"         : "Mohamed",
     *   "nom"            : "Trabelsi"
     * }
     */
    @PostMapping("/creer-avec-admin")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> creerAvecAdmin(@RequestBody Map<String, Object> body) {

        String nomEntreprise  = (String) body.get("nomEntreprise");
        String nomUtilisateur = (String) body.get("nomUtilisateur");
        String email          = (String) body.get("email");
        String motDePasse     = (String) body.get("motDePasse");
        String prenom         = (String) body.getOrDefault("prenom", "Admin");
        String nom            = (String) body.getOrDefault("nom", "");

        // ─── Validation des champs obligatoires ──────────────────────────
        if (nomEntreprise == null || nomUtilisateur == null || email == null || motDePasse == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Champs obligatoires : nomEntreprise, nomUtilisateur, email, motDePasse"
            ));
        }

        // ─── Règle : 1 seul admin par entreprise — vérification unicité ──
        if (utilisateurRepository.existsByNomUtilisateur(nomUtilisateur)) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Ce nom d'utilisateur est déjà pris : " + nomUtilisateur
            ));
        }
        if (utilisateurRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Un compte existe déjà avec cet email : " + email
            ));
        }

        try {
            // ÉTAPE 1 : Créer l'admin dans la base master
            Utilisateur admin = Utilisateur.builder()
                .nomUtilisateur(nomUtilisateur)
                .email(email)
                .motDePasse(passwordEncoder.encode(motDePasse))
                .prenom(prenom)
                .nom(nom)
                .role(Role.ADMIN)
                .statutCompte(StatutCompte.ACTIF)
                .actif(true)
                .modeTrial(false)
                .doitChangerMotDePasse(false)
                .build();
            admin = utilisateurRepository.save(admin);
            log.info("✓ Admin créé : {} (id={})", nomUtilisateur, admin.getId());

            // ÉTAPE 2 : Créer la base MySQL dédiée + user MySQL + GRANT
            Entreprise entreprise = entrepriseService.creerEntreprise(
                nomEntreprise, email, admin.getId()
            );
            log.info("✓ Entreprise créée : {} → {}", nomEntreprise, entreprise.getSchemaName());

            // ÉTAPE 3 : Lier l'admin à son entreprise
            admin.setEntrepriseId(entreprise.getId());
            admin.setEntrepriseSchema(entreprise.getSchemaName());
            utilisateurRepository.save(admin);
            log.info("✓ Lien admin {} ↔ entreprise {} établi", nomUtilisateur, entreprise.getSchemaName());

            // ÉTAPE 4 : Insérer l'admin AUSSI dans la base tenant (source de vérité pour l'authentification)
            // Sans cette étape, l'authentification multi-tenant échoue car le hash BCrypt
            // n'existe que dans la base Master et pas dans la base Tenant.
            try {
                String tenantDbUrl = entreprise.getDbUrl();
                String tenantDbUser = entreprise.getDbUsername();
                String tenantDbPass = entreprise.getDbPassword() != null ? entreprise.getDbPassword() : "";
                if (tenantDbUrl != null && !tenantDbUrl.isBlank()) {
                    String sqlInsertAdmin = """
                        INSERT IGNORE INTO utilisateurs
                            (nom_utilisateur, email, mot_de_passe, prenom, nom,
                             actif, role, langue_preferee, statut_compte, doit_changer_mot_de_passe)
                        VALUES (?, ?, ?, ?, ?, TRUE, 'ADMIN', 'fr', 'ACTIF', FALSE)
                        """;
                    try (Connection conn = DriverManager.getConnection(tenantDbUrl, tenantDbUser, tenantDbPass);
                         PreparedStatement ps = conn.prepareStatement(sqlInsertAdmin)) {
                        ps.setString(1, nomUtilisateur);
                        ps.setString(2, email);
                        ps.setString(3, admin.getMotDePasse()); // même hash BCrypt que dans Master
                        ps.setString(4, prenom != null ? prenom : "");
                        ps.setString(5, nom != null ? nom : "");
                        ps.executeUpdate();
                        log.info("✓ Admin '{}' inséré dans la base tenant '{}'", nomUtilisateur, entreprise.getSchemaName());
                    }
                }
            } catch (Exception ex) {
                log.warn("⚠️  Insertion admin dans tenant ignorée : {}", ex.getMessage());
            }

            // Réponse succès
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("message", "Entreprise et admin créés avec succès !");
            resp.put("entreprise", buildResponse(entreprise));
            resp.put("admin", Map.of(
                "id",             admin.getId(),
                "nomUtilisateur", nomUtilisateur,
                "email",          email,
                "role",           "ADMIN",
                "schema",         entreprise.getSchemaName()
            ));
            return ResponseEntity.ok(resp);

        } catch (Exception ex) {
            log.error("Erreur création entreprise+admin : {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Erreur : " + ex.getMessage()
            ));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SUSPENDRE / RÉACTIVER
    // ═══════════════════════════════════════════════════════════════════════

    @PutMapping("/{id}/suspendre")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> suspendreEntreprise(@PathVariable Long id) {
        return entrepriseRepository.findById(id).map(e -> {
            e.setStatut(Entreprise.StatutEntreprise.SUSPENDUE);
            entrepriseRepository.save(e);
            log.info("Entreprise '{}' suspendue", e.getNom());
            return ResponseEntity.ok(Map.of("success", true, "message", "Entreprise suspendue."));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/reactiver")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> reactiverEntreprise(@PathVariable Long id) {
        return entrepriseRepository.findById(id).map(e -> {
            e.setStatut(Entreprise.StatutEntreprise.ACTIVE);
            entrepriseRepository.save(e);
            log.info("Entreprise '{}' réactivée", e.getNom());
            return ResponseEntity.ok(Map.of("success", true, "message", "Entreprise réactivée."));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ANCIEN ENDPOINT (gardé pour compatibilité avec /register)
    // ═══════════════════════════════════════════════════════════════════════

    @PostMapping("/creer")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> creerEntreprise(@RequestBody Map<String, Object> body) {
        try {
            String nom   = body.getOrDefault("nom", "Entreprise sans nom").toString();
            String email = body.getOrDefault("emailContact", "").toString();
            Long adminId = body.containsKey("adminId") ?
                Long.valueOf(body.get("adminId").toString()) : null;
            Entreprise e = entrepriseService.creerEntreprise(nom, email, adminId);
            return ResponseEntity.ok(Map.of("success", true, "entreprise", buildResponse(e)));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HELPER
    // ═══════════════════════════════════════════════════════════════════════

    private Map<String, Object> buildResponse(Entreprise e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",           e.getId());
        m.put("nom",          e.getNom());
        m.put("schemaName",   e.getSchemaName());
        m.put("dbUsername",   e.getDbUsername());
        m.put("emailContact", e.getEmailContact() != null ? e.getEmailContact() : "");
        m.put("statut",       e.getStatut().name());
        m.put("adminId",      e.getAdminId() != null ? e.getAdminId() : 0);
        m.put("dateCreation", e.getDateCreation() != null ? e.getDateCreation().toString() : "");
        return m;
    }
}
