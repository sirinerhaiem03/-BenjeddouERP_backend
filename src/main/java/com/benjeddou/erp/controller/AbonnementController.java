package com.benjeddou.erp.controller;

import com.benjeddou.erp.model.*;
import com.benjeddou.erp.payload.response.MessageReponse;
import com.benjeddou.erp.repository.AbonnementRepository;
import com.benjeddou.erp.repository.UtilisateurRepository;
import com.benjeddou.erp.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import org.springframework.transaction.annotation.Transactional;

/**
 * Gestion des souscriptions et abonnements.
 *  - Client : soumettre une demande d'abonnement, voir son abonnement
 *  - Admin  : voir toutes les demandes, valider/refuser, activer le compte
 */
@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/abonnement")
@Transactional(readOnly = true)
public class AbonnementController {

    @Autowired
    AbonnementRepository abonnementRepository;

    @Autowired
    UtilisateurRepository utilisateurRepository;

    @Autowired
    EmailService emailService;

    // ══════════════════════════════════════════════════════════════
    //  PLANS DISPONIBLES (public)
    // ══════════════════════════════════════════════════════════════

    /** Retourne les plans disponibles avec prix et description */
    @GetMapping("/plans")
    public ResponseEntity<?> getPlans() {
        List<Map<String, Object>> plans = new ArrayList<>();

        Map<String, Object> mensuel = new LinkedHashMap<>();
        mensuel.put("type",        "MENSUEL");
        mensuel.put("label",       "Mensuel");
        mensuel.put("dureeMois",   1);
        mensuel.put("prix",        new BigDecimal("99.000"));
        mensuel.put("prixOriginal", null);
        mensuel.put("reduction",   null);
        mensuel.put("description", "Accès complet pendant 1 mois. Idéal pour tester la plateforme.");
        mensuel.put("fonctionnalites", List.of(
            "Tous les modules ERP",
            "Jusqu'à 5 utilisateurs",
            "Support par email",
            "Mises à jour incluses"
        ));
        plans.add(mensuel);

        Map<String, Object> trimestriel = new LinkedHashMap<>();
        trimestriel.put("type",        "TRIMESTRIEL");
        trimestriel.put("label",       "Trimestriel");
        trimestriel.put("dureeMois",   3);
        trimestriel.put("prix",        new BigDecimal("249.000"));
        trimestriel.put("prixOriginal", new BigDecimal("297.000"));
        trimestriel.put("reduction",   "−16%");
        trimestriel.put("description", "Accès complet pendant 3 mois. Économisez 48 DT.");
        trimestriel.put("fonctionnalites", List.of(
            "Tous les modules ERP",
            "Jusqu'à 10 utilisateurs",
            "Support prioritaire",
            "Mises à jour incluses",
            "Rapports avancés"
        ));
        plans.add(trimestriel);

        Map<String, Object> annuel = new LinkedHashMap<>();
        annuel.put("type",        "ANNUEL");
        annuel.put("label",       "Annuel");
        annuel.put("dureeMois",   12);
        annuel.put("prix",        new BigDecimal("799.000"));
        annuel.put("prixOriginal", new BigDecimal("1188.000"));
        annuel.put("reduction",   "−33%");
        annuel.put("description", "Accès complet pendant 12 mois. La meilleure valeur — économisez 389 DT.");
        annuel.put("fonctionnalites", List.of(
            "Tous les modules ERP",
            "Utilisateurs illimités",
            "Support dédié 24/7",
            "Mises à jour incluses",
            "Rapports avancés",
            "Accès API",
            "Formation incluse"
        ));
        plans.add(annuel);

        return ResponseEntity.ok(plans);
    }

    // ══════════════════════════════════════════════════════════════
    //  CÔTÉ CLIENT
    // ══════════════════════════════════════════════════════════════

