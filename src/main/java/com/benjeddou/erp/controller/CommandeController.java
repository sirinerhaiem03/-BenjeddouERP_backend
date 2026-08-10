package com.benjeddou.erp.controller;

import com.benjeddou.erp.model.*;
import com.benjeddou.erp.repository.*;
import com.benjeddou.erp.service.AuditService;
import com.benjeddou.erp.model.AuditLog.ActionAudit;
import com.benjeddou.erp.model.AuditLog.ResultatAudit;
import com.benjeddou.erp.security.services.UserDetailsImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
    @Autowired UtilisateurRepository utilisateurRepository;
    @Autowired AuditService auditService;

    /** Récupère l'utilisateur courant depuis le contexte de sécurité. */
    private UserDetailsImpl currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetailsImpl u) return u;
        return null;
    }

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
    public ResponseEntity<?> creerCommande(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            Long clientId = Long.valueOf(body.get("clientId").toString());

            // Cherche d'abord dans la table clients
            Client client = clientRepository.findById(clientId).orElse(null);

            // Si pas trouvé → cherche dans utilisateurs (ROLE_CLIENT) et auto-crée le Client
            if (client == null) {
                Utilisateur u = utilisateurRepository.findById(clientId)
                    .filter(usr -> usr.getRole() == Role.CLIENT)
                    .orElseThrow(() -> new RuntimeException("Client introuvable avec id: " + clientId));

                // Cherche ou crée la fiche Client par email
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

            // ── Génération automatique de la facture ──────────────────────────
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

            // ── Audit log ────────────────────────────────────────────────
            UserDetailsImpl cu = currentUser();
            auditService.log(ActionAudit.COMMANDE_CREEE, ResultatAudit.SUCCES,
                "Commande créée : " + commande.getNumeroCommande()
                + " | Client ID : " + clientId
                + " | Total TTC : " + montantTtc
                + (codePromoUtilise != null ? " | Promo : " + codePromoUtilise : ""),
                cu != null ? cu.getId() : null,
                cu != null ? cu.getUsername() : "system",
                request, "COMMANDES", commande.getId());

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
    public ResponseEntity<?> changerStatut(@PathVariable Long id, @RequestBody Map<String, String> body,
                                           HttpServletRequest request) {
        return commandeRepository.findById(id)
                .map(commande -> {
                    String nouveauStatut = body.get("statut");
                    if (!List.of("EN_ATTENTE", "PAYEE", "ANNULEE").contains(nouveauStatut)) {
                        return ResponseEntity.badRequest().body("Statut invalide : " + nouveauStatut);
                    }
                    String ancienStatut = commande.getStatut();
                    commande.setStatut(nouveauStatut);
                    commandeRepository.save(commande);
                    UserDetailsImpl cu = currentUser();
                    auditService.log(ActionAudit.STATUT_MODIFIE, ResultatAudit.SUCCES,
                        "Statut commande " + commande.getNumeroCommande()
                        + " : " + ancienStatut + " → " + nouveauStatut,
                        cu != null ? cu.getId() : null,
                        cu != null ? cu.getUsername() : "system",
                        request, "COMMANDES", commande.getId());
                    return ResponseEntity.ok(commande);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── DELETE ────────────────────────────────────────────────────────────────
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMMERCIAL')")
    public ResponseEntity<?> supprimerCommande(@PathVariable Long id, HttpServletRequest request) {
        return commandeRepository.findById(id)
                .map(commande -> {
                    List<LigneCommande> lignes = ligneCommandeRepository.findByCommandeId(id);
                    ligneCommandeRepository.deleteAll(lignes);
                    String numero = commande.getNumeroCommande();
                    commandeRepository.delete(commande);
                    UserDetailsImpl cu = currentUser();
                    auditService.log(ActionAudit.COMMANDE_SUPPRIMEE, ResultatAudit.SUCCES,
                        "Commande supprimée : " + numero,
                        cu != null ? cu.getId() : null,
                        cu != null ? cu.getUsername() : "system",
                        request, "COMMANDES", id);
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
