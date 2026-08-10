package com.benjeddou.erp.config;

import com.benjeddou.erp.model.Entreprise;
import com.benjeddou.erp.model.Utilisateur;
import com.benjeddou.erp.repository.EntrepriseRepository;
import com.benjeddou.erp.repository.UtilisateurRepository;
import com.benjeddou.erp.security.jwt.JwtUtils;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Optional;

/**
 * TenantFilter — Identifie le tenant de chaque requête HTTP et configure le routing DataSource.
 *
 * Ordre d'exécution :
 * 1. TenantFilter (Order=1) — identifie le tenant depuis le JWT
 * 2. AuthTokenFilter (Spring Security) — valide le JWT et charge les authorities
 * 3. Controllers — traitent la requête avec le DataSource correct
 *
 * Logique :
 * - Extrait le JWT depuis le header "Authorization: Bearer <token>"
 * - Lit le username depuis le JWT
 * - Charge l'utilisateur depuis la BASE MASTER (pas encore routé)
 * - Récupère son entrepriseSchema (ex: "erp_ent_00001")
 * - Définit TenantContextHolder.setCurrentTenant(schema)
 * - Toutes les requêtes JPA suivantes → DataSource de ce schema
 * - Nettoie dans finally pour prévenir les fuites mémoire
 *
 * Requêtes publiques (login, inscription) → aucun JWT → schema null → base master
 */
@Component
@Order(1) // S'exécute EN PREMIER, avant Spring Security
@Slf4j
public class TenantFilter implements Filter {

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private TenantDataSourceConfig tenantDataSourceConfig;

    /** Accès à la base master pour recharger un tenant manquant dynamiquement */
    @Autowired
    private EntrepriseRepository entrepriseRepository;

    @Value("${spring.datasource.username}")
    private String masterUsername;

    @Value("${spring.datasource.password}")
    private String masterPassword;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;

        try {
            // ── Endpoints d'authentification → toujours base master ──────────
            // Angular envoie le JWT même pour /api/auth/login, ce qui causerait
            // un routing vers la base tenant. On court-circuite ici.
            String uri = httpRequest.getRequestURI();
            if (uri.contains("/api/auth/")) {
                chain.doFilter(request, response);
                return;
            }

            String jwt = parseJwt(httpRequest);

            if (jwt != null && jwtUtils.validateJwtToken(jwt)) {
                String username = jwtUtils.getUserNameFromJwtToken(jwt);

                // Charge l'utilisateur depuis la BASE MASTER
                // ⚠️ À ce stade, TenantContextHolder n'est PAS encore défini → base master utilisée
                Optional<Utilisateur> userOpt = utilisateurRepository.findByNomUtilisateur(username);

                if (userOpt.isPresent()) {
                    Utilisateur user = userOpt.get();
                    String schema = user.getEntrepriseSchema();

                    if (schema != null && !schema.isBlank()) {
                        // Vérifie que le DataSource est enregistré dans le pool
                        if (!tenantDataSourceConfig.tenantExists(schema)) {
                            // ► RECHARGEMENT DYNAMIQUE depuis la base master
                            // Se produit après un redémarrage si le tenant n'était pas encore chargé
                            log.info("DataSource absent pour '{}' — rechargement depuis la base master", schema);
                            Optional<Entreprise> entrepriseOpt = entrepriseRepository.findBySchemaName(schema);
                            if (entrepriseOpt.isPresent()) {
                                Entreprise ent = entrepriseOpt.get();
                                tenantDataSourceConfig.addTenantDataSource(
                                    ent.getSchemaName(),
                                    ent.getDbUrl(),
                                    ent.getDbUsername(),
                                    ent.getDbPassword()
                                );
                                TenantContextHolder.setCurrentTenant(schema);
                                log.info("✓ Tenant '{}' rechargé dynamiquement pour '{}'", schema, username);
                            } else {
                                log.warn("⚠️ Tenant '{}' introuvable dans la base master — fallback sur master", schema);
                                // Pas de tenant trouvé → base master utilisée (sécurité)
                            }
                        } else {
                            // ✓ DataSource déjà en cache — routage normal
                            TenantContextHolder.setCurrentTenant(schema);
                            log.debug("Tenant défini : {} pour utilisateur '{}'", schema, username);
                        }
                    }
                    // Si schema est null (SuperAdmin, utilisateurs legacy) → base master utilisée
                }
            }
            // Si pas de JWT (requête publique) → base master par défaut

            chain.doFilter(request, response);

        } finally {
            // ⚠️ CRITIQUE : toujours nettoyer pour éviter les fuites entre requêtes dans le pool de threads
            TenantContextHolder.clear();
            log.debug("TenantContextHolder nettoyé.");
        }
    }

    /**
     * Extrait le token JWT depuis le header Authorization.
     * @return le token sans "Bearer " ou null si absent
     */
    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        return null;
    }
}
