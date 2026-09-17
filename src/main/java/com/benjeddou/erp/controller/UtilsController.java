package com.benjeddou.erp.controller;

import com.benjeddou.erp.service.GlobalSearchService;
import com.benjeddou.erp.service.NombreLettresService;
import com.benjeddou.erp.service.DictionnaireService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

/**
 * Contrôleur des fonctionnalités transversales :
 *  - Recherche globale cross-modules
 *  - Auto-complétion intelligente
 *  - Conversion montants en lettres (FR / AR / EN)
 *  - Dictionnaire / correction orthographique
 *  - Validation de dates
 *
 * Base URL : /api/utils
 */
@RestController
@RequestMapping("/api/utils")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UtilsController {

    private final GlobalSearchService searchService;
    private final NombreLettresService nombreLettresService;
    private final DictionnaireService dictionnaireService;

    // ══════════════════════════════════════════════════════════════════
    //  RECHERCHE GLOBALE (N°2)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Recherche globale dans toutes les entités.
     * GET /api/utils/search?q=dupont&limite=5
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, List<GlobalSearchService.ResultatRecherche>>> rechercherGlobal(
            @RequestParam String q,
            @RequestParam(defaultValue = "5") int limite) {
        if (q == null || q.trim().length() < 2) {
            return ResponseEntity.ok(Collections.emptyMap());
        }
        return ResponseEntity.ok(searchService.rechercherTout(q.trim(), limite));
    }

    /**
     * Auto-complétion rapide (8 résultats max, toutes catégories confondues).
     * GET /api/utils/autocomplete?q=sot
     */
    @GetMapping("/autocomplete")
    public ResponseEntity<List<GlobalSearchService.ResultatRecherche>> autocomplete(
            @RequestParam String q) {
        if (q == null || q.trim().length() < 2) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        return ResponseEntity.ok(searchService.autocomplete(q.trim()));
    }

    // ══════════════════════════════════════════════════════════════════
    //  CONVERSION MONTANTS EN LETTRES (N°7)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Convertit un montant en lettres.
     * GET /api/utils/montant-lettres?montant=1234.750&devise=TND&langue=fr
     */
    @GetMapping("/montant-lettres")
    public ResponseEntity<Map<String, String>> montantEnLettres(
            @RequestParam BigDecimal montant,
            @RequestParam(defaultValue = "TND") String devise,
            @RequestParam(defaultValue = "fr")  String langue) {
        String lettres = nombreLettresService.convertir(montant, devise, langue);
        return ResponseEntity.ok(Map.of(
            "montant",  montant.toPlainString(),
            "lettres",  lettres,
            "devise",   devise,
            "langue",   langue
        ));
    }

    /**
     * Calcule et convertit HT + TVA + TTC en lettres.
     * POST /api/utils/montant-complet
     * Body: { "montantHt": 1000.000, "tauxTva": 19.0, "devise": "TND", "langue": "fr" }
     */
    @PostMapping("/montant-complet")
    public ResponseEntity<Map<String, String>> montantComplet(
            @RequestBody Map<String, Object> body) {
        BigDecimal montantHt = new BigDecimal(body.get("montantHt").toString());
        double tauxTva = Double.parseDouble(body.getOrDefault("tauxTva", "19.0").toString());
        String devise  = body.getOrDefault("devise",  "TND").toString();
        String langue  = body.getOrDefault("langue",  "fr").toString();
        return ResponseEntity.ok(nombreLettresService.convertirMontantComplet(montantHt, tauxTva, devise, langue));
    }

    // ══════════════════════════════════════════════════════════════════
    //  DICTIONNAIRE / CORRECTION ORTHOGRAPHIQUE (N°4)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Corrige et améliore un texte via l'IA.
     * POST /api/utils/corriger
     * Body: { "texte": "...", "langue": "fr", "mode": "correction|amelioration|suggestion" }
     */
    @PostMapping("/corriger")
    public ResponseEntity<Map<String, Object>> corrigerTexte(
            @RequestBody Map<String, String> body) {
        String texte  = body.getOrDefault("texte", "");
        String langue = body.getOrDefault("langue", "fr");
        String mode   = body.getOrDefault("mode", "correction");
        if (texte.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("erreur", "Texte vide"));
        }
        return ResponseEntity.ok(dictionnaireService.corrigerEtAmeliorer(texte, langue, mode));
    }

    /**
     * Suggestions de formulation pour un contexte ERP.
     * POST /api/utils/suggerer
     * Body: { "contexte": "objet facture", "langue": "fr", "nbSuggestions": 3 }
     */
    @PostMapping("/suggerer")
    public ResponseEntity<Map<String, Object>> suggererFormulation(
            @RequestBody Map<String, Object> body) {
        String contexte = body.getOrDefault("contexte", "").toString();
        String langue   = body.getOrDefault("langue", "fr").toString();
        int nb = Integer.parseInt(body.getOrDefault("nbSuggestions", "3").toString());
        return ResponseEntity.ok(dictionnaireService.suggerer(contexte, langue, nb));
    }

    // ══════════════════════════════════════════════════════════════════
    //  VALIDATION DE DATES (N°5)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Valide et normalise une date saisie.
     * POST /api/utils/valider-date
     * Body: { "valeur": "09/07/2026", "format": "dd/MM/yyyy" }
     */
    @PostMapping("/valider-date")
    public ResponseEntity<Map<String, Object>> validerDate(
            @RequestBody Map<String, String> body) {
        String valeur  = body.getOrDefault("valeur", "");
        String format  = body.getOrDefault("format", "dd/MM/yyyy");
        Map<String, Object> resultat = new LinkedHashMap<>();
        try {
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern(format);
            java.time.LocalDate date = java.time.LocalDate.parse(valeur, fmt);
            resultat.put("valide", true);
            resultat.put("dateNormalisee", date.toString());                          // ISO
            resultat.put("affichageFr",    date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            resultat.put("jourSemaine",    date.getDayOfWeek().getDisplayName(
                java.time.format.TextStyle.FULL, java.util.Locale.FRENCH));
            // Détection si date passée/future
            java.time.LocalDate auj = java.time.LocalDate.now();
            resultat.put("estPasse",  date.isBefore(auj));
            resultat.put("estFutur",  date.isAfter(auj));
            resultat.put("estAujourdHui", date.isEqual(auj));
        } catch (Exception e) {
            resultat.put("valide",  false);
            resultat.put("erreur",  "Format invalide — attendu : " + format);
        }
        return ResponseEntity.ok(resultat);
    }

    // ══════════════════════════════════════════════════════════════════
    //  VALIDATION FORMULAIRES (N°6)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Valide un email (format + domaine).
     * GET /api/utils/valider-email?email=test@ex.com
     */
    @GetMapping("/valider-email")
    public ResponseEntity<Map<String, Object>> validerEmail(@RequestParam String email) {
        boolean valide = email != null && email.matches("^[\\w._%+\\-]+@[\\w.\\-]+\\.[a-zA-Z]{2,}$");
        return ResponseEntity.ok(Map.of("email", email, "valide", valide));
    }

    /**
     * Valide un numéro de téléphone tunisien ou international.
     * GET /api/utils/valider-telephone?tel=+21622123456
     */
    @GetMapping("/valider-telephone")
    public ResponseEntity<Map<String, Object>> validerTelephone(@RequestParam String tel) {
        boolean valide = tel != null && tel.matches("^\\+?[0-9\\s\\-().]{7,20}$");
        return ResponseEntity.ok(Map.of("telephone", tel, "valide", valide));
    }

    /**
     * Valide un matricule fiscal tunisien.
     * GET /api/utils/valider-matricule?matricule=0123456ABC
     */
    @GetMapping("/valider-matricule")
    public ResponseEntity<Map<String, Object>> validerMatricule(@RequestParam String matricule) {
        // Format TN : 7 chiffres + 1-3 lettres (ex: 1234567ABC)
        boolean valide = matricule != null && matricule.matches("^[0-9]{7}[A-Za-z]{1,3}(/[A-Z]/[0-9]{3})?$");
        return ResponseEntity.ok(Map.of("matricule", matricule, "valide", valide));
    }
}
