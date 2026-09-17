package com.benjeddou.erp.controller;

import com.benjeddou.erp.service.OpenAIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@lombok.extern.slf4j.Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/ai")
public class AiController {

    @Autowired
    private OpenAIService openAIService;

    @PostMapping("/chat")
    public ResponseEntity<?> chatWithAssistant(@RequestBody Map<String, String> request) {
        try {
            String userMessage = request != null ? request.get("message") : null;
            if (userMessage == null || userMessage.trim().isEmpty()) {
                return ResponseEntity.ok(Map.of("reply", "Bonjour ! Comment puis-je vous aider aujourd'hui ?"));
            }

            String lang  = request.getOrDefault("lang", "fr");
            String route = request.getOrDefault("route", "");
            String role  = request.getOrDefault("role", "");

            String aiResponse = openAIService.askAssistant(userMessage, lang, route, role);
            
            return ResponseEntity.ok(Map.of("reply", aiResponse != null ? aiResponse : "Je suis à votre écoute !"));
        } catch (Exception e) {
            log.error("❌ Exception AiController /chat : {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("reply", "🗄️ **Gestion Sécurisée des Bases de Données** :\n" +
                "• **Exportation .sql** : Téléchargement direct d'un dump complet structure + données.\n" +
                "• **Importation & Sauvegardes** : Exécution de scripts `.sql` et sauvegardes horodatées.\n" +
                "• **Sécurité renforcée** : Les opérations destructives exigent un token UUID à usage unique.\n" +
                "Accédez directement au module : [Gestion des Bases de Données](/superadmin/db-management)"));
        }
    }

    @PostMapping("/ocr")
    public ResponseEntity<?> processOcrDocument(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(new com.benjeddou.erp.payload.response.MessageReponse("Le fichier est vide."));
        }

        try {
            Map<String, Object> extractedData = openAIService.processLocalOcr(file);
            String texteExtrait = (String) extractedData.getOrDefault("texteBrut", "");
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Extraction réussie");
            response.put("data", extractedData);
            response.put("texte", texteExtrait);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(new com.benjeddou.erp.payload.response.MessageReponse("Erreur lors de l'extraction OCR : " + e.getMessage()));
        }
    }
}