    /** Client soumet une demande d'abonnement */
    @PostMapping("/souscrire")
    @Transactional
    public ResponseEntity<?> souscrire(@RequestBody Map<String, String> body) {
        Long clientId = Long.valueOf(body.get("clientId"));
        String typePlanStr = body.get("typePlan");
        String methode = body.get("methodePaiement");
        String reference = body.getOrDefault("referencePaiement", "");

        Optional<Utilisateur> userOpt = utilisateurRepository.findById(clientId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Utilisateur client = userOpt.get();

        // Vérifier qu'il n'a pas déjà un abonnement actif ou en attente
        List<Abonnement> existing = abonnementRepository.findByClientOrderByDateSoumissionDesc(client);
        boolean hasActive = existing.stream().anyMatch(a ->
            a.getStatut() == StatutAbonnement.ACTIF ||
            a.getStatut() == StatutAbonnement.EN_ATTENTE ||
            a.getStatut() == StatutAbonnement.VALIDE
        );
        if (hasActive) {
            return ResponseEntity.badRequest()
                .body(new MessageReponse("Vous avez déjà un abonnement actif ou en cours de validation."));
        }

        TypePlanAbonnement typePlan;
        try {
            typePlan = TypePlanAbonnement.valueOf(typePlanStr.toUpperCase());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new MessageReponse("Type de plan invalide."));
        }

        // Prix et durée selon le plan
        BigDecimal prix;
        int dureeMois;
        switch (typePlan) {
            case MENSUEL     -> { prix = new BigDecimal("99.000");  dureeMois = 1;  }
            case TRIMESTRIEL -> { prix = new BigDecimal("249.000"); dureeMois = 3;  }
            case ANNUEL      -> { prix = new BigDecimal("799.000"); dureeMois = 12; }
            default          -> { prix = new BigDecimal("99.000");  dureeMois = 1;  }
        }

        Abonnement abonnement = Abonnement.builder()
            .client(client)
            .typePlan(typePlan)
            .prix(prix)
            .dureeMois(dureeMois)
            .statut(StatutAbonnement.EN_ATTENTE)
            .methodePaiement(methode)
            .referencePaiement(reference)
            .build();

        abonnementRepository.save(abonnement);

        return ResponseEntity.ok(new MessageReponse(
            "Demande d'abonnement soumise avec succès. L'administrateur validera votre paiement."));
    }

    /** Client consulte son abonnement actuel */
    @GetMapping("/mon-abonnement/{clientId}")
    public ResponseEntity<?> monAbonnement(@PathVariable Long clientId) {
        Optional<Utilisateur> userOpt = utilisateurRepository.findById(clientId);
        if (userOpt.isEmpty()) return ResponseEntity.notFound().build();

        List<Abonnement> abonnements = abonnementRepository
            .findByClientOrderByDateSoumissionDesc(userOpt.get());

        if (abonnements.isEmpty()) {
            return ResponseEntity.ok(Map.of("abonnement", (Object) null));
        }

        Abonnement a = abonnements.get(0);
        return ResponseEntity.ok(buildAbonnementMap(a));
    }

    // ══════════════════════════════════════════════════════════════
    //  CÔTÉ ADMIN
    // ══════════════════════════════════════════════════════════════

    /** Admin : liste tous les abonnements */
    @GetMapping("/admin/tous")
    public ResponseEntity<?> tousLesAbonnements() {
        try {
            List<Abonnement> all = abonnementRepository.findAllByOrderByDateSoumissionDesc();
            if (all == null) all = Collections.emptyList();
            List<Map<String, Object>> result = all.stream()
                .filter(Objects::nonNull)
                .map(this::buildAbonnementMap)
                .toList();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok(Collections.emptyList());
        }
    }

    /** Admin : liste les abonnements EN_ATTENTE */
    @GetMapping("/admin/en-attente")
    public ResponseEntity<?> enAttente() {
        try {
            List<Abonnement> list = abonnementRepository
                .findByStatutOrderByDateSoumissionDesc(StatutAbonnement.EN_ATTENTE);
            if (list == null) list = Collections.emptyList();
            return ResponseEntity.ok(list.stream().filter(Objects::nonNull).map(this::buildAbonnementMap).toList());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok(Collections.emptyList());
        }
    }

