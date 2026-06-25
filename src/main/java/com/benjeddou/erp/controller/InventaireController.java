package com.benjeddou.erp.controller;

import com.benjeddou.erp.model.*;
import com.benjeddou.erp.repository.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/inventaires")
public class InventaireController {

    @Autowired
    InventaireRepository inventaireRepository;

    @Autowired
    LigneInventaireRepository ligneInventaireRepository;

    @Autowired
    EntrepotRepository entrepotRepository;

    @Autowired
    ProduitRepository produitRepository;

    @Autowired
    StockEntrepotRepository stockEntrepotRepository;

    @Autowired
    MouvementStockRepository mouvementStockRepository;

    @GetMapping("")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STOCK')")
    public List<Inventaire> getTousLesInventaires() {
        return inventaireRepository.findAllByOrderByDateInventaireDesc();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STOCK')")
    public ResponseEntity<?> getInventaireParId(@PathVariable Long id) {
        Optional<Inventaire> invOpt = inventaireRepository.findById(id);
        if (invOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        List<LigneInventaire> lignes = ligneInventaireRepository.findByInventaireId(id);
        // On renvoie un wrapper contenant l'inventaire et ses lignes
        class InventaireWrapper {
            public Inventaire inventaire;
            public List<LigneInventaire> lignes;
            public InventaireWrapper(Inventaire inv, List<LigneInventaire> l) {
                this.inventaire = inv;
                this.lignes = l;
            }
        }
        return ResponseEntity.ok(new InventaireWrapper(invOpt.get(), lignes));
    }

    @PostMapping("")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STOCK')")
    @Transactional
    public ResponseEntity<?> creerInventaire(@RequestBody Inventaire req) {
        Entrepot entrepot = entrepotRepository.findById(req.getEntrepot().getId())
                .orElse(null);
        if (entrepot == null) {
            return ResponseEntity.badRequest().body("Erreur : Entrepôt non trouvé !");
        }

        // Créer l'entête
        String code = "INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Inventaire inventaire = Inventaire.builder()
                .code(code)
                .entrepot(entrepot)
                .statut(StatutInventaire.EN_COURS)
                .description(req.getDescription())
                .dateInventaire(LocalDateTime.now())
                .build();
        
        Inventaire savedInventaire = inventaireRepository.save(inventaire);

        // Auto-générer les lignes pour tous les produits du catalogue
        List<Produit> produits = produitRepository.findAll();
        for (Produit prod : produits) {
            // Récupérer la quantité théorique en stock dans cet entrepôt
            int quantiteTheorique = stockEntrepotRepository.findByProduitIdAndEntrepotId(prod.getId(), entrepot.getId())
                    .map(StockEntrepot::getQuantite)
                    .orElse(0);

            LigneInventaire ligne = LigneInventaire.builder()
                    .inventaire(savedInventaire)
                    .produit(prod)
                    .quantiteTheorique(quantiteTheorique)
                    .quantitePhysique(quantiteTheorique) // Par défaut, préremplir avec la théorie
                    .build();
            
            ligneInventaireRepository.save(ligne);
        }

        return ResponseEntity.ok(savedInventaire);
    }

    @PutMapping("/{id}/lignes")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STOCK')")
    @Transactional
    public ResponseEntity<?> enregistrerSaisiesPhysiques(@PathVariable Long id, @RequestBody List<LigneInventaire> lignesSaisies) {
        Optional<Inventaire> invOpt = inventaireRepository.findById(id);
        if (invOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Inventaire inventaire = invOpt.get();
        if (inventaire.getStatut() == StatutInventaire.VALIDE) {
            return ResponseEntity.badRequest().body("Erreur : L'inventaire est déjà validé et ne peut plus être modifié !");
        }

        for (LigneInventaire ligneSaisie : lignesSaisies) {
            ligneInventaireRepository.findById(ligneSaisie.getId())
                    .ifPresent(ligne -> {
                        ligne.setQuantitePhysique(ligneSaisie.getQuantitePhysique());
                        ligneInventaireRepository.save(ligne);
                    });
        }

        return ResponseEntity.ok("Saisies enregistrées avec succès !");
    }

    @PostMapping("/{id}/valider")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STOCK')")
    @Transactional
    public ResponseEntity<?> validerInventaire(@PathVariable Long id) {
        Optional<Inventaire> invOpt = inventaireRepository.findById(id);
        if (invOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Inventaire inventaire = invOpt.get();
        if (inventaire.getStatut() == StatutInventaire.VALIDE) {
            return ResponseEntity.badRequest().body("Erreur : L'inventaire est déjà validé !");
        }

        List<LigneInventaire> lignes = ligneInventaireRepository.findByInventaireId(id);

        for (LigneInventaire ligne : lignes) {
            int ecart = ligne.getQuantitePhysique() - ligne.getQuantiteTheorique();
            
            // Si un écart physique est constaté, on met à jour la base
            StockEntrepot stock = stockEntrepotRepository.findByProduitIdAndEntrepotId(ligne.getProduit().getId(), inventaire.getEntrepot().getId())
                    .orElse(StockEntrepot.builder()
                            .produit(ligne.getProduit())
                            .entrepot(inventaire.getEntrepot())
                            .quantite(0)
                            .build());

            stock.setQuantite(ligne.getQuantitePhysique());
            stockEntrepotRepository.save(stock);

            // Mettre à jour la quantité totale dans le produit
            List<StockEntrepot> tousLesStocks = stockEntrepotRepository.findByProduitId(ligne.getProduit().getId());
            int quantiteTotale = tousLesStocks.stream().mapToInt(StockEntrepot::getQuantite).sum();
            ligne.getProduit().setQuantiteStock(quantiteTotale);
            produitRepository.save(ligne.getProduit());

            if (ecart != 0) {
                // Générer un mouvement de correction
                MouvementStock correction = MouvementStock.builder()
                        .produit(ligne.getProduit())
                        .entrepot(inventaire.getEntrepot())
                        .typeMouvement(TypeMouvement.CORRECTION)
                        .quantite(ligne.getQuantitePhysique()) // Le mouvement enregistre la valeur finale absolue corrigée
                        .description("Correction suite inventaire " + inventaire.getCode() + " (Écart: " + (ecart > 0 ? "+" : "") + ecart + ")")
                        .dateMouvement(LocalDateTime.now())
                        .build();
                
                mouvementStockRepository.save(correction);
            }
        }

        inventaire.setStatut(StatutInventaire.VALIDE);
        inventaireRepository.save(inventaire);

        return ResponseEntity.ok("Inventaire validé avec succès. Les stocks ont été ajustés.");
    }
}
