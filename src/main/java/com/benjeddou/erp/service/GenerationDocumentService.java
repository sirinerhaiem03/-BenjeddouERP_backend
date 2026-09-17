package com.benjeddou.erp.service;

import com.benjeddou.erp.model.DocumentGenere;
import com.benjeddou.erp.model.DocumentGenere.StatutDocument;
import com.benjeddou.erp.model.ModeleDocument;
import com.benjeddou.erp.model.Utilisateur;
import com.benjeddou.erp.repository.DocumentGenereRepository;
import com.benjeddou.erp.repository.ModeleDocumentRepository;
import com.benjeddou.erp.repository.UtilisateurRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service de génération de documents Word (.docx) par fusion de données.
 * Remplace les {{placeholders}} dans un modèle avec les données fournies.
 * Supporte les paragraphes, tableaux, en-têtes et pieds de page.
 * Gère l'export PDF via LibreOffice headless (si disponible) ou fallback texte.
 */
@Service
@Slf4j
public class GenerationDocumentService {

    private final ModeleDocumentRepository modeleRepository;
    private final DocumentGenereRepository documentGenereRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final ObjectMapper objectMapper;

    public GenerationDocumentService(
            ModeleDocumentRepository modeleRepository,
            DocumentGenereRepository documentGenereRepository,
            UtilisateurRepository utilisateurRepository,
            ObjectMapper objectMapper) {
        this.modeleRepository = modeleRepository;
        this.documentGenereRepository = documentGenereRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.objectMapper = objectMapper;
    }

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");

    /**
     * Génère un document Word en fusionnant les données dans le modèle.
     *
     * @param modeleId    ID du modèle .docx à utiliser
     * @param donnees     Map des valeurs : {"nom_client" -> "SOTRAPIL", "montant" -> "1500.00"}
     * @param titre       Titre du document généré
     * @param moduleSource Module ERP (COMMERCIAL, ACHATS, ...)
     * @param entiteId    ID de l'entité métier liée (factureId, commandeId...)
     * @param langue      Langue (fr, ar, en)
     * @param nomUtilisateur Login de l'utilisateur connecté
     */
    public DocumentGenere generer(Long modeleId, Map<String, String> donnees,
                                   String titre, String moduleSource, Long entiteId,
                                   String langue, String nomUtilisateur) throws Exception {

        ModeleDocument modele = modeleRepository.findById(modeleId)
                .orElseThrow(() -> new RuntimeException("Modèle introuvable : " + modeleId));

        // Fusionner les données dans le .docx
        byte[] docxFusionne = fusionnerDocx(modele.getContenuBlob(), donnees);

        // Tenter la conversion PDF
        byte[] pdfBytes = null;
        try {
            pdfBytes = convertirEnPdf(docxFusionne, titre);
        } catch (Exception e) {
            log.warn("Conversion PDF indisponible pour '{}' : {}", titre, e.getMessage());
        }

        // Récupérer l'utilisateur
        Utilisateur generateur = utilisateurRepository.findByNomUtilisateur(nomUtilisateur).orElse(null);

        DocumentGenere doc = DocumentGenere.builder()
                .modele(modele)
                .titreDocument(titre != null ? titre : modele.getNom())
                .contenuDocx(docxFusionne)
                .contenuPdf(pdfBytes)
                .moduleSource(moduleSource != null ? moduleSource.toUpperCase() : modele.getModuleSource())
                .entiteId(entiteId)
                .langue(langue != null ? langue : modele.getLangue())
                .statut(StatutDocument.GENERE)
                .donneesFusion(objectMapper.writeValueAsString(donnees))
                .version(1)
                .generePar(generateur)
                .build();

        log.info("Document '{}' généré depuis modèle '{}' pour module '{}' entite {}",
                titre, modele.getNom(), moduleSource, entiteId);

        return documentGenereRepository.save(doc);
    }

