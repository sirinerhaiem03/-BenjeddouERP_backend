package com.benjeddou.erp.controller;

import com.benjeddou.erp.model.*;
import com.benjeddou.erp.payload.response.MessageReponse;
import com.benjeddou.erp.repository.ConnexionLogRepository;
import com.benjeddou.erp.repository.DocumentKycRepository;
import com.benjeddou.erp.repository.UtilisateurRepository;
import com.benjeddou.erp.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    UtilisateurRepository utilisateurRepository;

    @Autowired
    ConnexionLogRepository connexionLogRepository;

    @Autowired
    DocumentKycRepository documentKycRepository;

    @Autowired
    com.benjeddou.erp.repository.FactureRepository factureRepository;

    @Autowired
    EmailService emailService;

    // ── Liste tous les utilisateurs ───────────────────────────────
    @GetMapping("/users")
    public ResponseEntity<?> listerUtilisateurs() {
        List<Utilisateur> users = utilisateurRepository.findAll();
        List<Map<String, Object>> result = users.stream().map(u -> {
            Integer nbMax    = u.getNbUtilisationsMax()  != null ? u.getNbUtilisationsMax()  : 30;
            Integer nbActuel = u.getNbUtilisations()     != null ? u.getNbUtilisations()     : 0;
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id",                    u.getId());
            m.put("nomUtilisateur",        u.getNomUtilisateur());
            m.put("email",                 u.getEmail());
            m.put("prenom",                u.getPrenom()  != null ? u.getPrenom()  : "");
            m.put("nom",                   u.getNom()     != null ? u.getNom()     : "");
            m.put("role",                  u.getRole().name());
            m.put("actif",                 Boolean.TRUE.equals(u.getActif()));
            m.put("statutCompte",          u.getStatutCompte() != null ? u.getStatutCompte().name() : "ACTIF");
            m.put("modeTrial",             Boolean.TRUE.equals(u.getModeTrial()));
            m.put("nbUtilisations",        nbActuel);
            m.put("nbUtilisationsMax",     nbMax);
            m.put("utilisationsRestantes", Math.max(0, nbMax - nbActuel));
            m.put("dateCreation",          u.getDateCreation() != null ? u.getDateCreation().toString() : "");
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // ── Changer le statut du compte ───────────────────────────────
    @PutMapping("/users/{id}/statut")
    public ResponseEntity<?> changerStatut(@PathVariable Long id, @RequestParam String statut) {
        Optional<Utilisateur> userOpt = utilisateurRepository.findById(id);
        if (userOpt.isEmpty()) return ResponseEntity.notFound().build();
        try {
            StatutCompte nouveauStatut = StatutCompte.valueOf(statut.toUpperCase());
            Utilisateur user = userOpt.get();
            user.setStatutCompte(nouveauStatut);
            user.setActif(nouveauStatut == StatutCompte.ACTIF || nouveauStatut == StatutCompte.VALIDE);
            utilisateurRepository.save(user);

            // ── Notifier le client par email si son KYC est validé ──
            if (nouveauStatut == StatutCompte.VALIDE) {
                emailService.envoyerNotificationValidationKyc(
                    user.getEmail(),
                    user.getPrenom() != null ? user.getPrenom() : user.getNomUtilisateur()
                );
            }

            return ResponseEntity.ok(new MessageReponse("Statut du compte mis à jour : " + nouveauStatut.name()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new MessageReponse("Statut invalide : " + statut));
        }
    }

    // ── Activer / Désactiver le mode Trial ───────────────────────
    @PutMapping("/users/{id}/trial")
    public ResponseEntity<?> toggleTrial(
            @PathVariable Long id,
            @RequestParam boolean activer,
            @RequestParam(defaultValue = "30") int nbMax) {
        Optional<Utilisateur> userOpt = utilisateurRepository.findById(id);
        if (userOpt.isEmpty()) return ResponseEntity.notFound().build();
        Utilisateur user = userOpt.get();
        user.setModeTrial(activer);
        user.setNbUtilisationsMax(nbMax);
        if (activer) user.setNbUtilisations(0);
        utilisateurRepository.save(user);
        String msg = activer
            ? "Mode trial activé (" + nbMax + " utilisations max). Compteur remis à 0."
            : "Mode trial désactivé.";
        return ResponseEntity.ok(new MessageReponse(msg));
    }

    // ── Réinitialiser le compteur trial ──────────────────────────
    @PutMapping("/users/{id}/trial/reset")
    public ResponseEntity<?> resetCompteurTrial(@PathVariable Long id) {
        Optional<Utilisateur> userOpt = utilisateurRepository.findById(id);
        if (userOpt.isEmpty()) return ResponseEntity.notFound().build();
        Utilisateur user = userOpt.get();
        user.setNbUtilisations(0);
        utilisateurRepository.save(user);
        return ResponseEntity.ok(new MessageReponse("Compteur trial remis à 0."));
    }

    // ── Historique de connexions ──────────────────────────────────
    @GetMapping("/users/{id}/connexions")
    public ResponseEntity<?> historiqueConnexions(@PathVariable Long id) {
        Optional<Utilisateur> userOpt = utilisateurRepository.findById(id);
        if (userOpt.isEmpty()) return ResponseEntity.notFound().build();
        List<ConnexionLog> logs = connexionLogRepository
            .findTop10ByUtilisateurOrderByDateConnexionDesc(userOpt.get());
        List<Map<String, Object>> result = logs.stream().map(log -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",            log.getId());
            m.put("dateConnexion", log.getDateConnexion() != null ? log.getDateConnexion().toString() : "");
            m.put("adresseIp",     log.getAdresseIp()   != null ? log.getAdresseIp()   : "");
            m.put("userAgent",     log.getUserAgent()   != null ? log.getUserAgent()   : "");
            m.put("succes",        Boolean.TRUE.equals(log.getSucces()));
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // ══════════════════════════════════════════════════════════════
    //  GESTION DES CLIENTS — Cycle de vie KYC
    // ══════════════════════════════════════════════════════════════

    /** Liste tous les comptes CLIENT avec leur statut KYC */
    @GetMapping("/clients")
    public ResponseEntity<?> listerClients() {
        List<Utilisateur> clients = utilisateurRepository.findAll().stream()
            .filter(u -> u.getRole() == Role.CLIENT)
            .collect(Collectors.toList());

        List<Map<String, Object>> result = clients.stream().map(u -> {
            List<DocumentKyc> docs = documentKycRepository.findByUtilisateur(u);
            long docsValides   = docs.stream().filter(d -> "VALIDE".equals(d.getStatutVerification())).count();
            long docsEnAttente = docs.stream().filter(d -> "EN_ATTENTE".equals(d.getStatutVerification())).count();

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",            u.getId());
            m.put("nomUtilisateur",u.getNomUtilisateur());
            m.put("email",         u.getEmail());
            m.put("prenom",        u.getPrenom()    != null ? u.getPrenom()    : "");
            m.put("nom",           u.getNom()       != null ? u.getNom()       : "");
            m.put("telephone",     u.getTelephone() != null ? u.getTelephone() : "");
            m.put("societe",       u.getSociete()   != null ? u.getSociete()   : "");
            m.put("statutCompte",  u.getStatutCompte() != null ? u.getStatutCompte().name() : "EN_ATTENTE");
            m.put("kycSoumis",     Boolean.TRUE.equals(u.getKycSoumis()));
            m.put("nbDocs",        docs.size());
            m.put("docsValides",   docsValides);
            m.put("docsEnAttente", docsEnAttente);
            m.put("dateCreation",  u.getDateCreation() != null ? u.getDateCreation().toString() : "");
            m.put("modeTrial",     Boolean.TRUE.equals(u.getModeTrial()));
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /** Valider ou refuser un client après vérification KYC */
    @PutMapping("/clients/{id}/valider")
    public ResponseEntity<?> validerClient(
            @PathVariable Long id,
            @RequestParam String decision,
            @RequestParam(defaultValue = "false") boolean activerTrial,
            @RequestParam(defaultValue = "30") int nbMaxTrial) {

        Optional<Utilisateur> userOpt = utilisateurRepository.findById(id);
        if (userOpt.isEmpty()) return ResponseEntity.notFound().build();

        Utilisateur client = userOpt.get();
        if (client.getRole() != Role.CLIENT) {
            return ResponseEntity.badRequest()
                .body(new MessageReponse("Cet utilisateur n'est pas un client."));
        }

        switch (decision.toUpperCase()) {
            case "VALIDE":
                client.setStatutCompte(StatutCompte.VALIDE);
                client.setActif(true);
                if (activerTrial) {
                    client.setModeTrial(true);
                    client.setNbUtilisationsMax(nbMaxTrial);
                    client.setNbUtilisations(0);
                }
                documentKycRepository.findByUtilisateur(client).forEach(doc -> {
                    if ("EN_ATTENTE".equals(doc.getStatutVerification())) {
                        doc.setStatutVerification("VALIDE");
                        documentKycRepository.save(doc);
                    }
                });
                utilisateurRepository.save(client);
                return ResponseEntity.ok(new MessageReponse(
                    "Client validé." + (activerTrial ? " Mode trial activé." : "")));

            case "REFUSE":
                client.setStatutCompte(StatutCompte.REFUSE);
                client.setActif(false);
                documentKycRepository.findByUtilisateur(client).forEach(doc -> {
                    if ("EN_ATTENTE".equals(doc.getStatutVerification())) {
                        doc.setStatutVerification("REFUSE");
                        documentKycRepository.save(doc);
                    }
                });
                utilisateurRepository.save(client);
                return ResponseEntity.ok(new MessageReponse("Client refusé."));

            case "ACTIF":
                client.setStatutCompte(StatutCompte.ACTIF);
                client.setActif(true);
                client.setModeTrial(false);
                utilisateurRepository.save(client);
                return ResponseEntity.ok(new MessageReponse("Compte client activé (abonnement payant)."));

            default:
                return ResponseEntity.badRequest()
                    .body(new MessageReponse("Décision invalide. Valeurs : VALIDE, REFUSE, ACTIF"));
        }
    }

    /** Documents KYC d'un client */
    @GetMapping("/clients/{id}/kyc")
    public ResponseEntity<?> voirKycClient(@PathVariable Long id) {
        Optional<Utilisateur> userOpt = utilisateurRepository.findById(id);
        if (userOpt.isEmpty()) return ResponseEntity.notFound().build();
        List<DocumentKyc> docs = documentKycRepository
            .findByUtilisateurOrderByDateSoumissionDesc(userOpt.get());
        List<Map<String, Object>> result = new ArrayList<>();
        for (DocumentKyc doc : docs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",                  doc.getId());
            m.put("typeDocument",        doc.getTypeDocument());
            m.put("nomFichier",          doc.getNomFichier());
            m.put("contentType",         doc.getContentType() != null ? doc.getContentType() : "application/octet-stream");
            m.put("tailleFichier",       doc.getContenuFichier() != null ? doc.getContenuFichier().length : 0);
            m.put("statutVerification",  doc.getStatutVerification());
            m.put("dateSoumission",      doc.getDateSoumission() != null ? doc.getDateSoumission().toString() : "");
            m.put("viewUrl",             "/api/client/kyc/document/" + doc.getId());
            result.add(m);
        }
        return ResponseEntity.ok(result);
    }

    /** Valider ou refuser un document KYC individuel */
    @PutMapping("/documents/{docId}/valider")
    public ResponseEntity<?> validerDocument(
            @PathVariable Long docId,
            @RequestParam String decision) {

        Optional<DocumentKyc> docOpt = documentKycRepository.findById(docId);
        if (docOpt.isEmpty()) return ResponseEntity.notFound().build();

        String statut = decision.toUpperCase();
        if (!statut.equals("VALIDE") && !statut.equals("REFUSE") && !statut.equals("EN_ATTENTE")) {
            return ResponseEntity.badRequest()
                .body(new MessageReponse("Décision invalide. Valeurs : VALIDE, REFUSE, EN_ATTENTE"));
        }

        DocumentKyc doc = docOpt.get();
        doc.setStatutVerification(statut);
        documentKycRepository.save(doc);

        // Recalculer les compteurs pour mettre à jour la liste client
        Utilisateur client = doc.getUtilisateur();
        List<DocumentKyc> allDocs = documentKycRepository.findByUtilisateur(client);
        long valides   = allDocs.stream().filter(d -> "VALIDE".equals(d.getStatutVerification())).count();
        long enAttente = allDocs.stream().filter(d -> "EN_ATTENTE".equals(d.getStatutVerification())).count();

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("message",      "Document " + statut.toLowerCase() + " avec succès.");
        resp.put("documentId",   docId);
        resp.put("statut",       statut);
        resp.put("docsValides",  valides);
        resp.put("docsEnAttente",enAttente);
        return ResponseEntity.ok(resp);
    }

    // ── Statistiques du Dashboard Admin ───────────────────────
    @GetMapping("/dashboard/stats")
    public ResponseEntity<?> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        
        long totalUsers = utilisateurRepository.count();
        long activeUsers = utilisateurRepository.findAll().stream()
            .filter(u -> Boolean.TRUE.equals(u.getActif())).count();
            
        long totalTransactions = connexionLogRepository.count();
        
        long pendingKyc = documentKycRepository.findAll().stream()
            .filter(d -> "EN_ATTENTE".equals(d.getStatutVerification())).count();
            
        List<Facture> factures = factureRepository.findByStatut("PAYEE");
        java.math.BigDecimal totalRevenue = java.math.BigDecimal.ZERO;
        for (Facture f : factures) {
            if (f.getMontantTotal() != null) {
                totalRevenue = totalRevenue.add(f.getMontantTotal());
            }
        }
        
        java.time.LocalDate now = java.time.LocalDate.now();
        List<String> months = new ArrayList<>();
        List<java.math.BigDecimal> revenueData = new ArrayList<>();
        
        for (int i = 5; i >= 0; i--) {
            java.time.LocalDate d = now.minusMonths(i);
            String monthName = d.getMonth().toString().substring(0, 3);
            months.add(monthName + " " + d.getYear());
            
            java.math.BigDecimal monthRev = factures.stream()
                .filter(f -> f.getDateEmission().getYear() == d.getYear() && f.getDateEmission().getMonth() == d.getMonth())
                .map(Facture::getMontantTotal)
                .filter(Objects::nonNull)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            revenueData.add(monthRev);
        }
        
        List<Utilisateur> users = utilisateurRepository.findAll();
        long countAdmin = users.stream().filter(u -> u.getRole().name().equals("ROLE_ADMIN")).count();
        long countCom = users.stream().filter(u -> u.getRole().name().equals("ROLE_COMMERCIAL")).count();
        long countComp = users.stream().filter(u -> u.getRole().name().equals("ROLE_COMPTABLE")).count();
        long countStock = users.stream().filter(u -> u.getRole().name().equals("ROLE_STOCK")).count();
        
        stats.put("totalUsers", totalUsers);
        stats.put("activeUsers", activeUsers);
        stats.put("totalTransactions", totalTransactions);
        stats.put("pendingKyc", pendingKyc);
        stats.put("totalRevenue", totalRevenue);
        
        Map<String, Object> revenueChart = new HashMap<>();
        revenueChart.put("labels", months);
        revenueChart.put("data", revenueData);
        stats.put("revenueChart", revenueChart);
        
        Map<String, Object> rolesChart = new HashMap<>();
        rolesChart.put("labels", Arrays.asList("Administrateurs", "Commerciaux", "Comptables", "Stock"));
        rolesChart.put("data", Arrays.asList(countAdmin, countCom, countComp, countStock));
        stats.put("rolesChart", rolesChart);
        
        return ResponseEntity.ok(stats);
    }
}
