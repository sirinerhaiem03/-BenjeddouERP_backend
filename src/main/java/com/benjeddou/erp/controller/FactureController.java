package com.benjeddou.erp.controller;

import com.benjeddou.erp.model.Facture;
import com.benjeddou.erp.model.AuditLog.ActionAudit;
import com.benjeddou.erp.model.AuditLog.ResultatAudit;
import com.benjeddou.erp.repository.FactureRepository;
import com.benjeddou.erp.security.services.UserDetailsImpl;
import com.benjeddou.erp.service.AuditService;
import com.benjeddou.erp.service.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

    @Autowired
    com.benjeddou.erp.repository.ClientRepository clientRepository;

    @Autowired
    com.benjeddou.erp.repository.CommandeRepository commandeRepository;

    @Autowired
    AuditService auditService;

    private UserDetailsImpl currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetailsImpl u) return u;
        return null;
    }

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
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMMERCIAL') or hasRole('COMPTABLE') or hasRole('CLIENT')")
    public ResponseEntity<?> changerStatut(@PathVariable Long id, @RequestBody Map<String, String> body,
                                           HttpServletRequest request) {
        return factureRepository.findById(id)
                .map(facture -> {
                    String statut = body.get("statut");
                    if (!List.of("EN_ATTENTE", "PAYEE", "ANNULEE", "IMPAYEE", "EN_RETARD").contains(statut)) {
                        return ResponseEntity.badRequest().body("Statut invalide !");
                    }
                    String ancien = facture.getStatut();
                    facture.setStatut(statut);

                    // Mettre à jour la commande associée et le solde impayé du client en BDD
                    if ("PAYEE".equals(statut) && facture.getCommande() != null) {
                        facture.getCommande().setStatut("PAYEE");
                        commandeRepository.save(facture.getCommande());

                        if (facture.getCommande().getClient() != null) {
                            var c = facture.getCommande().getClient();
                            clientRepository.save(c);
                        }
                    }

                    factureRepository.save(facture);
                    UserDetailsImpl cu = currentUser();
                    auditService.log(ActionAudit.FACTURE_STATUT_MODIFIE, ResultatAudit.SUCCES,
                        "Statut facture " + facture.getNumeroFacture()
                        + " : " + ancien + " → " + statut,
                        cu != null ? cu.getId() : null,
                        cu != null ? cu.getUsername() : "system",
                        request, "FACTURES", facture.getId());
                    return ResponseEntity.ok(facture);
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
    public ResponseEntity<?> supprimerFacture(@PathVariable Long id, HttpServletRequest request) {
        return factureRepository.findById(id)
                .map(facture -> {
                    String numero = facture.getNumeroFacture();
                    factureRepository.delete(facture);
                    UserDetailsImpl cu = currentUser();
                    auditService.log(ActionAudit.FACTURE_SUPPRIMEE, ResultatAudit.SUCCES,
                        "Facture supprimée : " + numero,
                        cu != null ? cu.getId() : null,
                        cu != null ? cu.getUsername() : "system",
                        request, "FACTURES", id);
                    return ResponseEntity.ok().body("Facture supprimée avec succès !");
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/ocr-import")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMMERCIAL')")
    public ResponseEntity<?> importerFactureOcr(@RequestBody Map<String, Object> body) {
        try {
            String fournisseur = (String) body.get("fournisseur");
            String numeroFacture = (String) body.get("numeroFacture");
            Double montantTtc = Double.valueOf(body.get("montantTtc").toString());
            Double montantHt = Double.valueOf(body.get("montantHt").toString());
            Double montantTva = Double.valueOf(body.get("montantTva").toString());

            // 1. Chercher ou créer le fournisseur (client)
            com.benjeddou.erp.model.Client client = clientRepository.findByNom(fournisseur).orElseGet(() -> {
                com.benjeddou.erp.model.Client newClient = new com.benjeddou.erp.model.Client();
                newClient.setNom(fournisseur);
                newClient.setEmail(fournisseur.toLowerCase().replace(" ", "") + "@example.com");
                return clientRepository.save(newClient);
            });

            // 2. Créer une commande associée
            com.benjeddou.erp.model.Commande commande = new com.benjeddou.erp.model.Commande();
            commande.setClient(client);
            commande.setNumeroCommande("CMD-OCR-" + System.currentTimeMillis());
            commande.setMontantTotal(java.math.BigDecimal.valueOf(montantTtc));
            commande.setStatut("PAYEE");
            commande = commandeRepository.save(commande);

            // 3. Créer la Facture
            Facture facture = new Facture();
            facture.setNumeroFacture(numeroFacture);
            facture.setCommande(commande);
            facture.setMontantTotal(java.math.BigDecimal.valueOf(montantTtc));
            facture.setMontantTva(java.math.BigDecimal.valueOf(montantTva));
            facture.setStatut("EN_ATTENTE");
            
            factureRepository.save(facture);
            
            return ResponseEntity.ok(Map.of("message", "Facture enregistrée avec succès !", "factureId", facture.getId()));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Erreur lors de l'enregistrement de la facture OCR : " + e.getMessage());
        }
    }
}
