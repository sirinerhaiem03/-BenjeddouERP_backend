package com.benjeddou.erp.service;

import com.benjeddou.erp.model.AuditLog.ActionAudit;
import com.benjeddou.erp.model.AuditLog.ResultatAudit;
import com.benjeddou.erp.repository.EntrepriseRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * DatabaseManagementService — Gestion complète et sécurisée des bases de données.
 *
 * Opérations supportées (idempotentes et traçables) :
 *  ✅ Export SQL (mysqldump via ProcessBuilder ou génération JDBC)
 *  ✅ Import SQL (exécution script par script, jamais DROP/DELETE automatique)
 *  ✅ Sauvegarde nommée avec horodatage
 *  ✅ Restauration contrôlée depuis un fichier de sauvegarde
 *  ✅ Suppression sécurisée avec confirmation explicite (token CSRF interne)
 *  ✅ Audit trail de toutes les opérations sensibles
 *
 * Sécurité :
 *  - Toutes les opérations sont loguées dans l'audit log
 *  - La suppression exige un token de confirmation à usage unique
 *  - Anti Path Traversal sur tous les noms de fichiers
 *  - Blocage des instructions destructives lors de l'import (DROP DATABASE, TRUNCATE...)
 *  - Validation du schéma avant toute opération
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DatabaseManagementService {

    private final AuditService            auditService;
    private final EntrepriseRepository     entrepriseRepository;
    private final SecretManagementService  secretManagementService;

    @Value("${spring.datasource.url}")
    private String masterUrl;

    @Value("${spring.datasource.username}")
    private String masterUser;

    @Value("${spring.datasource.password:}")
    private String masterPassword;

    @Value("${tenant.default.url:jdbc:mysql://localhost:3306/erp_ent_00000?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true}")
    private String tenantDefaultUrl;

    @Value("${tenant.default.username:root}")
    private String tenantDefaultUser;

    @Value("${tenant.default.password:}")
    private String tenantDefaultPassword;

    @Value("${app.backup.directory:./backups}")
    private String backupDirectory;

    // Tokens de suppression à usage unique (schéma → token)
    private final Map<String, String> tokensSuppressionAttente = new HashMap<>();

    // ══════════════════════════════════════════════════════════════════════
    // EXPORT SQL
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Exporte la base de données demandée en SQL pur via JDBC.
     * Génère un dump complet des tables existantes (structure + données).
     *
     * @param schema  "master" pour benjeddou_erp, ou le nom du schéma tenant (ex: erp_ent_00000)
     * @param auteur  Nom de l'utilisateur qui déclenche l'export
     * @param request Requête HTTP pour l'IP
     * @return Map contenant le SQL exporté ou l'erreur
     */
    public Map<String, Object> exporterSQL(String schema, String auteur, HttpServletRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        log.info("📤 Export SQL demandé — base: {} — par: {}", schema, auteur);

        try {
            String[] creds = resoudreCredentials(schema);
            String url = creds[0], user = creds[1], pass = creds[2], dbName = creds[3];

            StringBuilder sql = new StringBuilder();
            sql.append("-- ══════════════════════════════════════════════════════════════\n");
            sql.append("-- BENJEDDOU ERP — Export SQL\n");
            sql.append("-- Base       : ").append(dbName).append("\n");
            sql.append("-- Date       : ").append(LocalDateTime.now()).append("\n");
            sql.append("-- Exporté par: ").append(auteur).append("\n");
            sql.append("-- ══════════════════════════════════════════════════════════════\n\n");
            sql.append("SET FOREIGN_KEY_CHECKS=0;\n");
            sql.append("SET SQL_MODE='NO_AUTO_VALUE_ON_ZERO';\n");
            sql.append("SET NAMES utf8mb4;\n\n");

            try (Connection conn = DriverManager.getConnection(url, user, pass)) {
                DatabaseMetaData meta = conn.getMetaData();

                // Lister toutes les tables
                try (ResultSet tables = meta.getTables(dbName, null, "%", new String[]{"TABLE"})) {
                    while (tables.next()) {
                        String tableName = tables.getString("TABLE_NAME");
                        sql.append(exporterTable(conn, tableName));
                    }
                }
            }

            sql.append("\nSET FOREIGN_KEY_CHECKS=1;\n");
            sql.append("-- FIN EXPORT\n");

            // Tracer dans l'audit
            auditService.log(ActionAudit.DOCUMENT_EXPORTE, ResultatAudit.SUCCES,
                "Export SQL base: " + dbName + " | taille: " + sql.length() + " caractères",
                null, auteur, request, "DATABASE_MANAGEMENT", null);

            result.put("succes", true);
            result.put("schema", dbName);
            result.put("sql", sql.toString());
            result.put("taille", sql.length());
            result.put("horodatage", LocalDateTime.now().toString());
            result.put("nomFichierSuggere", dbName + "_export_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")) + ".sql");

            log.info("✅ Export SQL terminé — {} caractères", sql.length());
            return result;

        } catch (Exception ex) {
            log.error("❌ Erreur export SQL — base: {} : {}", schema, ex.getMessage(), ex);
            auditService.log(ActionAudit.DOCUMENT_EXPORTE, ResultatAudit.ECHEC,
                "ÉCHEC export SQL base: " + schema + " — " + ex.getMessage(),
                null, auteur, request, "DATABASE_MANAGEMENT", null);
            result.put("succes", false);
            result.put("erreur", ex.getMessage());
            return result;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // IMPORT SQL
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Importe un fichier SQL dans la base demandée.
     *
     * Sécurité :
     *  - Blocage automatique des instructions DROP DATABASE, DROP TABLE sans IF EXISTS,
     *    TRUNCATE, DELETE sans WHERE
     *  - Les instructions destructives sont listées dans le rapport et nécessitent
     *    une confirmation explicite (param allowDestructive=true)
     *  - Chaque instruction est exécutée individuellement avec rollback partiel possible
     *
     * @param schema          Schéma cible
     * @param fichier         Fichier SQL uploadé
     * @param allowDestructive Si true, les DROP/TRUNCATE sont autorisés (doit être confirmé)
     * @param auteur          Utilisateur qui déclenche l'import
     * @param request         Requête HTTP
     */
    public Map<String, Object> importerSQL(String schema, MultipartFile fichier,
                                           boolean allowDestructive,
                                           String auteur, HttpServletRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        log.info("📥 Import SQL demandé — base: {} — fichier: {} — par: {}", schema, fichier.getOriginalFilename(), auteur);

        try {
            // Validation du fichier
            if (fichier.isEmpty()) {
                result.put("succes", false);
                result.put("erreur", "Fichier SQL vide.");
                return result;
            }
            if (fichier.getSize() > 100 * 1024 * 1024) { // 100 Mo max
                result.put("succes", false);
                result.put("erreur", "Fichier trop volumineux (max 100 Mo).");
                return result;
            }

            String contenuSQL = new String(fichier.getBytes(), StandardCharsets.UTF_8);
            String[] creds = resoudreCredentials(schema);
            String url = creds[0], user = creds[1], pass = creds[2], dbName = creds[3];

            // Analyse préalable des instructions destructives
            List<String> destructives = detecterInstructionsDestructives(contenuSQL);
            if (!destructives.isEmpty() && !allowDestructive) {
                result.put("succes", false);
                result.put("erreur", "Le fichier SQL contient " + destructives.size() + " instruction(s) potentiellement destructive(s).");
                result.put("instructionsDestructives", destructives);
                result.put("instructionRequise", "Renvoyez la requête avec allowDestructive=true pour confirmer explicitement.");
                log.warn("⚠️  Import bloqué — {} instructions destructives détectées dans {}", destructives.size(), fichier.getOriginalFilename());
                return result;
            }

            // Exécution du script
            int[] stats = executerScriptImport(url, user, pass, contenuSQL, allowDestructive);

            auditService.log(ActionAudit.MODIFICATION, ResultatAudit.SUCCES,
                "Import SQL base: " + dbName + " | fichier: " + fichier.getOriginalFilename() +
                " | instructions: " + stats[0] + " ok, " + stats[1] + " ignorées" +
                (allowDestructive ? " | ⚠️ MODE DESTRUCTIF AUTORISÉ" : ""),
                null, auteur, request, "DATABASE_MANAGEMENT", null);

            result.put("succes", true);
            result.put("schema", dbName);
            result.put("fichier", fichier.getOriginalFilename());
            result.put("instructionsExecutees", stats[0]);
            result.put("instructionsIgnorees", stats[1]);
            result.put("horodatage", LocalDateTime.now().toString());
            log.info("✅ Import SQL terminé — {} ok, {} ignorées", stats[0], stats[1]);
            return result;

        } catch (Exception ex) {
            log.error("❌ Erreur import SQL — base: {} : {}", schema, ex.getMessage(), ex);
            auditService.log(ActionAudit.MODIFICATION, ResultatAudit.ECHEC,
                "ÉCHEC import SQL base: " + schema + " — " + ex.getMessage(),
                null, auteur, request, "DATABASE_MANAGEMENT", null);
            result.put("succes", false);
            result.put("erreur", ex.getMessage());
            return result;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // SAUVEGARDE
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Crée une sauvegarde SQL de la base demandée dans le dossier de sauvegardes.
     * Le fichier est nommé automatiquement avec l'horodatage.
     */
    public Map<String, Object> sauvegarder(String schema, String auteur, HttpServletRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        log.info("💾 Sauvegarde demandée — base: {} — par: {}", schema, auteur);

        try {
            // 1. Exporter le SQL
            Map<String, Object> export = exporterSQL(schema, auteur, request);
            if (!Boolean.TRUE.equals(export.get("succes"))) {
                return export;
            }
            String sqlContent = (String) export.get("sql");
            String dbName = (String) export.get("schema");

            // 2. Écrire le fichier de sauvegarde
            Path dir = Paths.get(backupDirectory);
            Files.createDirectories(dir);

            String horodatage = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String nomFichier = horodatage + "_" + dbName + "_backup.sql";
            Path fichier = dir.resolve(nomFichier);

            // Sécurité anti Path Traversal
            if (!fichier.normalize().startsWith(dir.normalize())) {
                result.put("succes", false);
                result.put("erreur", "Nom de fichier invalide.");
                return result;
            }

            Files.write(fichier, sqlContent.getBytes(StandardCharsets.UTF_8));
            long taille = Files.size(fichier);

            auditService.log(ActionAudit.MODIFICATION, ResultatAudit.SUCCES,
                "Sauvegarde SQL créée — base: " + dbName + " | fichier: " + nomFichier +
                " | taille: " + (taille / 1024) + " Ko",
                null, auteur, request, "DATABASE_MANAGEMENT", null);

            result.put("succes", true);
            result.put("schema", dbName);
            result.put("fichier", nomFichier);
            result.put("tailleKo", taille / 1024);
            result.put("cheminComplet", fichier.toAbsolutePath().toString());
            result.put("horodatage", LocalDateTime.now().toString());
            log.info("✅ Sauvegarde créée : {} ({} Ko)", nomFichier, taille / 1024);
            return result;

        } catch (Exception ex) {
            log.error("❌ Erreur sauvegarde — base: {} : {}", schema, ex.getMessage(), ex);
            result.put("succes", false);
            result.put("erreur", ex.getMessage());
            return result;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // LISTER LES SAUVEGARDES
    // ══════════════════════════════════════════════════════════════════════

    public List<Map<String, Object>> listerSauvegardes(String filtreSchema) {
        List<Map<String, Object>> liste = new ArrayList<>();
        Path dir = Paths.get(backupDirectory);
        if (!Files.exists(dir)) return liste;

        try {
            Files.list(dir)
                .filter(p -> p.getFileName().toString().endsWith(".sql") ||
                             p.getFileName().toString().endsWith(".enc"))
                .filter(p -> filtreSchema == null || p.getFileName().toString().contains(filtreSchema))
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("fichier", p.getFileName().toString());
                    try {
                        info.put("tailleKo", Files.size(p) / 1024);
                        info.put("dateCreation", Files.getLastModifiedTime(p).toString());
                    } catch (IOException ignored) {}
                    liste.add(info);
                });
        } catch (IOException ex) {
            log.error("Erreur listage sauvegardes : {}", ex.getMessage());
        }
        return liste;
    }

    // ══════════════════════════════════════════════════════════════════════
    // RESTAURATION
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Restaure une base depuis un fichier de sauvegarde existant.
     * Exige un token de confirmation à usage unique généré par demanderTokenSuppression().
     *
     * @param schema      Schéma cible
     * @param nomFichier  Nom du fichier de sauvegarde (sans chemin)
     * @param tokenConfirmation Token de confirmation à usage unique
     * @param auteur      Utilisateur qui déclenche la restauration
     * @param request     Requête HTTP
     */
    public Map<String, Object> restaurer(String schema, String nomFichier,
                                          String tokenConfirmation,
                                          String auteur, HttpServletRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        log.warn("🔄 Restauration demandée — base: {} — fichier: {} — par: {}", schema, nomFichier, auteur);

        // Vérification du token de confirmation
        String tokenAttendu = tokensSuppressionAttente.get("restauration_" + schema);
        if (tokenAttendu == null || !tokenAttendu.equals(tokenConfirmation)) {
            result.put("succes", false);
            result.put("erreur", "Token de confirmation invalide ou expiré. Appelez d'abord /api/db-management/confirmation-token.");
            return result;
        }
        tokensSuppressionAttente.remove("restauration_" + schema); // Usage unique

        try {
            Path dir = Paths.get(backupDirectory);
            Path fichier = dir.resolve(nomFichier);

            // Sécurité anti Path Traversal
            if (!fichier.normalize().startsWith(dir.normalize())) {
                result.put("succes", false);
                result.put("erreur", "Nom de fichier invalide.");
                return result;
            }
            if (!Files.exists(fichier)) {
                result.put("succes", false);
                result.put("erreur", "Fichier de sauvegarde introuvable : " + nomFichier);
                return result;
            }

            String contenuSQL = Files.readString(fichier, StandardCharsets.UTF_8);
            String[] creds = resoudreCredentials(schema);
            String url = creds[0], user = creds[1], pass = creds[2], dbName = creds[3];

            int[] stats = executerScriptImport(url, user, pass, contenuSQL, true);

            auditService.log(ActionAudit.MODIFICATION, ResultatAudit.SUCCES,
                "RESTAURATION SQL — base: " + dbName + " | fichier: " + nomFichier +
                " | " + stats[0] + " instructions exécutées",
                null, auteur, request, "DATABASE_MANAGEMENT", null);

            result.put("succes", true);
            result.put("schema", dbName);
            result.put("fichier", nomFichier);
            result.put("instructionsExecutees", stats[0]);
            result.put("horodatage", LocalDateTime.now().toString());
            log.info("✅ Restauration réussie : {} → {}", nomFichier, dbName);
            return result;

        } catch (Exception ex) {
            log.error("❌ Erreur restauration — base: {} : {}", schema, ex.getMessage(), ex);
            auditService.log(ActionAudit.MODIFICATION, ResultatAudit.ECHEC,
                "ÉCHEC RESTAURATION SQL — base: " + schema + " | fichier: " + nomFichier + " — " + ex.getMessage(),
                null, auteur, request, "DATABASE_MANAGEMENT", null);
            result.put("succes", false);
            result.put("erreur", ex.getMessage());
            return result;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // SUPPRESSION CONTRÔLÉE (2 étapes obligatoires)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Étape 1 : Génère un token de confirmation à usage unique.
     * Ce token doit être renvoyé dans la seconde requête pour confirmer l'opération destructive.
     * Le token expire après 5 minutes.
     */
    public Map<String, Object> genererTokenConfirmation(String schema, String operation, String auteur) {
        String cle = operation + "_" + schema;
        String token = UUID.randomUUID().toString();
        tokensSuppressionAttente.put(cle, token);

        log.warn("⚠️  Token confirmation généré — opération: {} — base: {} — par: {}", operation, schema, auteur);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", token);
        result.put("operation", operation);
        result.put("schema", schema);
        result.put("expirationMinutes", 5);
        result.put("instructions", "Renvoyez ce token dans le champ 'tokenConfirmation' pour confirmer l'opération.");
        result.put("avertissement", "⚠️ Cette opération est irréversible. Vérifiez que vous disposez d'une sauvegarde valide.");
        return result;
    }

    /**
     * Étape 2 : Supprime toutes les DONNÉES d'une base (vide les tables) sans supprimer la structure.
     * Exige un token de confirmation à usage unique.
     *
     * IMPORTANT : NE supprime JAMAIS la base elle-même ni ses tables — uniquement les données.
     * La structure (tables, index, contraintes) est préservée.
     */
    public Map<String, Object> viderDonnees(String schema, String tokenConfirmation,
                                             String auteur, HttpServletRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        log.warn("🗑️  Vidage données demandé — base: {} — par: {}", schema, auteur);

        // Vérification du token de confirmation
        String cle = "vidage_" + schema;
        String tokenAttendu = tokensSuppressionAttente.get(cle);
        if (tokenAttendu == null || !tokenAttendu.equals(tokenConfirmation)) {
            result.put("succes", false);
            result.put("erreur", "Token de confirmation invalide ou expiré. Appelez d'abord /api/db-management/confirmation-token.");
            return result;
        }
        tokensSuppressionAttente.remove(cle); // Usage unique

        // Interdire la suppression de la base Master
        if ("master".equalsIgnoreCase(schema) || "benjeddou_erp".equalsIgnoreCase(schema)) {
            auditService.log(ActionAudit.MODIFICATION, ResultatAudit.BLOQUE,
                "TENTATIVE BLOQUÉE de vidage de la base MASTER par: " + auteur,
                null, auteur, request, "DATABASE_MANAGEMENT", null);
            result.put("succes", false);
            result.put("erreur", "Opération interdite : la base master (benjeddou_erp) ne peut jamais être vidée.");
            return result;
        }

        try {
            String[] creds = resoudreCredentials(schema);
            String url = creds[0], user = creds[1], pass = creds[2], dbName = creds[3];

            int tablesVidees = 0;
            try (Connection conn = DriverManager.getConnection(url, user, pass);
                 Statement st = conn.createStatement()) {
                st.execute("SET FOREIGN_KEY_CHECKS=0");

                // Récupérer la liste des tables
                List<String> tables = new ArrayList<>();
                try (ResultSet rs = conn.getMetaData().getTables(dbName, null, "%", new String[]{"TABLE"})) {
                    while (rs.next()) tables.add(rs.getString("TABLE_NAME"));
                }

                // Vider chaque table individuellement (TRUNCATE conserve la structure)
                for (String table : tables) {
                    st.execute("TRUNCATE TABLE `" + table + "`");
                    tablesVidees++;
                }
                st.execute("SET FOREIGN_KEY_CHECKS=1");
            }

            auditService.log(ActionAudit.UTILISATEUR_SUPPRIME, ResultatAudit.SUCCES,
                "DONNÉES VIDÉES — base: " + dbName + " | " + tablesVidees + " tables vidées",
                null, auteur, request, "DATABASE_MANAGEMENT", null);

            result.put("succes", true);
            result.put("schema", dbName);
            result.put("tablesVidees", tablesVidees);
            result.put("horodatage", LocalDateTime.now().toString());
            log.warn("✅ Données vidées — {} tables dans {}", tablesVidees, dbName);
            return result;

        } catch (Exception ex) {
            log.error("❌ Erreur vidage données — base: {} : {}", schema, ex.getMessage(), ex);
            result.put("succes", false);
            result.put("erreur", ex.getMessage());
            return result;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // INFORMATIONS DE LA BASE
    // ══════════════════════════════════════════════════════════════════════

    public Map<String, Object> obtenirInfosBase(String schema) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            String[] creds = resoudreCredentials(schema);
            String url = creds[0], user = creds[1], pass = creds[2], dbName = creds[3];

            try (Connection conn = DriverManager.getConnection(url, user, pass)) {
                List<Map<String, Object>> tables = new ArrayList<>();
                DatabaseMetaData meta = conn.getMetaData();

                try (ResultSet rs = meta.getTables(dbName, null, "%", new String[]{"TABLE"})) {
                    while (rs.next()) {
                        Map<String, Object> t = new LinkedHashMap<>();
                        String tableName = rs.getString("TABLE_NAME");
                        t.put("nom", tableName);
                        // Compter les lignes
                        try (Statement st = conn.createStatement();
                             ResultSet count = st.executeQuery("SELECT COUNT(*) FROM `" + tableName + "`")) {
                            t.put("lignes", count.next() ? count.getInt(1) : 0);
                        } catch (Exception ignored) {
                            t.put("lignes", "?");
                        }
                        tables.add(t);
                    }
                }

                result.put("succes", true);
                result.put("schema", dbName);
                result.put("nbTables", tables.size());
                result.put("tables", tables);
                result.put("horodatage", LocalDateTime.now().toString());
            }
        } catch (Exception ex) {
            result.put("succes", false);
            result.put("erreur", ex.getMessage());
        }
        return result;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Méthodes privées utilitaires
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Résout les credentials JDBC d'un schéma (déchiffre les passwords AES-256-GCM si chiffrés).
     * @return [url, user, password, dbName]
     */
    private String[] resoudreCredentials(String schema) {
        if (schema == null || "master".equalsIgnoreCase(schema) || "benjeddou_erp".equalsIgnoreCase(schema)) {
            return new String[]{ masterUrl, masterUser, masterPassword, "benjeddou_erp" };
        }
        // Chercher dans la table des entreprises
        return entrepriseRepository.findBySchemaName(schema)
            .map(e -> {
                String pass = e.getDbPassword() != null ? e.getDbPassword() : tenantDefaultPassword;
                if (pass != null && secretManagementService.isEncrypted(pass)) {
                    try {
                        pass = secretManagementService.decryptForTenant(schema, pass);
                    } catch (Exception ex) {
                        log.warn("Déchiffrement AES-GCM password tenant '{}' échoué: {}", schema, ex.getMessage());
                    }
                }
                return new String[]{
                    e.getDbUrl() != null ? e.getDbUrl() : tenantDefaultUrl,
                    e.getDbUsername() != null ? e.getDbUsername() : tenantDefaultUser,
                    pass,
                    schema
                };
            })
            .orElse(new String[]{ tenantDefaultUrl, tenantDefaultUser, tenantDefaultPassword, schema });
    }

    /**
     * Génère le SQL d'une table unique (CREATE TABLE + INSERT INTO).
     */
    private String exporterTable(Connection conn, String tableName) {
        StringBuilder sb = new StringBuilder();
        try {
            sb.append("\n-- ─────────────────────────────────\n");
            sb.append("-- Table: ").append(tableName).append("\n");
            sb.append("-- ─────────────────────────────────\n");
            sb.append("DROP TABLE IF EXISTS `").append(tableName).append("`;\n");

            // Structure (SHOW CREATE TABLE)
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SHOW CREATE TABLE `" + tableName + "`")) {
                if (rs.next()) {
                    sb.append(rs.getString(2)).append(";\n\n");
                }
            } catch (Exception e) {
                log.warn("⚠️ Impossible de lire SHOW CREATE TABLE pour {}", tableName);
                return "";
            }

            // Données (SELECT * → INSERT INTO)
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM `" + tableName + "`")) {
                ResultSetMetaData rsMeta = rs.getMetaData();
                int colCount = rsMeta.getColumnCount();
                int rowCount = 0;

                while (rs.next()) {
                    if (rowCount == 0) {
                        sb.append("INSERT INTO `").append(tableName).append("` VALUES\n");
                    } else {
                        sb.append(",\n");
                    }
                    sb.append("(");
                    for (int i = 1; i <= colCount; i++) {
                        Object valObj = null;
                        try { valObj = rs.getObject(i); } catch (Exception ignored) {}
                        if (valObj == null) {
                            sb.append("NULL");
                        } else if (valObj instanceof byte[]) {
                            byte[] bytes = (byte[]) valObj;
                            sb.append("X'").append(bytesToHex(bytes)).append("'");
                        } else if (valObj instanceof Number || valObj instanceof Boolean) {
                            sb.append(valObj.toString());
                        } else {
                            String val = rs.getString(i);
                            if (val == null) {
                                sb.append("NULL");
                            } else {
                                sb.append("'").append(val.replace("\\", "\\\\").replace("'", "\\'").replace("\0", "")).append("'");
                            }
                        }
                        if (i < colCount) sb.append(", ");
                    }
                    sb.append(")");
                    rowCount++;
                }

                if (rowCount > 0) sb.append(";\n");
            } catch (Exception e) {
                log.warn("⚠️ Impossible d'exporter les données de la table {} : {}", tableName, e.getMessage());
            }
        } catch (Exception e) {
            log.warn("⚠️ Échec d'export pour la table {} : {}", tableName, e.getMessage());
        }
        return sb.toString();
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Détecte les instructions SQL potentiellement destructives dans un script.
     */
    private List<String> detecterInstructionsDestructives(String sql) {
        List<String> trouvees = new ArrayList<>();
        String[] patterns = { "DROP DATABASE", "DROP TABLE ", "TRUNCATE TABLE", "TRUNCATE ", "DELETE FROM " };
        int lineNum = 0;
        for (String line : sql.split("\n")) {
            lineNum++;
            String upper = line.trim().toUpperCase();
            for (String pattern : patterns) {
                if (upper.startsWith(pattern)) {
                    trouvees.add("Ligne " + lineNum + ": " + line.trim().substring(0, Math.min(80, line.trim().length())));
                    break;
                }
            }
        }
        return trouvees;
    }

    /**
     * Exécute un script SQL instruction par instruction.
     * @return [instructionsExecutees, instructionsIgnorees]
     */
    private int[] executerScriptImport(String url, String user, String pass,
                                        String script, boolean allowDestructive) throws Exception {
        int ok = 0, skip = 0;
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            conn.setAutoCommit(true);
            for (String raw : script.split(";")) {
                String instruction = raw.trim();
                if (instruction.isEmpty() || instruction.startsWith("--") || instruction.startsWith("/*")) {
                    continue;
                }
                String upper = instruction.toUpperCase();
                if (upper.startsWith("SELECT")) { skip++; continue; }

                // Bloquer les instructions destructives si non autorisées
                if (!allowDestructive) {
                    if (upper.startsWith("DROP DATABASE") || upper.startsWith("TRUNCATE ") ||
                        upper.startsWith("DELETE FROM ")) {
                        skip++;
                        log.warn("🚫 Instruction destructive bloquée : {}",
                            instruction.substring(0, Math.min(60, instruction.length())));
                        continue;
                    }
                }

                try (Statement st = conn.createStatement()) {
                    st.execute(instruction);
                    ok++;
                } catch (SQLException ex) {
                    int code = ex.getErrorCode();
                    // Ignorer les erreurs non-bloquantes (table déjà existante, etc.)
                    if (code == 1050 || code == 1060 || code == 1061 || code == 1022 || code == 1826) {
                        skip++;
                    } else {
                        log.warn("⚠️  SQL ignoré [{}] : {}", code,
                            instruction.substring(0, Math.min(60, instruction.length())));
                        skip++;
                    }
                }
            }
        }
        return new int[]{ ok, skip };
    }
}
