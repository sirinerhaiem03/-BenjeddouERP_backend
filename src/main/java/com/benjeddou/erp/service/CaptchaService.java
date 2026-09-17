package com.benjeddou.erp.service;

import org.springframework.stereotype.Service;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CaptchaService — CAPTCHA image local, sans dépendance externe.
 *
 * Génère une image PNG contenant un code alphanumérique aléatoire (6 caractères).
 * Le code est stocké en mémoire (ConcurrentHashMap) associé à un sessionId UUID.
 * Chaque code expire après 5 minutes et est à usage unique.
 *
 * Endpoints associés :
 *   GET  /api/auth/captcha           → génère et retourne image + sessionId
 *   Validation via AuthController    → validateCaptcha(sessionId, userCode)
 */
@Service
public class CaptchaService {

    /** Dimensions de l'image CAPTCHA */
    private static final int WIDTH  = 220;
    private static final int HEIGHT = 75;

    /** Durée de validité d'un code en secondes (5 min) */
    private static final int EXPIRY_SECONDS = 300;

    /**
     * Caractères utilisés — excluent les caractères ambigus :
     * 0 (zéro) / O (lettre O), 1 (un) / I (i majuscule) / l (L minuscule)
     */
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Stockage des codes en cours : sessionId → CaptchaEntry */
    private final Map<String, CaptchaEntry> store = new ConcurrentHashMap<>();

    // ── Modèles internes ────────────────────────────────────────────────────

    private record CaptchaEntry(String code, Instant expiresAt) {}

    /** Résultat retourné au frontend */
    public record CaptchaResult(String sessionId, String imageBase64) {}

    // ── API publique ─────────────────────────────────────────────────────────

    /**
     * Génère un nouveau CAPTCHA.
     *
     * @return CaptchaResult avec sessionId (UUID) et imageBase64 (PNG encodé en Base64)
     */
    public CaptchaResult generateCaptcha() {
        // Nettoyage des entrées expirées
        cleanExpired();

        // Générer code aléatoire 6 caractères
        String code      = generateCode(6);
        String sessionId = UUID.randomUUID().toString();

        // Stocker avec expiration
        store.put(sessionId, new CaptchaEntry(code, Instant.now().plusSeconds(EXPIRY_SECONDS)));

        // Générer l'image
        String imageBase64 = generateImage(code);

        return new CaptchaResult(sessionId, imageBase64);
    }

    /**
     * Valide un code CAPTCHA saisi par l'utilisateur.
     * La vérification est insensible à la casse.
     * Le code est supprimé après validation (usage unique).
     *
     * @param sessionId identifiant retourné par generateCaptcha()
     * @param userCode  code saisi par l'utilisateur
     * @return true si valide, false sinon
     */
    public boolean validateCaptcha(String sessionId, String userCode) {
        if (sessionId == null || userCode == null || sessionId.isBlank() || userCode.isBlank()) {
            return false;
        }

        CaptchaEntry entry = store.get(sessionId);
        if (entry == null) return false;

        // Vérifier expiration
        if (Instant.now().isAfter(entry.expiresAt())) {
            store.remove(sessionId);
            return false;
        }

        // Usage unique : supprimer même si invalide (force un nouveau CAPTCHA)
        store.remove(sessionId);

        return entry.code().equalsIgnoreCase(userCode.trim());
    }

    /**
     * Vérifie si un sessionId CAPTCHA existe et n'a pas expiré.
     * (Utile pour le débogage / tests.)
     */
    public boolean exists(String sessionId) {
        CaptchaEntry entry = store.get(sessionId);
        if (entry == null) return false;
        if (Instant.now().isAfter(entry.expiresAt())) {
            store.remove(sessionId);
            return false;
        }
        return true;
    }

    // ── Génération du code ───────────────────────────────────────────────────

    private String generateCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    // ── Génération de l'image ────────────────────────────────────────────────

    /**
     * Génère une image PNG contenant le code CAPTCHA avec :
     *  - fond clair avec bruit (lignes + points)
     *  - caractères légèrement inclinés et colorés
     *  - anti-aliasing activé
     */
    private String generateImage(String code) {
        BufferedImage image  = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D    g2d    = image.createGraphics();

        // ── Anti-aliasing ──────────────────────────────────────────────────
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,  RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING,          RenderingHints.VALUE_RENDER_QUALITY);

        // ── Fond ───────────────────────────────────────────────────────────
        g2d.setColor(new Color(245, 247, 250));
        g2d.fillRect(0, 0, WIDTH, HEIGHT);

        // ── Lignes de bruit ────────────────────────────────────────────────
        g2d.setStroke(new BasicStroke(1.2f));
        for (int i = 0; i < 10; i++) {
            g2d.setColor(new Color(
                190 + RANDOM.nextInt(50),
                190 + RANDOM.nextInt(50),
                190 + RANDOM.nextInt(50)
            ));
            g2d.drawLine(
                RANDOM.nextInt(WIDTH), RANDOM.nextInt(HEIGHT),
                RANDOM.nextInt(WIDTH), RANDOM.nextInt(HEIGHT)
            );
        }

        // ── Points de bruit ────────────────────────────────────────────────
        for (int i = 0; i < 120; i++) {
            g2d.setColor(new Color(
                170 + RANDOM.nextInt(70),
                170 + RANDOM.nextInt(70),
                170 + RANDOM.nextInt(70)
            ));
            g2d.fillOval(RANDOM.nextInt(WIDTH), RANDOM.nextInt(HEIGHT), 2, 2);
        }

        // ── Caractères ─────────────────────────────────────────────────────
        // Palette de couleurs sombres lisibles sur fond clair
        Color[] palette = {
            new Color(15, 23, 42),    // navy
            new Color(220, 88, 10),   // orange foncé
            new Color(30, 64, 175),   // bleu
            new Color(21, 128, 61),   // vert
            new Color(109, 40, 217),  // violet
            new Color(185, 28, 28),   // rouge foncé
        };

        int charSpacing = (WIDTH - 40) / code.length();

        for (int i = 0; i < code.length(); i++) {
            g2d.setColor(palette[i % palette.length]);

            // Taille de police variable (28–36)
            int fontSize = 28 + RANDOM.nextInt(8);
            g2d.setFont(new Font("Arial", Font.BOLD, fontSize));

            // Position avec légère variation verticale
            int x = 20 + i * charSpacing;
            int y = HEIGHT / 2 + 10 + (RANDOM.nextInt(12) - 6);

            // Rotation légère (±20°)
            double angle = (RANDOM.nextDouble() - 0.5) * 0.7;

            g2d.translate(x, y);
            g2d.rotate(angle);
            g2d.drawString(String.valueOf(code.charAt(i)), 0, 0);
            g2d.rotate(-angle);
            g2d.translate(-x, -y);
        }

        // ── Bordure ────────────────────────────────────────────────────────
        g2d.setColor(new Color(203, 213, 225));
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.drawRect(1, 1, WIDTH - 2, HEIGHT - 2);

        g2d.dispose();

        // ── Encodage Base64 PNG ────────────────────────────────────────────
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("Erreur génération image CAPTCHA : " + e.getMessage(), e);
        }
    }

    // ── Nettoyage ────────────────────────────────────────────────────────────

    /** Supprime les entrées expirées du store */
    private void cleanExpired() {
        Instant now = Instant.now();
        store.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt()));
    }
}
