package com.benjeddou.erp.controller;

import com.benjeddou.erp.model.Produit;
import com.benjeddou.erp.model.StockEntrepot;
import com.benjeddou.erp.repository.ProduitRepository;
import com.benjeddou.erp.repository.StockEntrepotRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.stream.Collectors;


@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/produits")
public class ProduitController {

    @Autowired
    ProduitRepository produitRepository;

    @Autowired
    StockEntrepotRepository stockEntrepotRepository;

    @GetMapping("")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STOCK') or hasRole('COMMERCIAL')")
    public List<Produit> getTousLesProduits() {
        return produitRepository.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STOCK') or hasRole('COMMERCIAL')")
    public ResponseEntity<Produit> getProduitParId(@PathVariable Long id) {
        return produitRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STOCK')")
    public ResponseEntity<?> creerProduit(@Valid @RequestBody Produit produit) {
        if (produitRepository.existsByReference(produit.getReference())) {
            return ResponseEntity.badRequest().body("Erreur : La référence du produit existe déjà !");
        }
        Produit nouveauProduit = produitRepository.save(produit);
        return ResponseEntity.ok(nouveauProduit);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STOCK')")
    public ResponseEntity<?> modifierProduit(@PathVariable Long id, @Valid @RequestBody Produit produitDetails) {
        return produitRepository.findById(id)
                .map(produit -> {
                    produit.setNom(produitDetails.getNom());
                    produit.setDescription(produitDetails.getDescription());
                    produit.setPrixUnitaire(produitDetails.getPrixUnitaire());
                    produit.setPrixAchat(produitDetails.getPrixAchat());
                    produit.setSeuilStockMin(produitDetails.getSeuilStockMin());
                    produit.setCategorie(produitDetails.getCategorie());
                    // On ne modifie pas directement la quantité de stock ici (gérée par les mouvements/inventaires)
                    Produit produitMaj = produitRepository.save(produit);
                    return ResponseEntity.ok(produitMaj);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STOCK')")
    public ResponseEntity<?> supprimerProduit(@PathVariable Long id) {
        return produitRepository.findById(id)
                .map(produit -> {
                    produitRepository.delete(produit);
                    return ResponseEntity.ok().body("Produit supprimé avec succès !");
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/alertes")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STOCK') or hasRole('COMMERCIAL')")
    public List<Produit> getProduitsEnAlerte() {
        return produitRepository.findAll().stream()
                .filter(p -> p.getQuantiteStock() <= p.getSeuilStockMin())
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}/stocks")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STOCK') or hasRole('COMMERCIAL')")
    public List<StockEntrepot> getStocksParProduit(@PathVariable Long id) {
        return stockEntrepotRepository.findByProduitId(id);
    }
}

