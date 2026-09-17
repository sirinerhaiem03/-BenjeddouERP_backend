package com.benjeddou.erp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * Service de dictionnaire intelligent.
 * Utilise OpenAI GPT pour :
 *  - Correction orthographique et grammaticale
 *  - Amélioration de la rédaction
 *  - Suggestions de formulations adaptées au contexte ERP
 *  - Support FR, AR, EN
 */
@Service
@Slf4j
public class DictionnaireService {

    @Value("${openai.api.key:}")
    private String openaiApiKey;

    private final ObjectMapper mapper = new ObjectMapper();
    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";

    /**
     * Corrige et améliore un texte selon le mode choisi.
     *
     * @param texte   Texte à traiter
     * @param langue  Langue : fr, ar, en
     * @param mode    "correction" | "amelioration" | "suggestion"
     */
    public Map<String, Object> corrigerEtAmeliorer(String texte, String langue, String mode) {
        String prompt = buildPromptCorrection(texte, langue, mode);
        String reponse = appelOpenAI(prompt);

        Map<String, Object> resultat = new LinkedHashMap<>();
        resultat.put("texteOriginal", texte);
        resultat.put("langue", langue);
        resultat.put("mode", mode);

        if (reponse != null) {
            // Tente de parser une réponse JSON structurée
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = mapper.readValue(reponse, Map.class);
                resultat.put("texteCorrected", parsed.getOrDefault("texte_corrige", reponse));
                resultat.put("erreursTrouvees", parsed.getOrDefault("erreurs", List.of()));
                resultat.put("explication", parsed.getOrDefault("explication", ""));
                resultat.put("scoreQualite", parsed.getOrDefault("score_qualite", null));
            } catch (Exception e) {
                // Fallback : réponse brute
                resultat.put("texteCorrected", reponse);
                resultat.put("erreursTrouvees", List.of());
                resultat.put("explication", "");
            }
            resultat.put("success", true);
        } else {
            resultat.put("texteCorrected", texte);
            resultat.put("success", false);
            resultat.put("erreur", "Service IA indisponible");
        }
        return resultat;
    }

    /**
     * Génère des suggestions de formulation pour un contexte ERP.
     */
    public Map<String, Object> suggerer(String contexte, String langue, int nbSuggestions) {
        String prompt = buildPromptSuggestion(contexte, langue, nbSuggestions);
        String reponse = appelOpenAI(prompt);

        Map<String, Object> resultat = new LinkedHashMap<>();
        resultat.put("contexte", contexte);
        resultat.put("langue", langue);

        if (reponse != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = mapper.readValue(reponse, Map.class);
                resultat.put("suggestions", parsed.getOrDefault("suggestions", List.of()));
                resultat.put("success", true);
            } catch (Exception e) {
                // Réponse en texte brut : split par lignes
                List<String> lines = Arrays.stream(reponse.split("\n"))
                    .map(String::trim).filter(s -> !s.isBlank()).limit(nbSuggestions).toList();
                resultat.put("suggestions", lines);
                resultat.put("success", true);
            }
        } else {
            resultat.put("suggestions", List.of());
            resultat.put("success", false);
        }
        return resultat;
    }

    // ── Prompts ────────────────────────────────────────────────────────

    private String buildPromptCorrection(String texte, String langue, String mode) {
        String instruction = switch (mode) {
            case "amelioration" -> switch (langue) {
                case "ar" -> "أنت مساعد كتابة محترف. حسّن النص التالي مع الحفاظ على المعنى الأصلي. قدم النتيجة بتنسيق JSON: {\"texte_corrige\": \"...\", \"explication\": \"...\", \"score_qualite\": 0-100}";
                case "en" -> "You are a professional writing assistant. Improve the following text while preserving the original meaning. Return JSON: {\"texte_corrige\": \"...\", \"explication\": \"...\", \"score_qualite\": 0-100}";
                default -> "Tu es un assistant de rédaction professionnel pour un ERP. Améliore le texte suivant en gardant le sens, en le rendant plus professionnel et fluide. Réponds en JSON: {\"texte_corrige\": \"...\", \"explication\": \"...\", \"score_qualite\": 0-100}";
            };
            default -> switch (langue) {
                case "ar" -> "أنت مدقق لغوي محترف. صحّح الأخطاء الإملائية والنحوية في النص التالي. قدم النتيجة بتنسيق JSON: {\"texte_corrige\": \"...\", \"erreurs\": [\"...\"], \"explication\": \"...\"}";
                case "en" -> "You are a professional proofreader. Correct spelling and grammar errors in the following text. Return JSON: {\"texte_corrige\": \"...\", \"erreurs\": [\"...\"], \"explication\": \"...\"}";
                default -> "Tu es un correcteur orthographique professionnel pour un logiciel ERP. Corrige les fautes d'orthographe et de grammaire. Réponds en JSON: {\"texte_corrige\": \"...\", \"erreurs\": [\"liste des erreurs corrigées\"], \"explication\": \"...\"}";
            };
        };
        return instruction + "\n\nTexte : " + texte;
    }

    private String buildPromptSuggestion(String contexte, String langue, int nb) {
        return switch (langue) {
            case "ar" -> String.format("اقترح %d صياغات احترافية لـ \"%s\" في سياق نظام ERP. الرد بتنسيق JSON: {\"suggestions\": [\"...\"]}", nb, contexte);
            case "en" -> String.format("Suggest %d professional formulations for \"%s\" in an ERP context. Return JSON: {\"suggestions\": [\"...\"]}", nb, contexte);
            default   -> String.format("Propose %d formulations professionnelles pour \"%s\" dans le contexte d'un ERP. Réponds en JSON: {\"suggestions\": [\"...\"]}", nb, contexte);
        };
    }

    // ── Appel OpenAI ──────────────────────────────────────────────────

    private String appelOpenAI(String prompt) {
        if (openaiApiKey == null || openaiApiKey.isBlank()) {
            log.warn("Clé OpenAI non configurée — DictionnaireService indisponible");
            return null;
        }
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openaiApiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("model", "gpt-4o-mini");
            body.put("max_tokens", 1000);
            body.put("temperature", 0.3);
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(OPENAI_URL, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
                    return (String) msg.get("content");
                }
            }
        } catch (Exception e) {
            log.error("Erreur DictionnaireService OpenAI : {}", e.getMessage());
        }
        return null;
    }
}
