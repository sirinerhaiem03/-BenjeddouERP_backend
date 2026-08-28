package com.benjeddou.erp.service;

import com.benjeddou.erp.model.ModeleDocument;
import com.benjeddou.erp.model.Utilisateur;
import com.benjeddou.erp.repository.ModeleDocumentRepository;
import com.benjeddou.erp.repository.UtilisateurRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service de gestion des modèles de documents Word (.docx).
 * Permet l'upload, l'extraction de placeholders, et le CRUD des modèles.
 */
@Service
@Slf4j
public class ModeleDocumentService {

    private final ModeleDocumentRepository modeleRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final ObjectMapper objectMapper;

    public ModeleDocumentService(
            ModeleDocumentRepository modeleRepository,
            UtilisateurRepository utilisateurRepository,
            ObjectMapper objectMapper) {
        this.modeleRepository = modeleRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.objectMapper = objectMapper;
    }

    /** Regex pour détecter les placeholders {{champ}} dans le texte */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");

    /**
     * Upload et sauvegarde un nouveau modèle .docx.
     * Extrait automatiquement les placeholders du document.
     */
    public ModeleDocument uploaderModele(String nom, String description, String categorie,
                                          String langue, String moduleSource,
                                          MultipartFile fichier, String nomUtilisateur) throws IOException {
        byte[] contenu = fichier.getBytes();

        // Extraire les placeholders
        Set<String> placeholders = extrairePlaceholders(contenu);
        String placeholdersJson = objectMapper.writeValueAsString(new ArrayList<>(placeholders));

        // Trouver l'utilisateur
        Utilisateur createur = utilisateurRepository.findByNomUtilisateur(nomUtilisateur).orElse(null);

        ModeleDocument modele = ModeleDocument.builder()
                .nom(nom)
                .description(description)
                .categorie(categorie != null ? categorie.toUpperCase() : "AUTRE")
                .langue(langue != null ? langue : "fr")
                .moduleSource(moduleSource != null ? moduleSource.toUpperCase() : "GLOBAL")
                .contenuBlob(contenu)
                .placeholders(placeholdersJson)
                .nomFichierOriginal(fichier.getOriginalFilename())
                .tailleFichier(fichier.getSize())
                .actif(true)
                .creePar(createur)
                .build();

        log.info("Modèle '{}' uploadé avec {} placeholder(s) : {}", nom, placeholders.size(), placeholders);
        return modeleRepository.save(modele);
    }

    /**
     * Extrait tous les {{placeholders}} présents dans un fichier .docx.
     * Analyse paragraphes, tableaux et en-têtes/pieds de page.
     */
    public Set<String> extrairePlaceholders(byte[] docxBytes) throws IOException {
        Set<String> placeholders = new LinkedHashSet<>();

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            // Paragraphes du corps
            for (XWPFParagraph para : doc.getParagraphs()) {
                extraireDuTexte(para.getText(), placeholders);
            }

            // Cellules des tableaux
            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph para : cell.getParagraphs()) {
                            extraireDuTexte(para.getText(), placeholders);
                        }
                    }
                }
            }

            // En-têtes
            for (XWPFHeader header : doc.getHeaderList()) {
                for (XWPFParagraph para : header.getParagraphs()) {
                    extraireDuTexte(para.getText(), placeholders);
                }
            }

            // Pieds de page
            for (XWPFFooter footer : doc.getFooterList()) {
                for (XWPFParagraph para : footer.getParagraphs()) {
                    extraireDuTexte(para.getText(), placeholders);
                }
            }
        }

        return placeholders;
    }

    /** Extrait les placeholders {{...}} d'un texte donné */
    private void extraireDuTexte(String texte, Set<String> placeholders) {
        if (texte == null || texte.isBlank()) return;
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(texte);
        while (matcher.find()) {
            placeholders.add("{{" + matcher.group(1).trim() + "}}");
        }
    }

    /** Récupère la liste des placeholders d'un modèle (désérialisée) */
    public List<String> getPlaceholders(Long modeleId) {
        ModeleDocument modele = modeleRepository.findById(modeleId)
                .orElseThrow(() -> new RuntimeException("Modèle introuvable : " + modeleId));
        try {
            return objectMapper.readValue(modele.getPlaceholders(), new TypeReference<>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** Liste tous les modèles actifs */
    public List<ModeleDocument> listerActifs() {
        return modeleRepository.findByActifTrue();
    }

    /** Liste les modèles actifs d'un module donné */
    public List<ModeleDocument> listerParModule(String moduleSource) {
        return modeleRepository.findByModuleSourceAndActifTrue(moduleSource.toUpperCase());
    }

    /** Retourne un modèle par ID */
    public ModeleDocument trouverParId(Long id) {
        return modeleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Modèle introuvable : " + id));
    }

    /** Met à jour les métadonnées d'un modèle (sans changer le contenu .docx) */
    public ModeleDocument mettreAJour(Long id, String nom, String description,
                                       String categorie, String langue, String moduleSource) {
        ModeleDocument modele = trouverParId(id);
        if (nom != null) modele.setNom(nom);
        if (description != null) modele.setDescription(description);
        if (categorie != null) modele.setCategorie(categorie.toUpperCase());
        if (langue != null) modele.setLangue(langue);
        if (moduleSource != null) modele.setModuleSource(moduleSource.toUpperCase());
        return modeleRepository.save(modele);
    }

    /** Désactive (suppression logique) un modèle */
    public void desactiver(Long id) {
        ModeleDocument modele = trouverParId(id);
        modele.setActif(false);
        modeleRepository.save(modele);
        log.info("Modèle {} désactivé", id);
    }

    /** Duplique un modèle existant avec un nouveau nom */
    public ModeleDocument dupliquer(Long id, String nouveauNom) {
        ModeleDocument original = trouverParId(id);
        ModeleDocument copie = ModeleDocument.builder()
                .nom(nouveauNom)
                .description(original.getDescription())
                .categorie(original.getCategorie())
                .langue(original.getLangue())
                .moduleSource(original.getModuleSource())
                .contenuBlob(original.getContenuBlob().clone())
                .placeholders(original.getPlaceholders())
                .nomFichierOriginal("copie_" + original.getNomFichierOriginal())
                .tailleFichier(original.getTailleFichier())
                .actif(true)
                .build();
        return modeleRepository.save(copie);
    }
}
