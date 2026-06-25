package com.benjeddou.erp.controller;

import com.benjeddou.erp.model.*;
import com.benjeddou.erp.payload.response.MessageReponse;
import com.benjeddou.erp.repository.AbonnementRepository;
import com.benjeddou.erp.repository.UtilisateurRepository;
import com.benjeddou.erp.service.EmailService;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Gestion du paiement Stripe pour les abonnements.
 *  - POST /api/stripe/create-checkout : crée une session Stripe Checkout
 *  - POST /api/stripe/webhook         : reçoit les événements Stripe (paiement confirmé)
 */
@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/stripe")
public class StripeController {

    // Valeurs par défaut vides pour éviter le crash au démarrage si les clés ne sont pas configurées
    @Value("${stripe.secret.key:}")
    private String stripeSecretKey;

    @Value("${stripe.publishable.key:}")
    private String stripePublishableKey;

    @Value("${stripe.webhook.secret:}")
    private String webhookSecret;

    @Value("${stripe.success.url:http://localhost:4200/paiement/succes}")
    private String successUrl;

    @Value("${stripe.cancel.url:http://localhost:4200/abonnement}")
    private String cancelUrl;

    @PostConstruct
    public void init() {
        if (stripeSecretKey == null || stripeSecretKey.isBlank() || stripeSecretKey.equals("sk_test_REMPLACE_MOI")) {
            System.err.println("\n");
            System.err.println("==================================================================");
            System.err.println("  ⚠️  STRIPE : Clés API non configurées !");
            System.err.println("  Ajoutez vos clés dans application.properties :");
            System.err.println("  stripe.secret.key=sk_test_...");
            System.err.println("  stripe.publishable.key=pk_test_...");
            System.err.println("  Générez-les sur : https://dashboard.stripe.com/test/apikeys");
            System.err.println("==================================================================");
            System.err.println();
        } else {
            Stripe.apiKey = stripeSecretKey;
            System.out.println("[Stripe] ✅ Clé API configurée : " + stripeSecretKey.substring(0, 12) + "...");
        }
    }

    @Autowired
    AbonnementRepository abonnementRepository;

    @Autowired
    UtilisateurRepository utilisateurRepository;

    @Autowired
    EmailService emailService;

    // ══════════════════════════════════════════════════════════════
    //  CRÉER UNE SESSION STRIPE CHECKOUT
    // ══════════════════════════════════════════════════════════════

