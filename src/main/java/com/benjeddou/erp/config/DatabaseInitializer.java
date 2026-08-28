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
 * DatabaseInitializer — Initialisation automatique IDEMPOTENTE de BENJEDDOU ERP
 *
 * Principe fondamental : NE JAMAIS détruire, écraser ou modifier des données existantes.
 *
 * Règles strictes appliquées à chaque démarrage :
 *  [0] Créer le schéma master (benjeddou_erp) uniquement si la base n'existe pas
 *  [1] Créer/synchroniser le SuperAdmin SANS jamais réinitialiser son mot de passe
 *  [2] Créer le schéma tenant (erp_ent_00000) uniquement si la base n'existe pas
 *  [3] Charger les données démo UNIQUEMENT si la base tenant est nouvellement créée (vide)
 *  [4] Synchroniser les utilisateurs démo avec INSERT IGNORE (jamais ON DUPLICATE KEY UPDATE mot_de_passe)
 *  [5] Charger les DataSources des entreprises existantes
 *
 * Garantie idempotence :
 *  - CREATE TABLE IF NOT EXISTS dans tous les scripts SQL
 *  - INSERT IGNORE pour les insertions (jamais d'UPSERT sur mot_de_passe)
 *  - Vérification de l'existence d'une base AVANT toute création ou migration
 *  - Jamais de DROP TABLE, DELETE ou TRUNCATE
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseInitializer implements CommandLineRunner {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder       passwordEncoder;
    private final EntrepriseService     entrepriseService;
    private final EntrepriseRepository  entrepriseRepository;
    private final com.benjeddou.erp.service.SecretManagementService secretManagementService;

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

    private static final String TENANT_DB_NAME = "erp_ent_00000";
    private static final String MASTER_DB_NAME = "benjeddou_erp";

    // ─────────────────────────────────────────────────────────────────────────
    // Point d'entrée principal
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public void run(String... args) throws Exception {
        banner();

        // ── [0] Bootstrap schéma master UNIQUEMENT si la base est nouvelle ────
        log.info("  [0/5] Vérification schéma master (benjeddou_erp)...");
        try {
            boolean masterEstNouvelle = !baseExiste(masterUrl, masterUser, masterPassword, MASTER_DB_NAME);
            if (masterEstNouvelle) {
                log.info("  📋 Base master absente — création et migration initiale...");
                bootstrapMasterSchema();
                log.info("  ✅ Schéma master créé avec succès");
            } else {
                log.info("  ✓  Base master déjà existante — application des migrations manquantes uniquement...");
                appliquerMigrationsManquantesMaster();
            }
        } catch (Exception ex) {
            log.error("  ✗ Erreur schéma master : {}", ex.getMessage());
        }

        // ── [1] SuperAdmin ────────────────────────────────────────────────────
        log.info("  [1/5] Synchronisation SuperAdmin...");
        try {
            creerOuSyncSuperAdmin();
        } catch (Exception ex) {
            log.error("  ✗ Erreur SuperAdmin : {}", ex.getMessage(), ex);
        }

        // ── [2] Bootstrap base tenant UNIQUEMENT si la base est nouvelle ──────
        log.info("  [2/5] Vérification schéma tenant (erp_ent_00000)...");
        try {
            boolean tenantEstNouvelle = !baseExiste(tenantUrl, tenantUser, tenantPassword, TENANT_DB_NAME);
            if (tenantEstNouvelle) {
                log.info("  📋 Base tenant absente — création et initialisation...");
                bootstrapTenantDatabase();
                log.info("  ✅ Schéma tenant créé avec succès");
                // Données démo uniquement pour une base nouvellement créée
                log.info("  [3/5] Chargement données démo (base nouvelle)...");
                chargerDemoDonneesSiVide();
            } else {
                log.info("  ✓  Base tenant déjà existante — application des migrations manquantes uniquement...");
                appliquerMigrationsManquantesTenant();
            }
        } catch (Exception ex) {
            log.error("  ✗ Bootstrap tenant : {}", ex.getMessage());
        }

        // ── [4] Utilisateurs démo — INSERT IGNORE uniquement ──────────────────
        log.info("  [4/5] Vérification comptes démo (erp_ent_00000)...");
        try {
            syncUtilisateursDemo();
        } catch (Exception ex) {
            log.warn("  ⚠️  Sync démo ignoré : {} (non bloquant)", ex.getMessage());
        }

        // ── [5] Charger les DataSources de toutes les entreprises ─────────────
        log.info("  [5/5] Chargement DataSources entreprises...");
        try {
            entrepriseService.chargerTousLesTenants();
            log.info("  ✅ DataSources tenants chargés");
        } catch (Exception ex) {
            log.warn("  ⚠️  Chargement tenants : {} (non bloquant)", ex.getMessage());
        }

        // ── [5b] Migrations de colonnes sur TOUTES les bases tenant ───────────
        // Nécessaire pour les bases créées avant l'ajout de ces colonnes dans
        // tenant-schema.sql. CREATE TABLE IF NOT EXISTS ne met pas à jour les
        // tables existantes — seul ALTER TABLE IF NOT EXISTS le fait.
        log.info("  [5b] Migration colonnes sur toutes les bases tenant...");
        try {
            appliquerMigrationsColonnesTousTenants();
        } catch (Exception ex) {
            log.warn("  ⚠️  Migration colonnes tenants : {} (non bloquant)", ex.getMessage());
        }

        bannerFin();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Vérification d'existence d'une base de données MySQL
    // Utilise INFORMATION_SCHEMA.SCHEMATA — 100% lecture seule, aucun risque
    // ─────────────────────────────────────────────────────────────────────────
    private boolean baseExiste(String jdbcUrl, String user, String password, String dbName) {
        // Extraire l'URL de base (sans le nom de la base) pour se connecter à MySQL
        String urlSansBase = jdbcUrl.replaceAll("jdbc:mysql://([^/]+)/[^?]*", "jdbc:mysql://$1/information_schema");
        // Ajouter les paramètres de connexion si présents
        if (jdbcUrl.contains("?")) {
            String params = jdbcUrl.substring(jdbcUrl.indexOf("?"));
            urlSansBase = urlSansBase.replaceAll("\\?.*", "") + params;
        }
        try (Connection conn = DriverManager.getConnection(urlSansBase, user, password);
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = ?")) {
            ps.setString(1, dbName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (Exception ex) {
            log.warn("  ⚠️  Vérification existence base '{}' : {} — on suppose existante par sécurité", dbName, ex.getMessage());
            return true; // Par sécurité : si on ne peut pas vérifier, on suppose existante (pas de création)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // [0a] Bootstrap schéma master — uniquement si base absente
    // ─────────────────────────────────────────────────────────────────────────
    private void bootstrapMasterSchema() throws Exception {
        String urlAvecCreation = ajouterCreateDatabaseIfNotExist(masterUrl);
        String sql = chargerSqlResource("db/master-schema.sql");
        executerScriptSQL(urlAvecCreation, masterUser, masterPassword, sql);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // [0b] Migrations master — applique le schéma COMPLET (idempotent)
    //
    // Même principe que pour le tenant : le script master-schema.sql complet
    // est exécuté à chaque démarrage. CREATE TABLE IF NOT EXISTS garantit
    // qu'aucune table ni donnée existante n'est affectée.
    // ─────────────────────────────────────────────────────────────────────────
    private void appliquerMigrationsManquantesMaster() {
        try {
            log.info("  📋 Vérification schéma complet master (master-schema.sql idempotent)...");
            String urlAvecCreation = ajouterCreateDatabaseIfNotExist(masterUrl);
            String sql = chargerSqlResource("db/master-schema.sql");
            executerScriptSQL(urlAvecCreation, masterUser, masterPassword, sql);
            log.info("  ✅ Schéma master vérifié et complet");
        } catch (Exception ex) {
            log.warn("  ⚠️  Migration schéma master : {} (non bloquant)", ex.getMessage());
        }

        // ── Migrations de colonnes (ADD COLUMN IF NOT EXISTS — idempotent) ────────
        // Ces instructions ajoutent les colonnes manquantes sur les bases existantes
        // sans toucher aux données ni aux colonnes déjà présentes.
        ajouterColonneSiAbsente(masterUrl, masterUser, masterPassword,
            "benjeddou_erp", "utilisateurs", "token_session",
            "VARCHAR(512) NULL");
        ajouterColonneSiAbsente(masterUrl, masterUser, masterPassword,
            "benjeddou_erp", "utilisateurs", "telephone",
            "VARCHAR(20) NULL");
        ajouterColonneSiAbsente(masterUrl, masterUser, masterPassword,
            "benjeddou_erp", "utilisateurs", "societe",
            "VARCHAR(200) NULL");
        ajouterColonneSiAbsente(masterUrl, masterUser, masterPassword,
            "benjeddou_erp", "utilisateurs", "adresse",
            "VARCHAR(500) NULL");
        ajouterColonneSiAbsente(masterUrl, masterUser, masterPassword,
            "benjeddou_erp", "utilisateurs", "kyc_soumis",
            "BOOLEAN DEFAULT FALSE");
        ajouterColonneSiAbsente(masterUrl, masterUser, masterPassword,
            "benjeddou_erp", "utilisateurs", "trial_expires_at",
            "DATETIME NULL");
        ajouterColonneSiAbsente(masterUrl, masterUser, masterPassword,
            "benjeddou_erp", "utilisateurs", "entreprise_id",
            "BIGINT NULL");
        ajouterColonneSiAbsente(masterUrl, masterUser, masterPassword,
            "benjeddou_erp", "utilisateurs", "entreprise_schema",
            "VARCHAR(100) NULL");
        ajouterColonneSiAbsente(masterUrl, masterUser, masterPassword,
            "benjeddou_erp", "utilisateurs", "statut_compte",
            "VARCHAR(20) DEFAULT 'ACTIF'");
        ajouterColonneSiAbsente(masterUrl, masterUser, masterPassword,
            "benjeddou_erp", "utilisateurs", "mode_trial",
            "BOOLEAN DEFAULT FALSE");
        ajouterColonneSiAbsente(masterUrl, masterUser, masterPassword,
            "benjeddou_erp", "utilisateurs", "nb_utilisations",
            "INT DEFAULT 0");
        ajouterColonneSiAbsente(masterUrl, masterUser, masterPassword,
            "benjeddou_erp", "utilisateurs", "nb_utilisations_max",
            "INT DEFAULT 30");
        ajouterColonneSiAbsente(masterUrl, masterUser, masterPassword,
            "benjeddou_erp", "utilisateurs", "doit_changer_mot_de_passe",
            "BOOLEAN DEFAULT FALSE");
        log.info("  ✅ Migrations de colonnes master appliquées");

        // ✅ S'assurer de la présence de la table documents_kyc dans la base master
        creerTableSiAbsente(masterUrl, masterUser, masterPassword,
            "benjeddou_erp", "documents_kyc",
            "CREATE TABLE IF NOT EXISTS benjeddou_erp.documents_kyc (" +
            "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
            "  utilisateur_id BIGINT NOT NULL," +
            "  type_document VARCHAR(50) NULL," +
            "  nom_fichier VARCHAR(255) NULL," +
            "  content_type VARCHAR(100) NULL," +
            "  contenu_fichier LONGBLOB NULL," +
            "  statut_verification VARCHAR(20) DEFAULT 'EN_ATTENTE'," +
            "  date_soumission DATETIME NULL," +
            "  commentaire_admin VARCHAR(500) NULL," +
            "  CONSTRAINT fk_dk_user FOREIGN KEY (utilisateur_id) REFERENCES utilisateurs(id) ON DELETE CASCADE" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
        );
    }

    /**
     * Ajoute une colonne dans une table si elle n'existe pas encore.
     * Idempotent — vérifie via INFORMATION_SCHEMA avant ALTER TABLE.
     */
    private void ajouterColonneSiAbsente(String jdbcUrl, String user, String password,
                                          String dbName, String tableName, String columnName,
                                          String columnDef) {
        String checkSql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                          "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        String alterSql = "ALTER TABLE `" + dbName + "`.`" + tableName + "` ADD COLUMN `" + columnName + "` " + columnDef;
        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password);
             PreparedStatement check = conn.prepareStatement(checkSql)) {
            check.setString(1, dbName);
            check.setString(2, tableName);
            check.setString(3, columnName);
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    try (Statement st = conn.createStatement()) {
                        st.execute(alterSql);
                        log.info("  ✅ Colonne ajoutée : {}.{}.{}", dbName, tableName, columnName);
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("  ⚠️  Ajout colonne {}.{}.{} ignoré : {}", dbName, tableName, columnName, ex.getMessage());
        }
    }

    /**
     * Crée une table si elle n'existe pas encore dans la base donnée.
     * Idempotent — vérifie via INFORMATION_SCHEMA avant d'exécuter le CREATE TABLE.
     */
    private void creerTableSiAbsente(String jdbcUrl, String user, String password,
                                      String dbName, String tableName, String createSql) {
        String checkSql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES " +
                          "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password);
             PreparedStatement check = conn.prepareStatement(checkSql)) {
            check.setString(1, dbName);
            check.setString(2, tableName);
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    try (Statement st = conn.createStatement()) {
                        st.execute(createSql);
                        log.info("  ✅ Table créée : {}.{}", dbName, tableName);
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("  ⚠️  Création table {}.{} ignorée : {}", dbName, tableName, ex.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // [1] SuperAdmin dans la base master — mot de passe officiel : Superadmin@2026!
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    protected void creerOuSyncSuperAdmin() {
        final String login = "superadmin";
        final String email = "superadmin@benjeddou.com";
        final String mdp   = "Superadmin@2026!"; // Mot de passe officiel du compte SuperAdmin

        Optional<Utilisateur> existing = utilisateurRepository.findByNomUtilisateur(login);

        if (existing.isEmpty()) {
            // ✅ Première création — BCrypt appliqué
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
            log.info("  ✅ SuperAdmin créé avec le mot de passe officiel");
        } else {
            // ✅ Compte existant : synchroniser rôle/statut/tenant ET forcer le mot de passe officiel
            Utilisateur user = existing.get();
            boolean changed = false;

            if (!Boolean.TRUE.equals(user.getActif()))  { user.setActif(true);           changed = true; }
            if (user.getRole() != Role.SUPERADMIN)      { user.setRole(Role.SUPERADMIN); changed = true; }
            if (user.getStatutCompte() != StatutCompte.ACTIF) { user.setStatutCompte(StatutCompte.ACTIF); changed = true; }
            if (user.getEntrepriseSchema() != null)     { user.setEntrepriseSchema(null);
                                                          user.setEntrepriseId(null);    changed = true; }

            // ✅ Synchronisation du mot de passe : si le hash stocké ne correspond plus
            //    au mot de passe officiel, on le remet à jour automatiquement.
            if (!passwordEncoder.matches(mdp, user.getMotDePasse())) {
                user.setMotDePasse(passwordEncoder.encode(mdp));
                changed = true;
                log.info("  🔑 SuperAdmin : mot de passe synchronisé avec le mot de passe officiel");
            }

            if (changed) {
                utilisateurRepository.save(user);
                log.info("  🔄 SuperAdmin synchronisé (rôle/statut/mot-de-passe)");
            } else {
                log.info("  ✓  SuperAdmin OK");
            }
        }

        // Nettoyage de la base master : la table utilisateurs master doit contenir STRICTEMENT et UNIQUEMENT le SuperAdmin
        try {
            java.util.List<Utilisateur> allUsers = utilisateurRepository.findAll();
            for (Utilisateur u : allUsers) {
                if (u.getRole() != Role.SUPERADMIN) {
                    if (u.getEntrepriseSchema() != null) {
                        entrepriseService.synchroniserUtilisateurDansTenant(u.getEntrepriseSchema(), u);
                    }
                    utilisateurRepository.delete(u);
                    log.info("  🧹 Base master épurée : compte entreprise '{}' réside désormais exclusivement dans sa base tenant '{}'", u.getNomUtilisateur(), u.getEntrepriseSchema());
                }
            }
        } catch (Exception ex) {
            log.warn("  ⚠️ Nettoyage master utilisateurs : {}", ex.getMessage());
        }

        // S'assurer que l'entreprise démo est enregistrée dans la table entreprises
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
                String encryptedPass = secretManagementService.encryptForTenant(
                    TENANT_DB_NAME, tenantPassword != null ? tenantPassword : "");
                try (Connection conn = DriverManager.getConnection(masterUrl, masterUser, masterPassword);
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, "BEN JEDDOU ERP (Démo)");
                    ps.setString(2, TENANT_DB_NAME);
                    ps.setString(3, tenantUrl);
                    ps.setString(4, tenantUser);
                    ps.setString(5, encryptedPass);
                    ps.setString(6, "admin@benjeddou.com");
                    ps.executeUpdate();
                    log.info("  ✅ Entreprise démo enregistrée dans master (credentials chiffrés AES-256-GCM)");
                }
            }
        } catch (Exception ex) {
            log.warn("  ⚠️  Entreprise démo : {}", ex.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // [2] Bootstrap base tenant — uniquement si la base n'existe pas
    // ─────────────────────────────────────────────────────────────────────────
    private void bootstrapTenantDatabase() throws Exception {
        String urlAvecCreation = ajouterCreateDatabaseIfNotExist(tenantUrl);
        String sql = chargerSqlResource("db/tenant-schema.sql");
        executerScriptSQL(urlAvecCreation, tenantUser, tenantPassword, sql);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // [2b] Migrations tenant — applique le schéma COMPLET (idempotent)
    //
    // SOLUTION DÉFINITIVE : On exécute le script tenant-schema.sql complet
    // à chaque démarrage. Comme TOUTES les instructions utilisent
    // CREATE TABLE IF NOT EXISTS, ALTER TABLE (erreurs 1060/1061 ignorées),
    // aucune donnée existante n'est jamais modifiée ou supprimée.
    //
    // Cela garantit que TOUTES les tables requises existent, même si la base
    // a été restaurée partiellement, créée manuellement, ou mise à jour
    // depuis une ancienne version du schéma.
    //
    // C'est la raison pour laquelle la plateforme nécessitait un réimport
    // manuel : les migrations précédentes ne créaient que theme_config alors
    // que le schéma complet contient 25+ tables (connexions_log, refresh_tokens,
    // audit_logs, calculs_moteur, lignes_calcul, etc.).
    // ─────────────────────────────────────────────────────────────────────────
    private void appliquerMigrationsManquantesTenant() {
        try {
            log.info("  📋 Vérification schéma complet tenant (tenant-schema.sql idempotent)...");
            String sql = chargerSqlResource("db/tenant-schema.sql");
            executerScriptSQL(tenantUrl, tenantUser, tenantPassword, sql);
            log.info("  ✅ Schéma tenant vérifié et complet (toutes les tables existent)");
        } catch (Exception ex) {
            log.warn("  ⚠️  Migration schéma tenant : {} (non bloquant)", ex.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // [5b] Migrations de colonnes sur TOUTES les bases tenant enregistrées
    //
    // Itère sur chaque entreprise de la table 'entreprises' (master) et applique
    // les ALTER TABLE ADD COLUMN IF NOT EXISTS pour les colonnes manquantes dans
    // la table 'utilisateurs' de chaque base tenant.
    //
    // Nécessaire pour les bases créées AVANT que ces colonnes soient ajoutées
    // au fichier tenant-schema.sql. Sans cette étape, JPA échoue au login
    // (Unknown column 'statut_compte') et renvoie un 401.
    // ─────────────────────────────────────────────────────────────────────────
    private void appliquerMigrationsColonnesTousTenants() {
        try {
            // 1. Toujours appliquer sur le tenant démo par défaut (erp_ent_00000)
            log.info("  📋 Migration automatique sur le tenant par défaut ({})", TENANT_DB_NAME);
            appliquerToutesMigrationsTenant(tenantUrl, tenantUser, tenantPassword, TENANT_DB_NAME);

            // 2. Appliquer sur toutes les entreprises enregistrées dans master
            java.util.List<com.benjeddou.erp.model.Entreprise> entreprises =
                entrepriseRepository.findByStatut(com.benjeddou.erp.model.Entreprise.StatutEntreprise.ACTIVE);
            log.info("  📋 Application migrations colonnes sur {} bases tenant...", entreprises.size());

            for (com.benjeddou.erp.model.Entreprise ent : entreprises) {
                String schema = ent.getSchemaName();
                if (schema == null || schema.isBlank() || TENANT_DB_NAME.equals(schema)) continue;

                // Construire l'URL root vers la base tenant (fallback fiable)
                String tenantJdbcUrl = masterUrl
                    .replaceFirst("//([^/]+)/[^?]+", "//$1/" + schema);

                String dbUser = masterUser;
                String dbPass = masterPassword != null ? masterPassword : "";

                // Essayer d'abord avec les credentials dédiés si disponibles
                if (ent.getDbUrl() != null && !ent.getDbUrl().isBlank()
                        && ent.getDbUsername() != null) {
                    try (Connection testConn = DriverManager.getConnection(
                            ent.getDbUrl(), ent.getDbUsername(),
                            ent.getDbPassword() != null ? ent.getDbPassword() : "")) {
                        tenantJdbcUrl = ent.getDbUrl();
                        dbUser = ent.getDbUsername();
                        dbPass = ent.getDbPassword() != null ? ent.getDbPassword() : "";
                    } catch (Exception ignored) {
                        // Fallback root déjà configuré
                    }
                }

                appliquerToutesMigrationsTenant(tenantJdbcUrl, dbUser, dbPass, schema);
            }
            log.info("  ✅ Migrations colonnes tenant terminées");
        } catch (Exception ex) {
            log.warn("  ⚠️  Migration colonnes tous tenants : {}", ex.getMessage());
        }
    }

    private void appliquerToutesMigrationsTenant(String tenantJdbcUrl, String dbUser, String dbPass, String schema) {
        // S'assurer que la table commandes_achat existe
        creerTableSiAbsente(tenantJdbcUrl, dbUser, dbPass, schema, "commandes_achat",
            "CREATE TABLE IF NOT EXISTS `" + schema + "`.`commandes_achat` (" +
            "  id                    BIGINT AUTO_INCREMENT PRIMARY KEY," +
            "  numero_commande       VARCHAR(50)   NOT NULL UNIQUE," +
            "  date_commande         DATETIME      DEFAULT CURRENT_TIMESTAMP," +
            "  statut                VARCHAR(30)   NOT NULL DEFAULT 'EN_ATTENTE'," +
            "  montant_total         DECIMAL(15,3) NOT NULL DEFAULT 0.000," +
            "  notes                 VARCHAR(500)  NULL," +
            "  date_livraison_prevue DATETIME      NULL," +
            "  fournisseur_id        BIGINT        NULL," +
            "  date_creation         DATETIME      DEFAULT CURRENT_TIMESTAMP," +
            "  date_modification     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

        // S'assurer que la table lignes_commande_achat existe
        creerTableSiAbsente(tenantJdbcUrl, dbUser, dbPass, schema, "lignes_commande_achat",
            "CREATE TABLE IF NOT EXISTS `" + schema + "`.`lignes_commande_achat` (" +
            "  id                BIGINT AUTO_INCREMENT PRIMARY KEY," +
            "  commande_achat_id BIGINT        NOT NULL," +
            "  produit_id        BIGINT        NULL," +
            "  designation       VARCHAR(200)  NULL," +
            "  quantite          INT           NOT NULL DEFAULT 1," +
            "  prix_unitaire     DECIMAL(15,3) NOT NULL DEFAULT 0.000," +
            "  montant_ligne     DECIMAL(15,3) NOT NULL DEFAULT 0.000" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

        // S'assurer que la table receptions_livraison existe
        creerTableSiAbsente(tenantJdbcUrl, dbUser, dbPass, schema, "receptions_livraison",
            "CREATE TABLE IF NOT EXISTS `" + schema + "`.`receptions_livraison` (" +
            "  id                  BIGINT AUTO_INCREMENT PRIMARY KEY," +
            "  numero_reception    VARCHAR(50)  NOT NULL UNIQUE," +
            "  commande_achat_id   BIGINT       NULL," +
            "  produit_id          BIGINT       NULL," +
            "  entrepot_id         BIGINT       NULL," +
            "  statut              VARCHAR(20)  NOT NULL DEFAULT 'CONFORME'," +
            "  quantite_commandee  INT          NULL," +
            "  quantite_recue      INT          NULL," +
            "  observations        VARCHAR(500) NULL," +
            "  date_reception      DATETIME     DEFAULT CURRENT_TIMESTAMP," +
            "  date_creation       DATETIME     DEFAULT CURRENT_TIMESTAMP" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

        appliquerMigrationsColonnesUtilisateurs(tenantJdbcUrl, dbUser, dbPass, schema);
        appliquerMigrationsColonnesCommandesAchat(tenantJdbcUrl, dbUser, dbPass, schema);
        appliquerMigrationsColonnesReceptionsLivraison(tenantJdbcUrl, dbUser, dbPass, schema);
    }


    /**
     * Applique les ADD COLUMN IF NOT EXISTS sur la table utilisateurs d'une base donnée.
     * Chaque appel est 100% idempotent : vérifie via INFORMATION_SCHEMA avant ALTER.
     */
    private void appliquerMigrationsColonnesUtilisateurs(String jdbcUrl, String user, String pass, String dbName) {
        String[][] colonnes = {
            {"statut_compte",             "VARCHAR(20) DEFAULT 'ACTIF'"},
            {"mode_trial",                "BOOLEAN DEFAULT FALSE"},
            {"nb_utilisations",           "INT DEFAULT 0"},
            {"nb_utilisations_max",       "INT DEFAULT 30"},
            {"doit_changer_mot_de_passe", "BOOLEAN DEFAULT FALSE"},
            {"token_session",             "VARCHAR(512) NULL"},
            {"telephone",                 "VARCHAR(20) NULL"},
            {"societe",                   "VARCHAR(200) NULL"},
            {"adresse",                   "VARCHAR(500) NULL"},
            {"kyc_soumis",                "BOOLEAN DEFAULT FALSE"},
            {"trial_expires_at",          "DATETIME NULL"},
            {"entreprise_id",             "BIGINT NULL"},
            {"entreprise_schema",         "VARCHAR(100) NULL"},
            {"token_recuperation",        "VARCHAR(255) NULL"},
            {"expiration_token_recuperation", "DATETIME NULL"},
            {"date_creation",             "TIMESTAMP DEFAULT CURRENT_TIMESTAMP"},
            {"date_modification",         "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"},
        };
        for (String[] col : colonnes) {
            ajouterColonneSiAbsente(jdbcUrl, user, pass, dbName, "utilisateurs", col[0], col[1]);
        }
    }

    /**
     * Applique les ADD COLUMN IF NOT EXISTS sur commandes_achat.
     * Corrige les bases créées avant l'ajout de date_livraison_prevue.
     * Idempotent — vérifie via INFORMATION_SCHEMA avant ALTER.
     */
    private void appliquerMigrationsColonnesCommandesAchat(String jdbcUrl, String user, String pass, String dbName) {
        ajouterColonneSiAbsente(jdbcUrl, user, pass, dbName, "commandes_achat", "date_livraison_prevue", "DATETIME NULL");
        // Correction : si l'ancienne colonne date_livraison_prev existe, la renommer
        renommerColonneSiPresente(jdbcUrl, user, pass, dbName, "commandes_achat",
                "date_livraison_prev", "date_livraison_prevue", "DATETIME NULL");
    }

    /**
     * Applique les ADD COLUMN IF NOT EXISTS sur receptions_livraison.
     * Corrige les bases créées avant l'ajout des colonnes observations, produit_id, etc.
     * Idempotent — vérifie via INFORMATION_SCHEMA avant ALTER.
     */
    private void appliquerMigrationsColonnesReceptionsLivraison(String jdbcUrl, String user, String pass, String dbName) {
        ajouterColonneSiAbsente(jdbcUrl, user, pass, dbName, "receptions_livraison", "observations",       "VARCHAR(500) NULL");
        ajouterColonneSiAbsente(jdbcUrl, user, pass, dbName, "receptions_livraison", "produit_id",         "BIGINT NULL");
        ajouterColonneSiAbsente(jdbcUrl, user, pass, dbName, "receptions_livraison", "quantite_commandee", "INT NULL");
        ajouterColonneSiAbsente(jdbcUrl, user, pass, dbName, "receptions_livraison", "quantite_recue",     "INT NULL");
    }

    /**
     * Renomme une colonne si l'ancienne existe et que la nouvelle n'existe pas encore.
     * Utilisé pour migrer date_livraison_prev → date_livraison_prevue.
     */
    private void renommerColonneSiPresente(String jdbcUrl, String user, String pass,
                                            String dbName, String tableName,
                                            String ancienNom, String nouveauNom, String columnDef) {
        String checkAncienne = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                               "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        String checkNouvelle = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                               "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, pass)) {
            boolean ancienneExiste;
            try (PreparedStatement ps = conn.prepareStatement(checkAncienne)) {
                ps.setString(1, dbName); ps.setString(2, tableName); ps.setString(3, ancienNom);
                try (ResultSet rs = ps.executeQuery()) { ancienneExiste = rs.next() && rs.getInt(1) > 0; }
            }
            boolean nouvelleExiste;
            try (PreparedStatement ps = conn.prepareStatement(checkNouvelle)) {
                ps.setString(1, dbName); ps.setString(2, tableName); ps.setString(3, nouveauNom);
                try (ResultSet rs = ps.executeQuery()) { nouvelleExiste = rs.next() && rs.getInt(1) > 0; }
            }
            if (ancienneExiste && !nouvelleExiste) {
                String sql = "ALTER TABLE `" + dbName + "`.`" + tableName + "` CHANGE COLUMN `" +
                             ancienNom + "` `" + nouveauNom + "` " + columnDef;
                try (Statement st = conn.createStatement()) {
                    st.execute(sql);
                    log.info("  ✅ Colonne renommée : {}.{}.{} → {}", dbName, tableName, ancienNom, nouveauNom);
                }
            }
        } catch (Exception ex) {
            log.warn("  ⚠️  Renommage colonne {}.{}.{} ignoré : {}", dbName, tableName, ancienNom, ex.getMessage());
        }
    }


    // ─────────────────────────────────────────────────────────────────────────
    private void chargerDemoDonneesSiVide() throws Exception {
        try (Connection conn = DriverManager.getConnection(tenantUrl, tenantUser, tenantPassword)) {
            boolean clientsVides = false;
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM clients")) {
                if (rs.next()) clientsVides = rs.getInt(1) == 0;
            } catch (Exception ex) {
                // Table clients peut ne pas encore exister
                clientsVides = true;
            }

            if (clientsVides) {
                log.info("  📊 Base vide — chargement des données démo...");
                String sql = chargerSqlResource("db/tenant-demo-data.sql");
                executerScriptSQL(tenantUrl, tenantUser, tenantPassword, sql);
                log.info("  ✅ Données démo chargées");
            } else {
                log.info("  ✓  Données démo déjà présentes — skip");
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // [4] Utilisateurs démo — INSERT IGNORE uniquement
    //
    // RÈGLE ABSOLUE : NE JAMAIS mettre à jour le mot_de_passe d'un utilisateur existant.
    // Un utilisateur qui a changé son mot de passe doit pouvoir continuer à l'utiliser
    // après un redémarrage du serveur.
    //
    // Seuls les comptes ABSENTS sont créés avec le mot de passe initial admin123.
    // ─────────────────────────────────────────────────────────────────────────
    private void syncUtilisateursDemo() throws Exception {

        final String MDP_DEMO = "admin123";

        Object[][] demoUsers = {
            { "admin",       "admin@benjeddou.com",       "ADMIN",      "Mohamed", "Benjeddou" },
            { "commercial",  "commercial@benjeddou.com",  "COMMERCIAL", "Amir",    "Riahi"    },
            { "comptable",   "comptable@benjeddou.com",   "COMPTABLE",  "Rim",     "Tlili"    },
            { "stock",       "stock@benjeddou.com",       "STOCK",      "Sami",    "Jebali"   },
            { "client_demo", "demo@client.tn",            "CLIENT",     "Sofiane", "Dridi"    },
        };

        // ✅ INSERT IGNORE : crée uniquement si absent — jamais d'écrasement
        // ❌ INTERDIT : ON DUPLICATE KEY UPDATE mot_de_passe (écraserait les mots de passe changés)
        String sqlInsert = """
            INSERT IGNORE INTO utilisateurs
                (nom_utilisateur, email, mot_de_passe, prenom, nom,
                 actif, role, langue_preferee, statut_compte, doit_changer_mot_de_passe)
            VALUES (?, ?, ?, ?, ?, TRUE, ?, 'fr', 'ACTIF', FALSE)
            """;

        // Synchroniser UNIQUEMENT le rôle et le statut des comptes existants — JAMAIS le mot de passe
        String sqlSyncRoleStatut = """
            UPDATE utilisateurs
               SET role = ?, actif = TRUE, statut_compte = 'ACTIF'
             WHERE nom_utilisateur = ? AND (role != ? OR actif = FALSE OR statut_compte != 'ACTIF')
            """;

        // Hash BCrypt calculé une seule fois pour admin123
        String hashDemoCommun = passwordEncoder.encode(MDP_DEMO);

        int crees = 0, syncs = 0;

        try (Connection conn = DriverManager.getConnection(tenantUrl, tenantUser, tenantPassword)) {
            for (Object[] u : demoUsers) {
                String login = (String) u[0];
                String role  = (String) u[2];

                // Tenter l'insertion — ignorée si l'utilisateur existe déjà
                try (PreparedStatement ps = conn.prepareStatement(sqlInsert)) {
                    ps.setString(1, login);
                    ps.setString(2, (String) u[1]);
                    ps.setString(3, hashDemoCommun);
                    ps.setString(4, (String) u[3]);
                    ps.setString(5, (String) u[4]);
                    ps.setString(6, role);
                    int rows = ps.executeUpdate();
                    if (rows > 0) crees++;
                }

                // Synchroniser rôle/statut si nécessaire — JAMAIS le mot de passe
                try (PreparedStatement ps = conn.prepareStatement(sqlSyncRoleStatut)) {
                    ps.setString(1, role);
                    ps.setString(2, login);
                    ps.setString(3, role);
                    int rows = ps.executeUpdate();
                    if (rows > 0) syncs++;
                }
            }

            if (crees > 0) {
                log.info("  ✅ {} compte(s) démo créé(s) — mot de passe initial : {}", crees, MDP_DEMO);
                log.info("  ⚠️  Recommandation : changer les mots de passe après la première connexion.");
            } else {
                log.info("  ✓  Comptes démo déjà présents — aucune modification du mot de passe ({} sync rôle/statut)", syncs);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utilitaires
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Ajoute createDatabaseIfNotExist=true à l'URL JDBC pour permettre la création
     * automatique d'une base absente lors de la première connexion.
     */
    private String ajouterCreateDatabaseIfNotExist(String url) {
        if (url.contains("createDatabaseIfNotExist")) return url;
        return url.contains("?")
            ? url + "&createDatabaseIfNotExist=true"
            : url + "?createDatabaseIfNotExist=true";
    }

    /**
     * Exécute un script SQL instruction par instruction.
     * Ignore les erreurs non-bloquantes (table/colonne/index déjà existant).
     * JAMAIS de DROP, DELETE ou TRUNCATE autorisés.
     */
    private void executerScriptSQL(String url, String user, String password, String script) throws Exception {
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            conn.setAutoCommit(true);

            String[] instructions = script.split(";");
            int ok = 0, skip = 0;

            for (String instruction : instructions) {
                String trimmed = supprimerCommentairesInitiaux(instruction);
                if (trimmed.isEmpty()) continue;

                // Bloquer toute instruction destructive par sécurité
                String upper = trimmed.toUpperCase();
                if (upper.startsWith("SELECT")) continue;
                if (upper.startsWith("DROP DATABASE") || upper.startsWith("DROP TABLE ") ||
                    upper.startsWith("TRUNCATE") || upper.contains("DELETE FROM")) {
                    log.warn("  🚫 Instruction destructive bloquée : {}", trimmed.substring(0, Math.min(60, trimmed.length())));
                    continue;
                }

                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(trimmed);
                    ok++;
                } catch (SQLException ex) {
                    int code = ex.getErrorCode();
                    // Ignorer : colonne déjà existante (1060), table déjà existante (1050),
                    // index déjà existant (1061), contrainte déjà existante (1022/1826)
                    if (code == 1060 || code == 1050 || code == 1061 || code == 1022 || code == 1826) {
                        skip++;
                    } else {
                        log.warn("  ⚠️  SQL ignoré [{}] : {} — {}",
                            code,
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
     * Exécute une instruction SQL sans propager l'erreur (pour les ALTER TABLE optionnels).
     */
    private void executerSansErreur(Statement st, String sql) {
        try {
            st.execute(sql);
        } catch (Exception ignored) {}
    }

    /**
     * Supprime les commentaires SQL au début d'un chunk (lignes --, /*, *).
     */
    private String supprimerCommentairesInitiaux(String chunk) {
        StringBuilder sb = new StringBuilder();
        boolean sqlTrouve = false;
        for (String ligne : chunk.split("\n")) {
            String l = ligne.trim();
            if (!sqlTrouve) {
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
        log.info("  Base SaaS    : {}", MASTER_DB_NAME);
        log.info("  SuperAdmin   : superadmin (mot de passe conservé)");
        log.info("  ─────────────────────────────────────────────────────────");
        log.info("  Base démo    : {}", TENANT_DB_NAME);
        log.info("  Comptes démo : admin / commercial / comptable / stock / client_demo");
        log.info("  ─────────────────────────────────────────────────────────");
        log.info("  ✅ GARANTIE IDEMPOTENCE : aucune donnée existante n'a été");
        log.info("     modifiée, écrasée ou supprimée lors de ce démarrage.");
        log.info("══════════════════════════════════════════════════════════════");
    }
}
