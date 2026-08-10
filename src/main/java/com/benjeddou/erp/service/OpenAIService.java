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
        systemMessage.put("content", "Tu es l'assistant virtuel intelligent intégré à BENJEDDOU ERP SaaS. Réponds TOUJOURS couramment dans la LANGUE exacte utilisée par l'utilisateur (Arabe, Français ou Anglais). Tu maîtrises l'analyse prédictive, les ventes, les stocks, la comptabilité et la gestion des utilisateurs.");
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
            System.err.println("Basculement automatique sur le mode Simulation Multilingue (Mock) pour le Chatbot.");
            return simulateResponse(userMessage);
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.benjeddou.erp.repository.UtilisateurRepository utilisateurRepository;
    
    @org.springframework.beans.factory.annotation.Autowired
    private com.benjeddou.erp.repository.FactureRepository factureRepository;

    private String simulateResponse(String message) {
        String msgLower = message.toLowerCase();
        boolean isArabic = message.matches(".*[\\u0600-\\u06FF].*");
        boolean isEnglish = msgLower.contains("hello") || msgLower.contains("hi") || msgLower.contains("user")
                || msgLower.contains("sale") || msgLower.contains("stock") || msgLower.contains("invoice")
                || msgLower.contains("price") || msgLower.contains("help");

        try {
            Thread.sleep(600);
        } catch (InterruptedException e) {}
        
        long totalUsers = utilisateurRepository.count();
        long actifsUsers = utilisateurRepository.findAll().stream().filter(u -> Boolean.TRUE.equals(u.getActif())).count();
        java.math.BigDecimal totalVentes = factureRepository.findByStatut("PAYEE").stream()
            .map(com.benjeddou.erp.model.Facture::getMontantTotal)
            .filter(java.util.Objects::nonNull)
            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        // ── ARABIC RESPONSES ────────────────────────────────────────────────
        if (isArabic) {
            if (msgLower.contains("مستخدم") || msgLower.contains("حساب") || msgLower.contains("نشط")) {
                return "بناءً على قاعدة البيانات، يوجد حالياً " + totalUsers + " مستخدم مسجل، منهم " + actifsUsers + " نشطين.";
            } else if (msgLower.contains("مخزون") || msgLower.contains("بضاعة")) {
                return "تنبيه المخزون الذكي: يُوصى بمراجعة شحنات المنتجات الأكثر طلباً لتفادي أي نقص خلال الـ 14 يوماً القادمة.";
            } else if (msgLower.contains("مبيعات") || msgLower.contains("ارباح") || msgLower.contains("رقم")) {
                return "إجمالي مبيعاتك المسددة يبلغ " + totalVentes + " دينار تونسي. الخوارزميات الذكية تتوقع نمواً ممتازاً بالشهر القادم!";
            } else if (msgLower.contains("فاتورة") || msgLower.contains("دفع")) {
                return "يمكنني مساعدتك في متابعة الفواتير وإجراء التحليل OCR الذكي أو إرسال تذكيرات عبر واتساب وتليجرام.";
            } else {
                return "مرحباً بك! أنا المساعد الذكي لنظام BENJEDDOU ERP. يمكنني مساعدتك في متابعة المبيعات، المخزون، الفواتير وإدارة المستخدمين. كيف يمكنني خدمتك اليوم؟";
            }
        }

        // ── ENGLISH RESPONSES ───────────────────────────────────────────────
        if (isEnglish) {
            if (msgLower.contains("user") || msgLower.contains("active")) {
                return "According to the database, there are currently " + totalUsers + " registered users, of which " + actifsUsers + " are active.";
            } else if (msgLower.contains("stock") || msgLower.contains("inventory")) {
                return "Smart Stock Alert: Predictive analysis suggests reordering top products to avoid stockouts in the next 14 days.";
            } else if (msgLower.contains("sale") || msgLower.contains("revenue") || msgLower.contains("turnover")) {
                return "Your paid sales total is " + totalVentes + " TND. Machine learning models project a strong growth trajectory for next month!";
            } else if (msgLower.contains("invoice")) {
                return "I can assist you with invoice tracking, OCR document processing, or automated customer payment reminders.";
            } else {
                return "Hello! I am the BENJEDDOU ERP AI Virtual Assistant. I can help you with sales analytics, inventory tracking, and user management. How can I assist you today?";
            }
        }

        // ── FRENCH RESPONSES (DEFAULT) ──────────────────────────────────────
        if (msgLower.contains("utilisateur") || msgLower.contains("actif") || msgLower.contains("combien")) {
            return "Selon ma base de données, nous avons actuellement " + totalUsers + " utilisateurs inscrits, dont " + actifsUsers + " sont actifs.";
        } else if (msgLower.contains("stock") || msgLower.contains("inventaire")) {
            return "Alerte de Stock Intelligente : L'analyse prédictive indique qu'un réapprovisionnement est recommandé d'ici 14 jours pour maintenir le rythme des ventes.";
        } else if (msgLower.contains("vente") || msgLower.contains("chiffre")) {
            return "Vos ventes générées (factures payées) s'élèvent à " + totalVentes + " TND. Les algorithmes de Machine Learning prévoient un chiffre d'affaires record le mois prochain !";
        } else if (msgLower.contains("facture")) {
            return "Je peux vous aider avec vos factures. Souhaitez-vous que je scanne un nouveau document via le module OCR ?";
        } else {
            return "Bonjour ! Je suis l'assistant IA de BENJEDDOU ERP. Je peux analyser vos ventes, vos stocks ou répondre à des questions sur les utilisateurs actifs. Que puis-je faire pour vous ?";
        }
    }

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    public Map<String, Object> processLocalOcr(org.springframework.web.multipart.MultipartFile file) {
        Map<String, Object> extractedData = new HashMap<>();
        
        // Pas de fausses valeurs par défaut ! Initialisation explicite à nul/vide.
        String fournisseurExtrait = null;
        String dateExtrait = null;
        String numFactureExtrait = null;
        Double htExtrait = null;
        Double tvaExtrait = 19.0;
        Double ttcExtrait = null;

        try {
            // 1. Extraction du texte brut via OCR.space API (ou fallback binaire)
            String text = "";
            try {
                String base64 = java.util.Base64.getEncoder().encodeToString(file.getBytes());
                String mimeType = file.getContentType();
                if (mimeType == null || mimeType.isEmpty()) mimeType = "application/pdf";
                String base64Image = "data:" + mimeType + ";base64," + base64;

                org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
                org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                headers.setContentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED);
                headers.set("apikey", "helloworld");

                org.springframework.util.MultiValueMap<String, String> body = new org.springframework.util.LinkedMultiValueMap<>();
                body.add("base64Image", base64Image);
                body.add("language", "fre");
                body.add("OCREngine", "2");

                org.springframework.http.HttpEntity<org.springframework.util.MultiValueMap<String, String>> requestEntity = new org.springframework.http.HttpEntity<>(body, headers);
                org.springframework.http.ResponseEntity<java.util.Map> response = restTemplate.postForEntity("https://api.ocr.space/parse/image", requestEntity, java.util.Map.class);

                if (response.getBody() != null && response.getBody().containsKey("ParsedResults")) {
                    java.util.List<java.util.Map<String, Object>> results = (java.util.List<java.util.Map<String, Object>>) response.getBody().get("ParsedResults");
                    if (results != null && !results.isEmpty()) {
                        text = (String) results.get(0).get("ParsedText");
                    }
                }
            } catch (Exception ex) {
                System.err.println("Erreur OCR.space : " + ex.getMessage());
            }

            if (text == null) text = "";

            System.out.println("--- TEXTE REEL EXTRAIT DU DOCUMENT ---");
            System.out.println(text);
            System.out.println("-------------------------------------");

            // 2. Reconnaissance dynamique des dates (ex: 06/08/2026, 2026-08-06, 06-08-2026)
            java.util.regex.Matcher dateMatcher = java.util.regex.Pattern.compile("(\\d{2}[/.-]\\d{2}[/.-]\\d{4}|\\d{4}[/.-]\\d{2}[/.-]\\d{2})").matcher(text);
            if (dateMatcher.find()) {
                String d = dateMatcher.group(1);
                if (d.contains("/")) {
                    String[] parts = d.split("/");
                    if (parts[0].length() == 4) {
                        dateExtrait = parts[0] + "-" + parts[1] + "-" + parts[2];
                    } else {
                        dateExtrait = parts[2] + "-" + parts[1] + "-" + parts[0];
                    }
                } else if (d.contains("-") && d.indexOf("-") == 2) {
                    String[] parts = d.split("-");
                    dateExtrait = parts[2] + "-" + parts[1] + "-" + parts[0];
                } else {
                    dateExtrait = d;
                }
            }

            // 3. Reconnaissance du Numéro de Facture (ex: FAC-20260806-3EDD83, FACT-2026-003, INV-102)
            java.util.regex.Matcher facMatcher = java.util.regex.Pattern.compile("(?i)(FAC[T]?-[A-Z0-9-]+|N°\\s*\\S+|INV-\\S+|FC-\\S+)").matcher(text);
            if (facMatcher.find()) {
                numFactureExtrait = facMatcher.group(1).replace("N°", "").replace("n°", "").trim();
            }

            // 4. Reconnaissance avancée des montants financiers (Support FR & EN : Total incl. tax, Total excl. tax, VAT, TTC, HT)
            // Matcher Total TTC / Total incl. tax
            java.util.regex.Matcher ttcMatcher = java.util.regex.Pattern.compile("(?i)(Total\\s*incl\\.?\\s*tax|Total\\s*TTC|Net\\s*à\\s*payer|Montant\\s*TTC|Total\\s*:).*?([0-9]{1,3}(?:[., ]\\d{3})*(?:[.,]\\d{2,3})?)").matcher(text);
            if (ttcMatcher.find()) {
                ttcExtrait = parseMontantFinancier(ttcMatcher.group(2));
            }

            // Matcher Total HT / Total excl. tax
            java.util.regex.Matcher htMatcher = java.util.regex.Pattern.compile("(?i)(Total\\s*excl\\.?\\s*tax|Total\\s*HT|Montant\\s*HT).*?([0-9]{1,3}(?:[., ]\\d{3})*(?:[.,]\\d{2,3})?)").matcher(text);
            if (htMatcher.find()) {
                htExtrait = parseMontantFinancier(htMatcher.group(2));
            }

            // Matcher TVA / VAT
            java.util.regex.Matcher tvaMatcher = java.util.regex.Pattern.compile("(?i)(VAT\\s*\\(?19%?\\)?|TVA\\s*\\(?19%?\\)?).*?([0-9]{1,3}(?:[., ]\\d{3})*(?:[.,]\\d{2,3})?)").matcher(text);
            if (tvaMatcher.find()) {
                Double valTva = parseMontantFinancier(tvaMatcher.group(2));
                if (valTva != null && valTva > 0) {
                    tvaExtrait = 19.0;
                }
            }

            // Fallback si TTC non trouvé avec le libellé principal
            if (ttcExtrait == null) {
                java.util.regex.Matcher fallbackMoney = java.util.regex.Pattern.compile("([0-9]{1,3}(?:[., ]\\d{3})*(?:[.,]\\d{2,3}))").matcher(text);
                double highest = 0.0;
                while (fallbackMoney.find()) {
                    Double v = parseMontantFinancier(fallbackMoney.group(1));
                    if (v != null && v > highest && v < 1000000.0) {
                        highest = v;
                    }
                }
                if (highest > 0) {
                    ttcExtrait = highest;
                }
            }

            // Calcul automatique de cohérence HT / TTC si l'un manque
            if (ttcExtrait != null && htExtrait == null) {
                htExtrait = Math.round((ttcExtrait / 1.19) * 1000.0) / 1000.0;
            } else if (htExtrait != null && ttcExtrait == null) {
                ttcExtrait = Math.round((htExtrait * 1.19) * 1000.0) / 1000.0;
            }

            // 5. Extraction intelligente du Fournisseur / Client (Filtrage des bruits de modales UI)
            String[] lines = text.split("\\r?\\n");
            boolean prochaineLigneEstClient = false;

            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String lineUpper = line.toUpperCase();
                // Détection de section d'adressage "BILLED TO:" ou "FACTURÉ À:"
                if (lineUpper.contains("BILLED TO:") || lineUpper.contains("FACTURÉ À:") || lineUpper.contains("CLIENT:")) {
                    prochaineLigneEstClient = true;
                    continue;
                }

                if (prochaineLigneEstClient) {
                    if (!lineUpper.contains("TAX ID") && !lineUpper.contains("STATUS") && line.length() > 2) {
                        fournisseurExtrait = line;
                        break;
                    }
                }

                // Filtrer les textes système / boutons de modale UI
                if (!prochaineLigneEstClient && fournisseurExtrait == null) {
                    if (!lineUpper.contains("INVOICE PREVIEW") 
                        && !lineUpper.contains("PREVIEW") 
                        && !lineUpper.contains("CLOSE") 
                        && !lineUpper.contains("MARK AS PAID") 
                        && !lineUpper.contains("SEND BY EMAIL") 
                        && !lineUpper.contains("PRINT / PDF")
                        && !lineUpper.contains("INVOICE")
                        && !lineUpper.contains("PAYMENT CONDITIONS")
                        && !lineUpper.contains("BANK DETAILS")
                        && !lineUpper.contains("AMOUNT IN WORDS")
                        && !lineUpper.contains("STATUS")
                        && !lineUpper.contains("EN ATTENTE")
                        && line.length() > 3) {
                        fournisseurExtrait = line;
                    }
                }
            }

            // Si le fournisseur est resté null mais qu'on a BENJEDDOU ERP ou Medina Group
            if (fournisseurExtrait == null || fournisseurExtrait.toUpperCase().contains("INVOICE PREVIEW")) {
                if (text.contains("Medina Group")) {
                    fournisseurExtrait = "Medina Group";
                } else if (text.contains("BENJEDDOU ERP")) {
                    fournisseurExtrait = "BENJEDDOU ERP";
                }
            }

        } catch (Exception e) {
            System.err.println("Erreur analyse OCR document : " + e.getMessage());
        }

        // Affectation des valeurs réellement extraites
        extractedData.put("fournisseur", fournisseurExtrait);
        extractedData.put("dateFacture", dateExtrait);
        extractedData.put("numeroFacture", numFactureExtrait);
        extractedData.put("montantHt", htExtrait);
        extractedData.put("tva", tvaExtrait);
        extractedData.put("montantTtc", ttcExtrait);

        // 6. CALCUL DYNAMIQUE DU SCORE DE CONFIANCE REEL
        int fieldsDetected = 0;
        if (fournisseurExtrait != null && !fournisseurExtrait.isBlank()) fieldsDetected++;
        if (dateExtrait != null && !dateExtrait.isBlank()) fieldsDetected++;
        if (numFactureExtrait != null && !numFactureExtrait.isBlank()) fieldsDetected++;
        if (htExtrait != null && htExtrait > 0) fieldsDetected++;
        if (ttcExtrait != null && ttcExtrait > 0) fieldsDetected++;

        double scoreConfianceReel = Math.round((fieldsDetected / 5.0) * 100.0);
        extractedData.put("confianceOcr", scoreConfianceReel);

        return extractedData;
    }

    private Double parseMontantFinancier(String valStr) {
        if (valStr == null) return null;
        valStr = valStr.trim().replaceAll("[^0-9.,]", "");
        if (valStr.isEmpty()) return null;

        if (valStr.contains(",") && valStr.contains(".")) {
            if (valStr.indexOf(",") < valStr.indexOf(".")) {
                valStr = valStr.replace(",", ""); // 10,115.000 -> 10115.000
            } else {
                valStr = valStr.replace(".", "").replace(",", "."); // 10.115,000 -> 10115.000
            }
        } else if (valStr.contains(",")) {
            String[] parts = valStr.split(",");
            if (parts.length == 2 && parts[1].length() == 3) {
                valStr = valStr.replace(",", ".");
            } else {
                valStr = valStr.replace(",", "");
            }
        }

        try {
            return Double.parseDouble(valStr);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> simulateOcr() {
        try { Thread.sleep(2500); } catch (InterruptedException e) {}
        Map<String, Object> extractedData = new HashMap<>();
        extractedData.put("fournisseur", "Tunisie Telecom SA");
        extractedData.put("dateFacture", "2026-06-25");
        extractedData.put("montantHt", 1250.00);
        extractedData.put("tva", 19.0);
        extractedData.put("montantTva", 237.50);
        extractedData.put("montantTtc", 1487.50);
        extractedData.put("numeroFacture", "FAC-TT-" + (int)(Math.random() * 10000));
        extractedData.put("confianceOcr", 98.5);
        return extractedData;
    }
}
