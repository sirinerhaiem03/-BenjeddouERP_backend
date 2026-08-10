package com.benjeddou.erp.controller;

import com.benjeddou.erp.model.*;
import com.benjeddou.erp.payload.response.MessageReponse;
import com.benjeddou.erp.repository.ConnexionLogRepository;
import com.benjeddou.erp.repository.DocumentKycRepository;
import com.benjeddou.erp.repository.UtilisateurRepository;
import com.benjeddou.erp.security.services.UserDetailsImpl;
import com.benjeddou.erp.service.AuditService;
import com.benjeddou.erp.model.AuditLog.ActionAudit;
import com.benjeddou.erp.model.AuditLog.ResultatAudit;
import com.benjeddou.erp.service.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import lombok.extern.slf4j.Slf4j;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
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
    com.benjeddou.erp.repository.AbonnementRepository abonnementRepository;

    @Autowired
    com.benjeddou.erp.repository.MouvementStockRepository mouvementStockRepository;

    @Autowired
    com.benjeddou.erp.repository.ClientRepository clientRepository;

    @Autowired
    EmailService emailService;

    @Autowired
    AuditService auditService;

    @Autowired
    org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    // ── Créer un collaborateur interne ──────────────────────────
    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> creerUtilisateur(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String nomUtilisateur = (String) body.get("nomUtilisateur");
        String email          = (String) body.get("email");
        String prenom         = (String) body.get("prenom");
        String nom            = (String) body.get("nom");
        String motDePasse     = (String) body.get("motDePasse");
        String roleStr        = (String) body.getOrDefault("role", "USER");

        if (nomUtilisateur == null || email == null || motDePasse == null) {
            return ResponseEntity.badRequest()
                .body(new MessageReponse("Champs obligatoires manquants : nomUtilisateur, email, motDePasse."));
        }
        if (utilisateurRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.badRequest()
                .body(new MessageReponse("Un compte existe déjà avec cet email."));
        }

        Role role;
        try { role = Role.valueOf(roleStr.toUpperCase()); }
        catch (IllegalArgumentException e) { role = Role.USER; }

        // ╔══════════════════════════════════════════════
        // MULTI-TENANT : Hériter de l'entreprise du créateur
        // Le nouvel utilisateur appartient à la MÊMe entreprise que l'admin
        // ╚══════════════════════════════════════════════
        Long entrepriseId = null;
        String entrepriseSchema = null;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetailsImpl createur) {
            // Récupérer directement depuis l'objet UserDetailsImpl
            Optional<Utilisateur> adminOpt = utilisateurRepository.findById(createur.getId());
            if (adminOpt.isPresent()) {
                entrepriseId = adminOpt.get().getEntrepriseId();
                entrepriseSchema = adminOpt.get().getEntrepriseSchema();
            }
        }

        Utilisateur user = Utilisateur.builder()
            .nomUtilisateur(nomUtilisateur)
            .email(email)
            .prenom(prenom != null ? prenom : "")
            .nom(nom != null ? nom : "")
            .motDePasse(passwordEncoder.encode(motDePasse))
            .role(role)
            .statutCompte(StatutCompte.ACTIF)
            .actif(true)
            .modeTrial(false)
            .doitChangerMotDePasse(true)   // Obligatoire à la 1ère connexion
            .entrepriseId(entrepriseId)     // ← même entreprise que le créateur
            .entrepriseSchema(entrepriseSchema) // ← même base MySQL
            .build();
        utilisateurRepository.save(user);

        // Audit log — création utilisateur
        auditService.log(ActionAudit.UTILISATEUR_CREE, ResultatAudit.SUCCES,
            "Utilisateur créé : " + nomUtilisateur + " | Rôle : " + role.name() + " | Email : " + email
            + (entrepriseSchema != null ? " | Entreprise : " + entrepriseSchema : ""),
            user.getId(), nomUtilisateur, request, "ADMIN", user.getId());

        // ── Si le rôle est CLIENT, créer automatiquement une fiche Client ──
        if (role == Role.CLIENT && !clientRepository.existsByEmail(email)) {
            Client client = Client.builder()
                .nom((nom != null && !nom.isBlank()) ? nom
                     : (nomUtilisateur != null ? nomUtilisateur : "Client"))
                .email(email)
                .telephone(null)
                .adresse(null)
                .build();
            clientRepository.save(client);
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("message", "Compte créé avec succès.");
        resp.put("nomUtilisateur", nomUtilisateur);
        resp.put("email", email);
        resp.put("role", role.name());
        resp.put("entrepriseSchema", entrepriseSchema != null ? entrepriseSchema : "master");
        return ResponseEntity.ok(resp);
    }

    // ── Changer le rôle d'un utilisateur ─────────────────────────
    @PutMapping("/users/{id}/role")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> changerRole(@PathVariable Long id, @RequestParam String role, HttpServletRequest request) {
        Optional<Utilisateur> userOpt = utilisateurRepository.findById(id);
        if (userOpt.isEmpty()) return ResponseEntity.notFound().build();
        Utilisateur user = userOpt.get();
        try {
            Role nouveauRole = Role.valueOf(role.toUpperCase());
            Role ancienRole = user.getRole();
            user.setRole(nouveauRole);
            utilisateurRepository.save(user);

            // ── Si le nouveau rôle est CLIENT, créer une fiche Client si absente ──
            if (nouveauRole == Role.CLIENT && !clientRepository.existsByEmail(user.getEmail())) {
                String nomClient = (user.getNom() != null && !user.getNom().isBlank())
                    ? user.getNom() : user.getNomUtilisateur();
                Client client = Client.builder()
                    .nom(nomClient)
                    .email(user.getEmail())
                    .telephone(user.getTelephone())
                    .adresse(user.getAdresse())
                    .build();
                clientRepository.save(client);
            }

            // Audit log — modification du rôle
            auditService.log(ActionAudit.ROLE_MODIFIE, ResultatAudit.SUCCES,
                "Rôle modifié : " + user.getNomUtilisateur()
                + " | " + ancienRole.name() + " → " + nouveauRole.name(),
                user.getId(), user.getNomUtilisateur(), request, "ADMIN", user.getId());

            return ResponseEntity.ok(new MessageReponse("Rôle mis à jour : " + nouveauRole.name()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new MessageReponse("Rôle invalide : " + role));
        }
    }

    // ── Synchroniser les utilisateurs CLIENT → table clients ──────
    // Lance une synchronisation manuelle : crée les fiches Client manquantes
    // pour tous les Utilisateurs ayant le rôle CLIENT.
    @PostMapping("/sync-clients")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> syncClients() {
        List<Utilisateur> clientUsers = utilisateurRepository.findAll().stream()
            .filter(u -> u.getRole() == Role.CLIENT)
            .collect(Collectors.toList());

        int created = 0;
        for (Utilisateur u : clientUsers) {
            if (!clientRepository.existsByEmail(u.getEmail())) {
                String nomClient = (u.getNom() != null && !u.getNom().isBlank())
                    ? u.getNom() : u.getNomUtilisateur();
                Client client = Client.builder()
                    .nom(nomClient)
                    .email(u.getEmail())
                    .telephone(u.getTelephone())
                    .adresse(u.getAdresse())
                    .build();
                clientRepository.save(client);
                created++;
            }
        }

        return ResponseEntity.ok(Map.of(
            "message", "Synchronisation terminée",
            "utilisateursClientTrouvés", clientUsers.size(),
            "fichesCréées", created
        ));
    }

    // ── Supprimer un utilisateur ──────────────────────────────────
    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> supprimerUtilisateur(@PathVariable Long id, HttpServletRequest request) {
        Optional<Utilisateur> userOpt = utilisateurRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Utilisateur user = userOpt.get();
        if (user.getRole() == Role.ADMIN) {
            return ResponseEntity.badRequest()
                .body(new MessageReponse("Impossible de supprimer un compte Administrateur."));
        }
        try {
            // 1. Supprimer tous les logs de connexion
            List<ConnexionLog> logs = connexionLogRepository.findByUtilisateurOrderByDateConnexionDesc(user);
            if (!logs.isEmpty()) connexionLogRepository.deleteAll(logs);

            // 2. Supprimer les documents KYC
            List<DocumentKyc> docs = documentKycRepository.findByUtilisateur(user);
            if (!docs.isEmpty()) documentKycRepository.deleteAll(docs);

            // 3. Supprimer les abonnements
            List<Abonnement> abonnements = abonnementRepository.findByClientOrderByDateSoumissionDesc(user);
            if (!abonnements.isEmpty()) abonnementRepository.deleteAll(abonnements);

            // 4. Délier les mouvements de stock (on conserve les données, on retire la référence)
            List<MouvementStock> mouvements = mouvementStockRepository.findByUtilisateur(user);
            for (MouvementStock m : mouvements) {
                m.setUtilisateur(null);
            }
            if (!mouvements.isEmpty()) mouvementStockRepository.saveAll(mouvements);

            // 5. Audit log AVANT suppression (on garde les infos)
            String nomSupprime   = user.getNomUtilisateur();
            Long   idSupprime    = user.getId();
            String roleSupprime  = user.getRole().name();

            // 6. Supprimer l'utilisateur
            utilisateurRepository.delete(user);

            auditService.log(ActionAudit.UTILISATEUR_SUPPRIME, ResultatAudit.SUCCES,
                "Utilisateur supprimé : " + nomSupprime + " | Rôle : " + roleSupprime,
                null, nomSupprime, request, "ADMIN", idSupprime);

            return ResponseEntity.ok(new MessageReponse(
                "Compte \"" + nomSupprime + "\" supprimé avec succès."));

        } catch (Exception e) {
            return ResponseEntity.status(500)
                .body(new MessageReponse("Erreur lors de la suppression : " + e.getMessage()));
        }
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
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
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> changerStatut(@PathVariable Long id, @RequestParam String statut, HttpServletRequest request) {
        Optional<Utilisateur> userOpt = utilisateurRepository.findById(id);
        if (userOpt.isEmpty()) return ResponseEntity.notFound().build();
        Utilisateur user = userOpt.get();
        try {
            StatutCompte nouveauStatut = StatutCompte.valueOf(statut.toUpperCase());
            StatutCompte ancienStatut = user.getStatutCompte();
            user.setStatutCompte(nouveauStatut);
            user.setActif(nouveauStatut == StatutCompte.ACTIF || nouveauStatut == StatutCompte.VALIDE);
            utilisateurRepository.save(user);

            // Audit log — changement de statut
            auditService.log(ActionAudit.STATUT_MODIFIE, ResultatAudit.SUCCES,
                "Statut modifié : " + user.getNomUtilisateur()
                + " | " + (ancienStatut != null ? ancienStatut.name() : "?") + " → " + nouveauStatut.name(),
                user.getId(), user.getNomUtilisateur(), request, "ADMIN", user.getId());

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
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> toggleTrial(
            @PathVariable Long id,
            @RequestParam boolean activer,
            @RequestParam(defaultValue = "30") int nbMax,
            HttpServletRequest request) {
        Optional<Utilisateur> userOpt = utilisateurRepository.findById(id);
        if (userOpt.isEmpty()) return ResponseEntity.notFound().build();
        Utilisateur user = userOpt.get();
        user.setModeTrial(activer);
        user.setNbUtilisationsMax(nbMax);
        if (activer) user.setNbUtilisations(0);
        utilisateurRepository.save(user);

        // Audit log — toggle trial
        auditService.log(ActionAudit.TRIAL_RESET, ResultatAudit.SUCCES,
            (activer ? "Trial activé" : "Trial désactivé") + " : " + user.getNomUtilisateur()
            + (activer ? " | Max: " + nbMax + " utilisations" : ""),
            user.getId(), user.getNomUtilisateur(), request, "ADMIN", user.getId());

        String msg = activer
            ? "Mode trial activé (" + nbMax + " utilisations max). Compteur remis à 0."
            : "Mode trial désactivé.";
        return ResponseEntity.ok(new MessageReponse(msg));
    }

    // ── Réinitialiser le compteur trial ──────────────────────────
    @PutMapping("/users/{id}/trial/reset")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> resetCompteurTrial(@PathVariable Long id, HttpServletRequest request) {
        Optional<Utilisateur> userOpt = utilisateurRepository.findById(id);
        if (userOpt.isEmpty()) return ResponseEntity.notFound().build();
        Utilisateur user = userOpt.get();
        user.setNbUtilisations(0);
        utilisateurRepository.save(user);

        // Audit log — reset compteur trial
        auditService.log(ActionAudit.TRIAL_RESET, ResultatAudit.SUCCES,
            "Compteur trial remis à 0 pour : " + user.getNomUtilisateur(),
            user.getId(), user.getNomUtilisateur(), request, "ADMIN", user.getId());

        return ResponseEntity.ok(new MessageReponse("Compteur trial remis à 0."));
    }

    // ── Historique de connexions ──────────────────────────────────
    @GetMapping("/users/{id}/connexions")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
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
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
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
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
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
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
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
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
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
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
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

    // ── RÔLES & PERMISSIONS : ENREGISTREMENT ET LECTURE ──────────────────────
    private static final Map<String, Object> rolesPermissionsStore = new HashMap<>();

    @GetMapping("/roles-permissions")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> getRolesPermissions() {
        return ResponseEntity.ok(rolesPermissionsStore);
    }

    @PutMapping("/roles-permissions")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> saveRolesPermissions(@RequestBody List<Map<String, Object>> rolesPayload, HttpServletRequest request) {
        log.info("🛡️ Sauvegarde des Rôles & Permissions ({} rôles)", rolesPayload.size());
        rolesPermissionsStore.put("roles", rolesPayload);

        auditService.log(ActionAudit.ROLE_MODIFIE, ResultatAudit.SUCCES,
            "Permissions rôles mises à jour (" + rolesPayload.size() + " rôles)",
            null, null, request, "ADMIN", null);

        return ResponseEntity.ok(Map.of(
            "message", "Rôles et permissions enregistrés avec succès.",
            "count", rolesPayload.size()
        ));
    }
}
