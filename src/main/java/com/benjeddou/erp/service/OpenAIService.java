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
        return askAssistant(userMessage, "fr", "", "");
    }

    public String askAssistant(String userMessage, String lang, String route, String role) {
        try {
            if (apiKey != null && !apiKey.isEmpty() && !apiKey.equals("REMPLACE_MOI_PAR_VOTRE_CLE_OPENAI")) {
                try {
                    RestTemplate restTemplate = new RestTemplate();
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.setBearerAuth(apiKey);

                    Map<String, Object> requestBody = new HashMap<>();
                    requestBody.put("model", "gpt-3.5-turbo");
                    
                    List<Map<String, String>> messages = new ArrayList<>();
                    Map<String, String> systemMessage = new HashMap<>();
                    systemMessage.put("role", "system");
                    systemMessage.put("content", "Tu es l'assistant virtuel intelligent intégré à BENJEDDOU ERP SaaS. " +
                        "Langue active: " + lang + ". Route actuelle: " + route + ". Rôle: " + role + ". " +
                        "Réponds TOUJOURS dans la langue exacte utilisée par l'utilisateur (Arabe, Français ou Anglais). " +
                        "Assiste l'utilisateur sur la gestion des bases de données (export, import, backup, vider, tokens), l'audit log, les utilisateurs, les entreprises SaaS, le stock, la comptabilité et la facturation.");
                    messages.add(systemMessage);
                    
                    Map<String, String> userMsg = new HashMap<>();
                    userMsg.put("role", "user");
                    userMsg.put("content", userMessage);
                    messages.add(userMsg);
                    
                    requestBody.put("messages", messages);
                    requestBody.put("max_tokens", 600);

                    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
                    ResponseEntity<Map> response = restTemplate.postForEntity(OPENAI_URL, entity, Map.class);
                    if (response.getBody() != null && response.getBody().containsKey("choices")) {
                        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
                        if (!choices.isEmpty()) {
                            Map<String, Object> messageObj = (Map<String, Object>) choices.get(0).get("message");
                            return (String) messageObj.get("content");
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Basculement automatique sur le moteur IA local multilingue : " + e.getMessage());
                }
            }
            return simulateResponse(userMessage, lang, route, role);
        } catch (Throwable t) {
            System.err.println("❌ Erreur moteur IA: " + t.getMessage());
            return "🗄️ **Gestion Sécurisée des Bases de Données** :\n" +
                   "• **Exportation .sql** : Téléchargement direct d'un dump complet structure + données.\n" +
                   "• **Importation & Sauvegardes** : Exécution de scripts `.sql` et sauvegardes horodatées.\n" +
                   "• **Sécurité renforcée** : Les opérations destructives exigent un token UUID à usage unique.\n" +
                   "Accédez directement au module : [Gestion des Bases de Données](/superadmin/db-management)";
        }
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.benjeddou.erp.repository.UtilisateurRepository utilisateurRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.benjeddou.erp.repository.FactureRepository factureRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.benjeddou.erp.repository.ProduitRepository produitRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.benjeddou.erp.repository.CommandeRepository commandeRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.benjeddou.erp.repository.ClientRepository clientRepository;

    private String simulateResponse(String message, String lang, String route, String role) {
        String msgLower = message.toLowerCase().trim();
        boolean isArabic = message.matches(".*[\\u0600-\\u06FF].*") || "ar".equalsIgnoreCase(lang);
        boolean isEnglish = "en".equalsIgnoreCase(lang);

        try { Thread.sleep(300); } catch (InterruptedException ignored) {}

        // ── Récupérer les données réelles ──────────────────────────────
        long totalUsers = 0; long actifsUsers = 0;
        try { if (utilisateurRepository != null) { totalUsers = utilisateurRepository.count(); actifsUsers = utilisateurRepository.findAll().stream().filter(u -> Boolean.TRUE.equals(u.getActif())).count(); } } catch (Throwable ignored) {}

        java.math.BigDecimal totalVentes = java.math.BigDecimal.ZERO;
        try { if (factureRepository != null) totalVentes = factureRepository.findByStatut("PAYEE").stream().map(com.benjeddou.erp.model.Facture::getMontantTotal).filter(java.util.Objects::nonNull).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add); } catch (Throwable ignored) {}

        long totalFactures = 0; long facturesAttente = 0;
        try { if (factureRepository != null) { totalFactures = factureRepository.count(); facturesAttente = factureRepository.findAll().stream().filter(f -> "EN_ATTENTE".equals(f.getStatut())).count(); } } catch (Throwable ignored) {}

        long totalProduits = 0; long produitsAlerte = 0;
        try { if (produitRepository != null) { java.util.List<com.benjeddou.erp.model.Produit> prods = produitRepository.findAll(); totalProduits = prods.size(); produitsAlerte = prods.stream().filter(p -> p.getQuantiteStock() != null && p.getSeuilStockMin() != null && p.getQuantiteStock() <= p.getSeuilStockMin()).count(); } } catch (Throwable ignored) {}

        long totalCommandes = 0; long commandesMois = 0;
        try { if (commandeRepository != null) { totalCommandes = commandeRepository.count(); java.time.LocalDate now = java.time.LocalDate.now(); commandesMois = commandeRepository.findAll().stream().filter(c -> c.getDateCommande() != null && c.getDateCommande().getYear() == now.getYear() && c.getDateCommande().getMonthValue() == now.getMonthValue()).count(); } } catch (Throwable ignored) {}

        long totalClients = 0;
        try { if (clientRepository != null) totalClients = clientRepository.count(); } catch (Throwable ignored) {}

        // ════════════════════════════════════════════════════════════════
        // ARABIC
        // ════════════════════════════════════════════════════════════════
        if (isArabic) {
            // Salutations
            if (msgLower.matches("مرحبا?|أهلا?|السلام عليكم|كيف حالك|صباح الخير|مساء الخير|هلا|سلام")) {
                return "مرحباً بك! 😊 أنا **المساعد الذكي لنظام BENJEDDOU ERP**.\n" +
                       "كيف يمكنني مساعدتك اليوم؟ يمكنني الإجابة على أسئلتك حول:\n" +
                       "• لوحة التحكم والإحصائيات\n• المخزون والمنتجات\n• الفواتير والمبيعات\n• المستخدمين والأدوار\n• قواعد البيانات والأمان";
            }
            // Tableau de bord / ملخص
            if (msgLower.contains("ملخص") || msgLower.contains("لوحة") || msgLower.contains("تحكم") || msgLower.contains("نظرة عامة") || msgLower.contains("إحصائي") || msgLower.contains("dashboard")) {
                return "📊 **ملخص لوحة التحكم — BENJEDDOU ERP**:\n\n" +
                       "👥 **المستخدمون**: " + totalUsers + " مسجل، منهم " + actifsUsers + " نشط\n" +
                       "🛒 **الطلبات**: " + totalCommandes + " إجمالية (" + commandesMois + " هذا الشهر)\n" +
                       "🧾 **الفواتير**: " + totalFactures + " إجمالية (" + facturesAttente + " في الانتظار)\n" +
                       "📦 **المنتجات**: " + totalProduits + " منتج (" + produitsAlerte + " في حالة تنبيه مخزون)\n" +
                       "💰 **إجمالي المبيعات المحصلة**: **" + totalVentes + " د.ت**\n" +
                       "👤 **العملاء**: " + totalClients + " عميل مسجل";
            }
            // Stock / مخزون
            if (msgLower.contains("مخزون") || msgLower.contains("تنبيه") || msgLower.contains("منتج") || msgLower.contains("بضاعة") || msgLower.contains("stock")) {
                return "📦 **حالة المخزون**:\n\n" +
                       "• **إجمالي المنتجات**: " + totalProduits + " منتج\n" +
                       "• **منتجات تحت حد التنبيه**: 🔴 **" + produitsAlerte + " منتج** بحاجة للتموين\n\n" +
                       (produitsAlerte > 0 ? "⚠️ يوجد **" + produitsAlerte + " منتج** وصل كميته إلى أو دون الحد الأدنى المحدد.\n" : "✅ جميع المنتجات لديها مخزون كافٍ.\n") +
                       "الانتقال إلى: [إدارة المخزون](/products)";
            }
            // Factures / فواتير
            if (msgLower.contains("فاتور") || msgLower.contains("مبيعات") || msgLower.contains("ربح") || msgLower.contains("ca") || msgLower.contains("رقم الأعمال") || msgLower.contains("مبلغ")) {
                return "💰 **المبيعات والفواتير**:\n\n" +
                       "• **إجمالي الفواتير**: " + totalFactures + "\n" +
                       "• **فواتير في الانتظار**: " + facturesAttente + "\n" +
                       "• **إجمالي المبيعات المحصلة**: **" + totalVentes + " د.ت**\n" +
                       "• **إجمالي الطلبات**: " + totalCommandes + " (" + commandesMois + " هذا الشهر)\n\n" +
                       "الانتقال إلى: [إدارة الفواتير](/commercial)";
            }
            // Utilisateurs
            if (msgLower.contains("مستخدم") || msgLower.contains("حساب") || msgLower.contains("صلاحية") || msgLower.contains("دور")) {
                return "👥 **إدارة المستخدمين والأدوار**:\n\n" +
                       "• **إجمالي المستخدمين**: " + totalUsers + "\n• **المستخدمون النشطون**: " + actifsUsers + "\n" +
                       "الأدوار المتاحة: Admin, Commercial, Comptable, Stock, Client.\n" +
                       "الانتقال إلى: [إدارة المستخدمين](/admin/users)";
            }
            // Base de données
            if (msgLower.contains("قاعدة") || msgLower.contains("بيانات") || msgLower.contains("تصدير") || msgLower.contains("استيراد") || msgLower.contains("نسخ احتياطي")) {
                return "🗄️ **إدارة قواعد البيانات**:\n\n" +
                       "• **تصدير SQL**: تحميل نسخة كاملة من قاعدة البيانات\n" +
                       "• **استيراد**: رفع ملف `.sql` لاستعادة البيانات\n" +
                       "• **نسخ احتياطي**: إنشاء نسخ مؤرخة تلقائياً\n" +
                       "• **الأمان**: العمليات الحساسة تطلب رمز تأكيد مؤقت\n" +
                       "الانتقال إلى: [إدارة قواعد البيانات](/superadmin/db-management)";
            }
            // Audit
            if (msgLower.contains("تدقيق") || msgLower.contains("سجل") || msgLower.contains("أمان") || msgLower.contains("حماية")) {
                return "🛡️ **سجل التدقيق والأمان**:\n\nيتم تسجيل جميع العمليات: تسجيل الدخول، التعديلات، الحذف وعمليات قاعدة البيانات.\n" +
                       "الانتقال إلى: [سجل التدقيق](/superadmin/audit)";
            }
            // Clients
            if (msgLower.contains("عميل") || msgLower.contains("زبون") || msgLower.contains("عملاء")) {
                return "👤 **العملاء**:\n\n• **إجمالي العملاء المسجلين**: " + totalClients + " عميل\n" +
                       "الانتقال إلى: [إدارة العملاء](/commercial)";
            }
            // Commandes
            if (msgLower.contains("طلب") || msgLower.contains("أمر") || msgLower.contains("commande")) {
                return "🛒 **الطلبات**:\n\n• **إجمالي الطلبات**: " + totalCommandes + "\n• **طلبات هذا الشهر**: " + commandesMois + "\n" +
                       "الانتقال إلى: [إدارة الطلبات](/commercial)";
            }
            // Réponse par défaut enrichie
            return "مرحباً! أنا **المساعد الذكي لنظام BENJEDDOU ERP** 🤖.\n\n" +
                   "📊 **إحصائيات سريعة**: " + totalUsers + " مستخدم | " + totalProduits + " منتج | " + totalFactures + " فاتورة\n\n" +
                   "يمكنني مساعدتك في:\n" +
                   "• **ملخص لوحة التحكم** — اكتب: ملخص لوحة التحكم\n" +
                   "• **المخزون والتنبيهات** — اكتب: حالة المخزون\n" +
                   "• **الفواتير والمبيعات** — اكتب: إجمالي المبيعات\n" +
                   "• **قاعدة البيانات** — اكتب: تصدير قاعدة البيانات\n" +
                   "كيف يمكنني خدمتك؟";
        }

        // ════════════════════════════════════════════════════════════════
        // ENGLISH
        // ════════════════════════════════════════════════════════════════
        if (isEnglish) {
            if (msgLower.matches("(hello|hi|hey|good morning|good evening|bonjour).*")) {
                return "Hello! I'm the **BENJEDDOU ERP AI Assistant** 🤖.\nHow can I help you today?";
            }
            if (msgLower.contains("dashboard") || msgLower.contains("summary") || msgLower.contains("overview") || msgLower.contains("kpi")) {
                return "📊 **Dashboard Summary — BENJEDDOU ERP**:\n\n" +
                       "👥 **Users**: " + totalUsers + " registered (" + actifsUsers + " active)\n" +
                       "🛒 **Orders**: " + totalCommandes + " total (" + commandesMois + " this month)\n" +
                       "🧾 **Invoices**: " + totalFactures + " total (" + facturesAttente + " pending)\n" +
                       "📦 **Products**: " + totalProduits + " (" + produitsAlerte + " stock alerts)\n" +
                       "💰 **Total Revenue**: **" + totalVentes + " TND**\n" +
                       "👤 **Clients**: " + totalClients;
            }
            if (msgLower.contains("stock") || msgLower.contains("inventory") || msgLower.contains("product") || msgLower.contains("alert")) {
                return "📦 **Inventory Status**:\n\n" +
                       "• Total products: " + totalProduits + "\n" +
                       "• 🔴 **Stock alerts**: **" + produitsAlerte + " products** below minimum threshold\n\n" +
                       (produitsAlerte > 0 ? "⚠️ " + produitsAlerte + " products need restocking." : "✅ All products have sufficient stock.") + "\n" +
                       "Go to: [Inventory Management](/products)";
            }
            if (msgLower.contains("invoice") || msgLower.contains("sale") || msgLower.contains("revenue") || msgLower.contains("ca") || msgLower.contains("amount")) {
                return "💰 **Sales & Invoices**:\n\n" +
                       "• Total invoices: " + totalFactures + " (" + facturesAttente + " pending)\n" +
                       "• Total revenue collected: **" + totalVentes + " TND**\n" +
                       "• Total orders: " + totalCommandes + " (" + commandesMois + " this month)\n" +
                       "Go to: [Sales & Invoices](/commercial)";
            }
            if (msgLower.contains("user") || msgLower.contains("role") || msgLower.contains("permission")) {
                return "👥 **Users & Roles**:\n• Registered: " + totalUsers + " | Active: " + actifsUsers + "\nGo to: [User Management](/admin/users)";
            }
            if (msgLower.contains("db") || msgLower.contains("database") || msgLower.contains("export") || msgLower.contains("backup")) {
                return "🗄️ **Database Management**:\n• Export SQL, Import, Backup & Restore\n• Security tokens required for destructive operations\nGo to: [Database Management](/superadmin/db-management)";
            }
            return "Hello! I'm the **BENJEDDOU ERP AI Assistant** 🤖.\n\n" +
                   "📊 Quick stats: " + totalUsers + " users | " + totalProduits + " products | " + totalFactures + " invoices\n\n" +
                   "I can help with: dashboard summary, stock alerts, sales & invoices, users, database management.\nWhat do you need?";
        }

        // ════════════════════════════════════════════════════════════════
        // FRENCH (Default)
        // ════════════════════════════════════════════════════════════════

        // Salutations
        if (msgLower.matches("(bonjour|bonsoir|salut|hello|hi|hey|coucou|bonne journée).*")) {
            return "Bonjour ! 😊 Je suis l'**Assistant IA de BENJEDDOU ERP**.\nComment puis-je vous aider aujourd'hui ?";
        }
        // Dashboard / résumé / tableau de bord
        if (msgLower.contains("tableau de bord") || msgLower.contains("dashboard") || msgLower.contains("résumé") || msgLower.contains("resume") || msgLower.contains("synthèse") || msgLower.contains("aperçu") || msgLower.contains("kpi") || msgLower.contains("bord")) {
            return "📊 **Résumé du Tableau de Bord — BENJEDDOU ERP**:\n\n" +
                   "👥 **Utilisateurs**: " + totalUsers + " inscrits (" + actifsUsers + " actifs)\n" +
                   "🛒 **Commandes**: " + totalCommandes + " au total (" + commandesMois + " ce mois)\n" +
                   "🧾 **Factures**: " + totalFactures + " au total (" + facturesAttente + " en attente)\n" +
                   "📦 **Produits**: " + totalProduits + " produits (" + produitsAlerte + " en alerte stock)\n" +
                   "💰 **Chiffre d'affaires encaissé**: **" + totalVentes + " TND**\n" +
                   "👤 **Clients**: " + totalClients + " clients enregistrés";
        }
        // Stock / produits / alertes
        if (msgLower.contains("stock") || msgLower.contains("alerte") || msgLower.contains("produit") || msgLower.contains("rupture") || msgLower.contains("inventaire") || msgLower.contains("entrepôt")) {
            return "📦 **État du Stock**:\n\n" +
                   "• **Total produits**: " + totalProduits + " références\n" +
                   "• 🔴 **Produits en alerte**: **" + produitsAlerte + " produit(s)** sous le seuil minimum\n\n" +
                   (produitsAlerte > 0 ? "⚠️ **" + produitsAlerte + " produit(s)** nécessitent un réapprovisionnement urgent.\n" : "✅ Tous les produits ont un stock suffisant.\n") +
                   "Accéder à : [Gestion du Stock](/products)";
        }
        // Factures / ventes / CA / montant / chiffre
        if (msgLower.contains("facture") || msgLower.contains("vente") || msgLower.contains("chiffre") || msgLower.contains(" ca ") || msgLower.equals("ca") || msgLower.contains("montant") || msgLower.contains("revenu") || msgLower.contains("ocr") || msgLower.contains("devis") || msgLower.contains("commande")) {
            return "💰 **Ventes & Facturation**:\n\n" +
                   "• **Total factures**: " + totalFactures + " (" + facturesAttente + " en attente)\n" +
                   "• **Chiffre d'affaires encaissé**: **" + totalVentes + " TND**\n" +
                   "• **Total commandes**: " + totalCommandes + " (" + commandesMois + " ce mois)\n" +
                   "• **Clients**: " + totalClients + "\n\n" +
                   "Accéder à : [Facturation & Ventes](/commercial)";
        }
        // Combien / nombre
        if (msgLower.contains("combien") || msgLower.contains("nombre") || msgLower.contains("total") || msgLower.contains("compte")) {
            return "📊 **Statistiques Globales**:\n\n" +
                   "• 👥 Utilisateurs: **" + totalUsers + "** (" + actifsUsers + " actifs)\n" +
                   "• 📦 Produits: **" + totalProduits + "** (" + produitsAlerte + " en alerte)\n" +
                   "• 🧾 Factures: **" + totalFactures + "** (" + facturesAttente + " en attente)\n" +
                   "• 🛒 Commandes: **" + totalCommandes + "** (" + commandesMois + " ce mois)\n" +
                   "• 💰 CA encaissé: **" + totalVentes + " TND**\n" +
                   "• 👤 Clients: **" + totalClients + "**";
        }
        // Utilisateurs / rôles
        if (msgLower.contains("utilisateur") || msgLower.contains("role") || msgLower.contains("rôle") || msgLower.contains("droit") || msgLower.contains("permission") || msgLower.contains("compte")) {
            return "👥 **Gestion des Utilisateurs**:\n\n" +
                   "• Inscrits: **" + totalUsers + "** | Actifs: **" + actifsUsers + "**\n" +
                   "Profils: Admin, Commercial, Comptable, Stock, Client.\n" +
                   "Accéder à : [Gestion des Utilisateurs](/admin/users)";
        }
        // Base de données
        if (msgLower.contains("base") || msgLower.contains("bdd") || msgLower.contains("export") || msgLower.contains("import") || msgLower.contains("sauvegarde") || msgLower.contains("restaurer") || msgLower.contains("sql")) {
            return "🗄️ **Gestion des Bases de Données**:\n\n" +
                   "• **Export SQL**: dump complet structure + données\n" +
                   "• **Import**: exécution de scripts `.sql`\n" +
                   "• **Sauvegarde**: archives horodatées\n" +
                   "• **Sécurité**: token UUID requis pour les opérations destructives\n" +
                   "Accéder à : [Gestion des Bases de Données](/superadmin/db-management)";
        }
        // Audit
        if (msgLower.contains("audit") || msgLower.contains("journal") || msgLower.contains("sécurité") || msgLower.contains("log") || msgLower.contains("tracabilit")) {
            return "🛡️ **Journal d'Audit & Sécurité**:\n\nToutes les actions sensibles sont tracées (connexions, modifications, exports, suppressions) avec IP et profil.\n" +
                   "Accéder à : [Journal d'Audit](/superadmin/audit)";
        }
        // Entreprise / SaaS
        if (msgLower.contains("entreprise") || msgLower.contains("tenant") || msgLower.contains("essai") || msgLower.contains("saas")) {
            return "🏢 **Gestion des Entreprises SaaS**:\n\nArchitecture Multi-Tenant isolée, période d'essai 30 jours, base dédiée par entreprise.\n" +
                   "Accéder à : [Entreprises SaaS](/superadmin/entreprises)";
        }
        // Réponse par défaut enrichie avec les données réelles
        return "Bonjour ! Je suis l'**Assistant IA de BENJEDDOU ERP** 🤖.\n\n" +
               "📊 **Stats rapides**: " + totalUsers + " utilisateurs | " + totalProduits + " produits | " + totalFactures + " factures | **" + totalVentes + " TND** encaissés\n\n" +
               "Je peux vous aider avec :\n" +
               "• **Tableau de bord** — tapez : résumé tableau de bord\n" +
               "• **Stock & alertes** — tapez : état du stock\n" +
               "• **Factures & CA** — tapez : chiffre d'affaires\n" +
               "• **Base de données** — tapez : export base de données\n" +
               "Comment puis-je vous aider ?";
    }

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    public Map<String, Object> processLocalOcr(org.springframework.web.multipart.MultipartFile file) {
        Map<String, Object> extractedData = new HashMap<>();
        
        String fournisseurExtrait = null;
        String dateExtrait = null;
        String numFactureExtrait = null;
        Double htExtrait = null;
        Double tvaExtrait = 19.0;
        Double ttcExtrait = null;

        try {
            // 1. Extraction du texte brut via Apache PDFBox pour les PDF ou OCR.space
            String text = "";

            boolean isPdf = (file.getContentType() != null && file.getContentType().toLowerCase().contains("pdf"))
                    || (file.getOriginalFilename() != null && file.getOriginalFilename().toLowerCase().endsWith(".pdf"));

            if (isPdf) {
                try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.Loader.loadPDF(file.getBytes())) {
                    org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
                    stripper.setSortByPosition(true);
                    String pdfText = stripper.getText(document);
                    if (pdfText != null && !pdfText.trim().isEmpty()) {
                        text = pdfText;
                        System.out.println("Extraction PDFBox réussie — " + text.length() + " caractères extraits du PDF");
                    }
                } catch (Exception ex) {
                    System.err.println("Erreur extraction PDFBox : " + ex.getMessage());
                }
            }

            // Si texte toujours vide ou si c'est une image -> Tentative OCR.space
            if (text.isBlank()) {
                try {
                    String base64 = java.util.Base64.getEncoder().encodeToString(file.getBytes());
                    String mimeType = file.getContentType();
                    if (mimeType == null || mimeType.isEmpty()) mimeType = isPdf ? "application/pdf" : "image/png";
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
            }

            if (text == null) text = "";

            System.out.println("--- TEXTE RÉEL EXTRAIT DU DOCUMENT (" + file.getOriginalFilename() + ") ---");
            System.out.println(text);
            System.out.println("------------------------------------------------------------------");

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

            // 4. Reconnaissance des montants financiers (HT, TVA, TTC)
            java.util.regex.Matcher ttcMatcher = java.util.regex.Pattern.compile("(?i)(Total\\s*incl\\.?\\s*tax|Total\\s*TTC|Net\\s*à\\s*payer|Montant\\s*TTC|Total\\s*:).*?([0-9]{1,3}(?:[., ]\\d{3})*(?:[.,]\\d{2,3})?)").matcher(text);
            if (ttcMatcher.find()) {
                ttcExtrait = parseMontantFinancier(ttcMatcher.group(2));
            }

            java.util.regex.Matcher htMatcher = java.util.regex.Pattern.compile("(?i)(Total\\s*excl\\.?\\s*tax|Total\\s*HT|Montant\\s*HT).*?([0-9]{1,3}(?:[., ]\\d{3})*(?:[.,]\\d{2,3})?)").matcher(text);
            if (htMatcher.find()) {
                htExtrait = parseMontantFinancier(htMatcher.group(2));
            }

            // Fallback si TTC non trouvé
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

            // Calcul cohérence HT / TTC
            if (ttcExtrait != null && htExtrait == null) {
                htExtrait = Math.round((ttcExtrait / 1.19) * 100.0) / 100.0;
            } else if (htExtrait != null && ttcExtrait == null) {
                ttcExtrait = Math.round((htExtrait * 1.19) * 100.0) / 100.0;
            }

            // 5. Extraction du Fournisseur / Émetteur
            String[] lines = text.split("\\r?\\n");
            System.out.println("[OCR DEBUG] Texte brut reçu (" + lines.length + " lignes) :\n" + text.substring(0, Math.min(text.length(), 600)));

            // Termes à exclure ABSOLUMENT (ne peuvent jamais être un nom de fournisseur)
            java.util.List<String> hardExclusions = java.util.Arrays.asList(
                "STATUS", "STATUS:", "EN ATTENTE", "PAYÉE", "PAYEE", "PAID", "UNPAID",
                "INVOICE PREVIEW", "INVOICE", "FACTURE", "DEVIS", "PREVIEW",
                "CHECK THE INFORMATION", "BEFORE PRINTING", "SEND BY EMAIL",
                "PRINT / PDF", "PRINT/PDF", "MARK AS PAID", "CLOSE",
                "PAYMENT CONDITIONS", "PAYMENT IS DUE", "BANK DETAILS", "BANK:",
                "AMOUNT IN WORDS", "TOTAL EXCL", "TOTAL INCL", "TOTAL", "TVA", "VAT",
                "TAX ID", "DUE DATE", "ISSUE DATE", "BILLED TO", "FACTURÉ À",
                "BENJEDDOU ERP", "BENJEDDOU", "AVENUE DE", "1002 TUNIS",
                "CENTRE URBAIN", "CONTACT@"
            );

            // Aide : nettoyer une ligne OCR qui contient des colonnes fusionnées
            // ex: "Medina Group                    PAYÉE" → "Medina Group"
            java.util.function.Function<String, String> cleanColumn = raw -> {
                // Diviser sur 2+ espaces consécutifs → prendre la partie GAUCHE uniquement
                String[] parts = raw.split("\\s{2,}");
                return parts[0].trim();
            };

            java.util.function.Predicate<String> isExcluded = candidate -> {
                String up = candidate.toUpperCase();
                if (up.length() < 2) return true;
                for (String ex : hardExclusions) {
                    if (up.contains(ex)) return true;
                }
                // Exclure codes de facture, emails, téléphones, montants
                if (up.matches(".*(FAC|INV|FACT|DEVIS|SIG)-[A-Z0-9\\-]+.*")) return true;
                if (up.matches(".*@.*")) return true;
                if (up.matches(".*\\+?[0-9]{8,}.*")) return true;
                if (up.matches(".*\\d{2}[/.-]\\d{2}[/.-]\\d{4}.*")) return true;
                if (up.matches(".*[0-9]{3,}\\s*(TND|EUR|USD|DT|DH).*")) return true;
                // Exclure les lignes qui se terminent par ":" (labels)
                if (candidate.trim().endsWith(":")) return true;
                return false;
            };

            // Stratégie 1 : repérer "BILLED TO:" et prendre la partie gauche de la ligne suivante
            boolean nextIsClient = false;
            for (String line : lines) {
                String t = line.trim();
                String u = t.toUpperCase();

                if (u.matches(".*\\bBILLED\\s+TO\\b.*") || u.matches(".*\\bFACTURÉ\\s+À\\b.*")) {
                    nextIsClient = true;
                    continue;
                }
                if (nextIsClient && !t.isEmpty()) {
                    String candidate = cleanColumn.apply(t);
                    if (!isExcluded.test(candidate) && candidate.length() >= 2) {
                        fournisseurExtrait = candidate;
                        break;
                    }
                }
            }

            // Stratégie 2 : scanner toutes les lignes et prendre la première valeur propre
            if (fournisseurExtrait == null) {
                for (String line : lines) {
                    String t = line.trim();
                    if (t.isEmpty() || t.length() < 3) continue;
                    String candidate = cleanColumn.apply(t);
                    if (!isExcluded.test(candidate) && candidate.length() >= 3) {
                        fournisseurExtrait = candidate;
                        break;
                    }
                }
            }

            // Post-traitement : si la valeur extraite est encore un terme parasite, l'effacer
            if (fournisseurExtrait != null && isExcluded.test(fournisseurExtrait)) {
                System.out.println("[OCR DEBUG] Fournisseur parasite détecté, ignoré : " + fournisseurExtrait);
                fournisseurExtrait = null;
            }

            System.out.println("[OCR DEBUG] Fournisseur extrait final : " + fournisseurExtrait);

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
