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
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import lombok.extern.slf4j.Slf4j;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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

    @Autowired
    com.benjeddou.erp.repository.EntrepriseRepository entrepriseRepository;

    @Autowired
    com.benjeddou.erp.repository.CommandeRepository commandeRepository;

    @Autowired
    com.benjeddou.erp.repository.DevisRepository devisRepository;

    @Autowired
    com.benjeddou.erp.repository.ProduitRepository produitRepository;


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

        // SYNCHRONISATION TENANT : Insérer aussi dans la base MySQL du tenant
        // Sans cela, l'authentification multi-tenant échoue car le hash BCrypt
        // n'existe que dans la base Master (benjeddou_erp) et pas dans la base Tenant.
        if (entrepriseSchema != null && !entrepriseSchema.isBlank()) {
            // Copies final pour utilisation dans la lambda (exigence Java)
            final String finalSchema         = entrepriseSchema;
            final String finalNomUtilisateur = nomUtilisateur;
            final String finalEmail          = email;
            final String finalPrenom         = prenom != null ? prenom : "";
            final String finalNom            = nom    != null ? nom    : "";
            final String finalRole           = role.name();
            final String finalHash           = user.getMotDePasse();

            entrepriseRepository.findBySchemaName(finalSchema).ifPresent(ent -> {
                final String tUrl  = ent.getDbUrl() != null ? ent.getDbUrl() : "";
                final String tUser = ent.getDbUsername() != null ? ent.getDbUsername() : "";
                final String tPass = ent.getDbPassword() != null ? ent.getDbPassword() : "";
                if (!tUrl.isBlank()) {
                    try {
                        String sqlInsert = """
                            INSERT IGNORE INTO utilisateurs
                                (nom_utilisateur, email, mot_de_passe, prenom, nom,
                                 actif, role, langue_preferee, statut_compte, doit_changer_mot_de_passe)
                            VALUES (?, ?, ?, ?, ?, TRUE, ?, 'fr', 'ACTIF', TRUE)
                            """;
                        try (Connection conn = DriverManager.getConnection(tUrl, tUser, tPass);
                             PreparedStatement ps = conn.prepareStatement(sqlInsert)) {
                            ps.setString(1, finalNomUtilisateur);
                            ps.setString(2, finalEmail);
                            ps.setString(3, finalHash);
                            ps.setString(4, finalPrenom);
                            ps.setString(5, finalNom);
                            ps.setString(6, finalRole);
                            ps.executeUpdate();
                            log.info("✓ Utilisateur '{}' synchronisé dans la base tenant '{}'", finalNomUtilisateur, finalSchema);
                        }
                    } catch (Exception ex) {
                        log.warn("⚠️  Sync tenant ignorée pour '{}' : {}", finalNomUtilisateur, ex.getMessage());
                    }
                }
            });
        }


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

    // ── Statistiques du Dashboard Admin ───────────────────────────────
    // Chaque admin voit les données de SON tenant (base isolée).
    // Le routing est géré automatiquement par TenantFilter avant cet appel.
    @GetMapping("/dashboard/stats")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();

        log.info("[DASHBOARD] Stats demandées, tenant actif: {}",
            com.benjeddou.erp.config.TenantContextHolder.getCurrentTenant());

        // ── KPIs utilisateurs ───────────────────────────────────────────────
        long totalUsers = utilisateurRepository.count();
        long activeUsers = utilisateurRepository.findAll().stream()
            .filter(u -> Boolean.TRUE.equals(u.getActif())).count();

        List<Utilisateur> users = utilisateurRepository.findAll();
        // L'enum Role contient : ADMIN, COMMERCIAL, COMPTABLE, STOCK (sans préfixe ROLE_)
        long countAdmin    = users.stream().filter(u -> Role.ADMIN.equals(u.getRole())).count();
        long countCom      = users.stream().filter(u -> Role.COMMERCIAL.equals(u.getRole())).count();
        long countComp     = users.stream().filter(u -> Role.COMPTABLE.equals(u.getRole())).count();
        long countStock    = users.stream().filter(u -> Role.STOCK.equals(u.getRole())).count();
        long countClient   = users.stream().filter(u -> Role.CLIENT.equals(u.getRole())).count();

        // ── KPIs financiers ───────────────────────────────────────────────
        List<Facture> facturesPayées;
        try { facturesPayées = factureRepository.findByStatut("PAYEE"); }
        catch (Exception e) { facturesPayées = new ArrayList<>(); }

        java.math.BigDecimal totalRevenue = facturesPayées.stream()
            .map(Facture::getMontantTotal)
            .filter(Objects::nonNull)
            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        long totalFactures;
        long facturesAttente;
        try {
            totalFactures   = factureRepository.count();
            facturesAttente = factureRepository.findAll().stream()
                .filter(f -> "EN_ATTENTE".equals(f.getStatut())).count();
        } catch (Exception e) { totalFactures = 0; facturesAttente = 0; }

        // ── KPIs commercial ───────────────────────────────────────────────
        long totalClients;
        long totalCommandes;
        long commandesMois;
        long totalDevis;
        try {
            totalClients  = clientRepository.count();
            java.time.LocalDate debut = java.time.LocalDate.now().withDayOfMonth(1);
            java.time.LocalDateTime debutDt = debut.atStartOfDay();
            totalCommandes = commandeRepository.count();
            commandesMois  = commandeRepository.findAll().stream()
                .filter(c -> c.getDateCommande() != null && !c.getDateCommande().isBefore(debutDt))
                .count();
            totalDevis = devisRepository.count();
        } catch (Exception e) { totalClients = 0; totalCommandes = 0; commandesMois = 0; totalDevis = 0; }

        // ── KPIs stock ──────────────────────────────────────────────────
        long totalProduits;
        long produitsEnAlerte;
        try {
            List<com.benjeddou.erp.model.Produit> produits = produitRepository.findAll();
            totalProduits    = produits.size();
            produitsEnAlerte = produits.stream()
                .filter(p -> p.getQuantiteStock() != null
                          && p.getSeuilStockMin() != null
                          && p.getQuantiteStock() <= p.getSeuilStockMin())
                .count();
        } catch (Exception e) { totalProduits = 0; produitsEnAlerte = 0; }

        // ── Graphique CA des 6 derniers mois ───────────────────────────────
        java.time.LocalDate now = java.time.LocalDate.now();
        List<String> months = new ArrayList<>();
        List<java.math.BigDecimal> revenueData = new ArrayList<>();
        String[] MOIS_FR = {"Jan","Fév","Mar","Avr","Mai","Jun","Jul","Aoû","Sep","Oct","Nov","Déc"};

        for (int i = 5; i >= 0; i--) {
            java.time.LocalDate d = now.minusMonths(i);
            months.add(MOIS_FR[d.getMonthValue() - 1] + " " + d.getYear());

            java.math.BigDecimal monthRev = facturesPayées.stream()
                .filter(f -> f.getDateEmission() != null
                    && f.getDateEmission().getYear() == d.getYear()
                    && f.getDateEmission().getMonth() == d.getMonth())
                .map(Facture::getMontantTotal)
                .filter(Objects::nonNull)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            revenueData.add(monthRev);
        }

        // ── Connexions (sécurité) ───────────────────────────────────────────────
        long totalConnexions;
        long pendingKyc;
        try {
            totalConnexions = connexionLogRepository.count();
            pendingKyc = documentKycRepository.findAll().stream()
                .filter(d -> "EN_ATTENTE".equals(d.getStatutVerification())).count();
        } catch (Exception e) { totalConnexions = 0; pendingKyc = 0; }

        // ── Assemblage de la réponse ───────────────────────────────────────────────
        stats.put("totalUsers",       totalUsers);
        stats.put("activeUsers",      activeUsers);
        stats.put("totalTransactions",totalConnexions);
        stats.put("pendingKyc",       pendingKyc);
        stats.put("totalRevenue",     totalRevenue);
        stats.put("totalFactures",    totalFactures);
        stats.put("facturesAttente",  facturesAttente);
        stats.put("totalClients",     totalClients);
        stats.put("totalCommandes",   totalCommandes);
        stats.put("commandesMois",    commandesMois);
        stats.put("totalDevis",       totalDevis);
        stats.put("totalProduits",    totalProduits);
        stats.put("produitsEnAlerte", produitsEnAlerte);

        Map<String, Object> revenueChart = new HashMap<>();
        revenueChart.put("labels", months);
        revenueChart.put("data",   revenueData);
        stats.put("revenueChart", revenueChart);

        Map<String, Object> rolesChart = new HashMap<>();
        rolesChart.put("labels", Arrays.asList("Admins", "Commerciaux", "Comptables", "Stock", "Clients"));
        rolesChart.put("data",   Arrays.asList(countAdmin, countCom, countComp, countStock, countClient));
        stats.put("rolesChart", rolesChart);

        log.info("[DASHBOARD] tenant={} users={} CA={} TND clients={} commandes={} produits={}",
            com.benjeddou.erp.config.TenantContextHolder.getCurrentTenant(),
            totalUsers, totalRevenue, totalClients, totalCommandes, totalProduits);

        return ResponseEntity.ok(stats);
    }

    // ── RÔLES & PERMISSIONS : PERSISTANCE EN BASE TENANT ─────────────────────
    // Solution définitive : les permissions sont stockées dans la table `roles_config`
    // de la base MySQL du tenant (un enregistrement JSON unique, upsert par id=1).
    // Plus de stockage volatile en mémoire — survit aux redémarrages du serveur.

    @Value("${spring.datasource.url}")
    private String rpMasterDbUrl;

    @Value("${spring.datasource.username}")
    private String rpMasterUsername;

    @Value("${spring.datasource.password:}")
    private String rpMasterPassword;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Résout l'URL JDBC vers la base tenant de l'admin connecté. */
    private Optional<String[]> resoudreTenantJdbc() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserDetailsImpl principal)) return Optional.empty();

        return utilisateurRepository.findById(principal.getId())
            .flatMap(u -> {
                String schema = u.getEntrepriseSchema();
                if (schema == null || schema.isBlank()) return Optional.empty();
                return entrepriseRepository.findBySchemaName(schema)
                    .map(ent -> new String[]{
                        ent.getDbUrl() != null ? ent.getDbUrl() : "",
                        ent.getDbUsername() != null ? ent.getDbUsername() : rpMasterUsername,
                        ent.getDbPassword() != null ? ent.getDbPassword() : (rpMasterPassword != null ? rpMasterPassword : "")
                    });
            });
    }

    /**
     * GET /api/admin/roles-permissions
     * Lit la configuration JSON depuis roles_config en base tenant.
     * Retourne { roles: [...] } si une config existe, ou {} sinon (le frontend utilisera ses défauts).
     */
    @GetMapping("/roles-permissions")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> getRolesPermissions() {
        Optional<String[]> jdbcOpt = resoudreTenantJdbc();
        if (jdbcOpt.isEmpty()) {
            log.warn("[RolesPerms] GET — tenant non résolu, retour vide");
            return ResponseEntity.ok(Map.of());
        }
        String[] jdbc = jdbcOpt.get();
        String sql = "SELECT config_json FROM roles_config WHERE id = 1";
        try (Connection conn = DriverManager.getConnection(jdbc[0], jdbc[1], jdbc[2]);
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                String json = rs.getString("config_json");
                @SuppressWarnings("unchecked")
                List<Object> roles = objectMapper.readValue(json, List.class);
                log.info("[RolesPerms] GET — {} rôles chargés depuis la DB", roles.size());
                return ResponseEntity.ok(Map.of("roles", roles));
            }
        } catch (Exception e) {
            log.warn("[RolesPerms] GET — erreur lecture DB : {}", e.getMessage());
        }
        return ResponseEntity.ok(Map.of()); // pas encore de config → le frontend initialise
    }

    /**
     * PUT /api/admin/roles-permissions
     * Persiste la matrice JSON dans roles_config (UPSERT — INSERT OR UPDATE sur id=1).
     * Les modifications survivent aux redémarrages. Disponibles immédiatement après enregistrement.
     */
    @PutMapping("/roles-permissions")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> saveRolesPermissions(@RequestBody List<Map<String, Object>> rolesPayload,
                                                   HttpServletRequest request) {
        log.info("🛡️ [RolesPerms] PUT — sauvegarde {} rôles en DB", rolesPayload.size());

        Optional<String[]> jdbcOpt = resoudreTenantJdbc();
        if (jdbcOpt.isEmpty()) {
            log.warn("[RolesPerms] PUT — tenant non résolu, sauvegarde ignorée");
            return ResponseEntity.ok(Map.of("message", "Config sauvegardée localement (tenant non résolu)."));
        }

        String[] jdbc = jdbcOpt.get();
        String upsertSql = """
            INSERT INTO roles_config (id, config_json, updated_at)
            VALUES (1, ?, NOW())
            ON DUPLICATE KEY UPDATE config_json = VALUES(config_json), updated_at = NOW()
            """;
        try {
            String json = objectMapper.writeValueAsString(rolesPayload);
            try (Connection conn = DriverManager.getConnection(jdbc[0], jdbc[1], jdbc[2]);
                 PreparedStatement ps = conn.prepareStatement(upsertSql)) {
                ps.setString(1, json);
                ps.executeUpdate();
                log.info("✅ [RolesPerms] {} rôles persistés en DB (JSON {} chars)", rolesPayload.size(), json.length());
            }
        } catch (Exception e) {
            log.error("❌ [RolesPerms] Erreur persistance : {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("message", "Erreur sauvegarde : " + e.getMessage()));
        }

        auditService.log(ActionAudit.ROLE_MODIFIE, ResultatAudit.SUCCES,
            "Permissions rôles mises à jour (" + rolesPayload.size() + " rôles)",
            null, null, request, "ADMIN", null);

        return ResponseEntity.ok(Map.of(
            "message", "Rôles et permissions enregistrés avec succès.",
            "count", rolesPayload.size()
        ));
    }

    /**
     * GET /api/admin/my-permissions
     *
     * Retourne les permissions du rôle de l'utilisateur connecté, lues depuis
     * la table roles_config du tenant. Accessible à TOUS les utilisateurs authentifiés
     * (pas seulement les admins) — le @PreAuthorize ici écrase le niveau classe.
     *
     * Réponse : { role: "COMMERCIAL", modulePermissions: [{module, permissions: {...}}] }
     * Si aucune config n'existe : { role: "...", modulePermissions: [] }
     */
    @GetMapping("/my-permissions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getMyPermissions() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return ResponseEntity.ok(Map.of("role", "", "modulePermissions", List.of()));
        }

        // Extraire le nom de rôle depuis les autorités Spring Security
        String springRole = auth.getAuthorities().stream()
            .map(a -> a.getAuthority())
            .findFirst().orElse("ROLE_USER");
        // Supprimer le préfixe ROLE_ → "COMMERCIAL", "COMPTABLE", etc.
        String roleName = springRole.startsWith("ROLE_") ? springRole.substring(5) : springRole;

        log.debug("[MyPermissions] Utilisateur={} rôle={}", auth.getName(), roleName);

        // Résoudre le tenant JDBC de l'utilisateur connecté
        Optional<String[]> jdbcOpt = resoudreTenantJdbc();
        if (jdbcOpt.isEmpty()) {
            log.warn("[MyPermissions] Tenant non résolu pour rôle={}, mode permissif", roleName);
            return ResponseEntity.ok(Map.of("role", roleName, "modulePermissions", List.of()));
        }

        String[] jdbc = jdbcOpt.get();
        String sql = "SELECT config_json FROM roles_config WHERE id = 1";
        try (Connection conn = DriverManager.getConnection(jdbc[0], jdbc[1], jdbc[2]);
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                String json = rs.getString("config_json");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> roles = objectMapper.readValue(json, List.class);

                // Trouver le rôle correspondant (insensible à la casse)
                final String roleNameFinal = roleName;
                Optional<Map<String, Object>> matchingRole = roles.stream()
                    .filter(r -> roleNameFinal.equalsIgnoreCase((String) r.get("nom")))
                    .findFirst();

                if (matchingRole.isPresent()) {
                    Object modulePermissions = matchingRole.get().get("modulePermissions");
                    log.info("[MyPermissions] Permissions trouvées pour rôle={}", roleName);
                    return ResponseEntity.ok(Map.of(
                        "role", roleName,
                        "modulePermissions", modulePermissions != null ? modulePermissions : List.of()
                    ));
                } else {
                    log.info("[MyPermissions] Rôle={} non trouvé dans la config → mode permissif", roleName);
                }
            }
        } catch (Exception e) {
            log.warn("[MyPermissions] Erreur lecture DB pour rôle={} : {}", roleName, e.getMessage());
        }

        // Aucune config trouvée → mode permissif (pas de restrictions)
        return ResponseEntity.ok(Map.of("role", roleName, "modulePermissions", List.of()));
    }
}

