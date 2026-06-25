package com.benjeddou.erp.controller;

import com.benjeddou.erp.service.OpenAIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/ai")
public class AiController {

    @Autowired
    private OpenAIService openAIService;

    @PostMapping("/chat")
    public ResponseEntity<?> chatWithAssistant(@RequestBody Map<String, String> request) {
        String userMessage = request.get("message");
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Le message ne peut pas être vide.");
        }

        String aiResponse = openAIService.askAssistant(userMessage);
        
        Map<String, String> response = new HashMap<>();
        response.put("reply", aiResponse);
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/ocr")
    public ResponseEntity<?> processOcrDocument(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(new com.benjeddou.erp.payload.response.MessageReponse("Le fichier est vide."));
        }

        try {
            // Simulate processing time for the AI OCR model (e.g. 2.5 seconds)
            Thread.sleep(2500);
            
            // Mock extracted data
            Map<String, Object> extractedData = new HashMap<>();
            extractedData.put("fournisseur", "Tunisie Telecom SA");
            extractedData.put("dateFacture", "2026-06-25");
            extractedData.put("montantHt", 1250.00);
            extractedData.put("tva", 19.0);
            extractedData.put("montantTva", 237.50);
            extractedData.put("montantTtc", 1487.50);
            extractedData.put("numeroFacture", "FAC-TT-" + (int)(Math.random() * 10000));
            extractedData.put("confianceOcr", 98.5); // 98.5% confidence
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Extraction réussie");
            response.put("data", extractedData);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new com.benjeddou.erp.payload.response.MessageReponse("Erreur lors de l'extraction OCR."));
        }
    }
}
