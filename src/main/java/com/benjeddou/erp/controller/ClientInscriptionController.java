package com.benjeddou.erp.controller;

import com.benjeddou.erp.model.*;
import com.benjeddou.erp.payload.response.MessageReponse;
import com.benjeddou.erp.repository.DocumentKycRepository;
import com.benjeddou.erp.repository.EntrepriseRepository;
import com.benjeddou.erp.repository.UtilisateurRepository;
import com.benjeddou.erp.service.EntrepriseService;
import com.benjeddou.erp.service.OtpService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.benjeddou.erp.config.MasterTenantContext;

import java.io.IOException;
import java.util.*;

@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/client")
public class ClientInscriptionController {

    @Autowired UtilisateurRepository utilisateurRepository;
    @Autowired DocumentKycRepository documentKycRepository;
    @Autowired EntrepriseRepository entrepriseRepository;
    @Autowired PasswordEncoder encoder;
    @Autowired OtpService otpService;
    @Autowired EntrepriseService entrepriseService;

    // ══════════════════════════════════════════════════════════════
    //  VÉRIFICATION DISPONIBILITÉ (username / email)
    // ══════════════════════════════════════════════════════════════
    @GetMapping("/check-username")
    public ResponseEntity<?> checkUsername(@RequestParam String username) {
        // ⚠ Forcé sur MASTER : les comptes CLIENT sont dans la base master
        return MasterTenantContext.run(() -> {
            Optional<Utilisateur> found = utilisateurRepository.findByNomUtilisateur(username);
            boolean available = found.isEmpty()
                || !Boolean.TRUE.equals(found.get().getActif())
                || found.get().getStatutCompte() == StatutCompte.EN_ATTENTE;
            return ResponseEntity.ok(Map.of("available", available));
        });
    }

    @GetMapping("/check-email")
    public ResponseEntity<?> checkEmail(@RequestParam String email) {
        // ⚠ Forcé sur MASTER : les comptes CLIENT sont dans la base master
        return MasterTenantContext.run(() -> {
            Optional<Utilisateur> found = utilisateurRepository.findByEmail(email);
            boolean available = found.isEmpty()
                || !Boolean.TRUE.equals(found.get().getActif())
                || found.get().getStatutCompte() == StatutCompte.EN_ATTENTE;
            return ResponseEntity.ok(Map.of("available", available));
        });
    }

    // ══════════════════════════════════════════════════════════════
    //  ÉTAPE 0a : Envoyer l'OTP
    // ══════════════════════════════════════════════════════════════
    @PostMapping("/otp/envoyer")
    public ResponseEntity<?> envoyerOtp(@RequestBody Map<String, String> payload) {
        String email  = payload.get("email");
        String prenom = payload.getOrDefault("prenom", "Client");

        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest()
                .body(new MessageReponse("Email requis."));
        }
        // ⚠ Vérification dans la base MASTER
        Boolean existeDeja = MasterTenantContext.run(() -> utilisateurRepository.existsByEmail(email));
        if (Boolean.TRUE.equals(existeDeja)) {
            return ResponseEntity.badRequest()
                .body(new MessageReponse("Cet email est déjà associé à un compte."));
        }

