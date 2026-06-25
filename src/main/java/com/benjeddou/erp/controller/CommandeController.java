package com.benjeddou.erp.controller;

import com.benjeddou.erp.model.*;
import com.benjeddou.erp.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/commandes")
public class CommandeController {

    @Autowired CommandeRepository commandeRepository;
    @Autowired LigneCommandeRepository ligneCommandeRepository;
    @Autowired ClientRepository clientRepository;
    @Autowired ProduitRepository produitRepository;
    @Autowired FactureRepository factureRepository;
    @Autowired CodePromoRepository codePromoRepository;

    // ── GET All ──────────────────────────────────────────────────────────────
    @GetMapping("")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMMERCIAL') or hasRole('COMPTABLE')")
    public List<Commande> getToutesLesCommandes() {
        return commandeRepository.findAll();
    }

    // ── GET One ──────────────────────────────────────────────────────────────
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMMERCIAL') or hasRole('COMPTABLE')")
    public ResponseEntity<Commande> getCommandeParId(@PathVariable Long id) {
        return commandeRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── GET Lignes ────────────────────────────────────────────────────────────
    @GetMapping("/{id}/lignes")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMMERCIAL') or hasRole('COMPTABLE')")
    public List<LigneCommande> getLignesCommande(@PathVariable Long id) {
        return ligneCommandeRepository.findByCommandeId(id);
    }

    // ── POST Create ───────────────────────────────────────────────────────────
    // Body: { clientId, lignes: [{produitId, quantite, remise}] }
    @PostMapping("")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMMERCIAL')")
    public ResponseEntity<?> creerCommande(@RequestBody Map<String, Object> body) {
        try {
            Long clientId = Long.valueOf(body.get("clientId").toString());
            Client client = clientRepository.findById(clientId)
                    .orElseThrow(() -> new RuntimeException("Client introuvable"));

            // Générer numéro commande unique
            String numero = "CMD-" + DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDateTime.now())
                    + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

            Commande commande = Commande.builder()
                    .numeroCommande(numero)
                    .client(client)
                    .statut("EN_ATTENTE")
                    .montantTotal(BigDecimal.ZERO)
                    .build();
            commande = commandeRepository.save(commande);

            // Calcul lignes
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> lignesData = (List<Map<String, Object>>) body.get("lignes");
            BigDecimal total = BigDecimal.ZERO;

            for (Map<String, Object> ligneData : lignesData) {
                Long produitId = Long.valueOf(ligneData.get("produitId").toString());
                Integer quantite = Integer.valueOf(ligneData.get("quantite").toString());
                BigDecimal remise = ligneData.containsKey("remise")
                        ? new BigDecimal(ligneData.get("remise").toString())
                        : BigDecimal.ZERO;

                Produit produit = produitRepository.findById(produitId)
                        .orElseThrow(() -> new RuntimeException("Produit introuvable: " + produitId));

                BigDecimal prixUnitaire = produit.getPrixUnitaire();

                LigneCommande ligne = LigneCommande.builder()
                        .commande(commande)
                        .produit(produit)
                        .quantite(quantite)
                        .prixUnitaire(prixUnitaire)
                        .remise(remise)
                        .build();
                ligneCommandeRepository.save(ligne);

                BigDecimal montantLigne = prixUnitaire
                        .multiply(BigDecimal.valueOf(quantite))
                        .multiply(BigDecimal.ONE.subtract(remise.divide(BigDecimal.valueOf(100))));
                total = total.add(montantLigne);
            }

            // Application du code promo
            BigDecimal remisePromo = BigDecimal.ZERO;
            String codePromoUtilise = null;
            if (body.containsKey("codePromo") && body.get("codePromo") != null
                    && !body.get("codePromo").toString().isBlank()) {
                String codeStr = body.get("codePromo").toString().toUpperCase().trim();
                CodePromo promo = codePromoRepository.findByCode(codeStr).orElse(null);
                if (promo != null && promo.estValide(total)) {
                    remisePromo = promo.calculerRemise(total);
                    promo.setUtilisationsActuelles(promo.getUtilisationsActuelles() + 1);
                    codePromoRepository.save(promo);
                    codePromoUtilise = codeStr;
                }
            }

            BigDecimal totalFinal = total.subtract(remisePromo);
            commande.setMontantTotal(totalFinal);
            if (codePromoUtilise != null) {
                commande.setCodePromoApplique(codePromoUtilise);
                commande.setRemisePromo(remisePromo);
            }
            commandeRepository.save(commande);

            // ── Génération automatique de la facture ──────────────────
            String numeroFacture = "FAC-"
                    + DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDateTime.now())
                    + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

            BigDecimal tauxTva    = new BigDecimal("0.19");
            BigDecimal montantTva = totalFinal.multiply(tauxTva)
                                              .setScale(3, java.math.RoundingMode.HALF_UP);
            BigDecimal montantTtc = totalFinal.add(montantTva);

            Facture facture = Facture.builder()
                    .numeroFacture(numeroFacture)
                    .commande(commande)
                    .montantTotal(montantTtc)
                    .montantTva(montantTva)
                    .statut("EN_ATTENTE")
                    .dateEcheance(LocalDateTime.now().plusDays(30))
                    .signatureNumerique("SIG-" + UUID.randomUUID().toString().toUpperCase())
                    .build();
            factureRepository.save(facture);
            // ─────────────────────────────────────────────────────────

            return ResponseEntity.ok(Map.of(
                "commande", commande,
                "facture",  facture,
                "message",  "Commande créée et facture " + numeroFacture + " générée automatiquement ✓"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Erreur création commande : " + e.getMessage());
        }
    }

    // ── PUT Statut ────────────────────────────────────────────────────────────
    @PutMapping("/{id}/statut")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMMERCIAL')")
    public ResponseEntity<?> changerStatut(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return commandeRepository.findById(id)
                .map(commande -> {
                    String nouveauStatut = body.get("statut");
                    if (!List.of("EN_ATTENTE", "PAYEE", "ANNULEE").contains(nouveauStatut)) {
                        return ResponseEntity.badRequest().body("Statut invalide : " + nouveauStatut);
                    }
                    commande.setStatut(nouveauStatut);
                    return ResponseEntity.ok(commandeRepository.save(commande));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── DELETE ────────────────────────────────────────────────────────────────
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMMERCIAL')")
    public ResponseEntity<?> supprimerCommande(@PathVariable Long id) {
        return commandeRepository.findById(id)
                .map(commande -> {
                    // Supprimer les lignes d'abord
                    List<LigneCommande> lignes = ligneCommandeRepository.findByCommandeId(id);
                    ligneCommandeRepository.deleteAll(lignes);
                    commandeRepository.delete(commande);
                    return ResponseEntity.ok().body("Commande supprimée avec succès !");
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── POST Générer Facture ──────────────────────────────────────────────────
    @PostMapping("/{id}/facture")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMMERCIAL')")
    public ResponseEntity<?> genererFacture(@PathVariable Long id) {
        return commandeRepository.findById(id)
                .map(commande -> {
                    // Vérifier si une facture existe déjà
                    if (factureRepository.findByCommandeId(id).isPresent()) {
                        return ResponseEntity.badRequest().body("Une facture existe déjà pour cette commande !");
                    }
                    // Générer numéro facture
                    String numeroFacture = "FAC-" + DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDateTime.now())
                            + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

                    // Calcul TVA 19%
                    BigDecimal tauxTva = new BigDecimal("0.19");
                    BigDecimal montantTva = commande.getMontantTotal().multiply(tauxTva).setScale(3, java.math.RoundingMode.HALF_UP);
                    BigDecimal montantTtc = commande.getMontantTotal().add(montantTva);

                    Facture facture = Facture.builder()
                            .numeroFacture(numeroFacture)
                            .commande(commande)
                            .montantTotal(montantTtc)
                            .montantTva(montantTva)
                            .statut("EN_ATTENTE")
                            .dateEcheance(LocalDateTime.now().plusDays(30))
                            .signatureNumerique("SIG-" + UUID.randomUUID().toString().toUpperCase())
                            .build();

                    Facture savedFacture = factureRepository.save(facture);

                    // Mettre à jour la commande en PAYEE
                    commande.setStatut("PAYEE");
                    commandeRepository.save(commande);

                    return ResponseEntity.ok(savedFacture);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
