package com.benjeddou.erp.security.encryption;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * EncryptionConverter — Chiffrement transparent des données sensibles en base de données.
 *
 * Algorithme : AES-256-GCM (authentifié, résistant aux attaques de padding oracle).
 * Chaque valeur chiffrée contient un IV (nonce) unique de 12 bytes + tag d'authentification.
 *
 * Champs chiffrés :
 *  - Client.email, Client.telephone, Client.adresse
 *  - Fournisseur.email, Fournisseur.telephone, Fournisseur.adresse
 *  - Utilisateur.tokenRecuperation
 *
 * Format stocké en BDD : Base64(IV[12] + CipherText + GCMTag[16])
 *
 * Configuration : app.encryption.key dans application.properties (32 bytes = 256 bits)
 */
@Component
@Converter
public class EncryptionConverter implements AttributeConverter<String, String> {

    private static final Logger log = LoggerFactory.getLogger(EncryptionConverter.class);

    private static final String ALGORITHM     = "AES/GCM/NoPadding";
    private static final int    KEY_LENGTH    = 32;   // 256 bits
    private static final int    IV_LENGTH     = 12;   // 96 bits — recommandé GCM
    private static final int    TAG_LENGTH    = 128;  // bits — tag d'authentification GCM

    @Value("${app.encryption.key:BenjeddouErpDefaultEncryptionKey32b}")
    private String encryptionKeyStr;

    private static EncryptionConverter INSTANCE;
    private SecretKeySpec secretKey;
    private boolean enabled = true;

    @PostConstruct
    public void init() {
        byte[] keyBytes = encryptionKeyStr.getBytes(StandardCharsets.UTF_8);
        // Ajuster à exactement 32 bytes (padding ou troncature)
        byte[] key = new byte[KEY_LENGTH];
        System.arraycopy(keyBytes, 0, key, 0, Math.min(keyBytes.length, KEY_LENGTH));
        this.secretKey = new SecretKeySpec(key, "AES");
        INSTANCE = this;
        log.info("✅ EncryptionConverter initialisé — AES-256-GCM actif");
    }

    /**
     * Chiffre la valeur avant écriture en BDD.
     * Retourne null si la valeur est null.
     * Si le chiffrement échoue, log une erreur et retourne la valeur en clair (graceful degradation).
     */
    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isBlank()) return attribute;
        if (!enabled || secretKey == null) return attribute;

        try {
            // Générer un IV aléatoire unique pour chaque chiffrement
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] cipherText = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

            // Concaténer IV + cipherText (+ GCM tag inclus dans cipherText)
            ByteBuffer byteBuffer = ByteBuffer.allocate(IV_LENGTH + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            return "ENC:" + Base64.getEncoder().encodeToString(byteBuffer.array());

        } catch (Exception e) {
            log.error("❌ Échec chiffrement données sensibles : {}", e.getMessage());
            // Graceful degradation : stocker en clair plutôt que crasher
            return attribute;
        }
    }

    /**
     * Déchiffre la valeur lue depuis la BDD.
     * Gère la rétrocompatibilité : si la valeur n'est pas préfixée "ENC:", elle est retournée telle quelle.
     */
    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return dbData;
        if (!enabled || secretKey == null) return dbData;

        // Rétrocompatibilité : valeur stockée en clair avant activation du chiffrement
        if (!dbData.startsWith("ENC:")) return dbData;

        try {
            byte[] decoded = Base64.getDecoder().decode(dbData.substring(4)); // "ENC:".length() = 4

            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[IV_LENGTH];
            byteBuffer.get(iv);
            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("❌ Échec déchiffrement données sensibles : {}", e.getMessage());
            // Retourner la valeur brute plutôt que crasher
            return dbData;
        }
    }

    /** Accès statique pour utilisation hors-contexte Spring si nécessaire */
    public static EncryptionConverter getInstance() {
        return INSTANCE;
    }
}
