package com.benjeddou.erp.controller;

import com.benjeddou.erp.model.CalculMoteur;
import com.benjeddou.erp.model.LigneCalcul;
import com.benjeddou.erp.security.services.UserDetailsImpl;
import com.benjeddou.erp.service.MoteurCalculService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MoteurCalculController — API REST pour le moteur de calcul.
 * Endpoints sous /api/calcul
 */
@RestController
@RequestMapping("/api/calcul")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class MoteurCalculController {

    private final MoteurCalculService moteurCalculService;

    // ─── Mode 1 : Taux unique ──────────────────────────────────────────────────

    /**
     * POST /api/calcul/taux-unique
     * Corps JSON :
     * {
     *   "montant": 25000,
     *   "dateDebut": "2026-02-18",
     *   "dateFin": "2026-06-29",
     *   "taux": 9.75,
     *   "moduleErp": "FINANCE",
     *   "libelle": "Calcul intérêts prêt",
     *   "userId": 1
     * }
     */
    @PostMapping("/taux-unique")
    public ResponseEntity<?> calculerTauxUnique(@RequestBody Map<String, Object> body) {
        try {
            BigDecimal montant = new BigDecimal(body.get("montant").toString());
            LocalDate dateDebut = LocalDate.parse(body.get("dateDebut").toString());
            LocalDate dateFin = LocalDate.parse(body.get("dateFin").toString());
            BigDecimal taux = new BigDecimal(body.get("taux").toString());
            String moduleErp = body.getOrDefault("moduleErp", "GENERAL").toString();
            String libelle = body.getOrDefault("libelle", "").toString();
            Long userId = body.containsKey("userId") ?
                    Long.valueOf(body.get("userId").toString()) : null;

            CalculMoteur result = moteurCalculService.calculerTauxUnique(
                    montant, dateDebut, dateFin, taux, moduleErp, libelle, userId);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "calcul", buildCalculResponse(result),
                "message", "Calcul effectué et sauvegardé avec succès"
            ));
        } catch (Exception e) {
            log.error("Erreur calcul taux unique: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ─── Mode 2 : Taux variables ───────────────────────────────────────────────

    /**
     * POST /api/calcul/taux-variable
     * Corps JSON :
     * {
     *   "montant": 150000,
     *   "dateDebut": "2026-02-18",
     *   "dateFin": "2026-10-27",
     *   "moduleErp": "COMPTABILITE",
     *   "libelle": "Calcul intérêts crédit",
     *   "userId": 1
     * }
     */
    @PostMapping("/taux-variable")
    public ResponseEntity<?> calculerTauxVariable(@RequestBody Map<String, Object> body) {
        try {
            BigDecimal montant = new BigDecimal(body.get("montant").toString());
            LocalDate dateDebut = LocalDate.parse(body.get("dateDebut").toString());
            LocalDate dateFin = LocalDate.parse(body.get("dateFin").toString());
            String moduleErp = body.getOrDefault("moduleErp", "GENERAL").toString();
            String libelle = body.getOrDefault("libelle", "").toString();
            Long userId = body.containsKey("userId") ?
                    Long.valueOf(body.get("userId").toString()) : null;

            CalculMoteur result = moteurCalculService.calculerTauxVariables(
                    montant, dateDebut, dateFin, moduleErp, libelle, userId);

            List<LigneCalcul> lignes = moteurCalculService.getLignes(result.getId());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "calcul", buildCalculResponse(result),
                "lignes", lignes.stream().map(this::buildLigneResponse).toList(),
                "message", "Calcul multi-périodes effectué et sauvegardé"
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage(),
                "code", "PERIODES_MANQUANTES"
            ));
        } catch (Exception e) {
            log.error("Erreur calcul taux variable: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * POST /api/calcul/simuler-taux-variable
     * Simulation sans sauvegarde — pour aperçu temps réel dans l'interface.
     */
    @PostMapping("/simuler-taux-variable")
    public ResponseEntity<?> simulerTauxVariable(@RequestBody Map<String, Object> body) {
        try {
            BigDecimal montant = new BigDecimal(body.get("montant").toString());
            LocalDate dateDebut = LocalDate.parse(body.get("dateDebut").toString());
            LocalDate dateFin = LocalDate.parse(body.get("dateFin").toString());

            List<LigneCalcul> lignes = moteurCalculService.simulerTauxVariables(
                    montant, dateDebut, dateFin);

            BigDecimal total = lignes.stream()
                    .map(LigneCalcul::getResultatLigne)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, java.math.RoundingMode.HALF_UP);

            long joursTotal = moteurCalculService.calculerNombreJours(dateDebut, dateFin);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "lignes", lignes.stream().map(this::buildLigneResponse).toList(),
                "resultatTotal", total,
                "nombreJoursTotal", joursTotal,
                "nbPeriodes", lignes.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ─── Utilitaire : calcul du nombre de jours ────────────────────────────────

    @GetMapping("/nombre-jours")
    public ResponseEntity<?> getNombreJours(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        long jours = moteurCalculService.calculerNombreJours(dateDebut, dateFin);
        return ResponseEntity.ok(Map.of("nombreJours", jours));
    }

    // ─── Historique ────────────────────────────────────────────────────────────

    @GetMapping("/historique")
    public ResponseEntity<?> getHistorique(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String type) {

        // ╔═════════════════════════════════════════════════════
        // ISOLATION MULTI-TENANT : Filtrage OBLIGATOIRE par utilisateur connecté
        // Chaque utilisateur ne voit QUE ses propres calculs.
        // L'isolation physique (base séparée) garantit qu'aucun calcul
        // d'une autre entreprise ne peut apparaître dans cette base.
        // ╚═════════════════════════════════════════════════════
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long currentUserId = null;
        boolean isSuperAdmin = false;

        if (auth != null && auth.getPrincipal() instanceof UserDetailsImpl userDetails) {
            currentUserId = userDetails.getId();
            isSuperAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPERADMIN"));
        }

        Pageable pageable = PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by("dateCreation").descending());

        Page<CalculMoteur> result;

        if (isSuperAdmin) {
            // SuperAdmin : voit tous les calculs de la base master
            result = (type != null && !type.isBlank())
                ? ((q != null && !q.isBlank())
                    ? moteurCalculService.rechercherHistoriqueParType(q, type, pageable)
                    : moteurCalculService.getHistoriqueParType(type, pageable))
                : ((q != null && !q.isBlank())
                    ? moteurCalculService.rechercherHistorique(q, pageable)
                    : moteurCalculService.getHistorique(pageable));
        } else if (currentUserId != null) {
            // Utilisateur normal : uniquement SES calculs
            result = (q != null && !q.isBlank())
                ? moteurCalculService.rechercherHistoriqueParUtilisateur(q, currentUserId, type, pageable)
                : moteurCalculService.getHistoriqueParUtilisateur(currentUserId, type, pageable);
        } else {
            // Pas authentifié : aucun résultat
            return ResponseEntity.status(401).body(Map.of("message", "Non authentifié"));
        }

        return ResponseEntity.ok(Map.of(
            "content", result.getContent().stream().map(this::buildCalculResponse).toList(),
            "totalElements", result.getTotalElements(),
            "totalPages", result.getTotalPages(),
            "currentPage", result.getNumber()
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return moteurCalculService.getById(id)
                .map(c -> ResponseEntity.ok((Object) buildCalculResponse(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/lignes")
    public ResponseEntity<?> getLignes(@PathVariable Long id) {
        List<LigneCalcul> lignes = moteurCalculService.getLignes(id);
        return ResponseEntity.ok(lignes.stream().map(this::buildLigneResponse).toList());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> supprimer(@PathVariable Long id) {
        try {
            moteurCalculService.supprimer(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Calcul supprimé"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ─── Helpers de sérialisation ──────────────────────────────────────────────

    private Map<String, Object> buildCalculResponse(CalculMoteur c) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId());
        m.put("reference", c.getReference());
        m.put("typeCalcul", c.getTypeCalcul());
        m.put("montant", c.getMontant());
        m.put("dateDebut", c.getDateDebut());
        m.put("dateFin", c.getDateFin());
        m.put("nombreJours", c.getNombreJours());
        m.put("tauxUnique", c.getTauxUnique());
        m.put("resultatTotal", c.getResultatTotal());
        m.put("moduleErp", c.getModuleErp());
        m.put("libelle", c.getLibelle());
        m.put("dateCreation", c.getDateCreation());
        m.put("creeParNom", c.getCreePar() != null ? c.getCreePar().getNom() : null);
        return m;
    }

    private Map<String, Object> buildLigneResponse(LigneCalcul l) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", l.getId());
        m.put("numeroLigne", l.getNumeroLigne());
        m.put("dateDebut", l.getDateDebut());
        m.put("dateFin", l.getDateFin());
        m.put("nombreJours", l.getNombreJours());
        m.put("taux", l.getTaux());
        m.put("montantBase", l.getMontantBase());
        m.put("resultatLigne", l.getResultatLigne());
        m.put("libellePeriode", l.getLibellePeriode());
        return m;
    }
}
