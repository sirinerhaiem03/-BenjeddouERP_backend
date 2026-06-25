package com.benjeddou.erp.controller;

import com.benjeddou.erp.model.Facture;
import com.benjeddou.erp.repository.FactureRepository;
import com.benjeddou.erp.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/factures")
public class FactureController {

    @Autowired
    FactureRepository factureRepository;

    @Autowired
    EmailService emailService;

    @GetMapping("")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMMERCIAL') or hasRole('COMPTABLE')")
    public List<Facture> getToutesLesFactures() {
        return factureRepository.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMMERCIAL') or hasRole('COMPTABLE')")
    public ResponseEntity<Facture> getFactureParId(@PathVariable Long id) {
        return factureRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/statut")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMMERCIAL') or hasRole('COMPTABLE')")
    public ResponseEntity<?> changerStatut(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return factureRepository.findById(id)
                .map(facture -> {
                    String statut = body.get("statut");
                    if (!List.of("EN_ATTENTE", "PAYEE", "ANNULEE", "IMPAYEE").contains(statut)) {
                        return ResponseEntity.badRequest().body("Statut invalide !");
                    }
                    facture.setStatut(statut);
                    return ResponseEntity.ok(factureRepository.save(facture));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── POST Envoyer la facture par email ─────────────────────────────────────
    @PostMapping("/{id}/envoyer")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMMERCIAL')")
    public ResponseEntity<?> envoyerParEmail(@PathVariable Long id) {
        return factureRepository.findById(id)
                .map(facture -> {
                    try {
                        emailService.envoyerFactureParEmail(facture);
                        return ResponseEntity.ok(Map.of(
                            "message", "Facture envoyée par email à " + facture.getCommande().getClient().getEmail()
                        ));
                    } catch (Exception e) {
                        return ResponseEntity.internalServerError()
                                .body("Erreur envoi email : " + e.getMessage());
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── POST Envoyer un rappel pour facture impayée ───────────────────────────
    @PostMapping("/{id}/rappel")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMMERCIAL') or hasRole('COMPTABLE')")
    public ResponseEntity<?> envoyerRappelImpayee(@PathVariable Long id) {
        return factureRepository.findById(id)
                .map(facture -> {
                    try {
                        emailService.envoyerRappelImpayee(facture);
                        return ResponseEntity.ok(Map.of(
                            "message", "Rappel envoyé à " + facture.getCommande().getClient().getEmail()
                        ));
                    } catch (Exception e) {
                        return ResponseEntity.internalServerError()
                                .body("Erreur envoi rappel : " + e.getMessage());
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> supprimerFacture(@PathVariable Long id) {
        return factureRepository.findById(id)
                .map(facture -> {
                    factureRepository.delete(facture);
                    return ResponseEntity.ok().body("Facture supprimée avec succès !");
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
