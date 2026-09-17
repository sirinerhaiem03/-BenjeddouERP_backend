package com.benjeddou.erp.security.jwt;

import com.benjeddou.erp.config.TenantContextHolder;
import com.benjeddou.erp.model.ConnexionLog;
import com.benjeddou.erp.model.Utilisateur;
import com.benjeddou.erp.repository.ConnexionLogRepository;
import com.benjeddou.erp.repository.UtilisateurRepository;
import com.benjeddou.erp.security.services.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Filtre JWT — J3 Sécurité
 * Valide l'access token ET vérifie que la session est toujours active en DB.
 */
public class AuthTokenFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private ConnexionLogRepository connexionLogRepository;

    private static final Logger logger = LoggerFactory.getLogger(AuthTokenFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = parseJwt(request);
            if (jwt != null && jwtUtils.validateJwtToken(jwt)) {
                String username = jwtUtils.getUserNameFromJwtToken(jwt);

                // ─────────────────────────────────────────────────────────────
                // IMPORTANT : forcer la BASE MASTER pour toutes les requêtes
                // d'authentification.
                // TenantFilter a déjà défini le tenant (ex: erp_ent_00005) pour
                // router les requêtes métier, mais les entités auth (Utilisateur,
                // ConnexionLog, RefreshToken) sont TOUJOURS dans la base master.
                // ─────────────────────────────────────────────────────────────
                String savedTenant = TenantContextHolder.getCurrentTenant();
                TenantContextHolder.clear(); // ← force base master

                try {
                    // Vérification J3 : le tokenSession en DB doit correspondre
                    Optional<Utilisateur> userOpt = utilisateurRepository.findByNomUtilisateur(username);
                    if (userOpt.isPresent()) {
                        String tokenEnDb = userOpt.get().getTokenSession();
                        if (tokenEnDb != null && !tokenEnDb.equals(jwt)) {
                            // Session invalide — récupérer le signalementToken de la session la plus récente
                            logger.warn("Session invalidée pour '{}' : token ne correspond plus.", username);

                            String sigToken = "";
                            List<ConnexionLog> sessions = connexionLogRepository
                                    .findByUtilisateurOrderByDateConnexionDesc(userOpt.get());
                            if (!sessions.isEmpty() && sessions.get(0).getSignalementToken() != null) {
                                sigToken = sessions.get(0).getSignalementToken();
                            }

                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json; charset=UTF-8");
                            response.getWriter().write(
                                "{\"message\":\"SESSION_INVALIDEE\",\"code\":\"SESSION_EXPIRED\",\"signalementToken\":\"" + sigToken + "\"}"
                            );
                            return;
                        }
                    }

                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                } finally {
                    // Restaurer le tenant pour les requêtes métier dans les controllers
                    if (savedTenant != null) {
                        TenantContextHolder.setCurrentTenant(savedTenant);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Impossible de définir l'authentification: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        return null;
    }
}
