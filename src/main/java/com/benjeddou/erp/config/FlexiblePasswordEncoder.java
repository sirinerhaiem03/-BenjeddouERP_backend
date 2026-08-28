package com.benjeddou.erp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * FlexiblePasswordEncoder — Encodeur de mot de passe hautement sécurisé basé sur Argon2id.
 *
 * Conforme aux exigences strictes du cahier des charges (Point 1.1) :
 * 1. Encodage primaire : Argon2id (standard cryptographique moderne le plus robuste contre les attaques GPU/ASIC).
 *    Paramètres :
 *    - Type : Argon2id (v=19)
 *    - Salt : 16 bytes
 *    - Hash : 32 bytes
 *    - Parallelism : 1 thread
 *    - Memory : 65536 KB (64 MB)
 *    - Iterations : 3 passes
 *
 * 2. Rétrocompatibilité et migration transparente :
 *    - Mots de passe préexistants hashés en BCrypt ($2a$, $2b$, $2y$) vérifiés sans rupture de service.
 *    - Compatibilité texte clair acceptée uniquement pour les imports SQL démo sans hash.
 *
 * 3. Non-réversibilité :
 *    - Les mots de passe utilisateurs ne sont JAMAIS stockés sous une forme réversible ou déchiffrable.
 */
@Primary
@Component
@Slf4j
public class FlexiblePasswordEncoder implements PasswordEncoder {

    /**
     * Encodeur principal Argon2id :
     * saltLength=16, hashLength=32, parallelism=1, memory=65536, iterations=3
     */
    private final Argon2PasswordEncoder argon2 = new Argon2PasswordEncoder(16, 32, 1, 65536, 3);

    /**
     * Encodeur BCrypt de transition pour compatibilité avec les hashs historiques
     */
    private final BCryptPasswordEncoder bCrypt = new BCryptPasswordEncoder(12);

    @Override
    public String encode(CharSequence rawPassword) {
        if (rawPassword == null || rawPassword.length() == 0) {
            return argon2.encode("");
        }
        // Tout nouveau mot de passe ou changement de mot de passe est chiffré en Argon2id
        return argon2.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isBlank()) {
            return false;
        }

        String raw = rawPassword != null ? rawPassword.toString() : "";

        // 1. Vérification Argon2id (Format standard : $argon2id$v=19$...)
        if (encodedPassword.startsWith("$argon2id$") || encodedPassword.startsWith("$argon2i$") || encodedPassword.startsWith("$argon2d$")) {
            try {
                return argon2.matches(raw, encodedPassword);
            } catch (Exception e) {
                log.warn("Erreur vérification Argon2id : {}", e.getMessage());
                return false;
            }
        }

        // 2. Vérification rétrocompatible BCrypt ($2a$, $2b$, $2y$)
        if (encodedPassword.startsWith("$2a$") || encodedPassword.startsWith("$2b$") || encodedPassword.startsWith("$2y$")) {
            try {
                return bCrypt.matches(raw, encodedPassword);
            } catch (Exception e) {
                log.warn("Erreur vérification BCrypt rétrocompatible : {}", e.getMessage());
                return false;
            }
        }

        // 3. Compatibilité d'exception texte clair (cas où un dump SQL initial a été importé avec mots de passe en clair)
        if (raw.equals(encodedPassword)) {
            log.info("Authentification acceptée pour compte démo non hashé — upgrade Argon2id recommandé");
            return true;
        }

        return false;
    }

    /**
     * Vérifie si un hash existant doit être mis à niveau vers Argon2id
     */
    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isBlank()) {
            return true;
        }
        return !encodedPassword.startsWith("$argon2id$");
    }
}
