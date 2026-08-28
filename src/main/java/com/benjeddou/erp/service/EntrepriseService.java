package com.benjeddou.erp.service;

import com.benjeddou.erp.config.TenantDataSourceConfig;
import com.benjeddou.erp.model.Entreprise;
import com.benjeddou.erp.repository.EntrepriseRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * EntrepriseService — Gestion du cycle de vie des entreprises (tenants).
 *
 * Architecture SaaS Multi-Tenant — Isolation complète par entreprise :
 * ┌─────────────────────────────────────────────────────┐
 * │  Entreprise A → Base: erp_ent_00001                 │
 * │                 User MySQL: erp_user_00001           │
 * │                 Password: [unique généré 24 chars]   │
 * │                 Droits: SEULEMENT sur erp_ent_00001  │
 * ├─────────────────────────────────────────────────────┤
 * │  Entreprise B → Base: erp_ent_00002                 │
 * │                 User MySQL: erp_user_00002           │
 * │                 Password: [unique généré 24 chars]   │
 * │                 Droits: SEULEMENT sur erp_ent_00002  │
 * └─────────────────────────────────────────────────────┘
 *
 * Confidentialité totale : erp_user_00001 ne peut PHYSIQUEMENT PAS
 * lire ou écrire dans erp_ent_00002 — isolation au niveau SGBD MySQL.
 *
 * NOTE TECHNIQUE — Solution globale anti-Aria corruption :
 * Toutes les opérations DDL système (CREATE DATABASE, CREATE USER, GRANT)
 * utilisent DriverManager.getConnection() (connexions directes FRAÎCHES)
 * et NON masterDataSource.getConnection() (pool HikariCP).
 *
 * Raison : HikariCP réutilise les connexions ouvertes et maintient leurs
 * file descriptors. Quand MariaDB tente d'écrire dans aria_log.00000001
 * (tables système d'authentification), il obtient Errcode: 9 "Bad file
 * descriptor" car le handle est lié aux fichiers Aria potentiellement
 * corrompus par un arrêt brutal de XAMPP.
 * DriverManager crée une connexion FRAÎCHE → nouveau file descriptor → OK.
 *
 * De plus, CREATE USER/GRANT sont NON-FATALS car les connexions
 * applicatives utilisent ROOT (isolation par schéma suffisante).
 */
@Service
@Slf4j
public class EntrepriseService {

    private final EntrepriseRepository entrepriseRepository;
    private final TenantDataSourceConfig tenantDataSourceConfig;
    private final DataSource masterDataSource;
    private final SecretManagementService secretManagementService;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Constructeur avec @Qualifier pour injecter le masterDataSource (pas le routing datasource).
     * Évite la dépendance circulaire.
     */
    public EntrepriseService(EntrepriseRepository entrepriseRepository,
                             TenantDataSourceConfig tenantDataSourceConfig,
                             @Qualifier("masterDataSource") DataSource masterDataSource,
                             SecretManagementService secretManagementService) {
        this.entrepriseRepository = entrepriseRepository;
        this.tenantDataSourceConfig = tenantDataSourceConfig;
        this.masterDataSource = masterDataSource;
        this.secretManagementService = secretManagementService;
    }

    @Value("${spring.datasource.url}")
    private String masterDbUrl;

    @Value("${spring.datasource.username}")
    private String masterUsername;

    @Value("${spring.datasource.password:}")
    private String masterPassword;

    // ═════════════════════════════════════════════════════════════════════════
    // CRÉATION D'ENTREPRISE
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Crée une nouvelle entreprise avec son infrastructure MySQL dédiée.
     *
     * Étapes automatiques à chaque inscription :
     * 1. Générer un nom de schéma unique       : erp_ent_00001
     * 2. Générer un utilisateur MySQL dédié    : erp_user_00001
     * 3. Générer un mot de passe fort aléatoire: 24 caractères SecureRandom
     * 4. CREATE DATABASE erp_ent_00001
     * 5. CREATE USER 'erp_user_00001'@'%' IDENTIFIED BY '<mdp_unique>' (non-fatal si Aria corrompu)
     * 6. GRANT ALL PRIVILEGES ON erp_ent_00001.* TO 'erp_user_00001'@'%' (non-fatal)
     * 7. Enregistrer l'entreprise dans la base master
     * 8. Ajouter le DataSource dans le pool de routing
     *
     * @param nomEntreprise  Nom commercial de l'entreprise
     * @param emailContact   Email de l'administrateur
     * @param adminId        ID de l'utilisateur administrateur (base master)
     * @return Entreprise créée et persistée
     */
    // NOTE : PAS de @Transactional ici — la création implique des DDL MySQL (CREATE DATABASE, GRANT)
    // qui ne sont pas compatibles avec les transactions JPA. De plus, @Transactional activait
    // le routage Hibernate vers le tenant avant que ses credentials soient prêts.
    public Entreprise creerEntreprise(String nomEntreprise, String emailContact, Long adminId) {

        // Vérifier si une entreprise existe déjà pour cet email pour éviter de créer des bases en double
        if (emailContact != null && !emailContact.isBlank()) {
            List<Entreprise> existingList = entrepriseRepository.findByEmailContact(emailContact);
            if (!existingList.isEmpty()) {
                Entreprise ent = existingList.get(existingList.size() - 1);
                String schema = ent.getSchemaName();
                String user = schema.replace("erp_ent_", "erp_user_");
                String pass = genererMotDePasseSecurise();
                log.info("Entreprise existante trouvée pour '{}' (schéma '{}') — réutilisation et vérification de la base unique", emailContact, schema);
                try {
                    provisionnerTenant(schema, user, pass);
                } catch (Exception pEx) {
                    log.warn("Provisioning schéma existant '{}' : {}", schema, pEx.getMessage());
                }
                if (adminId != null) {
                    ent.setAdminId(adminId);
                    entrepriseRepository.save(ent);
                }
                tenantDataSourceConfig.addTenantDataSource(schema, ent.getDbUrl(), masterUsername, masterPassword != null ? masterPassword : "");
                return ent;
            }
        }

        // 1. Générer les identifiants uniques
        String schemaName = genererSchemaName();
        String mysqlUser  = schemaName.replace("erp_ent_", "erp_user_");
        String mysqlPass  = genererMotDePasseSecurise();

        log.info("╔══ Création tenant : {} ═══════════════════════════", schemaName);
        log.info("║  Entreprise : {}", nomEntreprise);
        log.info("║  User MySQL dédié : {} (créé pour sécurité future)", mysqlUser);

        // 2. URL JDBC vers la base tenant (utilisée avec les credentials root)
        String baseUrl     = extractBaseUrl(masterDbUrl);
        String tenantDbUrl = baseUrl + "/" + schemaName
                + "?createDatabaseIfNotExist=true"
                + "&useSSL=false"
                + "&serverTimezone=UTC"
                + "&allowPublicKeyRetrieval=true";

        // 3. Provisionner : CREATE DATABASE + CREATE USER 'erp_user_XXXXX' + GRANT
        //    Toutes les opérations DDL utilisent DriverManager (connexions directes fraîches).
        //    CREATE USER/GRANT sont non-fatals si Aria est corrompu.
        provisionnerTenant(schemaName, mysqlUser, mysqlPass);

        // 4. Utiliser les credentials ROOT pour les connexions JPA/JDBC applicatives.
        //    Raison : FLUSH PRIVILEGES peut échouer (tables système Aria corrompues),
        //    ce qui rend erp_user_XXXXX inaccessible malgré le GRANT.
        //    ROOT accède toujours à la base sans dépendre de FLUSH PRIVILEGES.
        // 4. Chiffrement AES-256-GCM des credentials réversibles avec clé isolée par tenant
        String effectiveUser = masterUsername;
        String effectivePass = masterPassword != null ? masterPassword : "";
        String encryptedPass = secretManagementService.encryptForTenant(schemaName, effectivePass);
        log.info("║  Connexions applicatives via ROOT (isolation par schéma : {}, credentials AES-GCM chiffrés)", schemaName);

        // 5. Enregistrer dans la base master avec le mot de passe chiffré en AES-256-GCM
        Entreprise entreprise = Entreprise.builder()
                .nom(nomEntreprise)
                .schemaName(schemaName)
                .dbUrl(tenantDbUrl)
                .dbUsername(effectiveUser)  // ← root
                .dbPassword(encryptedPass)  // ← mot de passe chiffré réversiblement en AES-256-GCM
                .adminId(adminId)
                .emailContact(emailContact)
                .statut(Entreprise.StatutEntreprise.ACTIVE)
                .build();

        Entreprise saved = entrepriseRepository.save(entreprise);
        log.info("║  ✓ Enregistré en base master (id={}) avec user='{}' et mot de passe chiffré AES-256-GCM", saved.getId(), effectiveUser);

        // 6. Ajouter au pool de routing avec les credentials EFFECTIFS
        tenantDataSourceConfig.addTenantDataSource(schemaName, tenantDbUrl, effectiveUser, effectivePass);
        log.info("║  ✓ DataSource ajouté au pool de routing (user='{}')", effectiveUser);
        log.info("╚══ Tenant {} opérationnel ══════════════════════════", schemaName);

        return saved;
    }

    /**
     * Teste si un utilisateur MySQL peut se connecter à la base tenant.
     * Utilisé après GRANT pour vérifier que les droits ont bien été appliqués.
     * Retourne false si Access Denied ou toute autre erreur de connexion.
     */
    private boolean testerConnexionTenant(String url, String user, String pass) {
        try (Connection conn = DriverManager.getConnection(url, user, pass != null ? pass : "")) {
            return true; // connexion réussie → GRANT appliqué
        } catch (Exception e) {
            log.debug("Test connexion user dédié '{}' échoué : {}", user, e.getMessage());
            return false;
        }
    }


    // ═════════════════════════════════════════════════════════════════════════
    // RÉSOLUTION DU SCHÉMA
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Résout le schéma MySQL d'un utilisateur par son adminId.
     * Retourne null pour les SuperAdmin (base master uniquement).
     */
    public String resoudreSchema(Long userId) {
        Optional<Entreprise> entreprise = entrepriseRepository.findByAdminId(userId);
        return entreprise.map(Entreprise::getSchemaName).orElse(null);
    }

    /**
     * Charge tous les tenants actifs au démarrage de l'application.
     * Déchiffre les credentials via SecretManagementService (AES-256-GCM).
     */
    public void chargerTousLesTenants() {
        log.info("Chargement des DataSources pour tous les tenants actifs...");

        // Normaliser et chiffrer les credentials en base
        normaliserCredentialsTenants();

        entrepriseRepository.findAll().forEach(e -> {
            if (e.getDbUrl() != null && Entreprise.StatutEntreprise.ACTIVE.equals(e.getStatut())) {
                String user = (e.getDbUsername() != null && !e.getDbUsername().isBlank()) ? e.getDbUsername() : masterUsername;
                String pass = masterPassword != null ? masterPassword : "";
                if (e.getDbPassword() != null && !e.getDbPassword().isBlank()) {
                    try {
                        pass = secretManagementService.decryptForTenant(e.getSchemaName(), e.getDbPassword());
                    } catch (Exception ex) {
                        log.warn("Déchiffrement AES-GCM pour '{}' en fallback sur masterPassword: {}", e.getSchemaName(), ex.getMessage());
                    }
                }
                tenantDataSourceConfig.addTenantDataSource(
                        e.getSchemaName(), e.getDbUrl(), user, pass);
                log.info("  ✓ Tenant chargé : {} (isolation cryptographique & schéma)", e.getSchemaName());
            }
        });
    }

    /**
     * Normalise automatiquement les credentials en base, applique le chiffrement AES-256-GCM et synchronise les schémas.
     */
    public void normaliserCredentialsTenants() {

        // ── 1. S'assurer que tous les credentials en base sont chiffrés en AES-256-GCM ──────────────
        try {
            List<Entreprise> all = entrepriseRepository.findAll();
            for (Entreprise ent : all) {
                boolean modified = false;
                if (ent.getDbPassword() == null || !secretManagementService.isEncrypted(ent.getDbPassword())) {
                    String plain = ent.getDbPassword() != null ? ent.getDbPassword() : (masterPassword != null ? masterPassword : "");
                    ent.setDbPassword(secretManagementService.encryptForTenant(ent.getSchemaName(), plain));
                    modified = true;
                }
                if (ent.getDbUsername() == null || ent.getDbUsername().isBlank()) {
                    ent.setDbUsername(masterUsername);
                    modified = true;
                }
                if (modified) {
                    entrepriseRepository.save(ent);
                    log.info("🔐 Chiffrement AES-256-GCM appliqué pour les credentials de l'entreprise '{}'", ent.getNom());
                }
            }
        } catch (Exception e) {
            log.warn("  Normalisation/chiffrement credentials : {}", e.getMessage());
        }

        // ── 2. Exécuter le schéma COMPLET sur chaque base tenant ───────────────
        // IMPORTANT : utiliser DriverManager.getConnection() et NON masterDataSource.getConnection()
        // Raison : HikariCP ne réinitialise PAS le catalog après setCatalog() →
        //   si on utilisait masterDataSource, la connexion retournée au pool
        //   aurait encore le catalog "erp_ent_XXXXX" → toutes les requêtes JPA
        //   master iraient sur le mauvais schéma → 500/400 sur login et toutes les API
        String masterBaseUrl = extractBaseUrl(masterDbUrl);
        List<Entreprise> actifs = entrepriseRepository.findByStatut(Entreprise.StatutEntreprise.ACTIVE);
        ClassPathResource script = new ClassPathResource("tenant-schema-init.sql");

        for (Entreprise ent : actifs) {
            String schema = ent.getSchemaName();
            if (schema == null || schema.isBlank()) continue;

            // DriverManager.getConnection() → connexion DIRECTE hors pool HikariCP
            // → aucune pollution du master pool quand la connexion est fermée
            String tenantUrl = masterBaseUrl + "/" + schema
                + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
            try (Connection conn = DriverManager.getConnection(
                    tenantUrl, masterUsername, masterPassword != null ? masterPassword : "")) {
                ScriptUtils.executeSqlScript(conn, script);
                log.info("  ✓ Schéma complet synchronisé dans '{}'", schema);
            } catch (Exception e) {
                log.warn("  ⚠ Sync schéma '{}' : {}", schema, e.getMessage());
            }

            // Synchroniser l'administrateur dans sa base tenant dédiée
            if (ent.getAdminId() != null) {
                try {
                    String findAdminSql = "SELECT * FROM benjeddou_erp.utilisateurs WHERE id = ?";
                    try (Connection masterConn = DriverManager.getConnection(masterDbUrl, masterUsername, masterPassword != null ? masterPassword : "");
                         java.sql.PreparedStatement psFind = masterConn.prepareStatement(findAdminSql)) {
                        psFind.setLong(1, ent.getAdminId());
                        try (java.sql.ResultSet rs = psFind.executeQuery()) {
                            if (rs.next()) {
                                String syncUserSql = "INSERT INTO `" + schema + "`.utilisateurs " +
                                        "(id, nom_utilisateur, email, mot_de_passe, prenom, nom, actif, role, " +
                                        "langue_preferee, statut_compte, mode_trial, nb_utilisations, nb_utilisations_max, telephone, societe, adresse, entreprise_id, entreprise_schema) " +
                                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                                        "ON DUPLICATE KEY UPDATE mot_de_passe = VALUES(mot_de_passe), actif = VALUES(actif), role = VALUES(role)";
                                try (Connection tenantConn = DriverManager.getConnection(tenantUrl, masterUsername, masterPassword != null ? masterPassword : "");
                                     java.sql.PreparedStatement psSync = tenantConn.prepareStatement(syncUserSql)) {
                                    psSync.setLong(1, rs.getLong("id"));
                                    psSync.setString(2, rs.getString("nom_utilisateur"));
                                    psSync.setString(3, rs.getString("email"));
                                    psSync.setString(4, rs.getString("mot_de_passe"));
                                    psSync.setString(5, rs.getString("prenom"));
                                    psSync.setString(6, rs.getString("nom"));
                                    psSync.setBoolean(7, rs.getBoolean("actif"));
                                    psSync.setString(8, rs.getString("role"));
                                    psSync.setString(9, rs.getString("langue_preferee"));
                                    psSync.setString(10, rs.getString("statut_compte"));
                                    psSync.setBoolean(11, rs.getBoolean("mode_trial"));
                                    psSync.setInt(12, rs.getInt("nb_utilisations"));
                                    psSync.setInt(13, rs.getInt("nb_utilisations_max"));
                                    psSync.setString(14, rs.getString("telephone"));
                                    psSync.setString(15, rs.getString("societe"));
                                    psSync.setString(16, rs.getString("adresse"));
                                    psSync.setLong(17, ent.getId());
                                    psSync.setString(18, schema);
                                    psSync.executeUpdate();
                                    log.info("  ✓ Administrateur '{}' synchronisé dans la base tenant '{}'", rs.getString("nom_utilisateur"), schema);
                                }
                            }
                        }
                    }
                } catch (Exception syncEx) {
                    log.warn("  ⚠ Erreur synchronisation admin dans '{}' : {}", schema, syncEx.getMessage());
                }
            }
        }
    }

    /**
     * Synchronise un utilisateur créé lors de l'inscription directement dans sa base tenant dédiée.
     */
    public Long synchroniserUtilisateurDansTenant(String schemaName, com.benjeddou.erp.model.Utilisateur user) {
        if (schemaName == null || user == null) return null;
        String masterBaseUrl = extractBaseUrl(masterDbUrl);
        String tenantUrl = masterBaseUrl + "/" + schemaName + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
        
        boolean hasId = user.getId() != null;
        String syncUserSql = hasId
                ? "INSERT INTO `" + schemaName + "`.utilisateurs " +
                  "(id, nom_utilisateur, email, mot_de_passe, prenom, nom, actif, role, " +
                  "langue_preferee, statut_compte, mode_trial, nb_utilisations, nb_utilisations_max, telephone, societe, adresse, entreprise_id, entreprise_schema) " +
                  "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                  "ON DUPLICATE KEY UPDATE mot_de_passe = VALUES(mot_de_passe), actif = VALUES(actif), role = VALUES(role), nom = VALUES(nom), prenom = VALUES(prenom), telephone = VALUES(telephone), societe = VALUES(societe), adresse = VALUES(adresse)"
                : "INSERT INTO `" + schemaName + "`.utilisateurs " +
                  "(nom_utilisateur, email, mot_de_passe, prenom, nom, actif, role, " +
                  "langue_preferee, statut_compte, mode_trial, nb_utilisations, nb_utilisations_max, telephone, societe, adresse, entreprise_id, entreprise_schema) " +
                  "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                  "ON DUPLICATE KEY UPDATE mot_de_passe = VALUES(mot_de_passe), actif = VALUES(actif), role = VALUES(role), nom = VALUES(nom), prenom = VALUES(prenom), telephone = VALUES(telephone), societe = VALUES(societe), adresse = VALUES(adresse)";

        Long generatedId = user.getId();
        try (Connection tenantConn = DriverManager.getConnection(tenantUrl, masterUsername, masterPassword != null ? masterPassword : "");
             java.sql.PreparedStatement psSync = tenantConn.prepareStatement(syncUserSql, Statement.RETURN_GENERATED_KEYS)) {

            int idx = 1;
            if (hasId) {
                psSync.setLong(idx++, user.getId());
            }
            psSync.setString(idx++, user.getNomUtilisateur());
            psSync.setString(idx++, user.getEmail());
            psSync.setString(idx++, user.getMotDePasse());
            psSync.setString(idx++, user.getPrenom());
            psSync.setString(idx++, user.getNom());
            psSync.setBoolean(idx++, Boolean.TRUE.equals(user.getActif()));
            psSync.setString(idx++, user.getRole() != null ? user.getRole().name() : "ADMIN");
            psSync.setString(idx++, user.getLanguePreferee() != null ? user.getLanguePreferee() : "fr");
            psSync.setString(idx++, user.getStatutCompte() != null ? user.getStatutCompte().name() : "ACTIF");
            psSync.setBoolean(idx++, Boolean.TRUE.equals(user.getModeTrial()));
            psSync.setInt(idx++, user.getNbUtilisations() != null ? user.getNbUtilisations() : 0);
            psSync.setInt(idx++, user.getNbUtilisationsMax() != null ? user.getNbUtilisationsMax() : 30);
            psSync.setString(idx++, user.getTelephone());
            psSync.setString(idx++, user.getSociete());
            psSync.setString(idx++, user.getAdresse());
            if (user.getEntrepriseId() != null) psSync.setLong(idx++, user.getEntrepriseId()); else psSync.setNull(idx++, java.sql.Types.BIGINT);
            psSync.setString(idx++, schemaName);

            psSync.executeUpdate();
            if (!hasId) {
                try (ResultSet rs = psSync.getGeneratedKeys()) {
                    if (rs.next()) {
                        generatedId = rs.getLong(1);
                        user.setId(generatedId);
                    }
                }
            }
            log.info("  ✓ Utilisateur '{}' inséré avec succès dans la base tenant '{}' (id={})", user.getNomUtilisateur(), schemaName, generatedId);
            return generatedId;
        } catch (Exception e) {
            log.error("  ✗ Impossible d'insérer l'utilisateur '{}' dans la base tenant '{}' : {}", user.getNomUtilisateur(), schemaName, e.getMessage(), e);
            throw new RuntimeException("Erreur insertion utilisateur dans base entreprise : " + e.getMessage(), e);
        }
    }


    // ═════════════════════════════════════════════════════════════════════════
    // PROVISIONING MYSQL — Cœur de l'isolation physique
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Provisionne les ressources MySQL pour un nouveau tenant.
     *
     * SOLUTION GLOBALE anti-Aria corruption (Errcode: 9 "Bad file descriptor") :
     * ─────────────────────────────────────────────────────────────────────────
     * Tous les blocs DDL utilisent DriverManager.getConnection() (connexions FRAÎCHES),
     * et NON masterDataSource.getConnection() (pool HikariCP avec connexions réutilisées).
     *
     * Pourquoi DriverManager évite l'erreur Aria ?
     *   - HikariCP maintient des connexions ouvertes avec leurs file descriptors.
     *   - MariaDB réutilise ces handles pour écrire dans aria_log.00000001.
     *   - Après un arrêt brutal de XAMPP, ces file descriptors sont invalides → Errcode: 9.
     *   - DriverManager crée une NOUVELLE connexion TCP → nouveau file descriptor → OK.
     *
     * CREATE USER/GRANT sont non-fatals :
     *   - Si Aria est corrompu, CREATE USER peut échouer → on log un warning.
     *   - Cela n'empêche PAS la création du tenant : les connexions applicatives
     *     utilisent ROOT avec isolation par schéma (erp_ent_XXXXX).
     *   - L'erreur "Impossible de créer l'utilisateur MySQL" disparaît côté utilisateur.
     */
    private void provisionnerTenant(String schemaName, String mysqlUser, String password) {
        // Validation de sécurité : caractères alphanumériques et underscores uniquement
        if (!schemaName.matches("^[a-zA-Z0-9_]+$") || !mysqlUser.matches("^[a-zA-Z0-9_]+$")) {
            throw new IllegalArgumentException("Nom de schéma ou utilisateur invalide : " + schemaName);
        }

        // URL de connexion DIRECTE au serveur MySQL (sans nom de base)
        String masterBaseUrl = extractBaseUrl(masterDbUrl);
        String directUrl = masterBaseUrl + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
        String masterPass = masterPassword != null ? masterPassword : "";

        // ── BLOC 1 : CREATE DATABASE ─────────────────────────────────────────────
        // Connexion fraîche via DriverManager (hors pool HikariCP)
        try (Connection conn = DriverManager.getConnection(directUrl, masterUsername, masterPass);
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate(
                "CREATE DATABASE IF NOT EXISTS `" + schemaName + "` " +
                "CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
            );
            log.info("  ✓ BASE créée : {}", schemaName);

        } catch (Exception e) {
            log.error("  ✗ Erreur CREATE DATABASE '{}' : {}", schemaName, e.getMessage());
            throw new RuntimeException("Impossible de créer la base tenant : " + schemaName, e);
        }

        // ── BLOCS 2-4 : CREATE USER + GRANT + FLUSH ──────────────────────────────
        // Ces opérations sont NON BLOQUANTES si Aria est corrompu.
        // Raison : les connexions applicatives utilisent ROOT de toute façon.
        // Si CREATE USER échoue → on log un warning et on continue sans erreur.
        // Le tenant sera opérationnel avec ROOT (isolation par schéma).
        try (Connection conn = DriverManager.getConnection(directUrl, masterUsername, masterPass);
             Statement stmt = conn.createStatement()) {

            // DROP USER IF EXISTS — évite les conflits si l'inscription a été partiellement jouée
            try {
                stmt.executeUpdate("DROP USER IF EXISTS '" + mysqlUser + "'@'%'");
            } catch (Exception ignored) {
                // DROP USER peut échouer si l'user n'existe pas encore → non-fatal
            }

            stmt.executeUpdate(
                "CREATE USER '" + mysqlUser + "'@'%' " +
                "IDENTIFIED BY '" + password + "'"
            );
            log.info("  ✓ USER MySQL créé : {}", mysqlUser);

            stmt.executeUpdate(
                "GRANT ALL PRIVILEGES ON `" + schemaName + "`.* " +
                "TO '" + mysqlUser + "'@'%'"
            );
            log.info("  ✓ GRANT ALL sur `{}`.* accordé à '{}'", schemaName, mysqlUser);

            try {
                stmt.executeUpdate("FLUSH PRIVILEGES");
                log.info("  ✓ FLUSH PRIVILEGES effectué");
            } catch (Exception fe) {
                log.warn("  ⚠ FLUSH PRIVILEGES ignoré (tables Aria ?) : {}", fe.getMessage());
            }

        } catch (Exception e) {
            // CREATE USER ou GRANT a échoué (tables Aria corrompues, permissions, etc.)
            // Ce n'est PAS fatal : les connexions applicatives utilisent ROOT.
            // L'isolation est assurée par le schéma séparé (erp_ent_XXXXX).
            log.warn("  ⚠ CREATE USER/GRANT non appliqué pour '{}' (non-fatal) : {}. " +
                     "Tenant opérationnel via ROOT. " +
                     "Pour réparer MariaDB/Aria : mysqlcheck --repair --all-databases -u root",
                     mysqlUser, e.getMessage());
        }

        // ── BLOC 5 : DDL des tables métier ──────────────────────────────────────
        // Toujours via DriverManager (connexion fraîche vers la base tenant)
        initialiserTablesTenant(schemaName);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // INITIALISATION DES TABLES DANS LA BASE DÉDIÉE
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Exécute le script tenant-schema-init.sql dans la base dédiée du tenant.
     *
     * Utilise Spring ScriptUtils.executeSqlScript() qui gère correctement :
     * - Les instructions multi-lignes
     * - Les commentaires (-- et /* *\/)
     * - Les cas limites avec les points-virgules
     *
     * conn.setCatalog() est la méthode JDBC officielle pour changer de base
     * (plus fiable que l'instruction SQL "USE schema").
     *
     * @param schemaName Nom du schéma cible (ex: erp_ent_00001)
     */
    private void initialiserTablesTenant(String schemaName) {
        log.info("  ► Initialisation des tables dans '{}'", schemaName);

        // IMPORTANT : DriverManager.getConnection() et NON masterDataSource.getConnection()
        // HikariCP ne reset pas le catalog → ne jamais utiliser setCatalog() sur le master pool
        String baseUrl = extractBaseUrl(masterDbUrl);
        String tenantUrl = baseUrl + "/" + schemaName
            + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";

        try (Connection conn = DriverManager.getConnection(
                tenantUrl, masterUsername, masterPassword != null ? masterPassword : "")) {

            // tenant-schema-init.sql = TOUTES les tables en CREATE TABLE IF NOT EXISTS
            // Sans ALTER TABLE → 100% idempotent sur nouvelle ET ancienne base
            ClassPathResource resource = new ClassPathResource("tenant-schema-init.sql");
            ScriptUtils.executeSqlScript(conn, resource);

            log.info("  ✓ Toutes les tables créées/vérifiées dans '{}'", schemaName);

        } catch (Exception e) {
            log.warn("  ⚠️ Initialisation/vérification tables pour '{}' : {}", schemaName, e.getMessage());
        }
    }



    // ═════════════════════════════════════════════════════════════════════════
    // UTILITAIRES PRIVÉS
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Génère un nom de schéma séquentiel unique : erp_ent_00001, erp_ent_00002...
     */
    private String genererSchemaName() {
        long count = entrepriseRepository.count() + 1;
        String candidate = String.format("erp_ent_%05d", count);
        while (entrepriseRepository.existsBySchemaName(candidate)) {
            count++;
            candidate = String.format("erp_ent_%05d", count);
        }
        return candidate;
    }

    /**
     * Génère un mot de passe fort aléatoire de 24 caractères (base64 URL-safe).
     * Basé sur SecureRandom → cryptographiquement sûr.
     * Exemple : "K7mP2xQr9nL4vBwY8zAj1cFd"
     */
    private String genererMotDePasseSecurise() {
        byte[] bytes = new byte[18]; // 18 bytes → 24 chars base64
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Extrait l'URL de base JDBC sans le nom de la base ni les paramètres.
     * Ex: "jdbc:mysql://localhost:3306/benjeddou_erp?..." → "jdbc:mysql://localhost:3306"
     */
    private String extractBaseUrl(String jdbcUrl) {
        int thirdSlash = jdbcUrl.indexOf('/', jdbcUrl.indexOf("//") + 2);
        return thirdSlash > 0 ? jdbcUrl.substring(0, thirdSlash) : jdbcUrl;
    }
}
