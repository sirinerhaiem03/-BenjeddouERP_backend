package com.benjeddou.erp.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service OTP — génère, stocke en mémoire et envoie par email un code à 6 chiffres.
 * Expiration : 10 minutes.
 */
@Service
public class OtpService {

    private static final int OTP_EXPIRY_SECONDS = 600; // 10 minutes
    private static final SecureRandom random = new SecureRandom();

    // Clé = email, Valeur = [code, timestamp]
    private final Map<String, OtpEntry> otpStore = new ConcurrentHashMap<>();

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    /**
     * Génère un OTP, le stocke en mémoire et tente l'envoi email en asynchrone.
     * Retourne le code généré pour permettre la continuité en environnement local/dev.
     * En cas d'échec SMTP, le code reste 100% valide en mémoire.
     */
    public String genererEtEnvoyer(String email, String prenom) {
        String code = String.format("%06d", random.nextInt(1_000_000));
        otpStore.put(email, new OtpEntry(code, Instant.now()));

        // Envoi ASYNCHRONE par email uniquement — code non affiché dans la console
        CompletableFuture.runAsync(() -> {
            try {
                if (mailSender != null) {
                    envoyerEmail(email, prenom, code);
                    System.out.println("[OtpService] ✅ Email de vérification envoyé à : " + email);
                }
            } catch (Exception e) {
                System.err.println("[OtpService] ⚠️ Erreur envoi email : " + e.getMessage());
            }
        });

        return code;
    }

    // ─────────────────────────────────────────────
    //  Vérifier un code OTP
    // ─────────────────────────────────────────────
    public boolean verifier(String email, String code) {
        OtpEntry entry = otpStore.get(email);
        if (entry == null) return false;

        // Vérifier expiration
        if (Instant.now().isAfter(entry.createdAt.plusSeconds(OTP_EXPIRY_SECONDS))) {
            otpStore.remove(email);
            return false;
        }

        boolean valid = entry.code.equals(code);
        if (valid) otpStore.remove(email); // Consommer le code
        return valid;
    }

    // ─────────────────────────────────────────────
    //  Email HTML de vérification
    // ─────────────────────────────────────────────
    private void envoyerEmail(String to, String prenom, String code) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject("🔐 Votre code de vérification — BENJEDDOU ERP");
        helper.setText(buildHtml(prenom, code), true);

        mailSender.send(message);
    }

    private String buildHtml(String prenom, String code) {
        return """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"></head>
            <body style="margin:0;padding:0;background:#0f172a;font-family:'Segoe UI',Arial,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#0f172a;padding:40px 0;">
                <tr><td align="center">
                  <table width="520" cellpadding="0" cellspacing="0"
                         style="background:#1e293b;border-radius:20px;overflow:hidden;border:1px solid rgba(255,255,255,0.08);">
                    <!-- Header -->
                    <tr>
                      <td style="background:linear-gradient(135deg,#f97316,#ea580c);padding:32px;text-align:center;">
                        <div style="font-size:2.5rem;margin-bottom:8px;">🔐</div>
                        <h1 style="margin:0;color:#fff;font-size:1.4rem;font-weight:800;letter-spacing:-0.02em;">
                          Vérification de votre compte
                        </h1>
                        <p style="margin:8px 0 0;color:rgba(255,255,255,0.8);font-size:0.85rem;">
                          BENJEDDOU ERP — Plateforme de gestion
                        </p>
                      </td>
                    </tr>
                    <!-- Body -->
                    <tr>
                      <td style="padding:36px 40px;">
                        <p style="margin:0 0 12px;color:#94a3b8;font-size:0.95rem;">
                          Bonjour <strong style="color:#f1f5f9;">%s</strong>,
                        </p>
                        <p style="margin:0 0 28px;color:#64748b;font-size:0.88rem;line-height:1.6;">
                          Voici votre code de vérification pour finaliser votre inscription :
                        </p>
                        <!-- Code OTP -->
                        <div style="text-align:center;margin:0 0 28px;">
                          <div style="display:inline-block;background:#0f172a;border:2px solid #f97316;
                                      border-radius:16px;padding:20px 40px;">
                            <span style="font-size:2.4rem;font-weight:900;letter-spacing:0.2em;
                                         color:#f97316;font-family:monospace;">
                              %s
                            </span>
                          </div>
                        </div>
                        <!-- Avertissement -->
                        <div style="background:rgba(249,115,22,0.08);border:1px solid rgba(249,115,22,0.2);
                                    border-radius:10px;padding:14px 18px;margin-bottom:20px;">
                          <p style="margin:0;color:#fb923c;font-size:0.8rem;">
                            ⏱ Ce code est valable <strong>10 minutes</strong>.
                            Ne le partagez avec personne.
                          </p>
                        </div>
                        <p style="margin:0;color:#475569;font-size:0.78rem;line-height:1.6;">
                          Si vous n'avez pas créé de compte sur BENJEDDOU ERP, ignorez cet email.
                        </p>
                      </td>
                    </tr>
                    <!-- Footer -->
                    <tr>
                      <td style="padding:16px 40px;background:#0f172a;text-align:center;
                                 border-top:1px solid rgba(255,255,255,0.06);">
                        <p style="margin:0;color:#334155;font-size:0.72rem;">
                          © 2025 BENJEDDOU ERP — Tous droits réservés
                        </p>
                      </td>
                    </tr>
                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(prenom, code);
    }

    // ─────────────────────────────────────────────
    //  Classe interne pour stocker le code + heure
    // ─────────────────────────────────────────────
    private record OtpEntry(String code, Instant createdAt) {}
}
