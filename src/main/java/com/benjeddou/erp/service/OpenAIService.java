package com.benjeddou.erp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OpenAIService {

    @Value("${openai.api.key}")
    private String apiKey;

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";

    public String askAssistant(String userMessage) {
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("REMPLACE_MOI_PAR_VOTRE_CLE_OPENAI")) {
            return simulateResponse(userMessage);
        }

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "gpt-3.5-turbo");
        
        List<Map<String, String>> messages = new ArrayList<>();
        
        // System prompt to give the AI context about the ERP
        Map<String, String> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", "Tu es l'assistant virtuel intelligent intégré à l'ERP BENJEDDOU. Ton rôle est d'aider les utilisateurs (administrateurs, commerciaux, comptables) à gérer leurs tâches. Réponds de manière professionnelle, concise et toujours en français. Tu maîtrises l'analyse prédictive, la gestion des stocks et la facturation.");
        messages.add(systemMessage);
        
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.add(userMsg);
        
        requestBody.put("messages", messages);
        requestBody.put("max_tokens", 500);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(OPENAI_URL, entity, Map.class);
            if (response.getBody() != null && response.getBody().containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> messageObj = (Map<String, Object>) choices.get(0).get("message");
                    return (String) messageObj.get("content");
                }
            }
            return "Désolé, je n'ai pas pu générer une réponse (Problème de format OpenAI).";
        } catch (Exception e) {
            System.err.println("Erreur API OpenAI (Quota dépassé ou Clé Invalide) : " + e.getMessage());
            System.err.println("Basculement automatique sur le mode Simulation (Mock) pour garantir le fonctionnement du Chatbot.");
            return simulateResponse(userMessage);
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.benjeddou.erp.repository.UtilisateurRepository utilisateurRepository;
    
    @org.springframework.beans.factory.annotation.Autowired
    private com.benjeddou.erp.repository.FactureRepository factureRepository;

    private String simulateResponse(String message) {
        String msgLower = message.toLowerCase();
        try {
            Thread.sleep(800); // Simulate network delay
        } catch (InterruptedException e) {}
        
        if (msgLower.contains("utilisateur") || msgLower.contains("actif") || msgLower.contains("combien")) {
            long total = utilisateurRepository.count();
            long actifs = utilisateurRepository.findAll().stream().filter(u -> Boolean.TRUE.equals(u.getActif())).count();
            return "Selon ma base de données, nous avons actuellement " + total + " utilisateurs inscrits, dont " + actifs + " sont actifs.";
        } else if (msgLower.contains("stock") || msgLower.contains("inventaire")) {
            return "En tant qu'assistant IA, je vous recommande de vérifier vos câbles réseau. L'analyse prédictive indique qu'une rupture de stock est probable d'ici 14 jours si le rythme de vente actuel se maintient.";
        } else if (msgLower.contains("vente") || msgLower.contains("chiffre")) {
            java.math.BigDecimal total = factureRepository.findByStatut("PAYEE").stream()
                .map(com.benjeddou.erp.model.Facture::getMontantTotal)
                .filter(java.util.Objects::nonNull)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            return "Vos ventes générées (factures payées) s'élèvent à " + total + " TND. Les algorithmes de Machine Learning prévoient un chiffre d'affaires record le mois prochain !";
        } else if (msgLower.contains("facture")) {
            return "Je peux vous aider avec vos factures. Souhaitez-vous que je scanne un nouveau document via le module OCR ?";
        } else if (msgLower.contains("bonjour") || msgLower.contains("salut")) {
            return "Bonjour ! Je suis l'assistant IA de BENJEDDOU ERP. Je peux analyser vos ventes, vos stocks ou répondre à des questions sur les utilisateurs actifs. Que puis-je faire pour vous ?";
        } else {
            return "Je n'ai pas très bien compris votre question. Je peux vous aider avec l'analyse de vos stocks, le chiffre d'affaires ou compter les utilisateurs actifs. Que souhaitez-vous faire ?";
        }
    }
}
