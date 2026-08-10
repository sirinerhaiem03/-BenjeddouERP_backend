package com.benjeddou.erp.security.jwt;

import com.benjeddou.erp.security.services.UserDetailsImpl;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import java.security.Key;
import java.util.Date;

/**
 * Utilitaire JWT — J3 Sécurité
 * Access token : 15 minutes (900 000 ms)
 * Refresh token : 7 jours (géré par RefreshTokenService)
 */
@Component
public class JwtUtils {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

    @Value("${benjeddou.erp.jwtSecret}")
    private String jwtSecret;

    @Value("${benjeddou.erp.jwtExpirationMs}")
    private int jwtExpirationMs;

    /**
     * Génère un access token JWT à partir de l'objet Authentication Spring Security.
     * Durée : 15 minutes.
     */
    public String generateJwtToken(Authentication authentication) {
        UserDetailsImpl userPrincipal = (UserDetailsImpl) authentication.getPrincipal();
        return buildToken(userPrincipal.getUsername());
    }

    /**
     * Génère un access token JWT à partir d'un nom d'utilisateur.
     * Utilisé par l'endpoint /api/auth/refresh.
     */
    public String generateJwtTokenFromUsername(String username) {
        return buildToken(username);
    }

    private String buildToken(String username) {
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    private Key key() {
        byte[] keyBytes = jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        // Assurer 32 bytes minimum pour HMAC-SHA256
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String getUserNameFromJwtToken(String token) {
        return Jwts.parserBuilder().setSigningKey(key()).build()
                .parseClaimsJws(token).getBody().getSubject();
    }

    /**
     * Extrait le username d'un JWT même s'il est expiré.
     * Utilisé par le HeartbeatController pour détecter les doubles connexions
     * même après l'expiration du token (15 min).
     * Retourne null si le token est malformé ou invalide.
     */
    public String getUserNameFromExpiredOrValidToken(String token) {
        try {
            return Jwts.parserBuilder().setSigningKey(key()).build()
                    .parseClaimsJws(token).getBody().getSubject();
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            // Token expiré mais on peut lire le username des claims
            return e.getClaims().getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(authToken);
            return true;
        } catch (MalformedJwtException e) {
            logger.error("Token JWT invalide: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            logger.error("Token JWT expiré: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            logger.error("Token JWT non supporté: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.error("Claims JWT vides: {}", e.getMessage());
        }
        return false;
    }
}