        try {
            otpService.genererEtEnvoyer(email, prenom);
            Map<String, Object> resp = new HashMap<>();
            resp.put("message", "Code OTP envoyé à " + email);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new MessageReponse("Erreur envoi email : " + e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  ÉTAPE 0b : Vérifier l'OTP
    // ══════════════════════════════════════════════════════════════
    @PostMapping("/otp/verifier")
    public ResponseEntity<?> verifierOtp(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        String code  = payload.get("code");

        if (email == null || code == null) {
            return ResponseEntity.badRequest()
                .body(new MessageReponse("Email et code requis."));
        }

        if (otpService.verifier(email, code)) {
            return ResponseEntity.ok(new MessageReponse("OTP valide. Email vérifié."));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new MessageReponse("Code OTP incorrect ou expiré."));
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  ÉTAPE 1 : Inscription du client
    // ══════════════════════════════════════════════════════════════
    @PostMapping("/register")
    public ResponseEntity<?> inscrireClient(@RequestBody Map<String, Object> payload) {
        String nomUtilisateur = (String) payload.get("nomUtilisateur");
        String email          = (String) payload.get("email");
        String motDePasse     = (String) payload.get("motDePasse");
        String prenom         = (String) payload.get("prenom");
        String nom            = (String) payload.get("nom");
        String telephone      = (String) payload.get("telephone");
        String societe        = (String) payload.get("societe");
        String adresse        = (String) payload.get("adresse");

        // Lire modeTrial (peut être Boolean ou String)
        boolean modeTrial = false;
        Object modeTrialVal = payload.get("modeTrial");
        if (modeTrialVal instanceof Boolean) {
            modeTrial = (Boolean) modeTrialVal;
        } else if (modeTrialVal instanceof String) {
            modeTrial = Boolean.parseBoolean((String) modeTrialVal);
        }

        if (nomUtilisateur == null || email == null || motDePasse == null) {
            return ResponseEntity.badRequest()
                .body(new MessageReponse("Champs obligatoires manquants."));
        }

        // Variables finales pour lambda
        final boolean finalModeTrial     = modeTrial;
        final String  finalNomUtilisateur = nomUtilisateur;
        final String  finalEmail          = email;
        final String  finalMotDePasse     = motDePasse;
        final String  finalPrenom         = prenom;
        final String  finalNom            = nom;
        final String  finalTelephone      = telephone;
        final String  finalSociete        = societe;
        final String  finalAdresse        = adresse;

        // ╔═══════════════════════════════════════════════════════════════
        // ⚠ TOUT le flux JPA s'exécute sur la base MASTER.
        //   Raison : TenantFilter route les requêtes sans header tenant
        //   vers erp_ent_00000 par défaut, mais les tables utilisateurs,
        //   entreprises et documents_kyc n'existent QUE dans la base master.
        //
        //   La création de la BASE DÉDIÉE (erp_ent_XXXXX) et l'insertion
        //   de l'utilisateur admin dedans utilisent DriverManager (JDBC
        //   direct) et ne sont PAS affectées par ce contexte JPA.
        // ╚═══════════════════════════════════════════════════════════════
        return MasterTenantContext.run(() -> {

            // ── Supprimer les comptes non activés existants (tentatives précédentes) ──
            Optional<Utilisateur> existingByUsername = utilisateurRepository.findByNomUtilisateur(finalNomUtilisateur);
            if (existingByUsername.isPresent()) {
                Utilisateur existing = existingByUsername.get();
                boolean notActivated = !Boolean.TRUE.equals(existing.getActif())
                    || existing.getStatutCompte() == StatutCompte.EN_ATTENTE;
                if (notActivated) {
                    documentKycRepository.deleteAll(
                        documentKycRepository.findByUtilisateur(existing)
                    );
                    utilisateurRepository.delete(existing);
                } else {
                    return ResponseEntity.badRequest()
                        .body(new MessageReponse("Ce nom d'utilisateur est déjà pris."));
                }
            }

            Optional<Utilisateur> existingByEmail = utilisateurRepository.findByEmail(finalEmail);
            if (existingByEmail.isPresent()) {
                Utilisateur existing = existingByEmail.get();
                boolean notActivated = !Boolean.TRUE.equals(existing.getActif())
                    || existing.getStatutCompte() == StatutCompte.EN_ATTENTE;
                if (notActivated) {
                    documentKycRepository.deleteAll(
                        documentKycRepository.findByUtilisateur(existing)
                    );
                    utilisateurRepository.delete(existing);
                } else {
                    return ResponseEntity.badRequest()
                        .body(new MessageReponse("Cet email est déjà utilisé par un compte actif."));
                }
            }

            // Statut selon le mode choisi
            StatutCompte statut = finalModeTrial ? StatutCompte.ACTIF : StatutCompte.EN_ATTENTE;

            // ╔═══════════════════════════════════════════════════════════════
            // MULTI-TENANT : Créer la base dédiée erp_ent_XXXXX
            // creerEntreprise() utilise en interne :
            //   - entrepriseRepository (JPA) → déjà dans MasterTenantContext ✓
            //   - DriverManager (JDBC direct) pour CREATE DATABASE/USER/GRANT
            //     → non affecté par le contexte JPA tenant ✓
            // ╚═══════════════════════════════════════════════════════════════
            com.benjeddou.erp.model.Entreprise entreprise;
            try {
                entreprise = entrepriseService.creerEntreprise(
                    finalSociete != null && !finalSociete.isBlank() ? finalSociete : finalNomUtilisateur,
                    finalEmail,
                    null
                );
            } catch (Exception ex) {
                log.error("Erreur création base tenant pour '{}' : {}", finalNomUtilisateur, ex.getMessage(), ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageReponse("Erreur création base entreprise : " + ex.getMessage()));
            }

            // ╔═══════════════════════════════════════════════════════════════
            // Créer le client dans la base MASTER (pour KYC et portail client)
            // ╚═══════════════════════════════════════════════════════════════
            Utilisateur client = Utilisateur.builder()
                .nomUtilisateur(finalNomUtilisateur)
                .email(finalEmail)
                .motDePasse(encoder.encode(finalMotDePasse))
                .prenom(finalPrenom)
                .nom(finalNom)
                .telephone(finalTelephone)
                .societe(finalSociete)
                .adresse(finalAdresse)
                .role(Role.CLIENT)
                .statutCompte(statut)
                .modeTrial(finalModeTrial)
                .nbUtilisations(0)
                .nbUtilisationsMax(finalModeTrial ? 30 : 0)
                .actif(finalModeTrial)
                .kycSoumis(false)
                .languePreferee("fr")
                .entrepriseId(entreprise.getId())
                .entrepriseSchema(entreprise.getSchemaName())
                .build();

            // 1. Sauvegarde dans la base MASTER (pour visibilité admin KYC)
            utilisateurRepository.save(client);

            // 2. Synchronisation dans le tenant avec le rôle ADMIN
            //    → DriverManager (JDBC direct), non affecté par MasterTenantContext
            client.setRole(Role.ADMIN);
            Long adminUserId = entrepriseService.synchroniserUtilisateurDansTenant(entreprise.getSchemaName(), client);
            client.setRole(Role.CLIENT); // Restaurer rôle CLIENT en master

            if (adminUserId != null) {
                entreprise.setAdminId(adminUserId);
                entrepriseRepository.save(entreprise);
            }

            // Mettre à jour l'utilisateur en master avec les infos finales
            utilisateurRepository.save(client);

            log.info("✓ Compte '{}' créé dans MASTER et TENANT '{}' (adminId={})",
                finalNomUtilisateur, entreprise.getSchemaName(), adminUserId);

            String message = finalModeTrial
                ? "Inscription réussie ! Vous disposez de 30 connexions d'essai gratuites. Votre espace entreprise a été créé automatiquement."
                : "Inscription réussie ! Votre compte est en attente de validation KYC.";

            return ResponseEntity.ok(new MessageReponse(message));
        });
    }

    /**
     * Étape 2 : Upload d'un document KYC — stocké en BASE DE DONNÉES (BLOB)
     */
    @PostMapping("/kyc/upload")
    public ResponseEntity<?> uploadDocumentKyc(
            @RequestParam("nomUtilisateur") String nomUtilisateur,
            @RequestParam("typeDocument") String typeDocument,
            @RequestParam("fichier") MultipartFile fichier) {

        // Lire le fichier AVANT d'entrer dans le lambda (MultipartFile non sérialisable)
        final byte[] contenuFinal;
        final String contentTypeFinal;
        final String origNameFinal;
        try {
            contenuFinal  = fichier.getBytes();
            String ct     = fichier.getContentType();
            if (ct == null) ct = "application/octet-stream";
            String on = fichier.getOriginalFilename();
            if (on != null) {
                String lower = on.toLowerCase();
                if (lower.endsWith(".pdf"))                       ct = "application/pdf";
                if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) ct = "image/jpeg";
                if (lower.endsWith(".png"))                       ct = "image/png";
            }
            contentTypeFinal = ct;
            origNameFinal    = on;
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new MessageReponse("Erreur lecture fichier : " + e.getMessage()));
        }

        // ⚠ Toutes les opérations JPA sur MASTER
        return MasterTenantContext.run(() -> {
            Optional<Utilisateur> userOpt = utilisateurRepository.findByNomUtilisateur(nomUtilisateur);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new MessageReponse("Utilisateur introuvable."));
            }

            Utilisateur client = userOpt.get();
            if (client.getRole() != Role.CLIENT) {
                return ResponseEntity.badRequest()
                    .body(new MessageReponse("Cette fonctionnalité est réservée aux clients."));
            }

            // Sauvegarder le document dans la base MASTER
            DocumentKyc doc = DocumentKyc.builder()
                .utilisateur(client)
                .typeDocument(typeDocument)
                .nomFichier(origNameFinal != null ? origNameFinal : typeDocument)
                .contentType(contentTypeFinal)
                .contenuFichier(contenuFinal)
                .statutVerification("EN_ATTENTE")
                .build();

            documentKycRepository.save(doc);

            // Marquer le client comme ayant soumis des documents
            client.setKycSoumis(true);
            client.setStatutCompte(StatutCompte.EN_ATTENTE);
            utilisateurRepository.save(client);

            return ResponseEntity.ok(new MessageReponse(
                "Document '" + typeDocument + "' soumis avec succès."));
        });
    }

    /**
     * Récupérer la liste des documents KYC (sans le contenu binaire)
     */
    @GetMapping("/kyc/{userId}")
    public ResponseEntity<?> getDocumentsKyc(@PathVariable Long userId) {
        return MasterTenantContext.run(() -> {
            Optional<Utilisateur> userOpt = utilisateurRepository.findById(userId);
            if (userOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            List<DocumentKyc> docs = documentKycRepository
                .findByUtilisateurOrderByDateSoumissionDesc(userOpt.get());

            List<Map<String, Object>> result = new ArrayList<>();
            for (DocumentKyc doc : docs) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id",               doc.getId());
                m.put("typeDocument",     doc.getTypeDocument());
                m.put("nomFichier",       doc.getNomFichier());
                m.put("contentType",      doc.getContentType());
                m.put("tailleFichier",    doc.getContenuFichier() != null ? doc.getContenuFichier().length : 0);
                m.put("statutVerification", doc.getStatutVerification());
                m.put("dateSoumission",   doc.getDateSoumission() != null ? doc.getDateSoumission().toString() : "");
                m.put("commentaireAdmin", doc.getCommentaireAdmin() != null ? doc.getCommentaireAdmin() : "");
                // URL pour voir/télécharger depuis la base
                m.put("viewUrl", "/api/client/kyc/document/" + doc.getId());
                result.add(m);
            }
            return ResponseEntity.ok(result);
        });
    }

    /**
     * Servir un fichier KYC directement depuis la base de données
     */
    @GetMapping("/kyc/document/{docId}")
    public ResponseEntity<byte[]> voirDocument(@PathVariable Long docId) {
        return MasterTenantContext.run(() -> {
            Optional<DocumentKyc> docOpt = documentKycRepository.findById(docId);
            if (docOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            DocumentKyc doc = docOpt.get();
            byte[] contenu = doc.getContenuFichier();

            if (contenu == null || contenu.length == 0) {
                return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
            }

            String contentType = doc.getContentType() != null
                ? doc.getContentType()
                : "application/octet-stream";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));
            // inline = afficher dans le navigateur ; attachment = forcer téléchargement
            headers.setContentDisposition(
                ContentDisposition.inline()
                    .filename(doc.getNomFichier() != null ? doc.getNomFichier() : "document")
                    .build()
            );
            headers.setContentLength(contenu.length);

            return new ResponseEntity<>(contenu, headers, HttpStatus.OK);
        });
    }
}
