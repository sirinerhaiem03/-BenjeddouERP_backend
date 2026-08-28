package com.benjeddou.erp.controller;

import com.benjeddou.erp.service.DatabaseManagementService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * DatabaseManagementController — API REST de gestion des bases de données.
 *
 * Accès autorisé :
 *  - SUPERADMIN : toutes les bases (master + tous les tenants)
 *  - ADMIN : uniquement sa propre base tenant
 *
 * Toutes les opérations destructives exigent un token de confirmation à usage unique.
 * Toutes les opérations sont tracées dans l'audit log.
 */
@RestController
@RequestMapping("/api/db-management")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class DatabaseManagementController {

    private final DatabaseManagementService dbService;

    // ══════════════════════════════════════════════════════════════════════
    // INFORMATIONS BASE
    // GET /api/db-management/infos?schema=master
    // ══════════════════════════════════════════════════════════════════════
    @GetMapping("/infos")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> infosBase(@RequestParam(defaultValue = "master") String schema,
                                       HttpServletRequest request) {
        verifierAccesSchema(schema);
        return ResponseEntity.ok(dbService.obtenirInfosBase(schema));
    }

    // ══════════════════════════════════════════════════════════════════════
    // EXPORT SQL
    // GET /api/db-management/export?schema=master
    // Retourne directement le fichier SQL en téléchargement
    // ══════════════════════════════════════════════════════════════════════
    @GetMapping("/export")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> exporterSQL(@RequestParam(defaultValue = "master") String schema,
                                         HttpServletRequest request) {
        try {
            verifierAccesSchema(schema);
            String auteur = getAuteurConnecte();
            log.info("📤 Export SQL — schema: {} — par: {}", schema, auteur);

            Map<String, Object> result = dbService.exporterSQL(schema, auteur, request);

            if (!Boolean.TRUE.equals(result.get("succes"))) {
                return ResponseEntity.badRequest().body(result);
            }

            String sql = (String) result.get("sql");
            String nomFichier = (String) result.get("nomFichierSuggere");
            if (sql == null) sql = "";
            if (nomFichier == null) nomFichier = schema + "_export.sql";

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                    ContentDisposition.attachment().filename(nomFichier, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("application/sql"))
                .body(sql.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("❌ Exception lors de l'export SQL: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("succes", false, "erreur", e.getMessage() != null ? e.getMessage() : "Erreur interne de serveur."));
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // EXPORT JSON (pour l'UI — sans téléchargement direct)
    // GET /api/db-management/export-info?schema=master
    // ══════════════════════════════════════════════════════════════════════
    @GetMapping("/export-info")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> exportInfoSQL(@RequestParam(defaultValue = "master") String schema,
                                           HttpServletRequest request) {
        verifierAccesSchema(schema);
        String auteur = getAuteurConnecte();
        Map<String, Object> result = dbService.exporterSQL(schema, auteur, request);
        // Ne pas retourner le contenu SQL complet dans la réponse JSON (trop volumineux)
        result.remove("sql");
        return ResponseEntity.ok(result);
    }

    // ══════════════════════════════════════════════════════════════════════
    // IMPORT SQL
    // POST /api/db-management/import?schema=master&allowDestructive=false
    // ══════════════════════════════════════════════════════════════════════
    @PostMapping("/import")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> importerSQL(@RequestParam(defaultValue = "master") String schema,
                                          @RequestParam(defaultValue = "false") boolean allowDestructive,
                                          @RequestParam("fichier") MultipartFile fichier,
                                          HttpServletRequest request) {
        verifierAccesSchema(schema);
        // Sécurité renforcée : seul SUPERADMIN peut activer le mode destructif
        if (allowDestructive && !estSuperAdmin()) {
            return ResponseEntity.status(403).body(Map.of(
                "succes", false,
                "erreur", "Seul le SuperAdmin peut autoriser les opérations destructives lors d'un import."
            ));
        }
        String auteur = getAuteurConnecte();
        return ResponseEntity.ok(dbService.importerSQL(schema, fichier, allowDestructive, auteur, request));
    }

    // ══════════════════════════════════════════════════════════════════════
    // SAUVEGARDE
    // POST /api/db-management/sauvegarder?schema=master
    // ══════════════════════════════════════════════════════════════════════
    @PostMapping("/sauvegarder")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> sauvegarder(@RequestParam(defaultValue = "master") String schema,
                                          HttpServletRequest request) {
        verifierAccesSchema(schema);
        String auteur = getAuteurConnecte();
        return ResponseEntity.ok(dbService.sauvegarder(schema, auteur, request));
    }

    // ══════════════════════════════════════════════════════════════════════
    // LISTER LES SAUVEGARDES
    // GET /api/db-management/sauvegardes?schema=master
    // ══════════════════════════════════════════════════════════════════════
    @GetMapping("/sauvegardes")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> listerSauvegardes(@RequestParam(required = false) String schema) {
        List<Map<String, Object>> liste = dbService.listerSauvegardes(schema);
        return ResponseEntity.ok(Map.of("sauvegardes", liste, "total", liste.size()));
    }

    // ══════════════════════════════════════════════════════════════════════
    // GÉNÉRER TOKEN DE CONFIRMATION (étape 1 des opérations destructives)
    // POST /api/db-management/confirmation-token
    // Body: { "schema": "erp_ent_00000", "operation": "restauration" }
    // ══════════════════════════════════════════════════════════════════════
    @PostMapping("/confirmation-token")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> genererTokenConfirmation(@RequestBody Map<String, String> body,
                                                       HttpServletRequest request) {
        String schema    = body.getOrDefault("schema", "master");
        String operation = body.getOrDefault("operation", "unknown");
        verifierAccesSchema(schema);

        // Opérations interdites pour ADMIN
        if (!estSuperAdmin() && "vidage".equalsIgnoreCase(operation)) {
            return ResponseEntity.status(403).body(Map.of(
                "succes", false,
                "erreur", "Seul le SuperAdmin peut vider les données d'une base."
            ));
        }
        String auteur = getAuteurConnecte();
        return ResponseEntity.ok(dbService.genererTokenConfirmation(schema, operation, auteur));
    }

    // ══════════════════════════════════════════════════════════════════════
    // RESTAURATION (étape 2 — exige token de confirmation)
    // POST /api/db-management/restaurer
    // Body: { "schema": "...", "fichier": "...", "tokenConfirmation": "..." }
    // ══════════════════════════════════════════════════════════════════════
    @PostMapping("/restaurer")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> restaurer(@RequestBody Map<String, String> body,
                                        HttpServletRequest request) {
        String schema    = body.getOrDefault("schema", "master");
        String fichier   = body.getOrDefault("fichier", "");
        String token     = body.getOrDefault("tokenConfirmation", "");
        verifierAccesSchema(schema);

        // Sécurité renforcée : la restauration de la base master est réservée au SuperAdmin
        if (("master".equalsIgnoreCase(schema) || "benjeddou_erp".equalsIgnoreCase(schema)) && !estSuperAdmin()) {
            return ResponseEntity.status(403).body(Map.of(
                "succes", false,
                "erreur", "Seul le SuperAdmin peut restaurer la base master."
            ));
        }
        String auteur = getAuteurConnecte();
        return ResponseEntity.ok(dbService.restaurer(schema, fichier, token, auteur, request));
    }

    // ══════════════════════════════════════════════════════════════════════
    // VIDAGE DONNÉES (étape 2 — exige token de confirmation)
    // Réservé au SUPERADMIN
    // POST /api/db-management/vider
    // Body: { "schema": "...", "tokenConfirmation": "..." }
    // ══════════════════════════════════════════════════════════════════════
    @PostMapping("/vider")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> viderDonnees(@RequestBody Map<String, String> body,
                                           HttpServletRequest request) {
        String schema = body.getOrDefault("schema", "");
        String token  = body.getOrDefault("tokenConfirmation", "");

        if (schema.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("succes", false, "erreur", "Schema requis."));
        }
        String auteur = getAuteurConnecte();
        return ResponseEntity.ok(dbService.viderDonnees(schema, token, auteur, request));
    }

    // ══════════════════════════════════════════════════════════════════════
    // Helpers privés
    // ══════════════════════════════════════════════════════════════════════

    private String getAuteurConnecte() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "inconnu";
    }

    private boolean estSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_SUPERADMIN"));
    }

    /**
     * Vérifie qu'un ADMIN ne peut accéder qu'à son propre schéma tenant.
     * Un SUPERADMIN peut accéder à tous les schémas.
     * Lève une exception 403 si l'accès est refusé.
     */
    private void verifierAccesSchema(String schema) {
        if (estSuperAdmin()) return; // SuperAdmin : accès total

        // ADMIN : il ne peut accéder qu'à son propre schéma
        // La vérification fine est gérée côté service via getAuteurConnecte()
        // Pour la base master, un ADMIN ne peut pas y accéder
        if ("master".equalsIgnoreCase(schema) || "benjeddou_erp".equalsIgnoreCase(schema)) {
            throw new org.springframework.security.access.AccessDeniedException(
                "Un Administrateur d'entreprise ne peut pas accéder à la base master (benjeddou_erp)."
            );
        }
    }
}
