package com.benjeddou.erp.controller;

import com.benjeddou.erp.model.CodePromo;
import com.benjeddou.erp.repository.CodePromoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/promo")
public class CodePromoController {

    @Autowired
    CodePromoRepository codePromoRepository;

    // ── GET All ───────────────────────────────────────────────────────────────
    @GetMapping("")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMMERCIAL')")
    public List<Map<String, Object>> getTousLesCodes() {
        return codePromoRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // ── GET One ───────────────────────────────────────────────────────────────
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMMERCIAL')")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return codePromoRepository.findById(id)
                .map(c -> ResponseEntity.ok(toDto(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    // ── POST Créer ────────────────────────────────────────────────────────────
    @PostMapping("")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMMERCIAL')")
    public ResponseEntity<?> creer(@RequestBody Map<String, Object> body) {
        try {
            String code = body.get("code").toString().toUpperCase().trim();
            if (codePromoRepository.existsByCode(code)) {
                return ResponseEntity.badRequest().body("Ce code promo existe déjà : " + code);
            }

            CodePromo promo = CodePromo.builder()
                    .code(code)
                    .description(getString(body, "description", ""))
                    .typeRemise(getString(body, "typeRemise", "POURCENTAGE"))
                    .valeur(new BigDecimal(body.get("valeur").toString()))
                    .montantMinimum(body.containsKey("montantMinimum")
                            ? new BigDecimal(body.get("montantMinimum").toString()) : BigDecimal.ZERO)
                    .plafondRemise(body.containsKey("plafondRemise") && body.get("plafondRemise") != null
                            && !body.get("plafondRemise").toString().isBlank()
                            ? new BigDecimal(body.get("plafondRemise").toString()) : null)
                    .utilisationsMax(body.containsKey("utilisationsMax") && body.get("utilisationsMax") != null
                            && !body.get("utilisationsMax").toString().isBlank()
                            ? Integer.valueOf(body.get("utilisationsMax").toString()) : null)
                    .dateDebut(parseDate(body, "dateDebut"))
                    .dateFin(parseDate(body, "dateFin"))
                    .actif(true)
                    .build();

            return ResponseEntity.ok(toDto(codePromoRepository.save(promo)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Erreur création : " + e.getMessage());
        }
    }

    // ── PUT Modifier ──────────────────────────────────────────────────────────
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMMERCIAL')")
    public ResponseEntity<?> modifier(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return codePromoRepository.findById(id)
                .map(promo -> {
                    try {
                        if (body.containsKey("description"))
                            promo.setDescription(getString(body, "description", ""));
                        if (body.containsKey("typeRemise"))
                            promo.setTypeRemise(body.get("typeRemise").toString());
                        if (body.containsKey("valeur"))
                            promo.setValeur(new BigDecimal(body.get("valeur").toString()));
                        if (body.containsKey("montantMinimum"))
                            promo.setMontantMinimum(new BigDecimal(body.get("montantMinimum").toString()));
                        if (body.containsKey("plafondRemise"))
                            promo.setPlafondRemise(body.get("plafondRemise") != null
                                    && !body.get("plafondRemise").toString().isBlank()
                                    ? new BigDecimal(body.get("plafondRemise").toString()) : null);
                        if (body.containsKey("utilisationsMax"))
                            promo.setUtilisationsMax(body.get("utilisationsMax") != null
                                    && !body.get("utilisationsMax").toString().isBlank()
                                    ? Integer.valueOf(body.get("utilisationsMax").toString()) : null);
                        if (body.containsKey("dateDebut"))
                            promo.setDateDebut(parseDate(body, "dateDebut"));
                        if (body.containsKey("dateFin"))
                            promo.setDateFin(parseDate(body, "dateFin"));
                        if (body.containsKey("actif"))
                            promo.setActif(Boolean.parseBoolean(body.get("actif").toString()));
                        return ResponseEntity.ok(toDto(codePromoRepository.save(promo)));
                    } catch (Exception e) {
                        return ResponseEntity.badRequest().body("Erreur modification : " + e.getMessage());
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── PATCH Toggle actif ────────────────────────────────────────────────────
    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMMERCIAL')")
    public ResponseEntity<?> toggleActif(@PathVariable Long id) {
        return codePromoRepository.findById(id)
                .map(promo -> {
                    promo.setActif(!Boolean.TRUE.equals(promo.getActif()));
                    return ResponseEntity.ok(toDto(codePromoRepository.save(promo)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── DELETE ────────────────────────────────────────────────────────────────
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> supprimer(@PathVariable Long id) {
        return codePromoRepository.findById(id)
                .map(promo -> {
                    codePromoRepository.delete(promo);
                    return ResponseEntity.ok().body("Code promo supprimé !");
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── POST Vérifier un code ─────────────────────────────────────────────────
    // Appelé côté client pour valider un code avant de créer la commande
    @PostMapping("/verifier")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMMERCIAL')")
    public ResponseEntity<?> verifier(@RequestBody Map<String, Object> body) {
        try {
            String code = body.get("code").toString().toUpperCase().trim();
            BigDecimal montant = new BigDecimal(body.get("montantCommande").toString());

            CodePromo promo = codePromoRepository.findByCode(code).orElse(null);
            if (promo == null) {
                return ResponseEntity.ok(Map.of("valide", false, "message", "Code promo introuvable"));
            }

            if (!promo.estValide(montant)) {
                String raison = switch (promo.getStatutCalcule()) {
                    case "EXPIRE"    -> "Ce code promo a expiré";
                    case "EPUISE"    -> "Ce code promo a atteint sa limite d'utilisation";
                    case "DESACTIVE" -> "Ce code promo est désactivé";
                    case "PLANIFIE"  -> "Ce code promo n'est pas encore actif";
                    default -> "Montant minimum requis : " + promo.getMontantMinimum() + " TND";
                };
                return ResponseEntity.ok(Map.of("valide", false, "message", raison));
            }

            BigDecimal remise = promo.calculerRemise(montant);
            BigDecimal nouveauTotal = montant.subtract(remise);

            Map<String, Object> result = new HashMap<>();
            result.put("valide", true);
            result.put("code", promo.getCode());
            result.put("description", promo.getDescription());
            result.put("typeRemise", promo.getTypeRemise());
            result.put("valeur", promo.getValeur());
            result.put("remiseCalculee", remise);
            result.put("nouveauTotal", nouveauTotal);
            result.put("message", buildSuccessMessage(promo));
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("valide", false, "message", "Erreur : " + e.getMessage()));
        }
    }

    // ── GET Codes actifs (pour le formulaire de commande) ─────────────────────
    @GetMapping("/actifs")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMMERCIAL')")
    public List<Map<String, Object>> getActifs() {
        return codePromoRepository.findByActifTrue().stream()
                .filter(p -> "ACTIF".equals(p.getStatutCalcule()))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // ════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════

    private Map<String, Object> toDto(CodePromo p) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", p.getId());
        dto.put("code", p.getCode());
        dto.put("description", p.getDescription());
        dto.put("typeRemise", p.getTypeRemise());
        dto.put("valeur", p.getValeur());
        dto.put("montantMinimum", p.getMontantMinimum());
        dto.put("plafondRemise", p.getPlafondRemise());
        dto.put("utilisationsMax", p.getUtilisationsMax());
        dto.put("utilisationsActuelles", p.getUtilisationsActuelles());
        dto.put("actif", p.getActif());
        dto.put("dateDebut", p.getDateDebut() != null ? p.getDateDebut().toString() : null);
        dto.put("dateFin", p.getDateFin() != null ? p.getDateFin().toString() : null);
        dto.put("dateCreation", p.getDateCreation() != null ? p.getDateCreation().toString() : null);
        dto.put("statut", p.getStatutCalcule());
        return dto;
    }

    private String getString(Map<String, Object> body, String key, String def) {
        return body.containsKey(key) && body.get(key) != null ? body.get(key).toString() : def;
    }

    private LocalDateTime parseDate(Map<String, Object> body, String key) {
        if (!body.containsKey(key) || body.get(key) == null || body.get(key).toString().isBlank()) return null;
        try { return LocalDateTime.parse(body.get(key).toString()); } catch (Exception e) { return null; }
    }

    private String buildSuccessMessage(CodePromo promo) {
        String val = "POURCENTAGE".equals(promo.getTypeRemise())
                ? promo.getValeur().stripTrailingZeros().toPlainString() + "%"
                : promo.getValeur().stripTrailingZeros().toPlainString() + " TND";
        return "Code valide ! Réduction de " + val + " appliquée";
    }
}
