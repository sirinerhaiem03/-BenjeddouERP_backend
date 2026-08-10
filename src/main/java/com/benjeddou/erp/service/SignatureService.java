package com.benjeddou.erp.service;

import com.benjeddou.erp.model.DocumentGenere;
import com.benjeddou.erp.model.DocumentGenere.StatutDocument;
import com.benjeddou.erp.repository.DocumentGenereRepository;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Service de signature électronique visuelle pour les documents PDF générés.
 * Applique une image de signature (base64) à une position donnée sur le PDF.
 * La signature est conservée dans DocumentGenere.signatureBase64.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SignatureService {

    private final DocumentGenereRepository documentGenereRepository;
    private final GenerationDocumentService generationService;

    /**
     * Applique une signature visuelle sur le PDF d'un document généré.
     *
     * @param documentId    ID du document à signer
     * @param signatureBase64 Image de la signature encodée en base64
     * @param pageNum       Numéro de page sur laquelle placer la signature (1-based)
     * @param x             Position X (pts, depuis le bas gauche) — défaut 50
     * @param y             Position Y (pts, depuis le bas gauche) — défaut 50
     * @param largeur       Largeur de la signature en pts — défaut 150
     * @return Contenu PDF signé
     */
    public byte[] appliquerSignature(Long documentId, String signatureBase64,
                                      int pageNum, float x, float y, float largeur) throws Exception {
        DocumentGenere doc = documentGenereRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document introuvable : " + documentId));

        // S'assurer que le PDF existe
        byte[] pdfOriginal = doc.getContenuPdf();
        if (pdfOriginal == null || pdfOriginal.length == 0) {
            pdfOriginal = generationService.regenererPdf(documentId);
        }

        // Décoder l'image de signature depuis base64
        String base64Data = signatureBase64;
        if (base64Data.contains(",")) {
            base64Data = base64Data.split(",")[1]; // Supprimer le préfixe data:image/...;base64,
        }
        byte[] imageBytes = Base64.getDecoder().decode(base64Data);

        // Ouvrir le PDF et y ajouter la signature
        byte[] pdfSigne = ajouterImageSignature(pdfOriginal, imageBytes, pageNum, x, y, largeur);

        // Sauvegarder la signature et le PDF signé dans la base
        doc.setContenuPdf(pdfSigne);
        doc.setSignatureBase64(signatureBase64);
        doc.setDateSignature(LocalDateTime.now());
        doc.setStatut(StatutDocument.SIGNE);
        documentGenereRepository.save(doc);

        log.info("Document {} signé électroniquement (page {}, x={}, y={})", documentId, pageNum, x, y);
        return pdfSigne;
    }

    /**
     * Insère une image de signature dans un PDF existant via iText 7.
     * La signature est placée en superposition transparente sur le PDF.
     */
    private byte[] ajouterImageSignature(byte[] pdfBytes, byte[] imageBytes,
                                           int pageNum, float x, float y, float largeur) throws Exception {
        try (ByteArrayInputStream pdfIn = new ByteArrayInputStream(pdfBytes);
             ByteArrayOutputStream pdfOut = new ByteArrayOutputStream()) {

            PdfReader reader = new PdfReader(pdfIn);
            PdfWriter writer = new PdfWriter(pdfOut);
            PdfDocument pdfDoc = new PdfDocument(reader, writer);
            Document document = new Document(pdfDoc);

            // Créer l'image de signature
            Image sigImage = new Image(ImageDataFactory.create(imageBytes));
            sigImage.setWidth(largeur);
            sigImage.setFixedPosition(pageNum, x, y);

            document.add(sigImage);
            document.close();

            return pdfOut.toByteArray();
        }
    }

    /**
     * Retourne le PDF signé d'un document.
     * Si le document n'est pas encore signé, retourne le PDF non signé.
     */
    public byte[] getPdfSigne(Long documentId) throws Exception {
        DocumentGenere doc = documentGenereRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document introuvable : " + documentId));

        byte[] pdf = doc.getContenuPdf();
        if (pdf == null || pdf.length == 0) {
            pdf = generationService.regenererPdf(documentId);
        }
        return pdf;
    }

    /**
     * Vérifie si un document est signé.
     */
    public boolean estSigne(Long documentId) {
        return documentGenereRepository.findById(documentId)
                .map(d -> d.getStatut() == StatutDocument.SIGNE)
                .orElse(false);
    }
}
