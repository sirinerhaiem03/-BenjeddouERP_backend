package com.benjeddou.erp.service;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * BrevoEmailService — Envoi d'emails via l'API HTTP de Brevo (ex-Sendinblue).
 *
 * Remplace l'envoi SMTP classique (JavaMailSender), qui est bloqué sur le
 * plan gratuit de Render : les services web gratuits ne peuvent pas envoyer
 * de trafic sortant sur les ports SMTP standards (25, 465, 587).
 *
 * L'API Brevo fonctionne en HTTPS (port 443), qui n'est jamais bloqué.
 *
 * Configuration nécessaire (application.properties / variable d'env Render) :
 *   brevo.api.key=xkeysib-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
 *   brevo.sender.email=ton-adresse-verifiee@exemple.com
 *   brevo.sender.name=BENJEDDOU ERP
 */
@Service

public class BrevomailService {

    private static final Logger log = LoggerFactory.getLogger(BrevomailService.class);
    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

    // ══════════════════════════════════════════════════════
    // Lus depuis les variables d'environnement Render :
    // BREVO_API_KEY, BREVO_SENDER_EMAIL
    // ══════════════════════════════════════════════════════
    @Value("${BREVO_API_KEY}")
    private String apiKey;

    @Value("${BREVO_SENDER_EMAIL}")
    private String senderEmail;

    private static final String SENDER_NAME = "BENJEDDOU ERP";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Envoie un email HTML via l'API Brevo.
     *
     * @param toEmail      adresse du destinataire
     * @param toName       nom du destinataire (peut être vide)
     * @param subject      sujet de l'email
     * @param htmlContent  contenu HTML du corps de l'email
     * @throws RuntimeException si l'envoi échoue (réseau ou réponse d'erreur Brevo)
     */
    public void envoyerEmail(String toEmail, String toName, String subject, String htmlContent) {
        try {
            String safeToName = (toName == null || toName.isBlank()) ? toEmail : escapeJson(toName);

            String jsonBody = """
                {
                  "sender": {"name": "%s", "email": "%s"},
                  "to": [{"email": "%s", "name": "%s"}],
                  "subject": "%s",
                  "htmlContent": "%s"
                }
                """.formatted(
                    escapeJson(SENDER_NAME), escapeJson(senderEmail),
                    escapeJson(toEmail), safeToName,
                    escapeJson(subject), escapeJson(htmlContent)
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BREVO_API_URL))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201 || response.statusCode() == 202) {
                log.info("✅ Email envoyé via Brevo à {} (statut {})", toEmail, response.statusCode());
            } else {
                log.error("❌ Échec envoi email Brevo — statut {} — réponse : {}", response.statusCode(), response.body());
                throw new RuntimeException("Échec de l'envoi de l'email via Brevo (statut " + response.statusCode() + ")");
            }

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("❌ Erreur réseau lors de l'envoi de l'email via Brevo : {}", e.getMessage());
            throw new RuntimeException("Erreur réseau lors de l'envoi de l'email via Brevo", e);
        }
    }

    /** Échappe les caractères spéciaux JSON pour éviter de casser le corps de la requête. */
    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}