    @PostMapping("/create-checkout")
    public ResponseEntity<?> createCheckoutSession(@RequestBody Map<String, Object> body) {
        // Vérifier que les clés Stripe sont configurées
        if (stripeSecretKey == null || stripeSecretKey.isBlank() || stripeSecretKey.equals("sk_test_REMPLACE_MOI")) {
            return ResponseEntity.badRequest().body(new MessageReponse(
                "Stripe non configuré. Ajoutez stripe.secret.key dans application.properties."));
        }

        try {
            Stripe.apiKey = stripeSecretKey;

            Long clientId   = Long.valueOf(body.get("clientId").toString());
            String typePlan = body.get("typePlan").toString().toUpperCase();

            Optional<Utilisateur> userOpt = utilisateurRepository.findById(clientId);
            if (userOpt.isEmpty()) return ResponseEntity.notFound().build();
            Utilisateur client = userOpt.get();

            // Prix et durée selon le plan
            long prixCentimes;
            int  dureeMois;
            String nomPlan;
            switch (typePlan) {
                case "MENSUEL"      -> { prixCentimes = 9900L;   dureeMois = 1;  nomPlan = "Plan Mensuel — BENJEDDOU ERP"; }
                case "TRIMESTRIEL"  -> { prixCentimes = 24900L;  dureeMois = 3;  nomPlan = "Plan Trimestriel — BENJEDDOU ERP"; }
                case "ANNUEL"       -> { prixCentimes = 79900L;  dureeMois = 12; nomPlan = "Plan Annuel — BENJEDDOU ERP"; }
                default             -> { prixCentimes = 9900L;   dureeMois = 1;  nomPlan = "Plan Mensuel — BENJEDDOU ERP"; }
            }

            // Créer l'abonnement en base AVANT la redirection (statut EN_ATTENTE)
            // Vérifier si l'utilisateur a déjà un abonnement en attente
            List<Abonnement> existing = abonnementRepository.findByClientOrderByDateSoumissionDesc(client);
            boolean hasActive = existing.stream().anyMatch(a ->
                a.getStatut() == StatutAbonnement.ACTIF ||
                a.getStatut() == StatutAbonnement.EN_ATTENTE ||
                a.getStatut() == StatutAbonnement.VALIDE
            );
            if (hasActive) {
                return ResponseEntity.badRequest()
                    .body(new MessageReponse("Vous avez déjà un abonnement actif ou en cours."));
            }

            // Enregistrer l'abonnement en attente de paiement Stripe
            TypePlanAbonnement planEnum = TypePlanAbonnement.valueOf(typePlan);
            BigDecimal prix = BigDecimal.valueOf(prixCentimes).divide(BigDecimal.valueOf(100));
            Abonnement abonnement = Abonnement.builder()
                .client(client)
                .typePlan(planEnum)
                .prix(prix)
                .dureeMois(dureeMois)
                .statut(StatutAbonnement.EN_ATTENTE)
                .methodePaiement("STRIPE")
                .build();
            abonnement = abonnementRepository.save(abonnement);

            // Construire la session Stripe Checkout
            SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl + "?session_id={CHECKOUT_SESSION_ID}&abonnement_id=" + abonnement.getId())
                .setCancelUrl(cancelUrl)
                .addLineItem(
                    SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(
                            SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency("eur")           // Stripe Checkout exige une devise — adaptez selon votre pays
                                .setUnitAmount(prixCentimes)  // centimes
                                .setProductData(
                                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(nomPlan)
                                        .setDescription(dureeMois + " mois d'accès complet à BENJEDDOU ERP")
                                        .addImage("https://via.placeholder.com/100x100/f97316/ffffff?text=ERP")
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                // Metadata pour le webhook : identifier l'abonnement et le client
                .putMetadata("abonnement_id", abonnement.getId().toString())
                .putMetadata("client_id",     clientId.toString())
                .putMetadata("type_plan",     typePlan)
                .setCustomerEmail(client.getEmail())
                .build();

            Session session = Session.create(params);

            Map<String, String> response = new HashMap<>();
            response.put("sessionId",   session.getId());
            response.put("checkoutUrl", session.getUrl());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest()
                .body(new MessageReponse("Erreur Stripe : " + e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  WEBHOOK STRIPE — Paiement confirmé
    // ══════════════════════════════════════════════════════════════

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            System.err.println("[Stripe Webhook] Signature invalide : " + e.getMessage());
            return ResponseEntity.badRequest().body("Signature invalide");
        } catch (Exception e) {
            System.err.println("[Stripe Webhook] Erreur : " + e.getMessage());
            return ResponseEntity.badRequest().body("Erreur parsing");
        }

        // Traiter uniquement les paiements complétés
        if ("checkout.session.completed".equals(event.getType())) {
            EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
            if (deserializer.getObject().isPresent()) {
                Session session = (Session) deserializer.getObject().get();
                String abonnementIdStr = session.getMetadata().get("abonnement_id");
                String clientIdStr     = session.getMetadata().get("client_id");
                String typePlan        = session.getMetadata().get("type_plan");

                try {
                    Long abonnementId = Long.valueOf(abonnementIdStr);
                    Long clientId     = Long.valueOf(clientIdStr);

                    activerAbonnement(abonnementId, clientId, session.getId(), typePlan);
                } catch (Exception ex) {
                    System.err.println("[Stripe Webhook] Erreur activation : " + ex.getMessage());
                }
            }
        }

        return ResponseEntity.ok("Webhook reçu");
    }

    // ══════════════════════════════════════════════════════════════
    //  VÉRIFICATION MANUELLE (retour depuis la page succès)
    // ══════════════════════════════════════════════════════════════

    /**
     * Appelé depuis la page de succès Angular pour confirmer un paiement.
     * Utilisé comme fallback au cas où le webhook n'est pas configuré.
     */
    @GetMapping("/verify-session")
    public ResponseEntity<?> verifySession(
            @RequestParam String sessionId,
            @RequestParam Long abonnementId,
            @RequestParam Long clientId) {
        try {
            Stripe.apiKey = stripeSecretKey;
            Session session = Session.retrieve(sessionId);

            if ("complete".equals(session.getPaymentStatus()) || "paid".equals(session.getPaymentStatus())) {
                activerAbonnement(abonnementId, clientId, sessionId,
                    session.getMetadata().getOrDefault("type_plan", "MENSUEL"));
                return ResponseEntity.ok(new MessageReponse("Paiement confirmé. Compte activé !"));
            } else {
                return ResponseEntity.badRequest()
                    .body(new MessageReponse("Paiement non finalisé (statut : " + session.getPaymentStatus() + ")"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(new MessageReponse("Erreur vérification : " + e.getMessage()));
        }
    }

    // ── Retourner la clé publique au frontend ──────────────────
    @GetMapping("/public-key")
    public ResponseEntity<?> getPublicKey() {
        return ResponseEntity.ok(Map.of("publishableKey", stripePublishableKey));
    }

    // ── Méthode interne : activer l'abonnement après paiement ──
    private void activerAbonnement(Long abonnementId, Long clientId, String sessionId, String typePlan) {
        Optional<Abonnement> abOpt = abonnementRepository.findById(abonnementId);
        Optional<Utilisateur> userOpt = utilisateurRepository.findById(clientId);

        if (abOpt.isEmpty() || userOpt.isEmpty()) return;

        Abonnement ab     = abOpt.get();
        Utilisateur client = userOpt.get();

        // Ne pas réactiver si déjà actif
        if (ab.getStatut() == StatutAbonnement.ACTIF) return;

        ab.setStatut(StatutAbonnement.ACTIF);
        ab.setDateDebut(LocalDateTime.now());
        ab.setDateFin(LocalDateTime.now().plusMonths(ab.getDureeMois()));
        ab.setReferencePaiement("stripe_" + sessionId);
        ab.setNotesAdmin("Paiement confirmé par Stripe Checkout");
        abonnementRepository.save(ab);

        // Activer le compte client
        client.setStatutCompte(StatutCompte.ACTIF);
        client.setActif(true);
        client.setModeTrial(false);
        client.setNbUtilisations(0);
        utilisateurRepository.save(client);

        // Email de confirmation
        String dateFin = ab.getDateFin() != null ? ab.getDateFin().toString() : "";
        emailService.envoyerNotificationActivationCompte(
            client.getEmail(),
            client.getPrenom() != null ? client.getPrenom() : client.getNomUtilisateur(),
            typePlan,
            dateFin
        );

        System.out.println("[Stripe] Compte " + client.getEmail() + " activé ✅");
    }
}
