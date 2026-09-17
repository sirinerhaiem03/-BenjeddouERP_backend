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
@RequestMapping("/api/devis")
public class DevisController {

    @Autowired DevisRepository devisRepository;
    @Autowired LigneDevisRepository ligneDevisRepository;
    @Autowired ClientRepository clientRepository;
    @Autowired ProduitRepository produitRepository;
    @Autowired CommandeRepository commandeRepository;
    @Autowired LigneCommandeRepository ligneCommandeRepository;
    @Autowired UtilisateurRepository utilisateurRepository;

    // ── GET All ───────────────────────────────────────────────────────────────
    @GetMapping("")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMMERCIAL')")
    public List<Devis> getTousLesDevis() {
        return devisRepository.findAll();
    }

    // ── GET One ───────────────────────────────────────────────────────────────
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMMERCIAL')")
    public ResponseEntity<Devis> getDevisParId(@PathVariable Long id) {
        return devisRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── GET Lignes ────────────────────────────────────────────────────────────
    @GetMapping("/{id}/lignes")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMMERCIAL')")
    public List<LigneDevis> getLignesDevis(@PathVariable Long id) {
        return ligneDevisRepository.findByDevisId(id);
    }

    // ── POST Create ──────────────────────────────────────────────────────────
    // Body: { clientId, notes, dateValidite, lignes: [{produitId, quantite, remise}] }
    @PostMapping("")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMMERCIAL')")
    public ResponseEntity<?> creerDevis(@RequestBody Map<String, Object> body) {
        try {
            Long clientId = Long.valueOf(body.get("clientId").toString());

            // Cherche d'abord dans la table clients
            Client client = clientRepository.findById(clientId).orElse(null);

            // Si pas trouvé → cherche dans utilisateurs (ROLE_CLIENT) et auto-crée le Client
            if (client == null) {
                Utilisateur u = utilisateurRepository.findById(clientId)
                    .filter(usr -> usr.getRole() == Role.CLIENT)
                    .orElseThrow(() -> new RuntimeException("Client introuvable avec id: " + clientId));

                client = clientRepository.findByEmail(u.getEmail()).orElseGet(() -> {
                    String nomClient = (u.getNom() != null && !u.getNom().isBlank())
                        ? u.getNom()
                        : (u.getSociete() != null && !u.getSociete().isBlank() ? u.getSociete() : u.getNomUtilisateur());
                    return clientRepository.save(Client.builder()
                        .nom(nomClient)
                        .email(u.getEmail())
                        .telephone(u.getTelephone())
                        .adresse(u.getAdresse())
                        .build());
                });
            }

            // Numéro unique
            String numero = "DEV-" + DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDateTime.now())
                    + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

            // Date de validité (30 jours par défaut)
            LocalDateTime dateValidite = LocalDateTime.now().plusDays(30);
            if (body.containsKey("dateValidite") && body.get("dateValidite") != null) {
                try {
                    dateValidite = LocalDateTime.parse(body.get("dateValidite").toString());
                } catch (Exception ignored) {}
            }

            String notes = body.containsKey("notes") ? body.get("notes").toString() : "";

            Devis devis = Devis.builder()
                    .numeroDevis(numero)
                    .client(client)
                    .statut("BROUILLON")
                    .montantTotal(BigDecimal.ZERO)
                    .dateValidite(dateValidite)
                    .notes(notes)
                    .build();
            devis = devisRepository.save(devis);

            // Lignes
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> lignesData = (List<Map<String, Object>>) body.get("lignes");
            BigDecimal total = BigDecimal.ZERO;

            for (Map<String, Object> ld : lignesData) {
                Long produitId = Long.valueOf(ld.get("produitId").toString());
                Integer quantite = Integer.valueOf(ld.get("quantite").toString());
                BigDecimal remise = ld.containsKey("remise")
                        ? new BigDecimal(ld.get("remise").toString())
                        : BigDecimal.ZERO;

                Produit produit = produitRepository.findById(produitId)
                        .orElseThrow(() -> new RuntimeException("Produit introuvable"));

                BigDecimal prixUnitaire = produit.getPrixUnitaire();

                LigneDevis ligne = LigneDevis.builder()
                        .devis(devis)
                        .produit(produit)
                        .quantite(quantite)
                        .prixUnitaire(prixUnitaire)
                        .remise(remise)
                        .build();
                ligneDevisRepository.save(ligne);

                BigDecimal montantLigne = prixUnitaire
                        .multiply(BigDecimal.valueOf(quantite))
                        .multiply(BigDecimal.ONE.subtract(remise.divide(BigDecimal.valueOf(100))));
                total = total.add(montantLigne);
            }

            devis.setMontantTotal(total);
            devisRepository.save(devis);
            return ResponseEntity.ok(devis);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Erreur création devis : " + e.getMessage());
        }
    }

    // ── PUT Statut ────────────────────────────────────────────────────────────
    // BROUILLON → ENVOYE → ACCEPTE / REFUSE
    @PutMapping("/{id}/statut")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMMERCIAL')")
    public ResponseEntity<?> changerStatut(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return devisRepository.findById(id)
                .map(devis -> {
                    String s = body.get("statut");
                    if (!List.of("DEMANDE_CLIENT", "BROUILLON", "ENVOYE", "ACCEPTE", "REFUSE").contains(s)) {
                        return ResponseEntity.badRequest().body("Statut invalide : " + s);
                    }
                    devis.setStatut(s);
                    return ResponseEntity.ok(devisRepository.save(devis));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── POST Convertir en Commande ────────────────────────────────────────────
    @PostMapping("/{id}/convertir")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMMERCIAL')")
    public ResponseEntity<?> convertirEnCommande(@PathVariable Long id) {
        try {
            Devis devis = devisRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Devis introuvable"));

            if (!"ACCEPTE".equals(devis.getStatut())) {
                return ResponseEntity.badRequest().body("Le devis doit être ACCEPTE pour être converti");
            }

            // Créer la commande
            String numeroCmd = "CMD-" + DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDateTime.now())
                    + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

            Commande commande = Commande.builder()
                    .numeroCommande(numeroCmd)
                    .client(devis.getClient())
                    .statut("EN_ATTENTE")
                    .montantTotal(devis.getMontantTotal())
                    .build();
            commande = commandeRepository.save(commande);

            // Copier les lignes du devis → commande
            List<LigneDevis> lignesDevis = ligneDevisRepository.findByDevisId(devis.getId());
            for (LigneDevis ld : lignesDevis) {
                LigneCommande lc = LigneCommande.builder()
                        .commande(commande)
                        .produit(ld.getProduit())
                        .quantite(ld.getQuantite())
                        .prixUnitaire(ld.getPrixUnitaire())
                        .remise(ld.getRemise())
                        .build();
                ligneCommandeRepository.save(lc);
            }

            return ResponseEntity.ok(Map.of(
                    "message", "Devis converti en commande avec succès !",
                    "commande", commande
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Erreur conversion : " + e.getMessage());
        }
    }

    // ── DELETE ────────────────────────────────────────────────────────────────
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMMERCIAL')")
    public ResponseEntity<?> supprimerDevis(@PathVariable Long id) {
        return devisRepository.findById(id)
                .map(devis -> {
                    List<LigneDevis> lignes = ligneDevisRepository.findByDevisId(id);
                    ligneDevisRepository.deleteAll(lignes);
                    devisRepository.delete(devis);
                    return ResponseEntity.ok().body("Devis supprimé !");
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
