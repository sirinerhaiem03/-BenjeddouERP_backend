package com.benjeddou.erp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.Map;

/**
 * RecaptchaService — Vérification du token Google reCAPTCHA v2.
 *
 * Flux :
 *   1. Frontend obtient un token via le widget reCAPTCHA côté navigateur.
 *   2. Ce token est envoyé dans le champ recaptchaToken du ConnexionRequest.
 *   3. Le backend appelle l'API Google siteverify pour valider le token.
 *   4. Si success=false → connexion rejetée (HTTP 400).
 *
 * Clés de test Google (localhost uniquement) :
 *   Site Key   : 6LeIxAcTAAAAAJcZVRqyHh71UMIEGNQ_MvqkvMZU
 *   Secret Key : 6LeIxAcTAAAAAGG-vFI1TnRWxMZNFuojJ4WwGj_u
 *   → Ces clés acceptent toujours (success=true) sur localhost.
 *
 * En production : enregistrer le domaine sur google.com/recaptcha
 *   et remplacer les propriétés google.recaptcha.* dans application.properties.
 */
@Service
public class RecaptchaService {

    private static final Logger log = LoggerFactory.getLogger(RecaptchaService.class);

    @Value("${google.recaptcha.secret}")
    private String recaptchaSecret;

    @Value("${google.recaptcha.verify-url}")
    private String verifyUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Vérifie un token reCAPTCHA v2 auprès de l'API Google.
     *
     * @param token  Le token généré par le widget reCAPTCHA dans le navigateur.
     *               Null ou vide si le widget n'a pas encore été coché.
     * @return true si le token est valide (success=true dans la réponse Google),
     *         false sinon (token absent, invalide, expiré ou IP non autorisée).
     */
    @SuppressWarnings("unchecked")
    public boolean verifierToken(String token) {
        if (token == null || token.isBlank()) {
            log.warn("[reCAPTCHA] Token absent ou vide — vérification échouée");
            return false;
        }

        try {
            // Corps de la requête POST vers Google siteverify
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("secret",   recaptchaSecret);
            params.add("response", token);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            HttpEntity<MultiValueMap<String, String>> requestEntity =
                    new HttpEntity<>(params, headers);

            // Appel HTTP vers Google
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    verifyUrl, requestEntity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                boolean success = Boolean.TRUE.equals(response.getBody().get("success"));
                if (!success) {
                    Object errorCodes = response.getBody().get("error-codes");
                    log.warn("[reCAPTCHA] Vérification échouée — error-codes: {}", errorCodes);
                }
                return success;
            }

            log.warn("[reCAPTCHA] Réponse HTTP inattendue : {}", response.getStatusCode());
            return false;

        } catch (Exception e) {
            // En cas d'erreur réseau (pas de connexion à Google), on laisse passer
            // pour ne pas bloquer les utilisateurs en cas d'indisponibilité Google.
            log.error("[reCAPTCHA] Erreur lors de l'appel à Google siteverify : {}", e.getMessage());
            // Politique de tolérance : retourner true si l'API Google est indisponible
            // (évite le blocage de tous les utilisateurs si Google est down)
            return true;
        }
    }
}