    /**
     * Fusionne les données dans le .docx en remplaçant tous les {{placeholders}}.
     * Traite les paragraphes, tableaux, en-têtes et pieds de page.
     * Gère le cas où le placeholder est fragmenté sur plusieurs runs POI.
     */
    public byte[] fusionnerDocx(byte[] docxBytes, Map<String, String> donnees) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // ── Corps du document — paragraphes ─────────────────────────────
            for (XWPFParagraph para : doc.getParagraphs()) {
                remplacerDansParagraphe(para, donnees);
            }

            // ── Tableaux ────────────────────────────────────────────────────
            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph para : cell.getParagraphs()) {
                            remplacerDansParagraphe(para, donnees);
                        }
                    }
                }
            }

            // ── En-têtes ────────────────────────────────────────────────────
            for (XWPFHeader header : doc.getHeaderList()) {
                for (XWPFParagraph para : header.getParagraphs()) {
                    remplacerDansParagraphe(para, donnees);
                }
            }

            // ── Pieds de page ───────────────────────────────────────────────
            for (XWPFFooter footer : doc.getFooterList()) {
                for (XWPFParagraph para : footer.getParagraphs()) {
                    remplacerDansParagraphe(para, donnees);
                }
            }

            doc.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Remplace les {{placeholders}} dans un paragraphe POI.
     * Stratégie : reconstruire le texte complet du paragraphe, remplacer,
     * puis réécrire dans le premier run en preservant le style.
     */
    private void remplacerDansParagraphe(XWPFParagraph para, Map<String, String> donnees) {
        // Reconstruire le texte complet du paragraphe (les runs peuvent fragmenter les placeholders)
        StringBuilder textComplet = new StringBuilder();
        for (XWPFRun run : para.getRuns()) {
            textComplet.append(run.getText(0) != null ? run.getText(0) : "");
        }

        String texte = textComplet.toString();
        if (!texte.contains("{{")) return;

        // Remplacer tous les placeholders
        String texteRemplace = texte;
        for (Map.Entry<String, String> entry : donnees.entrySet()) {
            String cle = entry.getKey();
            String valeur = entry.getValue() != null ? entry.getValue() : "";
            // Support de la clé avec ou sans accolades
            texteRemplace = texteRemplace.replace("{{" + cle + "}}", valeur);
            texteRemplace = texteRemplace.replace(cle, valeur);
        }

        if (texteRemplace.equals(texte)) return; // Aucun changement

        // Vider tous les runs sauf le premier
        List<XWPFRun> runs = para.getRuns();
        if (runs.isEmpty()) return;

        // Écrire le texte fusionné dans le premier run, vider les autres
        XWPFRun premierRun = runs.get(0);
        premierRun.setText(texteRemplace, 0);

        for (int i = 1; i < runs.size(); i++) {
            runs.get(i).setText("", 0);
        }
    }

    /**
     * Convertit un .docx en PDF.
     * Tente d'abord LibreOffice headless (meilleure fidélité).
     * Si LibreOffice n'est pas disponible, utilise iText pour un PDF simple.
     */
    public byte[] convertirEnPdf(byte[] docxBytes, String titre) throws Exception {
        // Tentative LibreOffice headless
        try {
            return convertirAvecLibreOffice(docxBytes);
        } catch (Exception e) {
            log.debug("LibreOffice indisponible, génération PDF simple : {}", e.getMessage());
        }

        // Fallback : PDF simple avec iText (structure basique, sans préservation mise en page Word)
        return genererPdfSimple(docxBytes, titre);
    }

    /**
     * Convertit un .docx en PDF via LibreOffice headless.
     * LibreOffice doit être installé sur le serveur.
     * Commande : soffice --headless --convert-to pdf fichier.docx
     */
    private byte[] convertirAvecLibreOffice(byte[] docxBytes) throws Exception {
        // Écrire le .docx dans un fichier temporaire
        java.io.File tempDir = java.io.File.createTempFile("erp_doc_", "");
        tempDir.delete();
        tempDir.mkdirs();

        java.io.File tempDocx = new java.io.File(tempDir, "document.docx");
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tempDocx)) {
            fos.write(docxBytes);
        }

        // Lancer LibreOffice headless
        ProcessBuilder pb = new ProcessBuilder(
                "soffice", "--headless", "--convert-to", "pdf",
                "--outdir", tempDir.getAbsolutePath(),
                tempDocx.getAbsolutePath()
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();
        boolean done = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);

        if (!done || process.exitValue() != 0) {
            throw new RuntimeException("LibreOffice conversion failed");
        }

        // Lire le PDF généré
        java.io.File pdfFile = new java.io.File(tempDir, "document.pdf");
        if (!pdfFile.exists()) throw new RuntimeException("PDF non généré");

        byte[] pdfBytes = java.nio.file.Files.readAllBytes(pdfFile.toPath());

        // Nettoyer les fichiers temporaires
        tempDocx.delete();
        pdfFile.delete();
        tempDir.delete();

        return pdfBytes;
    }

    /**
     * Génère un PDF simple via iText en extrayant le texte du .docx.
     * Utilisé en fallback quand LibreOffice n'est pas disponible.
     * La mise en page est simplifiée (texte brut avec styles basiques).
     */
    private byte[] genererPdfSimple(byte[] docxBytes, String titre) throws Exception {
        // Extraire le texte du .docx avec POI
        StringBuilder texteDoc = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            for (XWPFParagraph para : doc.getParagraphs()) {
                texteDoc.append(para.getText()).append("\n");
            }
            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        texteDoc.append(cell.getText()).append("\t");
                    }
                    texteDoc.append("\n");
                }
            }
        }

        // Générer le PDF avec iText 7
        ByteArrayOutputStream pdfOut = new ByteArrayOutputStream();
        com.itextpdf.kernel.pdf.PdfWriter writer = new com.itextpdf.kernel.pdf.PdfWriter(pdfOut);
        com.itextpdf.kernel.pdf.PdfDocument pdfDoc = new com.itextpdf.kernel.pdf.PdfDocument(writer);
        com.itextpdf.layout.Document document = new com.itextpdf.layout.Document(pdfDoc);

        // Titre
        if (titre != null && !titre.isBlank()) {
            com.itextpdf.layout.element.Paragraph titrePdf =
                    new com.itextpdf.layout.element.Paragraph(titre)
                            .setBold()
                            .setFontSize(16)
                            .setMarginBottom(20);
            document.add(titrePdf);
        }

        // Contenu texte
        String[] lignes = texteDoc.toString().split("\n");
        for (String ligne : lignes) {
            if (!ligne.isBlank()) {
                document.add(new com.itextpdf.layout.element.Paragraph(ligne).setFontSize(11));
            }
        }

        document.close();
        return pdfOut.toByteArray();
    }

    /** Régénère le PDF d'un document déjà généré */
    public byte[] regenererPdf(Long documentId) throws Exception {
        DocumentGenere doc = documentGenereRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document introuvable : " + documentId));
        byte[] pdf = convertirEnPdf(doc.getContenuDocx(), doc.getTitreDocument());
        doc.setContenuPdf(pdf);
        documentGenereRepository.save(doc);
        return pdf;
    }

    /** Retourne les documents générés pour une entité métier */
    public List<DocumentGenere> listerParEntite(String module, Long entiteId) {
        return documentGenereRepository.findByModuleSourceAndEntiteId(module.toUpperCase(), entiteId);
    }

    /** Retourne tous les documents d'un utilisateur */
    public List<DocumentGenere> listerParUtilisateur(Long userId) {
        return documentGenereRepository.findByGenereParIdOrderByDateGenerationDesc(userId);
    }

    /** Retourne tous les documents */
    public List<DocumentGenere> listerTous() {
        return documentGenereRepository.findAll();
    }

    /** Trouve un document par ID */
    public DocumentGenere trouverParId(Long id) {
        return documentGenereRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document introuvable : " + id));
    }

    /** Archive un document */
    public void archiver(Long id) {
        DocumentGenere doc = trouverParId(id);
        doc.setStatut(StatutDocument.ARCHIVE);
        documentGenereRepository.save(doc);
    }
}
