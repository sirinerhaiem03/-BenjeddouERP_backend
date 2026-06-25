package com.benjeddou.erp.controller;

import com.benjeddou.erp.model.Fournisseur;
import com.benjeddou.erp.repository.FournisseurRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/fournisseurs")
public class FournisseurController {

    @Autowired
    FournisseurRepository fournisseurRepository;

    @GetMapping("")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMMERCIAL') or hasRole('STOCK')")
    public List<Fournisseur> getTousLesFournisseurs() {
        return fournisseurRepository.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMMERCIAL') or hasRole('STOCK')")
    public ResponseEntity<Fournisseur> getFournisseurParId(@PathVariable Long id) {
        return fournisseurRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMMERCIAL')")
    public ResponseEntity<?> creerFournisseur(@Valid @RequestBody Fournisseur fournisseur) {
        if (fournisseurRepository.existsByEmail(fournisseur.getEmail())) {
            return ResponseEntity.badRequest().body("Erreur : Un fournisseur avec cet email existe déjà !");
        }
        Fournisseur nouveau = fournisseurRepository.save(fournisseur);
        return ResponseEntity.ok(nouveau);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMMERCIAL')")
    public ResponseEntity<?> modifierFournisseur(@PathVariable Long id, @Valid @RequestBody Fournisseur details) {
        return fournisseurRepository.findById(id)
                .map(f -> {
                    f.setNom(details.getNom());
                    f.setEmail(details.getEmail());
                    f.setTelephone(details.getTelephone());
                    f.setAdresse(details.getAdresse());
                    f.setMatriculeFiscale(details.getMatriculeFiscale());
                    return ResponseEntity.ok(fournisseurRepository.save(f));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMMERCIAL')")
    public ResponseEntity<?> supprimerFournisseur(@PathVariable Long id) {
        return fournisseurRepository.findById(id)
                .map(f -> {
                    fournisseurRepository.delete(f);
                    return ResponseEntity.ok().body("Fournisseur supprimé avec succès !");
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
