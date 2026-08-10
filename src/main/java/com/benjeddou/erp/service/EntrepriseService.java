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
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Base64;
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
 */
@Service
@Slf4j
public class EntrepriseService {

    private final EntrepriseRepository entrepriseRepository;
    private final TenantDataSourceConfig tenantDataSourceConfig;
    private final DataSource masterDataSource;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Constructeur avec @Qualifier pour injecter le masterDataSource (pas le routing datasource).
     * Évite la dépendance circulaire.
     */
    public EntrepriseService(EntrepriseRepository entrepriseRepository,
                             TenantDataSourceConfig tenantDataSourceConfig,
                             @Qualifier("masterDataSource") DataSource masterDataSource) {
        this.entrepriseRepository = entrepriseRepository;
        this.tenantDataSourceConfig = tenantDataSourceConfig;
        this.masterDataSource = masterDataSource;
    }

    @Value("${spring.datasource.url}")
    private String masterDbUrl;

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
     * 5. CREATE USER 'erp_user_00001'@'localhost' IDENTIFIED BY '<mdp_unique>'
     * 6. GRANT ALL PRIVILEGES ON erp_ent_00001.* TO 'erp_user_00001'@'localhost'
     * 7. Enregistrer l'entreprise dans la base master
     * 8. Ajouter le DataSource dans le pool de routing
     *
     * @param nomEntreprise  Nom commercial de l'entreprise
     * @param emailContact   Email de l'administrateur
     * @param adminId        ID de l'utilisateur administrateur (base master)
     * @return Entreprise créée et persistée
     */
    @Transactional
    public Entreprise creerEntreprise(String nomEntreprise, String emailContact, Long adminId) {

        // 1. Générer les identifiants uniques
        String schemaName  = genererSchemaName();
        String mysqlUser   = schemaName.replace("erp_ent_", "erp_user_");
        String motDePasse  = genererMotDePasseSecurise();

        log.info("╔══ Création tenant : {} ══════════════════════════", schemaName);
        log.info("║  Entreprise : {}", nomEntreprise);
        log.info("║  User MySQL : {}  (mot de passe dédié généré)", mysqlUser);

        // 2. URL JDBC avec l'utilisateur dédié (pas root)
        String baseUrl    = extractBaseUrl(masterDbUrl);
        String tenantDbUrl = baseUrl + "/" + schemaName
                + "?createDatabaseIfNotExist=true"
                + "&useSSL=false"
                + "&serverTimezone=UTC"
                + "&allowPublicKeyRetrieval=true";

        // 3. Provisionner : créer la base + l'utilisateur MySQL + les droits
        provisionnerTenant(schemaName, mysqlUser, motDePasse);

        // 4. Enregistrer dans la base master
        Entreprise entreprise = Entreprise.builder()
                .nom(nomEntreprise)
                .schemaName(schemaName)
                .dbUrl(tenantDbUrl)
                .dbUsername(mysqlUser)    // ← utilisateur dédié, pas root
                .dbPassword(motDePasse)   // ← mot de passe unique chiffré
                .adminId(adminId)
                .emailContact(emailContact)
                .statut(Entreprise.StatutEntreprise.ACTIVE)
                .build();

        Entreprise saved = entrepriseRepository.save(entreprise);
        log.info("║  ✓ Enregistré en base master (id={})", saved.getId());

        // 5. Ajouter au pool de routing avec les credentials dédiés
        tenantDataSourceConfig.addTenantDataSource(schemaName, tenantDbUrl, mysqlUser, motDePasse);
        log.info("║  ✓ DataSource ajouté au pool de routing");
        log.info("╚══ Tenant {} opérationnel ══════════════════════════", schemaName);

        return saved;
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
     * Chaque tenant utilise ses propres credentials MySQL.
     */
    public void chargerTousLesTenants() {
        log.info("Chargement des DataSources pour tous les tenants actifs...");
        entrepriseRepository.findAll().forEach(e -> {
            if (e.getDbUrl() != null && Entreprise.StatutEntreprise.ACTIVE.equals(e.getStatut())) {
                tenantDataSourceConfig.addTenantDataSource(
                        e.getSchemaName(), e.getDbUrl(), e.getDbUsername(), e.getDbPassword());
                log.info("  ✓ Tenant chargé : {} → user MySQL : {}", e.getSchemaName(), e.getDbUsername());
            }
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    // PROVISIONING MYSQL — Cœur de l'isolation physique
    // ═════════════════════════════════════════════════════════════════════════

    private void provisionnerTenant(String schemaName, String mysqlUser, String password) {
        // Validation de sécurité : caractères alphanumériques et underscores uniquement
        if (!schemaName.matches("^[a-zA-Z0-9_]+$") || !mysqlUser.matches("^[a-zA-Z0-9_]+$")) {
            throw new IllegalArgumentException("Nom de schéma ou utilisateur invalide : " + schemaName);
        }

        // ── BLOC 1 : CREATE DATABASE + USER + GRANT + FLUSH ─────────────────────
        // Exécuté via root (masterDataSource) dans sa propre connexion.
        try (Connection conn = masterDataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate(
                "CREATE DATABASE IF NOT EXISTS `" + schemaName + "` " +
                "CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
            );
            log.info("  ✓ BASE créée : {}", schemaName);

            stmt.executeUpdate(
                "CREATE USER IF NOT EXISTS '" + mysqlUser + "'@'%' " +
                "IDENTIFIED BY '" + password + "'"
            );
            log.info("  ✓ USER MySQL créé : {}", mysqlUser);

            // Droits EXCLUSIVEMENT sur sa propre base
            stmt.executeUpdate(
                "GRANT ALL PRIVILEGES ON `" + schemaName + "`.* " +
                "TO '" + mysqlUser + "'@'%'"
            );
            stmt.executeUpdate("FLUSH PRIVILEGES");
            log.info("  ✓ GRANT ALL sur `{}`.* accordé à '{}'", schemaName, mysqlUser);

        } catch (Exception e) {
            log.error("  ✗ Erreur provisioning (CREATE/GRANT) pour '{}' : {}", schemaName, e.getMessage());
            throw new RuntimeException("Impossible de provisionner le tenant : " + schemaName, e);
        }

        // ── BLOC 2 : DDL des tables métier — connexion FRAÎCHE et SÉPARÉE ───────
        // On ouvre une nouvelle Connection distincte pour éviter d'avoir deux
        // Statement ouverts simultanément sur la même Connection (cause de l'erreur).
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
        try (Connection conn = masterDataSource.getConnection()) {

            log.info("  ► Initialisation des tables dans '{}'", schemaName);

            // Pointer sur la base dédiée via l'API JDBC (plus fiable que USE)
            conn.setCatalog(schemaName);

            // Spring ScriptUtils gère le parsing robuste du script SQL
            ClassPathResource resource = new ClassPathResource("tenant-schema-init.sql");
            ScriptUtils.executeSqlScript(conn, resource);

            log.info("  ✓ Toutes les tables créées dans '{}'", schemaName);

        } catch (Exception e) {
            log.error("  ✗ Erreur initialisation tables pour '{}' : {}", schemaName, e.getMessage());
            throw new RuntimeException("Impossible d'initialiser les tables du tenant : " + schemaName, e);
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
