package com.benjeddou.erp.controller;

import com.benjeddou.erp.model.Entrepot;
import com.benjeddou.erp.model.StockEntrepot;
import com.benjeddou.erp.repository.EntrepotRepository;
import com.benjeddou.erp.repository.StockEntrepotRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/entrepots")
public class EntrepotController {

    @Autowired
    EntrepotRepository entrepotRepository;

    @Autowired
    StockEntrepotRepository stockEntrepotRepository;

    @GetMapping("")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STOCK') or hasRole('COMMERCIAL')")
    public List<Entrepot> getTousLesEntrepots() {
        return entrepotRepository.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STOCK') or hasRole('COMMERCIAL')")
    public ResponseEntity<Entrepot> getEntrepotParId(@PathVariable Long id) {
        return entrepotRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STOCK')")
    public ResponseEntity<?> creerEntrepot(@Valid @RequestBody Entrepot entrepot) {
        if (entrepotRepository.existsByCode(entrepot.getCode())) {
            return ResponseEntity.badRequest().body("Erreur : Le code de l'entrepôt existe déjà !");
        }
        Entrepot nouvelEntrepot = entrepotRepository.save(entrepot);
        return ResponseEntity.ok(nouvelEntrepot);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STOCK')")
    public ResponseEntity<?> modifierEntrepot(@PathVariable Long id, @Valid @RequestBody Entrepot entrepotDetails) {
        return entrepotRepository.findById(id)
                .map(entrepot -> {
                    entrepot.setNom(entrepotDetails.getNom());
                    entrepot.setAdresse(entrepotDetails.getAdresse());
                    entrepot.setDescription(entrepotDetails.getDescription());
                    Entrepot entrepotMaj = entrepotRepository.save(entrepot);
                    return ResponseEntity.ok(entrepotMaj);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STOCK')")
    public ResponseEntity<?> supprimerEntrepot(@PathVariable Long id) {
        return entrepotRepository.findById(id)
                .map(entrepot -> {
                    entrepotRepository.delete(entrepot);
                    return ResponseEntity.ok().body("Entrepôt supprimé avec succès !");
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/stocks")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STOCK') or hasRole('COMMERCIAL')")
    public List<StockEntrepot> getStocksParEntrepot(@PathVariable Long id) {
        return stockEntrepotRepository.findByEntrepotId(id);
    }
}
