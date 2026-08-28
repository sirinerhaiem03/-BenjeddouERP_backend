package com.benjeddou.erp.controller;

import com.benjeddou.erp.repository.EntrepriseRepository;
import com.benjeddou.erp.repository.UtilisateurRepository;
import com.benjeddou.erp.security.services.UserDetailsImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * UserPermissionsController — RBAC Enforcement
 *
 * Endpoint accessible à TOUS les utilisateurs authentifiés (aucune restriction de rôle).
 * Retourne les permissions du rôle de l'utilisateur connecté depuis la table roles_config
 * de la base tenant.
 *
 * Séparé de AdminController intentionnellement pour éviter la restriction de classe
 * @PreAuthorize("hasRole('ADMIN')") qui empêcherait les autres rôles d'y accéder.
 */
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/user")
public class UserPermissionsController {

    @Autowired
    UtilisateurRepository utilisateurRepository;

    @Autowired
    EntrepriseRepository entrepriseRepository;

    @Value("${spring.datasource.username}")
    private String masterUsername;

    @Value("${spring.datasource.password:}")
    private String masterPassword;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * GET /api/user/permissions
     *
     * Retourne les permissions du rôle de l'utilisateur connecté depuis la table roles_config.
     * Accessible à tous les utilisateurs authentifiés (Commercial, Comptable, Stock, etc.)
     *
     * Réponse succès : { role: "COMMERCIAL", modulePermissions: [{module, permissions}] }
     * Réponse sans config : { role: "COMMERCIAL", modulePermissions: [] }  → mode permissif frontend
     */
    @GetMapping("/permissions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getMesPermissions() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            log.warn("[UserPermissions] Appel non authentifié");
            return ResponseEntity.ok(Map.of("role", "", "modulePermissions", List.of()));
        }

        // Extraire le rôle principal depuis les autorités Spring Security
        String springRole = auth.getAuthorities().stream()
            .map(a -> a.getAuthority())
            .findFirst()
            .orElse("ROLE_USER");

        // Supprimer le préfixe ROLE_ : "ROLE_COMMERCIAL" → "COMMERCIAL"
        String roleName = springRole.startsWith("ROLE_") ? springRole.substring(5) : springRole;

        log.info("[UserPermissions] Utilisateur={} rôle={}  — chargement permissions", auth.getName(), roleName);

        // ADMIN et SUPERADMIN ont toujours tout → retourner vide (le frontend gère le bypass)
        if ("ADMIN".equals(roleName) || "SUPERADMIN".equals(roleName)) {
            log.info("[UserPermissions] ADMIN/SUPERADMIN → accès total, retour vide");
            return ResponseEntity.ok(Map.of("role", roleName, "modulePermissions", List.of()));
        }

        // Résoudre la connexion JDBC vers la base du tenant
        Optional<String[]> jdbcOpt = resoudreTenantJdbc();
        if (jdbcOpt.isEmpty()) {
            log.warn("[UserPermissions] Tenant non résolu pour rôle={} — mode permissif", roleName);
            return ResponseEntity.ok(Map.of("role", roleName, "modulePermissions", List.of()));
        }

        String[] jdbc = jdbcOpt.get();
        log.debug("[UserPermissions] Connexion tenant : {}", jdbc[0]);

        String sql = "SELECT config_json FROM roles_config WHERE id = 1";
        try (Connection conn = DriverManager.getConnection(jdbc[0], jdbc[1], jdbc[2])) {

            // Créer la table si elle n'existe pas encore (première utilisation du tenant)
            String createSql = """
                CREATE TABLE IF NOT EXISTS roles_config (
                    id INT PRIMARY KEY,
                    config_json LONGTEXT NOT NULL,
                    updated_at DATETIME DEFAULT NOW()
                )
                """;
            try (PreparedStatement psCreate = conn.prepareStatement(createSql)) {
                psCreate.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    String json = rs.getString("config_json");
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> roles = objectMapper.readValue(json, List.class);

                    // Rechercher le rôle par son nom (insensible à la casse)
                    final String roleNameFinal = roleName;
                    Optional<Map<String, Object>> roleConfig = roles.stream()
                        .filter(r -> roleNameFinal.equalsIgnoreCase((String) r.get("nom")))
                        .findFirst();

                    if (roleConfig.isPresent()) {
                        Object modulePermissions = roleConfig.get().get("modulePermissions");
                        int nbModules = modulePermissions instanceof List ? ((List<?>) modulePermissions).size() : 0;
                        log.info("[UserPermissions] ✅ Rôle={} → {} modules de permissions trouvés", roleName, nbModules);
                        return ResponseEntity.ok(Map.of(
                            "role", roleName,
                            "modulePermissions", modulePermissions != null ? modulePermissions : List.of()
                        ));
                    } else {
                        log.warn("[UserPermissions] Rôle='{}' non trouvé dans la config ({} rôles) — mode permissif",
                            roleName, roles.size());
                    }
                } else {
                    log.warn("[UserPermissions] Table roles_config vide — mode permissif. Enregistrez d'abord la matrice depuis l'interface Admin.");
                }
            }
        } catch (Exception e) {
            log.error("[UserPermissions] ❌ Erreur DB pour rôle={} : {}", roleName, e.getMessage());
        }

        return ResponseEntity.ok(Map.of("role", roleName, "modulePermissions", List.of()));
    }

    /** Résout l'URL JDBC de la base tenant de l'utilisateur connecté. */
    private Optional<String[]> resoudreTenantJdbc() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserDetailsImpl principal)) {
            return Optional.empty();
        }

        return utilisateurRepository.findById(principal.getId())
            .flatMap(u -> {
                String schema = u.getEntrepriseSchema();
                log.debug("[UserPermissions] entrepriseSchema={}", schema);
                if (schema == null || schema.isBlank()) return Optional.empty();
                return entrepriseRepository.findBySchemaName(schema)
                    .map(ent -> new String[]{
                        ent.getDbUrl() != null ? ent.getDbUrl() : "",
                        ent.getDbUsername() != null ? ent.getDbUsername() : masterUsername,
                        ent.getDbPassword() != null ? ent.getDbPassword() : (masterPassword != null ? masterPassword : "")
                    });
            });
    }
}
