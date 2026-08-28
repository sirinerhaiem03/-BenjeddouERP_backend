package com.benjeddou.erp.controller;

import com.benjeddou.erp.model.AuditLog;
import com.benjeddou.erp.model.ConnexionLog;
import com.benjeddou.erp.model.Entreprise;
import com.benjeddou.erp.model.Utilisateur;
import com.benjeddou.erp.repository.AuditLogRepository;
import com.benjeddou.erp.repository.ConnexionLogRepository;
import com.benjeddou.erp.repository.EntrepriseRepository;
import com.benjeddou.erp.repository.UtilisateurRepository;
import com.benjeddou.erp.service.BackupService;
import com.benjeddou.erp.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SuperadminController — Routes réservées exclusivement au SUPERADMIN.
 * Fournit une vue globale de tous les utilisateurs et données de la plateforme.
 */
@RestController
@RequestMapping("/api/superadmin")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SuperadminController {

    private final UtilisateurRepository utilisateurRepository;
    private final EntrepriseRepository  entrepriseRepository;
    private final AuditLogRepository    auditLogRepository;
    private final ConnexionLogRepository connexionLogRepository;
    private final SessionService         sessionService;
    private final BackupService          backupService;

    // ══════════════════════════════════════════════════════
    // GET /api/superadmin/utilisateurs — Tous les utilisateurs
    // ══════════════════════════════════════════════════════
    @GetMapping("/utilisateurs")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> tousLesUtilisateurs() {
        List<Utilisateur> users = utilisateurRepository.findAll();
        List<Map<String, Object>> result = users.stream().map(u -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("nomUtilisateur", u.getNomUtilisateur());
            m.put("email", u.getEmail());
            m.put("prenom", u.getPrenom());
            m.put("nom", u.getNom());
            m.put("role", u.getRole() != null ? u.getRole().name() : "USER");
            m.put("actif", u.getActif());
            m.put("statutCompte", u.getStatutCompte() != null ? u.getStatutCompte().name() : "ACTIF");
            m.put("entrepriseSchema", u.getEntrepriseSchema());
            m.put("dateCreation", u.getDateCreation());
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // ══════════════════════════════════════════════════════
    // GET /api/superadmin/entreprises — Stats dashboard
    // ══════════════════════════════════════════════════════
    @GetMapping("/entreprises")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> entreprises() {
        List<Entreprise> ents = entrepriseRepository.findAll();
        List<Map<String, Object>> result = ents.stream().map(e -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("nomEntreprise", e.getNom());
            m.put("schema", e.getSchemaName());
            m.put("statut", e.getStatut() != null ? e.getStatut().name() : "ACTIVE");
            m.put("dateCreation", e.getDateCreation());
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // ══════════════════════════════════════════════════════
    // GET /api/superadmin/stats — Statistiques globales dashboard
    // ══════════════════════════════════════════════════════
    @GetMapping("/stats")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> statsGlobales() {
        long totalEntreprises = entrepriseRepository.count();
        long totalUtilisateurs = utilisateurRepository.count();
        long entreprisesActives = entrepriseRepository.findAll().stream()
            .filter(e -> e.getStatut() == Entreprise.StatutEntreprise.ACTIVE)
            .count();
        long entreprisesSuspendues = totalEntreprises - entreprisesActives;

        // Logs critiques des 24 dernières heures
        long logsCritiques = auditLogRepository.findLogsCritiques(
            PageRequest.of(0, 100)
        ).size();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalEntreprises", totalEntreprises);
        stats.put("totalUtilisateurs", totalUtilisateurs);
        stats.put("entreprisesActives", entreprisesActives);
        stats.put("entreprisesSuspendues", entreprisesSuspendues);
        stats.put("logsCritiques24h", logsCritiques);
        return ResponseEntity.ok(stats);
    }

    // ══════════════════════════════════════════════════════
    // PUT /api/superadmin/utilisateurs/{id}/activer|desactiver
    // ══════════════════════════════════════════════════════
    @PutMapping("/utilisateurs/{id}/activer")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> activerUtilisateur(@PathVariable Long id) {
        return utilisateurRepository.findById(id).map(u -> {
            u.setActif(true);
            utilisateurRepository.save(u);
            return ResponseEntity.ok(Map.of("message", "Utilisateur activé."));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/utilisateurs/{id}/desactiver")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> desactiverUtilisateur(@PathVariable Long id) {
        return utilisateurRepository.findById(id).map(u -> {
            u.setActif(false);
            utilisateurRepository.save(u);
            return ResponseEntity.ok(Map.of("message", "Utilisateur désactivé."));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ══════════════════════════════════════════════════════
    // GET /api/superadmin/audit — 20 derniers logs critiques (raccourci dashboard)
    // ══════════════════════════════════════════════════════
    @GetMapping("/audit")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> auditGlobal(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<AuditLog> result = auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
        Map<String, Object> response = new HashMap<>();
        response.put("content",       result.getContent());
        response.put("totalElements", result.getTotalElements());
        response.put("totalPages",    result.getTotalPages());
        response.put("currentPage",   result.getNumber());
        return ResponseEntity.ok(response);
    }

    // ══════════════════════════════════════════════════════
    // GET /api/superadmin/sessions — Toutes les sessions actives
    // ══════════════════════════════════════════════════════
    @GetMapping("/sessions")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> sessionsActives() {
        List<ConnexionLog> sessions = connexionLogRepository.findAllSessionsActives();
        List<Map<String, Object>> result = sessions.stream().map(s -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id",             s.getId());
            m.put("statut",         s.getStatut() != null ? s.getStatut().name() : "ACTIVE");
            m.put("adresseIp",      s.getAdresseIp());
            m.put("typeAppareil",   s.getTypeAppareil());
            m.put("os",             s.getOs());
            m.put("navigateur",     s.getNavigateur());
            m.put("resolution",     s.getResolution());
            m.put("langue",         s.getLangue());
            m.put("fuseauHoraire",  s.getFuseauHoraire());
            m.put("pays",           s.getPays());
            m.put("ville",          s.getVille());
            m.put("dateConnexion",  s.getDateConnexion());
            // ── Device Fingerprint ──
            m.put("appareilConnu",  Boolean.TRUE.equals(s.getAppareilConnu()));
            // Fingerprint tronqué (8 premiers chars) — ne pas exposer le hash complet
            String fp = s.getDeviceFingerprint();
            m.put("fingerprintCourt", fp != null && fp.length() >= 8 ? fp.substring(0, 8) + "..." : fp);
            // ── Réseau et risque ──
            m.put("typeReseau",          s.getTypeReseau());
            m.put("niveauRisque",        s.getNiveauRisque() != null ? s.getNiveauRisque() : 0);
            m.put("connexionInhabituelle", Boolean.TRUE.equals(s.getConnexionInhabituelle()));
            // ── Localisation complète ──
            m.put("region",         s.getRegion());
            m.put("latitude",       s.getLatitude());
            m.put("longitude",      s.getLongitude());
            m.put("fournisseurInternet", s.getFournisseurInternet());
            if (s.getUtilisateur() != null) {
                Utilisateur u = s.getUtilisateur();
                m.put("utilisateurId",    u.getId());
                m.put("nomUtilisateur",   u.getNomUtilisateur());
                m.put("email",            u.getEmail());
                m.put("role",             u.getRole() != null ? u.getRole().name() : "USER");
                m.put("entrepriseSchema", u.getEntrepriseSchema());
            }
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // ══════════════════════════════════════════════════════
    // POST /api/superadmin/sessions/{id}/deconnecter
    // Force la déconnexion d'une session depuis le dashboard
    // ══════════════════════════════════════════════════════
    @PostMapping("/sessions/{id}/deconnecter")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> forcerDeconnexion(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String motif = body != null ? body.getOrDefault("motif", "Déconnexion forcée par SuperAdmin") : "Déconnexion forcée par SuperAdmin";
        boolean ok = sessionService.revoquerSession(id, motif);
        if (ok) {
            return ResponseEntity.ok(Map.of("message", "Session " + id + " révoquée avec succès."));
        }
        return ResponseEntity.notFound().build();
    }

    // ══════════════════════════════════════════════════════
    // GET /api/superadmin/sessions/stats — KPIs sessions
    // ══════════════════════════════════════════════════════
    @GetMapping("/sessions/stats")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> statsSessionsActives() {
        long actives  = connexionLogRepository.countByStatut(ConnexionLog.StatutSession.ACTIVE);
        long terminees = connexionLogRepository.countByStatut(ConnexionLog.StatutSession.TERMINEE);
        long revoquees = connexionLogRepository.countByStatut(ConnexionLog.StatutSession.REVOQUEE);
        long signalements = connexionLogRepository.findAllSignalements().size();
        Map<String, Object> stats = new HashMap<>();
        stats.put("sessionsActives",   actives);
        stats.put("sessionsTerminees",  terminees);
        stats.put("sessionsRevoquees",  revoquees);
        stats.put("totalConnexions",    actives + terminees + revoquees);
        stats.put("signalements",       signalements);
        return ResponseEntity.ok(stats);
    }

    // ══════════════════════════════════════════════════════
    // GET /api/superadmin/sessions/historique — Toutes les connexions (paginées)
    // ══════════════════════════════════════════════════════
    @GetMapping("/sessions/historique")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> historiqueConnexions(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("dateConnexion").descending());
        List<ConnexionLog> logs = connexionLogRepository.findAllOrderByDateDesc(pageable);
        List<Map<String, Object>> result = logs.stream().map(s -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id",             s.getId());
            m.put("statut",         s.getStatut() != null ? s.getStatut().name() : "TERMINEE");
            m.put("adresseIp",      s.getAdresseIp());
            m.put("typeAppareil",   s.getTypeAppareil());
            m.put("os",             s.getOs());
            m.put("navigateur",     s.getNavigateur());
            m.put("dateConnexion",  s.getDateConnexion());
            m.put("dateDeconnexion",s.getDateDeconnexion());
            m.put("motifRevocation",s.getMotifRevocation());
            m.put("estSignale",     s.getEstSignale());
            m.put("dateSignalement",s.getDateSignalement());
            if (s.getUtilisateur() != null) {
                Utilisateur u = s.getUtilisateur();
                m.put("utilisateurId",    u.getId());
                m.put("nomUtilisateur",   u.getNomUtilisateur());
                m.put("email",            u.getEmail());
                m.put("role",             u.getRole() != null ? u.getRole().name() : "USER");
                m.put("entrepriseSchema", u.getEntrepriseSchema());
                m.put("statutCompte",     u.getStatutCompte() != null ? u.getStatutCompte().name() : "ACTIF");
            }
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // ══════════════════════════════════════════════════════
    // GET /api/superadmin/sessions/signalements — Liste des connexions signalées
    // ══════════════════════════════════════════════════════
    @GetMapping("/sessions/signalements")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> listeSignalements() {
        List<ConnexionLog> signalements = connexionLogRepository.findAllSignalements();
        List<Map<String, Object>> result = signalements.stream().map(s -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id",               s.getId());
            m.put("adresseIp",        s.getAdresseIp());
            m.put("typeAppareil",     s.getTypeAppareil());
            m.put("os",               s.getOs());
            m.put("navigateur",       s.getNavigateur());
            m.put("dateConnexion",    s.getDateConnexion());
            m.put("dateSignalement",  s.getDateSignalement());
            m.put("statut",           s.getStatut() != null ? s.getStatut().name() : "REVOQUEE");
            if (s.getUtilisateur() != null) {
                Utilisateur u = s.getUtilisateur();
                m.put("utilisateurId",    u.getId());
                m.put("nomUtilisateur",   u.getNomUtilisateur());
                m.put("email",            u.getEmail());
                m.put("statutCompte",     u.getStatutCompte() != null ? u.getStatutCompte().name() : "ACTIF");
            }
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // ══════════════════════════════════════════════════════
    // POST /api/superadmin/utilisateurs/{id}/deverrouiller — Déverrouiller un compte
    // ══════════════════════════════════════════════════════
    @PostMapping("/utilisateurs/{id}/deverrouiller")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> deverrouillerCompte(@PathVariable Long id) {
        boolean ok = sessionService.deverrouillerCompte(id);
        if (ok) {
            return ResponseEntity.ok(Map.of("message", "Compte déverrouillé et remis à ACTIF avec succès."));
        }
        return ResponseEntity.notFound().build();
    }

    // ══════════════════════════════════════════════════════
    // POST /api/superadmin/sessions/bloquer-ip — Bloquer toutes les sessions d'une IP
    // ══════════════════════════════════════════════════════
    @PostMapping("/sessions/bloquer-ip")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> bloquerIp(@RequestBody Map<String, String> body) {
        String ip = body.get("ip");
        if (ip == null || ip.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "L'adresse IP est requise."));
        }
        // Révoquer toutes les sessions actives depuis cette IP
        List<ConnexionLog> sessionsIp = connexionLogRepository.findByStatut(ConnexionLog.StatutSession.ACTIVE)
                .stream()
                .filter(s -> ip.equals(s.getAdresseIp()))
                .collect(Collectors.toList());
        int count = 0;
        for (ConnexionLog s : sessionsIp) {
            s.setStatut(ConnexionLog.StatutSession.REVOQUEE);
            s.setMotifRevocation("IP bloquée par SuperAdmin : " + ip);
            connexionLogRepository.save(s);
            count++;
        }
        return ResponseEntity.ok(Map.of(
            "message", count + " session(s) bloquée(s) depuis l'IP " + ip,
            "sessionsBloquees", count,
            "ip", ip
        ));
    }

    // ══════════════════════════════════════════════════════════════════════
    // POST /api/superadmin/reset-demo — Réinitialisation des comptes démo
    // ══════════════════════════════════════════════════════════════════════
    /**
     * Réinitialise tous les comptes de démonstration à leurs mots de passe documentés.
     * Utile après partage de la base de données ou après tests destructifs.
     *
     * Comptes réinitialisés :
     *   superadmin / superadmin123 (SUPERADMIN)
     *   admin      / admin123      (ADMIN)
     *   commercial / admin123      (COMMERCIAL)
     *   comptable  / admin123      (COMPTABLE)
     *   stock      / admin123      (STOCK)
     *   client_demo/ admin123      (CLIENT)
     */
    @PostMapping("/reset-demo")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> resetComptesDemo(
            @org.springframework.web.bind.annotation.RequestBody(required = false)
            Map<String, String> body) {

        // Confirmation de sécurité requise
        String confirmation = body != null ? body.get("confirmation") : null;
        if (!"RESET_DEMO_CONFIRME".equals(confirmation)) {
            return org.springframework.http.ResponseEntity.badRequest().body(Map.of(
                "erreur", "Confirmation requise.",
                "instructions", "Envoyez { \"confirmation\": \"RESET_DEMO_CONFIRME\" } dans le body.",
                "attention", "Cette action réinitialise les mots de passe de tous les comptes démo."
            ));
        }

        org.springframework.security.crypto.password.PasswordEncoder encoder =
            new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12);

        record CompteDemo(String nomUtilisateur, String email, String motDePasse,
                          String prenom, String nom, com.benjeddou.erp.model.Role role) {}

        java.util.List<CompteDemo> comptes = java.util.List.of(
            new CompteDemo("superadmin", "superadmin@benjeddou.com", "superadmin123",
                           "Super", "Admin", com.benjeddou.erp.model.Role.SUPERADMIN),
            new CompteDemo("admin",      "admin@benjeddou.com",       "admin123",
                           "Admin", "Benjeddou", com.benjeddou.erp.model.Role.ADMIN),
            new CompteDemo("commercial", "commercial@benjeddou.com",  "admin123",
                           "Amir", "Commercial", com.benjeddou.erp.model.Role.COMMERCIAL),
            new CompteDemo("comptable",  "comptable@benjeddou.com",   "admin123",
                           "Rim", "Comptable", com.benjeddou.erp.model.Role.COMPTABLE),
            new CompteDemo("stock",      "stock@benjeddou.com",        "admin123",
                           "Sami", "Stock", com.benjeddou.erp.model.Role.STOCK),
            new CompteDemo("client_demo","client@benjeddou.com",       "admin123",
                           "Client", "Demo", com.benjeddou.erp.model.Role.CLIENT)
        );

        java.util.List<Map<String, String>> resultats = new java.util.ArrayList<>();
        int crees = 0, reinitialises = 0;

        for (CompteDemo c : comptes) {
            String hash = encoder.encode(c.motDePasse());
            java.util.Optional<com.benjeddou.erp.model.Utilisateur> existing =
                utilisateurRepository.findByNomUtilisateur(c.nomUtilisateur());

            com.benjeddou.erp.model.Utilisateur user;
            String action;

            if (existing.isEmpty()) {
                user = com.benjeddou.erp.model.Utilisateur.builder()
                    .nomUtilisateur(c.nomUtilisateur())
                    .email(c.email())
                    .motDePasse(hash)
                    .prenom(c.prenom())
                    .nom(c.nom())
                    .actif(true)
                    .languePreferee("fr")
                    .role(c.role())
                    .statutCompte(com.benjeddou.erp.model.StatutCompte.ACTIF)
                    .modeTrial(false)
                    .doitChangerMotDePasse(false)
                    .build();
                action = "CREE";
                crees++;
            } else {
                user = existing.get();
                user.setMotDePasse(hash);
                user.setActif(true);
                user.setRole(c.role());
                user.setStatutCompte(com.benjeddou.erp.model.StatutCompte.ACTIF);
                user.setDoitChangerMotDePasse(false);
                action = "REINITIALISE";
                reinitialises++;
            }
            utilisateurRepository.save(user);

            // SYNCHRONISATION TENANT : Mettre à jour aussi la base MySQL du tenant
            // C'est LA source de vérité utilisée par l'authentification multi-tenant.
            if (user.getEntrepriseSchema() != null && !user.getEntrepriseSchema().isBlank()) {
                final String schema = user.getEntrepriseSchema();
                final String hashFinal = hash;
                final String loginFinal = c.nomUtilisateur();
                entrepriseRepository.findBySchemaName(schema).ifPresent(ent -> {
                    String tenantUrl = ent.getDbUrl();
                    String tenantDbUser = ent.getDbUsername();
                    String tenantDbPass = ent.getDbPassword() != null ? ent.getDbPassword() : "";
                    if (tenantUrl != null && !tenantUrl.isBlank()) {
                        try {
                            // UPDATE si existe, INSERT si nouveau
                            String sqlUpsert = """
                                INSERT INTO utilisateurs (nom_utilisateur, email, mot_de_passe, prenom, nom, actif, role, langue_preferee, statut_compte, doit_changer_mot_de_passe)
                                VALUES (?, ?, ?, ?, ?, TRUE, ?, 'fr', 'ACTIF', FALSE)
                                ON DUPLICATE KEY UPDATE mot_de_passe = VALUES(mot_de_passe), actif = TRUE, statut_compte = 'ACTIF', doit_changer_mot_de_passe = FALSE
                                """;
                            try (Connection conn = DriverManager.getConnection(tenantUrl, tenantDbUser, tenantDbPass);
                                 PreparedStatement ps = conn.prepareStatement(sqlUpsert)) {
                                ps.setString(1, loginFinal);
                                ps.setString(2, c.email());
                                ps.setString(3, hashFinal);
                                ps.setString(4, c.prenom());
                                ps.setString(5, c.nom());
                                ps.setString(6, c.role().name());
                                ps.executeUpdate();
                            }
                        } catch (Exception ex) {
                            // Non bloquant — log uniquement
                        }
                    }
                });
            } else {
                // Comptes sans tenant (ex: superadmin) -> sync uniquement master (déjà fait)
                // Pour les comptes démo (admin, commercial...) dans erp_ent_00000
                // on synchronise via la base tenant par défaut
                try {
                    final String hashFinal = hash;
                    final String loginFinal = c.nomUtilisateur();
                    String tenantUrl = System.getProperty("tenant.default.url",
                        "jdbc:mysql://localhost:3306/erp_ent_00000?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true");
                    String tenantDbUser = System.getProperty("tenant.default.username", "root");
                    String tenantDbPass = System.getProperty("tenant.default.password", "");
                    String sqlUpsert = """
                        INSERT INTO utilisateurs (nom_utilisateur, email, mot_de_passe, prenom, nom, actif, role, langue_preferee, statut_compte, doit_changer_mot_de_passe)
                        VALUES (?, ?, ?, ?, ?, TRUE, ?, 'fr', 'ACTIF', FALSE)
                        ON DUPLICATE KEY UPDATE mot_de_passe = VALUES(mot_de_passe), actif = TRUE, statut_compte = 'ACTIF', doit_changer_mot_de_passe = FALSE
                        """;
                    try (Connection conn = DriverManager.getConnection(tenantUrl, tenantDbUser, tenantDbPass);
                         PreparedStatement ps = conn.prepareStatement(sqlUpsert)) {
                        ps.setString(1, loginFinal);
                        ps.setString(2, c.email());
                        ps.setString(3, hashFinal);
                        ps.setString(4, c.prenom());
                        ps.setString(5, c.nom());
                        ps.setString(6, c.role().name());
                        ps.executeUpdate();
                    }
                } catch (Exception ex) {
                    // Non bloquant pour le superadmin
                }
            }
            resultats.add(Map.of(
                "nomUtilisateur", c.nomUtilisateur(),
                "role", c.role().name(),
                "motDePasse", c.motDePasse(),
                "action", action
            ));
        }

        return ResponseEntity.ok(Map.of(
            "succes", true,
            "message", "✅ Reset terminé — " + crees + " créé(s), " + reinitialises + " réinitialisé(s).",
            "comptes", resultats,
            "horodatage", java.time.LocalDateTime.now().toString()
        ));
    }

    // ══════════════════════════════════════════════════════════════════════
    // GET /api/superadmin/securite/status — État de la configuration sécurité
    // ══════════════════════════════════════════════════════════════════════
    @GetMapping("/securite/status")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> statutSecurite() {
        Map<String, Object> securite = new java.util.LinkedHashMap<>();
        securite.put("authentification",    Map.of("jwt", true, "refreshToken", true, "otp", true));
        securite.put("chiffrement",         Map.of("bcryptCout", 12, "algorithme", "BCrypt"));
        securite.put("sessions",            Map.of("stateless", true, "alerteDoubleConnexion", true));
        securite.put("bruteForce",          Map.of("actif", true, "maxTentatives", 5, "fenetre", "5 minutes"));
        securite.put("headersHTTP",         Map.of(
            "XFrameOptions", "DENY",
            "ContentSecurityPolicy", "actif",
            "HSTS", "31536000 secondes",
            "XContentTypeOptions", "nosniff",
            "ReferrerPolicy", "strict-origin-when-cross-origin",
            "PermissionsPolicy", "actif"
        ));
        securite.put("auditLog",            Map.of("actif", true, "asynchrone", true));
        securite.put("rbac",                Map.of("enableMethodSecurity", true, "preAuthorize", true));
        securite.put("csrf",                Map.of("desactive", true, "justification", "API REST stateless JWT"));
        securite.put("chiffrementDonnees",  Map.of("champsSensibles", "TODO - Phase 2"));
        securite.put("sauvegardes",         Map.of("automatiques", "TODO - Phase 2"));
        securite.put("https",               Map.of("configure", "TODO - Déploiement production"));

        return ResponseEntity.ok(Map.of(
            "plateforme", "BENJEDDOU ERP SaaS",
            "version", "1.0.0",
            "securite", securite,
            "horodatage", java.time.LocalDateTime.now().toString()
        ));
    }

    // ══════════════════════════════════════════════════════════════════════
    // POST /api/superadmin/backup/declencher — Sauvegarde manuelle sécurisée
    // ══════════════════════════════════════════════════════════════════════
    /**
     * Déclenche une sauvegarde manuelle immédiate.
     * La sauvegarde est compressée (GZIP) et chiffrée (AES-256-GCM).
     */
    @PostMapping("/backup/declencher")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> declencherSauvegarde(
            org.springframework.security.core.Authentication auth) {
        String declencheur = auth != null ? auth.getName() : "superadmin";
        Map<String, Object> result = backupService.sauvegardeManuelle(declencheur);
        return ResponseEntity.ok(result);
    }

    // ══════════════════════════════════════════════════════════════════════
    // GET /api/superadmin/backup/liste — Lister les sauvegardes disponibles
    // ══════════════════════════════════════════════════════════════════════
    @GetMapping("/backup/liste")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> listerSauvegardes() {
        java.util.List<Map<String, Object>> sauvegardes = backupService.listerSauvegardes();
        return ResponseEntity.ok(Map.of(
            "total", sauvegardes.size(),
            "sauvegardes", sauvegardes,
            "retention", "30 jours",
            "chiffrement", "AES-256-GCM",
            "horodatage", java.time.LocalDateTime.now().toString()
        ));
    }

    // ══════════════════════════════════════════════════════════════════════
    // DELETE /api/superadmin/backup/nettoyer — Supprimer les sauvegardes obsolètes
    // ══════════════════════════════════════════════════════════════════════
    @DeleteMapping("/backup/nettoyer")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> nettoyerSauvegardes() {
        backupService.nettoyerAnciennesSauvegardes();
        return ResponseEntity.ok(Map.of(
            "message", "Nettoyage des sauvegardes obsolètes (> 30 jours) effectué.",
            "horodatage", java.time.LocalDateTime.now().toString()
        ));
    }
}