    /** Admin : valider ou refuser un abonnement et activer le compte */
    @PutMapping("/admin/{id}/decider")
    @Transactional
    public ResponseEntity<?> decider(
            @PathVariable Long id,
            @RequestParam String decision,
            @RequestParam(defaultValue = "") String notes) {

        Optional<Abonnement> abOpt = abonnementRepository.findById(id);
        if (abOpt.isEmpty()) return ResponseEntity.notFound().build();

        Abonnement ab = abOpt.get();

        switch (decision.toUpperCase()) {
            case "VALIDER" -> {
                ab.setStatut(StatutAbonnement.ACTIF);
                ab.setDateDebut(LocalDateTime.now());
                ab.setDateFin(LocalDateTime.now().plusMonths(ab.getDureeMois() > 0 ? ab.getDureeMois() : 1));
                ab.setNotesAdmin(notes);

                // Activer le compte client
                Utilisateur client = ab.getClient();
                if (client != null) {
                    client.setStatutCompte(StatutCompte.ACTIF);
                    client.setActif(true);
                    client.setModeTrial(false);
                    client.setNbUtilisations(0);
                    utilisateurRepository.save(client);
                }
                abonnementRepository.save(ab);

                // ── Notifier le client par email ──
                if (client != null && client.getEmail() != null) {
                    String dateFin = ab.getDateFin() != null ? ab.getDateFin().toString() : "";
                    emailService.envoyerNotificationActivationCompte(
                        client.getEmail(),
                        client.getPrenom() != null ? client.getPrenom() : client.getNomUtilisateur(),
                        ab.getTypePlan() != null ? ab.getTypePlan().name() : "MENSUEL",
                        dateFin
                    );
                }

                return ResponseEntity.ok(new MessageReponse(
                    "Abonnement validé. Compte client activé avec succès."));
            }
            case "REFUSER" -> {
                ab.setStatut(StatutAbonnement.ANNULE);
                ab.setNotesAdmin(notes);
                abonnementRepository.save(ab);
                return ResponseEntity.ok(new MessageReponse("Abonnement refusé."));
            }
            default -> {
                return ResponseEntity.badRequest()
                    .body(new MessageReponse("Décision invalide. Valeurs : VALIDER, REFUSER"));
            }
        }
    }

    /** Admin : abonnements d'un client spécifique */
    @GetMapping("/admin/client/{clientId}")
    public ResponseEntity<?> parClient(@PathVariable Long clientId) {
        Optional<Utilisateur> userOpt = utilisateurRepository.findById(clientId);
        if (userOpt.isEmpty()) return ResponseEntity.notFound().build();
        List<Abonnement> list = abonnementRepository
            .findByClientOrderByDateSoumissionDesc(userOpt.get());
        if (list == null) list = Collections.emptyList();
        return ResponseEntity.ok(list.stream().filter(Objects::nonNull).map(this::buildAbonnementMap).toList());
    }

    // ── Helpers ──────────────────────────────────────────────────
    private Map<String, Object> buildAbonnementMap(Abonnement a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",                a.getId());
        m.put("typePlan",          a.getTypePlan() != null ? a.getTypePlan().name() : "MENSUEL");
        m.put("prix",              a.getPrix() != null ? a.getPrix() : BigDecimal.ZERO);
        m.put("dureeMois",         a.getDureeMois());
        m.put("statut",            a.getStatut() != null ? a.getStatut().name() : "EN_ATTENTE");
        m.put("methodePaiement",   a.getMethodePaiement() != null ? a.getMethodePaiement() : "");
        m.put("referencePaiement", a.getReferencePaiement() != null ? a.getReferencePaiement() : "");
        m.put("dateDebut",         a.getDateDebut() != null ? a.getDateDebut().toString() : null);
        m.put("dateFin",           a.getDateFin()   != null ? a.getDateFin().toString()   : null);
        m.put("dateSoumission",    a.getDateSoumission() != null ? a.getDateSoumission().toString() : "");
        m.put("notesAdmin",        a.getNotesAdmin() != null ? a.getNotesAdmin() : "");
        // Info client
        Utilisateur c = a.getClient();
        if (c != null) {
            m.put("clientId",      c.getId());
            m.put("clientNom",     (c.getPrenom() != null ? c.getPrenom() + " " : "") + (c.getNom() != null ? c.getNom() : (c.getNomUtilisateur() != null ? c.getNomUtilisateur() : "")));
            m.put("clientEmail",   c.getEmail() != null ? c.getEmail() : "");
            m.put("clientSociete", c.getSociete() != null ? c.getSociete() : "");
        } else {
            m.put("clientId",      null);
            m.put("clientNom",     "Client (Non renseigné)");
            m.put("clientEmail",   "");
            m.put("clientSociete", "");
        }
        return m;
    }
}
