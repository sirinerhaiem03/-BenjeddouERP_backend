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
        Optional<Utilisateur> found = utilisateurRepository.findByNomUtilisateur(username);
        boolean available = found.isEmpty()
            || !Boolean.TRUE.equals(found.get().getActif())
            || found.get().getStatutCompte() == StatutCompte.EN_ATTENTE;
        return ResponseEntity.ok(Map.of("available", available));
    }

    @GetMapping("/check-email")
    public ResponseEntity<?> checkEmail(@RequestParam String email) {
        Optional<Utilisateur> found = utilisateurRepository.findByEmail(email);
        boolean available = found.isEmpty()
            || !Boolean.TRUE.equals(found.get().getActif())
            || found.get().getStatutCompte() == StatutCompte.EN_ATTENTE;
        return ResponseEntity.ok(Map.of("available", available));
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
        // Vérifier si l'email est déjà utilisé
        if (utilisateurRepository.existsByEmail(email)) {
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

        // ── Supprimer les comptes non activés existants (tentatives précédentes) ──
        Optional<Utilisateur> existingByUsername = utilisateurRepository.findByNomUtilisateur(nomUtilisateur);
        if (existingByUsername.isPresent()) {
            Utilisateur existing = existingByUsername.get();
            boolean notActivated = !Boolean.TRUE.equals(existing.getActif())
                || existing.getStatutCompte() == StatutCompte.EN_ATTENTE;
            if (notActivated) {
                // Supprimer les documents KYC liés avant de supprimer l'utilisateur
                documentKycRepository.deleteAll(
                    documentKycRepository.findByUtilisateur(existing)
                );
                utilisateurRepository.delete(existing);
            } else {
                return ResponseEntity.badRequest()
                    .body(new MessageReponse("Ce nom d'utilisateur est déjà pris."));
            }
        }

        Optional<Utilisateur> existingByEmail = utilisateurRepository.findByEmail(email);
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
        StatutCompte statut = modeTrial ? StatutCompte.ACTIF : StatutCompte.EN_ATTENTE;

        // ╔════════════════════════════════════════════════════════════
        // MULTI-TENANT : Créer la base de données dédiée de l'entreprise (erp_ent_XXXXX)
        // ╚════════════════════════════════════════════════════════════
        com.benjeddou.erp.model.Entreprise entreprise;
        try {
            entreprise = entrepriseService.creerEntreprise(
                societe != null && !societe.isBlank() ? societe : nomUtilisateur,
                email,
                null
            );
        } catch (Exception ex) {
            log.error("Erreur création base tenant pour '{}' : {}", nomUtilisateur, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new MessageReponse("Erreur création base entreprise : " + ex.getMessage()));
        }

        // ╔════════════════════════════════════════════════════════════
        // Créer l'administrateur UNIQUEMENT dans la base de son entreprise (erp_ent_XXXXX.utilisateurs)
        // La base master (benjeddou_erp.utilisateurs) contient STRICTEMENT ET UNIQUEMENT le Superadmin
        // ╚════════════════════════════════════════════════════════════
        Utilisateur client = Utilisateur.builder()
            .nomUtilisateur(nomUtilisateur)
            .email(email)
            .motDePasse(encoder.encode(motDePasse))
            .prenom(prenom)
            .nom(nom)
            .telephone(telephone)
            .societe(societe)
            .adresse(adresse)
            .role(Role.ADMIN)
            .statutCompte(statut)
            .modeTrial(modeTrial)
            .nbUtilisations(0)
            .nbUtilisationsMax(modeTrial ? 30 : 0)
            .actif(modeTrial)
            .kycSoumis(false)
            .languePreferee("fr")
            .entrepriseId(entreprise.getId())
            .entrepriseSchema(entreprise.getSchemaName())
            .build();

        // Insertion EXCLUSIVE dans la table utilisateurs de la base dédiée de l'entreprise
        Long adminUserId = entrepriseService.synchroniserUtilisateurDansTenant(entreprise.getSchemaName(), client);
        if (adminUserId != null) {
            entreprise.setAdminId(adminUserId);
            entrepriseRepository.save(entreprise);
        }
        log.info("✓ Compte entreprise '{}' créé EXCLUSIVEMENT dans la base tenant '{}' (id={})", nomUtilisateur, entreprise.getSchemaName(), adminUserId);

        // Nettoyage de sécurité : s'assurer qu'aucun utilisateur entreprise ne subsiste dans la base master
        try {
            utilisateurRepository.findByNomUtilisateur(nomUtilisateur).ifPresent(u -> {
                if (u.getRole() != Role.SUPERADMIN) {
                    utilisateurRepository.delete(u);
                }
            });
            utilisateurRepository.findByEmail(email).ifPresent(u -> {
                if (u.getRole() != Role.SUPERADMIN) {
                    utilisateurRepository.delete(u);
                }
            });
        } catch (Exception ignored) {}

        String message = modeTrial
            ? "Inscription réussie ! Vous disposez de 30 connexions d'essai gratuites. Votre espace entreprise a été créé automatiquement."
            : "Inscription réussie ! Votre compte est en attente de validation KYC.";

        return ResponseEntity.ok(new MessageReponse(message));
    }

    /**
     * Étape 2 : Upload d'un document KYC — stocké en BASE DE DONNÉES (BLOB)
     */
    @PostMapping("/kyc/upload")
    public ResponseEntity<?> uploadDocumentKyc(
            @RequestParam("nomUtilisateur") String nomUtilisateur,
            @RequestParam("typeDocument") String typeDocument,
            @RequestParam("fichier") MultipartFile fichier) {

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

        try {
            byte[] contenu = fichier.getBytes();
            String contentType = fichier.getContentType();
            if (contentType == null) contentType = "application/octet-stream";

            // Détecter le content type depuis l'extension si null
            String origName = fichier.getOriginalFilename();
            if (origName != null) {
                String lower = origName.toLowerCase();
                if (lower.endsWith(".pdf"))  contentType = "application/pdf";
                if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) contentType = "image/jpeg";
                if (lower.endsWith(".png"))  contentType = "image/png";
            }

            // Sauvegarder le document avec le contenu binaire en base
            DocumentKyc doc = DocumentKyc.builder()
                .utilisateur(client)
                .typeDocument(typeDocument)
                .nomFichier(origName != null ? origName : typeDocument)
                .contentType(contentType)
                .contenuFichier(contenu)
                .statutVerification("EN_ATTENTE")
                .build();

            documentKycRepository.save(doc);

            // Marquer le client comme ayant soumis des documents
            client.setKycSoumis(true);
            client.setStatutCompte(StatutCompte.EN_ATTENTE);
            utilisateurRepository.save(client);

            return ResponseEntity.ok(new MessageReponse(
                "Document '" + typeDocument + "' soumis avec succès."));

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new MessageReponse("Erreur lors de l'upload : " + e.getMessage()));
        }
    }

    /**
     * Récupérer la liste des documents KYC (sans le contenu binaire)
     */
    @GetMapping("/kyc/{userId}")
    public ResponseEntity<?> getDocumentsKyc(@PathVariable Long userId) {
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
    }

    /**
     * Servir un fichier KYC directement depuis la base de données
     */
    @GetMapping("/kyc/document/{docId}")
    public ResponseEntity<byte[]> voirDocument(@PathVariable Long docId) {
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
    }
}
