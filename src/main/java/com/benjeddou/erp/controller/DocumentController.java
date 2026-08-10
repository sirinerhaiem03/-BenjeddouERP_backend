package com.benjeddou.erp.controller;

import com.benjeddou.erp.model.DocumentGenere;
import com.benjeddou.erp.model.ModeleDocument;
import com.benjeddou.erp.model.VersionDocument;
import com.benjeddou.erp.service.DocumentVersionService;
import com.benjeddou.erp.service.GenerationDocumentService;
import com.benjeddou.erp.service.ModeleDocumentService;
import com.benjeddou.erp.service.OcrDocumentService;
import com.benjeddou.erp.service.SignatureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Contrôleur REST principal du module de Gestion Documentaire.
 * Base URL : /api/documents
 *
 * Fonctionnalités :
 * - CRUD modèles de documents Word (.docx)
 * - Génération automatique de documents par fusion de données
 * - Export en .docx et PDF
 * - OCR sur images et PDFs
 * - Historique des versions
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class DocumentController {

    private final ModeleDocumentService modeleService;
    private final GenerationDocumentService generationService;
    private final OcrDocumentService ocrService;
    private final DocumentVersionService versionService;
    private final SignatureService signatureService;

    // ══════════════════════════════════════════════════════════════════════
    // MODÈLES DE DOCUMENTS
    // ══════════════════════════════════════════════════════════════════════

    /** Liste tous les modèles actifs, optionnellement filtrés par module */
    @GetMapping("/modeles")
    public ResponseEntity<List<ModeleDocument>> listerModeles(
            @RequestParam(required = false) String module) {
        if (module != null && !module.isBlank()) {
            return ResponseEntity.ok(modeleService.listerParModule(module));
        }
        return ResponseEntity.ok(modeleService.listerActifs());
    }

    /** Retourne un modèle par son ID */
    @GetMapping("/modeles/{id}")
    public ResponseEntity<ModeleDocument> getModele(@PathVariable Long id) {
        return ResponseEntity.ok(modeleService.trouverParId(id));
    }

    /**
     * Upload d'un fichier .docx comme modèle de document.
     * Les {{placeholders}} sont extraits automatiquement.
     */
    @PostMapping(value = "/modeles/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploaderModele(
            @RequestParam("fichier") MultipartFile fichier,
            @RequestParam("nom") String nom,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "categorie", defaultValue = "AUTRE") String categorie,
            @RequestParam(value = "langue", defaultValue = "fr") String langue,
            @RequestParam(value = "moduleSource", defaultValue = "GLOBAL") String moduleSource,
            Authentication auth) {
        try {
            if (fichier.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("erreur", "Fichier vide"));
            }
            String originalFilename = fichier.getOriginalFilename();
            if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".docx")) {
                return ResponseEntity.badRequest().body(Map.of("erreur", "Le fichier doit être au format .docx"));
            }

            ModeleDocument modele = modeleService.uploaderModele(
                    nom, description, categorie, langue, moduleSource, fichier,
                    auth != null ? auth.getName() : "system"
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(modele);

        } catch (Exception e) {
            log.error("Erreur upload modèle : {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("erreur", e.getMessage()));
        }
    }

    /** Retourne les {{placeholders}} détectés dans un modèle */
    @GetMapping("/modeles/{id}/placeholders")
    public ResponseEntity<List<String>> getPlaceholders(@PathVariable Long id) {
        return ResponseEntity.ok(modeleService.getPlaceholders(id));
    }

    /** Met à jour les métadonnées d'un modèle */
    @PutMapping("/modeles/{id}")
    public ResponseEntity<ModeleDocument> mettreAJourModele(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        ModeleDocument updated = modeleService.mettreAJour(
                id, body.get("nom"), body.get("description"),
                body.get("categorie"), body.get("langue"), body.get("moduleSource")
        );
        return ResponseEntity.ok(updated);
    }

    /** Désactive (suppression logique) un modèle */
    @DeleteMapping("/modeles/{id}")
    public ResponseEntity<Map<String, String>> supprimerModele(@PathVariable Long id) {
        modeleService.desactiver(id);
        return ResponseEntity.ok(Map.of("message", "Modèle désactivé avec succès"));
    }

    /** Duplique un modèle existant */
    @PostMapping("/modeles/{id}/dupliquer")
    public ResponseEntity<ModeleDocument> dupliquerModele(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String nouveauNom = body.getOrDefault("nom", "Copie du modèle");
        return ResponseEntity.ok(modeleService.dupliquer(id, nouveauNom));
    }

    // ══════════════════════════════════════════════════════════════════════
    // GÉNÉRATION DE DOCUMENTS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Génère un document Word en fusionnant les données dans le modèle.
     *
     * Corps de la requête :
     * {
     *   "modeleId": 1,
     *   "donnees": { "nom_client": "SOTRAPIL", "montant": "1500.00", "date": "08/07/2026" },
     *   "titre": "Facture F2026-001",
     *   "moduleSource": "COMMERCIAL",
     *   "entiteId": 42,
     *   "langue": "fr"
     * }
     */
    @PostMapping("/generer")
    public ResponseEntity<?> genererDocument(
            @RequestBody Map<String, Object> body,
            Authentication auth) {
        try {
            Long modeleId = ((Number) body.get("modeleId")).longValue();
            @SuppressWarnings("unchecked")
            Map<String, String> donnees = (Map<String, String>) body.getOrDefault("donnees", Map.of());
            String titre = (String) body.getOrDefault("titre", "Document généré");
            String moduleSource = (String) body.getOrDefault("moduleSource", "GLOBAL");
            Long entiteId = body.get("entiteId") != null ? ((Number) body.get("entiteId")).longValue() : null;
            String langue = (String) body.getOrDefault("langue", "fr");
            String nomUtilisateur = auth != null ? auth.getName() : "system";

            DocumentGenere doc = generationService.generer(
                    modeleId, donnees, titre, moduleSource, entiteId, langue, nomUtilisateur
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "id", doc.getId(),
                    "titre", doc.getTitreDocument(),
                    "statut", doc.getStatut(),
                    "dateGeneration", doc.getDateGeneration().toString(),
                    "hasPdf", doc.getContenuPdf() != null
            ));
        } catch (Exception e) {
            log.error("Erreur génération document : {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("erreur", e.getMessage()));
        }
    }

    /** Liste tous les documents générés de l'utilisateur connecté */
    @GetMapping("/generes")
    public ResponseEntity<List<DocumentGenere>> listerDocumentsGeneres(Authentication auth) {
        return ResponseEntity.ok(generationService.listerTous());
    }

    /** Retourne les infos d'un document généré */
    @GetMapping("/generes/{id}")
    public ResponseEntity<DocumentGenere> getDocumentGenere(@PathVariable Long id) {
        DocumentGenere doc = generationService.trouverParId(id);
        // Ne pas retourner les blobs dans la réponse JSON (trop volumineux)
        doc.setContenuDocx(null);
        doc.setContenuPdf(null);
        return ResponseEntity.ok(doc);
    }

    /**
     * Télécharge le fichier .docx d'un document généré.
     */
    @GetMapping("/generes/{id}/docx")
    public ResponseEntity<byte[]> telechargerDocx(@PathVariable Long id) {
        DocumentGenere doc = generationService.trouverParId(id);
        if (doc.getContenuDocx() == null) {
            return ResponseEntity.notFound().build();
        }
        String filename = doc.getTitreDocument().replaceAll("[^a-zA-Z0-9_\\-]", "_") + ".docx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(doc.getContenuDocx());
    }

    /**
     * Télécharge le PDF d'un document généré.
     * Si le PDF n'existe pas encore, il est généré à la volée.
     */
    @GetMapping("/generes/{id}/pdf")
    public ResponseEntity<byte[]> telechargerPdf(@PathVariable Long id) {
        try {
            DocumentGenere doc = generationService.trouverParId(id);
            byte[] pdf = doc.getContenuPdf();
            if (pdf == null || pdf.length == 0) {
                pdf = generationService.regenererPdf(id);
            }
            String filename = doc.getTitreDocument().replaceAll("[^a-zA-Z0-9_\\-]", "_") + ".pdf";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        } catch (Exception e) {
            log.error("Erreur génération PDF : {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /** Retourne les documents liés à une entité métier (factureId, commandeId, etc.) */
    @GetMapping("/generes/entite/{module}/{entiteId}")
    public ResponseEntity<List<DocumentGenere>> documentsParEntite(
            @PathVariable String module,
            @PathVariable Long entiteId) {
        return ResponseEntity.ok(generationService.listerParEntite(module, entiteId));
    }


    /** Archive un document */
    @PutMapping("/generes/{id}/archiver")
    public ResponseEntity<Map<String, String>> archiverDocument(@PathVariable Long id) {
        generationService.archiver(id);
        return ResponseEntity.ok(Map.of("message", "Document archivé"));
    }

    // ══════════════════════════════════════════════════════════════════════
    // OCR — RECONNAISSANCE OPTIQUE DE CARACTÈRES
    // ══════════════════════════════════════════════════════════════════════

    /**
     * OCR sur une image (JPG, PNG, WEBP).
     * Retourne le texte extrait via OpenAI GPT-4 Vision.
     * Supporte l'arabe, le français et l'anglais.
     */
    @PostMapping(value = "/ocr/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> ocrImage(
            @RequestParam("fichier") MultipartFile fichier,
            @RequestParam(value = "langue", defaultValue = "fr") String langue) {
        try {
            Map<String, Object> resultat = ocrService.ocrFichier(fichier, langue);
            return ResponseEntity.ok(resultat);
        } catch (Exception e) {
            log.error("Erreur OCR image : {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("erreur", e.getMessage()));
        }
    }

    /**
     * OCR sur un fichier PDF.
     * Utilise PDFBox pour les PDFs à couche texte, OpenAI Vision pour les PDFs scannés.
     */
    @PostMapping(value = "/ocr/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> ocrPdf(
            @RequestParam("fichier") MultipartFile fichier,
            @RequestParam(value = "langue", defaultValue = "fr") String langue) {
        try {
            Map<String, Object> resultat = ocrService.ocrFichier(fichier, langue);
            return ResponseEntity.ok(resultat);
        } catch (Exception e) {
            log.error("Erreur OCR PDF : {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("erreur", e.getMessage()));
        }
    }

    /**
     * OCR sur une image et conversion en document Word (.docx) éditable.
     * Retourne directement le fichier .docx à télécharger.
     */
    @PostMapping(value = {"/ocr/image-vers-docx", "/convert-to-word"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> imageVersDocx(
            @RequestParam("fichier") MultipartFile fichier,
            @RequestParam(value = "langue", defaultValue = "fr") String langue,
            @RequestParam(value = "fournisseur", required = false) String fournisseur,
            @RequestParam(value = "dateFacture", required = false) String dateFacture,
            @RequestParam(value = "numeroFacture", required = false) String numeroFacture,
            @RequestParam(value = "montantHt", required = false) String montantHt,
            @RequestParam(value = "tva", required = false) String tva,
            @RequestParam(value = "montantTtc", required = false) String montantTtc) {
        try {
            byte[] docxBytes = ocrService.imageVersDocxEditableAvecData(
                    fichier, langue, fournisseur, dateFacture, numeroFacture, montantHt, tva, montantTtc
            );
            String filename = "ocr_" + (fichier.getOriginalFilename() != null ?
                    fichier.getOriginalFilename().replaceAll("\\.[^.]+$", "") : "document") + ".docx";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .body(docxBytes);
        } catch (Exception e) {
            log.error("Erreur image vers DOCX : {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // VERSIONING — HISTORIQUE DES VERSIONS
    // ══════════════════════════════════════════════════════════════════════

    /** Retourne l'historique des versions d'un document */
    @GetMapping("/generes/{id}/versions")
    public ResponseEntity<List<VersionDocument>> listerVersions(@PathVariable Long id) {
        List<VersionDocument> versions = versionService.listerVersions(id);
        versions.forEach(v -> v.setContenuBlob(null)); // Ne pas retourner les blobs
        return ResponseEntity.ok(versions);
    }

    /** Sauvegarde manuellement la version courante d'un document */
    @PostMapping("/generes/{id}/versions/sauvegarder")
    public ResponseEntity<VersionDocument> sauvegarderVersion(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        String commentaire = body.getOrDefault("commentaire", "Version manuelle");
        VersionDocument version = versionService.sauvegarderVersion(
                id, commentaire, auth != null ? auth.getName() : "system"
        );
        version.setContenuBlob(null);
        return ResponseEntity.ok(version);
    }

    /** Restaure une version antérieure d'un document */
    @PostMapping("/versions/{versionId}/restaurer")
    public ResponseEntity<Map<String, Object>> restaurerVersion(
            @PathVariable Long versionId,
            Authentication auth) {
        DocumentGenere doc = versionService.restaurerVersion(
                versionId, auth != null ? auth.getName() : "system"
        );
        return ResponseEntity.ok(Map.of(
                "message", "Version restaurée avec succès",
                "documentId", doc.getId(),
                "versionActuelle", doc.getVersion()
        ));
    }

    // ══════════════════════════════════════════════════════════════════════
    // SIGNATURE ÉLECTRONIQUE
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Applique une signature électronique visuelle sur le PDF d'un document.
     *
     * Corps de la requête :
     * {
     *   "signatureBase64": "data:image/png;base64,...",
     *   "pageNum": 1,
     *   "x": 50,
     *   "y": 50,
     *   "largeur": 150
     * }
     */
    @PostMapping("/generes/{id}/signer")
    public ResponseEntity<?> signerDocument(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        try {
            String signatureBase64 = (String) body.get("signatureBase64");
            if (signatureBase64 == null || signatureBase64.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("erreur", "Image de signature manquante"));
            }
            int pageNum = ((Number) body.getOrDefault("pageNum", 1)).intValue();
            float x = ((Number) body.getOrDefault("x", 50)).floatValue();
            float y = ((Number) body.getOrDefault("y", 50)).floatValue();
            float largeur = ((Number) body.getOrDefault("largeur", 150)).floatValue();

            byte[] pdfSigne = signatureService.appliquerSignature(id, signatureBase64, pageNum, x, y, largeur);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"document_signe_" + id + ".pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfSigne);

        } catch (Exception e) {
            log.error("Erreur signature document {} : {}", id, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("erreur", e.getMessage()));
        }
    }

    /** Indique si un document est signé */
    @GetMapping("/generes/{id}/signature-status")
    public ResponseEntity<Map<String, Object>> statutSignature(@PathVariable Long id) {
        boolean signe = signatureService.estSigne(id);
        return ResponseEntity.ok(Map.of("documentId", id, "signe", signe));
    }
}

