package com.benjeddou.erp.config;

import com.benjeddou.erp.model.Role;
import com.benjeddou.erp.model.StatutCompte;
import com.benjeddou.erp.model.Utilisateur;
import com.benjeddou.erp.repository.EntrepriseRepository;
import com.benjeddou.erp.repository.UtilisateurRepository;
import com.benjeddou.erp.service.EntrepriseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileCopyUtils;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Optional;

/**
 * DatabaseInitializer — Initialisation automatique complète BENJEDDOU ERP
 *
 * Responsabilités (ordre d'exécution) :
 *  [0] Bootstrap du schéma master (benjeddou_erp) via master-schema.sql
 *  [1] Créer/synchroniser le compte SuperAdmin dans benjeddou_erp
 *  [2] Bootstrap de la base tenant (erp_ent_00000) via tenant-schema.sql
 *  [3] Charger les données démo si la base tenant est vide
 *  [4] Synchroniser les comptes démo dans erp_ent_00000 avec BCrypt frais
 *  [5] Charger les DataSources de toutes les entreprises actives
 *
 * Architecture Multi-Tenant :
 *  - benjeddou_erp   → Base SaaS : SuperAdmin + gestion entreprises
 *  - erp_ent_00000   → Base démo entreprise : tous les modules ERP
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseInitializer implements CommandLineRunner {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder       passwordEncoder;
    private final EntrepriseService     entrepriseService;
    private final EntrepriseRepository  entrepriseRepository;

    @Value("${spring.datasource.url}")
    private String masterUrl;

    @Value("${spring.datasource.username}")
    private String masterUser;

    @Value("${spring.datasource.password:}")
    private String masterPassword;

    @Value("${tenant.default.url:jdbc:mysql://localhost:3306/erp_ent_00000?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true}")
    private String tenantUrl;

    @Value("${tenant.default.username:root}")
    private String tenantUser;

    @Value("${tenant.default.password:}")
    private String tenantPassword;

    // URL de base MySQL (sans nom de base) pour créer les bases si absentes
    private static final String TENANT_DB_NAME = "erp_ent_00000";

    @Override
    public void run(String... args) throws Exception {
        banner();

        // ── [0] Bootstrap schéma master ──────────────────────────────────────
        log.info("  [0/5] Bootstrap schéma benjeddou_erp...");
        try {
            bootstrapMasterSchema();
            log.info("  ✅ Schéma master prêt");
        } catch (Exception ex) {
            log.error("  ✗ Bootstrap master : {}", ex.getMessage());
        }

        // ── [0b] Créer codes_promo dans les DEUX bases (sécurité table manquante) ─
        try {
            creerCodesPromoSiAbsent();
            log.info("  ✅ Table codes_promo synchronisée (master + tenant)");
        } catch (Exception ex) {
            log.warn("  ⚠️  codes_promo sync : {} (non bloquant)", ex.getMessage());
        }

        // ── [1] SuperAdmin dans benjeddou_erp ─────────────────────────────────
        log.info("  [1/5] Synchronisation SuperAdmin SaaS...");
        try {
            creerOuSyncSuperAdmin();
        } catch (Exception ex) {
            log.error("  ✗ Erreur SuperAdmin : {}", ex.getMessage(), ex);
        }

        // ── [2] Bootstrap base tenant erp_ent_00000 ──────────────────────────
        log.info("  [2/5] Bootstrap base tenant erp_ent_00000...");
        try {
            bootstrapTenantDatabase();
            log.info("  ✅ Schéma tenant prêt");
        } catch (Exception ex) {
            log.error("  ✗ Bootstrap tenant : {}", ex.getMessage());
        }

        // ── [3] Charger les données démo si vides ─────────────────────────────
        log.info("  [3/5] Vérification données démo...");
        try {
            chargerDemoDonneesSiVide();
        } catch (Exception ex) {
            log.warn("  ⚠️  Données démo : {} (non bloquant)", ex.getMessage());
        }

        // ── [4] Synchroniser les utilisateurs démo ────────────────────────────
        log.info("  [4/5] Synchronisation utilisateurs démo (erp_ent_00000)...");
        try {
            syncUtilisateursDemo();
        } catch (Exception ex) {
            log.warn("  ⚠️  Sync démo ignoré : {} (non bloquant)", ex.getMessage());
        }

        // ── [5] Charger les DataSources de toutes les entreprises ─────────────
        log.info("  [5/5] Chargement des DataSources entreprises...");
        try {
            entrepriseService.chargerTousLesTenants();
            log.info("  ✅ DataSources tenants chargés");
        } catch (Exception ex) {
            log.warn("  ⚠️  Chargement tenants : {} (non bloquant)", ex.getMessage());
        }

        bannerFin();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // [0] Bootstrap schéma master via SQL embarqué
    // ─────────────────────────────────────────────────────────────────────────
    private void bootstrapMasterSchema() throws Exception {
        String sql = chargerSqlResource("db/master-schema.sql");
        executerScriptSQL(masterUrl, masterUser, masterPassword, sql);
    }

    // ───────────────────────────────────────────────────────────────────────────────
    // [0b] Créer les tables système (abonnements, theme_config, codes_promo)
    // dans les DEUX bases (master + tenant) si absentes
    // ───────────────────────────────────────────────────────────────────────────────
    private void creerCodesPromoSiAbsent() throws Exception {
        final String ddlCodesPromo = """
            CREATE TABLE IF NOT EXISTS `codes_promo` (
                `id`                     BIGINT        AUTO_INCREMENT PRIMARY KEY,
                `code`                   VARCHAR(50)   NOT NULL,
                `description`            VARCHAR(255)  NULL,
                `type_remise`            VARCHAR(20)   NOT NULL DEFAULT 'POURCENTAGE',
                `valeur`                 DECIMAL(10,3) NOT NULL,
                `montant_minimum`        DECIMAL(15,3) NOT NULL DEFAULT 0.000,
                `plafond_remise`         DECIMAL(15,3) NULL,
                `date_debut`             DATETIME      NULL,
                `date_fin`               DATETIME      NULL,
                `utilisations_max`       INT           NULL,
                `utilisations_actuelles` INT           NOT NULL DEFAULT 0,
                `actif`                  TINYINT(1)    NOT NULL DEFAULT 1,
                `date_creation`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
                `date_modification`      DATETIME      NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                CONSTRAINT uq_code_promo UNIQUE (`code`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;

        final String ddlAbonnements = """
            CREATE TABLE IF NOT EXISTS `abonnements` (
                `id`                 BIGINT AUTO_INCREMENT PRIMARY KEY,
                `client_id`          BIGINT NULL,
                `type_plan`          VARCHAR(20) NULL,
                `prix`               DECIMAL(10,3) DEFAULT 0.000,
                `duree_mois`         INT DEFAULT 1,
                `statut`             VARCHAR(20) DEFAULT 'EN_ATTENTE',
                `methode_paiement`   VARCHAR(30) NULL,
                `reference_paiement` VARCHAR(100) NULL,
                `date_debut`         DATETIME NULL,
                `date_fin`           DATETIME NULL,
                `date_soumission`    DATETIME DEFAULT CURRENT_TIMESTAMP,
                `notes_admin`        VARCHAR(500) NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;

        final String ddlThemeConfig = """
            CREATE TABLE IF NOT EXISTS `theme_config` (
                `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
                `primary_color`   VARCHAR(20) DEFAULT '#f97316',
                `accent_color`    VARCHAR(20) DEFAULT '#ea580c',
                `sidebar_color`   VARCHAR(20) DEFAULT '#0f172a',
                `font_family`     VARCHAR(100) DEFAULT 'Inter',
                `border_radius`   VARCHAR(20) DEFAULT '12px',
                `dark_mode`       TINYINT(1) DEFAULT 0,
                `compact_mode`    TINYINT(1) DEFAULT 0,
                `logo_text`       VARCHAR(100) DEFAULT 'BENJEDDOU ERP',
                `logo_url`        VARCHAR(255) NULL,
                `icon_set`        VARCHAR(30) DEFAULT 'outlined',
                `updated_at`      DATETIME NULL,
                `updated_by`      VARCHAR(100) NULL,
                `visible_modules` TEXT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;

        // Exécuter dans la base MASTER (benjeddou_erp)
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(masterUrl, masterUser, masterPassword);
             java.sql.Statement  st   = conn.createStatement()) {
            st.execute(ddlCodesPromo);
            st.execute(ddlAbonnements);
            st.execute(ddlThemeConfig);
            try { st.execute("ALTER TABLE `theme_config` ADD COLUMN `accent_color` VARCHAR(20) DEFAULT '#ea580c'"); } catch (Exception ignored) {}
            log.info("  ✔  Tables système OK dans benjeddou_erp");
        }

        // Exécuter dans la base TENANT (erp_ent_00000)
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(tenantUrl, tenantUser, tenantPassword);
             java.sql.Statement  st   = conn.createStatement()) {
            st.execute(ddlCodesPromo);
            st.execute(ddlAbonnements);
            st.execute(ddlThemeConfig);
            try { st.execute("ALTER TABLE `theme_config` ADD COLUMN `accent_color` VARCHAR(20) DEFAULT '#ea580c'"); } catch (Exception ignored) {}
            log.info("  ✔  Tables système OK dans erp_ent_00000");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // [1] SuperAdmin dans la base master
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    protected void creerOuSyncSuperAdmin() {
        final String login = "superadmin";
        final String email = "superadmin@benjeddou.com";
        // Mot de passe initial : admin123
        // Cohérent avec le fichier SQL de restauration : benjeddou_erp_master.sql
        final String mdp   = "admin123";

        Optional<Utilisateur> existing = utilisateurRepository.findByNomUtilisateur(login);

        if (existing.isEmpty()) {
            // ✅ Première création seulement — BCrypt appliqué
            utilisateurRepository.save(
                Utilisateur.builder()
                    .nomUtilisateur(login)
                    .email(email)
                    .motDePasse(passwordEncoder.encode(mdp))
                    .prenom("Super").nom("Admin")
                    .actif(true)
                    .languePreferee("fr")
                    .role(Role.SUPERADMIN)
                    .statutCompte(StatutCompte.ACTIF)
                    .modeTrial(false)
                    .doitChangerMotDePasse(false)
                    .entrepriseId(null)
                    .entrepriseSchema(null)
                    .build()
            );
            log.info("  ✅ SuperAdmin créé : {} / mot de passe initial : {}", login, mdp);
        } else {
            // ✅ Compte existant — NE JAMAIS RÉINITIALISER LE MOT DE PASSE
            // Seuls les attributs non-sensibles sont synchronisés.
            Utilisateur user = existing.get();
            boolean changed = false;

            // Réactiver si désactivé accidentellement
            if (!Boolean.TRUE.equals(user.getActif()))  { user.setActif(true);           changed = true; }
            // Corriger le rôle si altéré
            if (user.getRole() != Role.SUPERADMIN)      { user.setRole(Role.SUPERADMIN); changed = true; }
            // S'assurer qu'il n'appartient à aucun tenant
            if (user.getEntrepriseSchema() != null)     { user.setEntrepriseSchema(null);
                                                          user.setEntrepriseId(null);    changed = true; }
            // ❌ Le mot de passe N'EST PAS réinitialisé — l'admin peut le changer librement

            if (changed) {
                utilisateurRepository.save(user);
                log.info("  🔄 SuperAdmin synchronisé (rôle/statut uniquement)");
            } else {
                log.info("  ✓  SuperAdmin OK : {}", login);
            }
        }

        // S'assurer que l'entreprise démo existe dans la table entreprises
        creerEntrepriseDemoSiAbsente();
    }

    private void creerEntrepriseDemoSiAbsente() {
        try {
            if (entrepriseRepository.findBySchemaName(TENANT_DB_NAME).isEmpty()) {
                String sql = """
                    INSERT IGNORE INTO entreprises
                        (nom, schema_name, db_url, db_username, db_password, email_contact, statut)
                    VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE')
                    """;
                try (Connection conn = DriverManager.getConnection(masterUrl, masterUser, masterPassword);
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, "BEN JEDDOU ERP (Démo)");
                    ps.setString(2, TENANT_DB_NAME);
                    ps.setString(3, tenantUrl);
                    ps.setString(4, tenantUser);
                    ps.setString(5, tenantPassword);
                    ps.setString(6, "admin@benjeddou.com");
                    ps.executeUpdate();
                    log.info("  ✅ Entreprise démo créée dans master");
                }
            }
        } catch (Exception ex) {
            log.warn("  ⚠️  Entreprise démo : {}", ex.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // [2] Bootstrap base tenant erp_ent_00000
    // ─────────────────────────────────────────────────────────────────────────
    private void bootstrapTenantDatabase() throws Exception {
        // Ajouter createDatabaseIfNotExist=true pour créer la base si absente
        String urlAvecCreation = tenantUrl.contains("?")
            ? tenantUrl + "&createDatabaseIfNotExist=true"
            : tenantUrl + "?createDatabaseIfNotExist=true";

        String sql = chargerSqlResource("db/tenant-schema.sql");
        executerScriptSQL(urlAvecCreation, tenantUser, tenantPassword, sql);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // [3] Données démo si la table clients est vide
    // ─────────────────────────────────────────────────────────────────────────
    private void chargerDemoDonneesSiVide() throws Exception {
        try (Connection conn = DriverManager.getConnection(tenantUrl, tenantUser, tenantPassword)) {
            // Vérifier si les clients sont vides
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM clients")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    log.info("  📊 Base vide — chargement des données démo...");
                    String sql = chargerSqlResource("db/tenant-demo-data.sql");
                    executerScriptSQL(tenantUrl, tenantUser, tenantPassword, sql);
                    log.info("  ✅ Données démo chargées (clients, produits, factures, etc.)");
                } else {
                    log.info("  ✓  Données démo déjà présentes — skip");
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // [4] Utilisateurs démo dans erp_ent_00000 (JDBC direct)
    //
    // Mot de passe initial UNIFORME : admin123
    // Cohérent avec les fichiers SQL de restauration fournis.
    // Les administrateurs doivent changer leurs mots de passe après la première connexion.
    // ─────────────────────────────────────────────────────────────────────────
    private void syncUtilisateursDemo() throws Exception {

        // Mot de passe initial UNIQUE pour tous les comptes démo
        // Aligné avec les fichiers SQL : erp_ent_00000_data.sql / data.sql
        final String MDP_DEMO = "admin123";

        Object[][] demoUsers = {
            { "admin",       "admin@benjeddou.com",       "ADMIN",      "Mohamed", "Benjeddou" },
            { "commercial",  "commercial@benjeddou.com",  "COMMERCIAL", "Amir",    "Riahi"    },
            { "comptable",   "comptable@benjeddou.com",   "COMPTABLE",  "Rim",     "Tlili"    },
            { "stock",       "stock@benjeddou.com",       "STOCK",      "Sami",    "Jebali"   },
            { "client_demo", "demo@client.tn",            "CLIENT",     "Sofiane", "Dridi"    },
        };

        // ✅ INSERT IGNORE : n'insère que si l'utilisateur n'existe PAS encore.
        // ❌ NE JAMAIS utiliser ON DUPLICATE KEY UPDATE mot_de_passe :
        //    cela écraserait les mots de passe changés par les utilisateurs à chaque redémarrage.
        String sqlInsert = """
            INSERT IGNORE INTO utilisateurs
                (nom_utilisateur, email, mot_de_passe, prenom, nom,
                 actif, role, langue_preferee, statut_compte, doit_changer_mot_de_passe)
            VALUES (?, ?, ?, ?, ?, TRUE, ?, 'fr', 'ACTIF', FALSE)
            """;

        // Pour les comptes existants, on synchronise uniquement le rôle et le statut (jamais le mot de passe)
        String sqlUpdateRole = """
            UPDATE utilisateurs
               SET role = ?, actif = TRUE, statut_compte = 'ACTIF'
             WHERE nom_utilisateur = ? AND (role != ? OR actif = FALSE OR statut_compte != 'ACTIF')
            """;

        // Hash BCrypt calculé UNE FOIS pour admin123 (optimisation + cohérence)
        String hashDemoCommun = passwordEncoder.encode(MDP_DEMO);

        int crees = 0, syncs = 0;

        try (Connection conn = DriverManager.getConnection(tenantUrl, tenantUser, tenantPassword)) {
            for (Object[] u : demoUsers) {
                String login = (String) u[0];
                String role  = (String) u[2];

                // Tenter l'insertion (ignorée si déjà existant)
                try (PreparedStatement ps = conn.prepareStatement(sqlInsert)) {
                    ps.setString(1, login);
                    ps.setString(2, (String) u[1]);
                    ps.setString(3, hashDemoCommun);  // BCrypt d'admin123
                    ps.setString(4, (String) u[3]);
                    ps.setString(5, (String) u[4]);
                    ps.setString(6, role);
                    int rows = ps.executeUpdate();
                    if (rows > 0) crees++;
                }

                // Synchroniser rôle/statut si nécessaire (jamais le mot de passe)
                try (PreparedStatement ps = conn.prepareStatement(sqlUpdateRole)) {
                    ps.setString(1, role);
                    ps.setString(2, login);
                    ps.setString(3, role);
                    int rows = ps.executeUpdate();
                    if (rows > 0) syncs++;
                }
            }
            log.info("  ✅ Utilisateurs démo : {} créés, {} synchronisés (rôle/statut uniquement)", crees, syncs);
            if (crees > 0) {
                log.info("  📋 Comptes créés — mot de passe initial de tous les comptes démo : admin123");
                log.info("  ⚠️  Recommandation : changer les mots de passe après la première connexion.");
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utilitaires
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Charge un fichier SQL depuis classpath et l'exécute instruction par instruction.
     * Ignore les erreurs non-bloquantes (colonnes déjà existantes, etc.)
     */
    private void executerScriptSQL(String url, String user, String password, String script) throws Exception {
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            conn.setAutoCommit(true);

            // Découper par ";" en gérant les commentaires et lignes vides
            String[] instructions = script.split(";");
            int ok = 0, skip = 0;

            for (String instruction : instructions) {
                // Supprimer les lignes de commentaires au début du chunk,
                // puis vérifier s'il reste une instruction SQL valide
                String trimmed = supprimerCommentairesInitiaux(instruction);

                if (trimmed.isEmpty()) {
                    continue;
                }
                // Ignorer les instructions SELECT (vérification)
                if (trimmed.toUpperCase().startsWith("SELECT")) {
                    continue;
                }

                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(trimmed);
                    ok++;
                } catch (SQLException ex) {
                    // Ignorer : colonne déjà existante (1060), table déjà existante (1050),
                    // index déjà existant (1061), contrainte déjà existante (1022/1826)
                    int code = ex.getErrorCode();
                    if (code == 1060 || code == 1050 || code == 1061 || code == 1022 || code == 1826) {
                        skip++;
                    } else {
                        log.warn("  ⚠️  SQL ignoré [{}] : {} — {}", code,
                                trimmed.substring(0, Math.min(60, trimmed.length())).replace("\n", " "),
                                ex.getMessage().split("\n")[0]);
                        skip++;
                    }
                }
            }
            log.debug("  SQL : {} ok, {} ignorés", ok, skip);
        }
    }

    /**
     * Supprime les lignes de commentaires SQL (-- ...) au début d'un chunk.
     * Retourne la première ligne non-commentaire et ce qui suit.
     * Corriger le bug : un chunk « -- commentaire\nCREATE TABLE » était entièrement sauté.
     */
    private String supprimerCommentairesInitiaux(String chunk) {
        StringBuilder sb = new StringBuilder();
        boolean sqlTrouve = false;
        for (String ligne : chunk.split("\n")) {
            String l = ligne.trim();
            if (!sqlTrouve) {
                // Ignorer les lignes vides et les lignes de commentaires avant la 1ère instruction
                if (l.isEmpty() || l.startsWith("--") || l.startsWith("/*") || l.startsWith("*")) {
                    continue;
                }
                sqlTrouve = true;
            }
            sb.append(ligne).append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * Charge un fichier SQL depuis les ressources classpath.
     */
    private String chargerSqlResource(String chemin) throws Exception {
        ClassPathResource resource = new ClassPathResource(chemin);
        try (var reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            return FileCopyUtils.copyToString(reader);
        }
    }

    private void banner() {
        log.info("══════════════════════════════════════════════════════════════");
        log.info("  BENJEDDOU ERP — Démarrage Multi-Tenant SaaS");
        log.info("══════════════════════════════════════════════════════════════");
    }

    private void bannerFin() {
        log.info("══════════════════════════════════════════════════════════════");
        log.info("  ✅ BENJEDDOU ERP prêt — Architecture Multi-Tenant active");
        log.info("  Base SaaS    : benjeddou_erp");
        log.info("  SuperAdmin   : superadmin / admin123");
        log.info("  ─────────────────────────────────────────────────────────");
        log.info("  Base démo    : erp_ent_00000");
        log.info("  Admin        : admin       / admin123");
        log.info("  Commercial   : commercial  / admin123");
        log.info("  Comptable    : comptable   / admin123");
        log.info("  Stock        : stock       / admin123");
        log.info("  Client       : client_demo / admin123");
        log.info("  ─────────────────────────────────────────────────────────");
        log.info("  ⚠️  IMPORTANT : Les mots de passe ci-dessus sont les valeurs");
        log.info("  initiales uniquement. Ils ne sont JAMAIS réinitialisés au redémarrage.");
        log.info("  Moteur calc  : tables calculs_moteur + lignes_calcul + periodes_taux ✓");
        log.info("══════════════════════════════════════════════════════════════");
    }
}

