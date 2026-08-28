package com.benjeddou.erp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SecretManagementService — Système de gestion centralisée des secrets et KMS Multi-Tenant.
 *
 * Principes et garanties de sécurité :
 * 1. Chiffrement Réversible : AES-256-GCM (Galois/Counter Mode) avec Tag d'authentification 128 bits.
 * 2. Master Key / Root Key : Définie via variable d'environnement SECURITY_MASTER_KEY ou conservée
 *    de manière persistante dans un keystore sécurisé local (.secrets/master.key).
 * 3. Isolation Cryptographique Stricte entre Tenants :
 *    Chaque entreprise dispose d'une clé AES-256 dédiée, dérivée cryptographiquement via HMAC-SHA256
 *    à partir de la Master Key et de son identifiant de schéma unique.
 *    La compromission d'une entreprise ne permet en aucun cas de déchiffrer les données d'une autre.
 * 4. Cycle complet : Chiffrer → Stocker → Déployer → Restaurer → Déchiffrer → Réutiliser
 *    sans perte ou invalidation des credentials existants.
 */
@Service
@Slf4j
public class SecretManagementService {

    private static final String ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int KEY_SIZE_BYTES = 32; // 256 bits
    private static final String PREFIX_V1 = "ENC:AES-GCM:v1:";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** Cache mémoire des clés dérivées par tenant pour performance optimale */
    private final ConcurrentHashMap<String, SecretKey> tenantKeyCache = new ConcurrentHashMap<>();

    private final SecretKey masterKey;

    public SecretManagementService(
            @Value("${app.security.master-key:${SECURITY_MASTER_KEY:}}") String configuredMasterKey,
            @Value("${app.security.secrets-dir:.secrets}") String secretsDir) {
        this.masterKey = initializeMasterKey(configuredMasterKey, secretsDir);
        log.info("🔐 SecretManagementService (KMS AES-256-GCM) initialisé avec succès.");
    }

