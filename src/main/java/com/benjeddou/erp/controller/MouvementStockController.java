package com.benjeddou.erp.controller;

import com.benjeddou.erp.model.*;
import com.benjeddou.erp.repository.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/mouvements")
public class MouvementStockController {

    @Autowired
    MouvementStockRepository mouvementStockRepository;

    @Autowired
    ProduitRepository produitRepository;

    @Autowired
    EntrepotRepository entrepotRepository;

    @Autowired
    StockEntrepotRepository stockEntrepotRepository;

    @Autowired
    UtilisateurRepository utilisateurRepository;

    @GetMapping("")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STOCK') or hasRole('COMMERCIAL')")
    public List<MouvementStock> getTousLesMouvements() {
        return mouvementStockRepository.findAllByOrderByDateMouvementDesc();
    }

    @GetMapping("/produit/{produitId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STOCK') or hasRole('COMMERCIAL')")
    public List<MouvementStock> getMouvementsParProduit(@PathVariable Long produitId) {
        return mouvementStockRepository.findByProduitId(produitId);
    }


    @PostMapping("")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STOCK')")
    @Transactional
    public ResponseEntity<?> creerMouvement(@Valid @RequestBody MouvementStock mouvement) {
        // Validation existence produit et entrepot
        Produit produit = produitRepository.findById(mouvement.getProduit().getId())
                .orElse(null);
        if (produit == null) {
            return ResponseEntity.badRequest().body("Erreur : Produit non trouvé !");
        }

        Entrepot entrepot = entrepotRepository.findById(mouvement.getEntrepot().getId())
                .orElse(null);
        if (entrepot == null) {
            return ResponseEntity.badRequest().body("Erreur : Entrepôt non trouvé !");
        }

        // Récupérer ou initialiser le stock dans cet entrepôt
        StockEntrepot stock = stockEntrepotRepository.findByProduitIdAndEntrepotId(produit.getId(), entrepot.getId())
                .orElse(StockEntrepot.builder()
                        .produit(produit)
                        .entrepot(entrepot)
                        .quantite(0)
                        .build());

        int quantiteMouvement = mouvement.getQuantite();
        if (quantiteMouvement <= 0) {
            return ResponseEntity.badRequest().body("Erreur : La quantité doit être supérieure à 0 !");
        }

        // Adapter le calcul selon le type
        if (mouvement.getTypeMouvement() == TypeMouvement.ENTREE) {
            stock.setQuantite(stock.getQuantite() + quantiteMouvement);
        } else if (mouvement.getTypeMouvement() == TypeMouvement.SORTIE) {
            if (stock.getQuantite() < quantiteMouvement) {
                return ResponseEntity.badRequest().body("Erreur : Stock insuffisant dans cet entrepôt ! (Disponible: " + stock.getQuantite() + ")");
            }
            stock.setQuantite(stock.getQuantite() - quantiteMouvement);
        } else if (mouvement.getTypeMouvement() == TypeMouvement.CORRECTION) {
            // Pour la correction, la quantité du mouvement représente le nouvel état cible directe ou l'écart
            // On considère que quantite est la nouvelle valeur absolue dans l'entrepôt
            stock.setQuantite(quantiteMouvement);
        } else if (mouvement.getTypeMouvement() == TypeMouvement.TRANSFERT) {
            // Note: Pour un transfert simple, on soustrait de la source. La création du mouvement d'entrée sur la destination sera faite séparément.
            if (stock.getQuantite() < quantiteMouvement) {
                return ResponseEntity.badRequest().body("Erreur : Stock insuffisant pour le transfert !");
            }
            stock.setQuantite(stock.getQuantite() - quantiteMouvement);
        }

        // Sauvegarder la position de stock
        stockEntrepotRepository.save(stock);

        // Mettre à jour la quantité totale dans le produit
        List<StockEntrepot> tousLesStocks = stockEntrepotRepository.findByProduitId(produit.getId());
        int quantiteTotale = tousLesStocks.stream().mapToInt(StockEntrepot::getQuantite).sum();
        produit.setQuantiteStock(quantiteTotale);
        produitRepository.save(produit);

        // Sauvegarder le mouvement
        mouvement.setProduit(produit);
        mouvement.setEntrepot(entrepot);
        MouvementStock nouveauMouvement = mouvementStockRepository.save(mouvement);

        return ResponseEntity.ok(nouveauMouvement);
    }
}
