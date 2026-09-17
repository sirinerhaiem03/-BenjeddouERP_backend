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
            // ── Endpoints d'authentification publics → toujours base master ──────
            // On court-circuite uniquement les endpoints qui n'ont pas besoin de tenant
            // (login, inscription, récupération de mot de passe).
            // ⚠️ /api/auth/refresh et /api/auth/logout ont besoin du contexte tenant
            //    pour trouver les refresh_tokens dans la bonne base.
            String uri = httpRequest.getRequestURI();
            boolean isPublicAuthEndpoint = uri.contains("/api/auth/login")
                    || uri.contains("/api/auth/register")
                    || uri.contains("/api/auth/inscription")
                    || uri.contains("/api/auth/mot-de-passe")
                    || uri.contains("/api/auth/reset")
                    || uri.contains("/api/auth/verify")
                    || uri.contains("/api/auth/otp");
            if (isPublicAuthEndpoint) {
                chain.doFilter(request, response);
                return;
            }

            String jwt = parseJwt(httpRequest);

            // Pour les endpoints qui nécessitent le tenant (/refresh, /logout, etc.),
            // on tente d'extraire le username même si le JWT est expiré.
            // validateJwtToken() retourne false pour les tokens expirés, mais
            // getUserNameFromExpiredOrValidToken() peut quand même lire le subject.
            String username = null;
            if (jwt != null) {
                if (jwtUtils.validateJwtToken(jwt)) {
                    username = jwtUtils.getUserNameFromJwtToken(jwt);
                } else {
                    // JWT invalide ou expiré — on tente quand même pour le routing tenant
                    // (sera refusé par Spring Security si le token n'est pas valide)
                    username = jwtUtils.getUserNameFromExpiredOrValidToken(jwt);
                }
            }

            String schema = null;
            if (jwt != null) {
                schema = jwtUtils.getSchemaFromJwtToken(jwt);
            }

            if (schema != null && !schema.isBlank()) {
                if (!tenantDataSourceConfig.tenantExists(schema)) {
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
                        log.info("✓ Tenant '{}' rechargé dynamiquement", schema);
                    } else {
                        log.warn("⚠️ Tenant '{}' introuvable dans la base master", schema);
                    }
                } else {
                    TenantContextHolder.setCurrentTenant(schema);
                    log.debug("Tenant défini depuis JWT : {}", schema);
                }
            } else if (username != null) {
                // Fallback pour tokens sans claim schema : recherche master
                Optional<Utilisateur> userOpt = utilisateurRepository.findByNomUtilisateur(username);
                if (userOpt.isPresent()) {
                    Utilisateur user = userOpt.get();
                    String userSchema = user.getEntrepriseSchema();
                    if (userSchema != null && !userSchema.isBlank()) {
                        if (!tenantDataSourceConfig.tenantExists(userSchema)) {
                            Optional<Entreprise> entrepriseOpt = entrepriseRepository.findBySchemaName(userSchema);
                            entrepriseOpt.ifPresent(ent -> tenantDataSourceConfig.addTenantDataSource(
                                ent.getSchemaName(), ent.getDbUrl(), ent.getDbUsername(), ent.getDbPassword()));
                        }
                        TenantContextHolder.setCurrentTenant(userSchema);
                    }
                }
            }

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