    /**
     * Initialise la Master Root Key :
     * 1. Si fournie via configuration/environnement -> utilise celle-ci (SHA-256 pour obtenir 256 bits).
     * 2. Sinon -> charge depuis le fichier persistant .secrets/master.key.
     * 3. Si le fichier n'existe pas -> génère une nouvelle clé aléatoire de 256 bits et la persiste.
     */
    private SecretKey initializeMasterKey(String configuredKey, String secretsDirPath) {
        if (configuredKey != null && !configuredKey.trim().isEmpty() && !configuredKey.contains("REMPLACE")) {
            byte[] keyBytes = sha256(configuredKey.trim().getBytes(StandardCharsets.UTF_8));
            log.info("🔑 Master Key KMS chargée depuis l'environnement / configuration.");
            return new SecretKeySpec(keyBytes, ALGORITHM);
        }

        // Gestion du fichier de secret persistant pour survie aux redémarrages / déploiements
        try {
            Path dir = Paths.get(secretsDirPath);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            Path keyFile = dir.resolve("master.key");
            if (Files.exists(keyFile)) {
                byte[] raw = Files.readAllBytes(keyFile);
                if (raw.length == KEY_SIZE_BYTES) {
                    log.info("🔑 Master Key KMS chargée depuis le keystore persistant ({})", keyFile.toAbsolutePath());
                    return new SecretKeySpec(raw, ALGORITHM);
                } else {
                    byte[] keyBytes = sha256(raw);
                    return new SecretKeySpec(keyBytes, ALGORITHM);
                }
            } else {
                // Génération nouvelle clé persistante
                byte[] randomBytes = new byte[KEY_SIZE_BYTES];
                SECURE_RANDOM.nextBytes(randomBytes);
                Files.write(keyFile, randomBytes);
                log.info("✨ Nouvelle Master Key KMS générée et persistée dans ({})", keyFile.toAbsolutePath());
                return new SecretKeySpec(randomBytes, ALGORITHM);
            }
        } catch (IOException e) {
            log.warn("⚠️ Impossible de persister .secrets/master.key, utilisation d'une clé dérivée par défaut: {}", e.getMessage());
            byte[] fallback = sha256("BENJEDDOU_ERP_DEFAULT_PERSISTENT_ROOT_KMS_KEY_2026".getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(fallback, ALGORITHM);
        }
    }

    /**
     * Dérivation cryptographique de clé dédiée par Entreprise / Tenant (HMAC-SHA256).
     * Garantit l'isolation cryptographique totale :
     * K_tenant = HMAC-SHA256(MasterKey, "tenant:" + schemaName)
     */
    public SecretKey getTenantKey(String schemaName) {
        if (schemaName == null || schemaName.isBlank() || "master".equalsIgnoreCase(schemaName) || "benjeddou_erp".equalsIgnoreCase(schemaName)) {
            return masterKey;
        }

        return tenantKeyCache.computeIfAbsent(schemaName, schema -> {
            try {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(masterKey);
                byte[] derivationData = ("BENJEDDOU_TENANT_ISOLATION_SALT:" + schema.trim().toLowerCase()).getBytes(StandardCharsets.UTF_8);
                byte[] derivedKeyBytes = mac.doFinal(derivationData);
                return new SecretKeySpec(derivedKeyBytes, ALGORITHM);
            } catch (Exception e) {
                log.error("Erreur lors de la dérivation de clé pour tenant '{}' : {}", schema, e.getMessage());
                throw new RuntimeException("Échec de dérivation de clé cryptographique tenant", e);
            }
        });
    }

    /**
     * Chiffre une chaîne en clair avec la clé isolée d'un Tenant spécifique (AES-256-GCM).
     */
    public String encryptForTenant(String schemaName, String plainText) {
        if (plainText == null) return null;
        if (isEncrypted(plainText)) return plainText; // Déjà chiffré

        SecretKey key = getTenantKey(schemaName);
        return encryptWithKey(key, plainText);
    }

    /**
     * Déchiffre une chaîne chiffrée avec la clé isolée d'un Tenant spécifique (AES-256-GCM).
     * Si la chaîne n'est pas chiffrée, la renvoie telle quelle (rétrocompatibilité transparente).
     */
    public String decryptForTenant(String schemaName, String cipherText) {
        if (cipherText == null) return null;
        if (!isEncrypted(cipherText)) return cipherText; // Non chiffré (legacy en clair)

        SecretKey key = getTenantKey(schemaName);
        try {
            return decryptWithKey(key, cipherText);
        } catch (Exception e) {
            // Tenter avec la master key au cas où
            try {
                return decryptWithKey(masterKey, cipherText);
            } catch (Exception ex) {
                log.error("✗ Impossible de déchiffrer le secret pour tenant '{}' : {}", schemaName, e.getMessage());
                throw new RuntimeException("Déchiffrement AES-GCM échoué", e);
            }
        }
    }

    /**
     * Chiffre pour le SuperAdmin / Plateforme avec la Master Key.
     */
    public String encryptMaster(String plainText) {
        if (plainText == null) return null;
        if (isEncrypted(plainText)) return plainText;
        return encryptWithKey(masterKey, plainText);
    }

    /**
     * Déchiffre pour le SuperAdmin / Plateforme avec la Master Key.
     */
    public String decryptMaster(String cipherText) {
        if (cipherText == null) return null;
        if (!isEncrypted(cipherText)) return cipherText;
        return decryptWithKey(masterKey, cipherText);
    }

    /**
     * Détermine si une chaîne est dans le format d'enveloppe chiffrée AES-256-GCM.
     */
    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX_V1);
    }

    /**
     * Cœur d'implémentation du chiffrement AES-256-GCM.
     */
    private String encryptWithKey(SecretKey key, String plainText) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);

            byte[] plainBytes = plainText.getBytes(StandardCharsets.UTF_8);
            byte[] cipherBytes = cipher.doFinal(plainBytes);

            // Structure du payload : [IV (12 bytes)] + [Ciphertext + AuthTag]
            byte[] combined = new byte[iv.length + cipherBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherBytes, 0, combined, iv.length, cipherBytes.length);

            String encoded = Base64.getEncoder().encodeToString(combined);
            return PREFIX_V1 + encoded;
        } catch (Exception e) {
            log.error("Erreur lors du chiffrement AES-256-GCM : {}", e.getMessage());
            throw new RuntimeException("Erreur de chiffrement AES-256-GCM", e);
        }
    }

    /**
     * Cœur d'implémentation du déchiffrement AES-256-GCM.
     */
    private String decryptWithKey(SecretKey key, String formattedCipherText) {
        try {
            String base64Payload = formattedCipherText.substring(PREFIX_V1.length());
            byte[] combined = Base64.getDecoder().decode(base64Payload);

            if (combined.length < GCM_IV_LENGTH_BYTES) {
                throw new IllegalArgumentException("Payload chiffré trop court pour contenir l'IV");
            }

            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH_BYTES);

            int cipherLength = combined.length - GCM_IV_LENGTH_BYTES;
            byte[] cipherBytes = new byte[cipherLength];
            System.arraycopy(combined, GCM_IV_LENGTH_BYTES, cipherBytes, 0, cipherLength);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);

            byte[] decryptedBytes = cipher.doFinal(cipherBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Erreur de déchiffrement AES-GCM", e);
        }
    }

    private static byte[] sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
