package com.benjeddou.erp.controller;

import com.benjeddou.erp.model.*;
import com.benjeddou.erp.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/achats")
public class AchatController {

    @Autowired CommandeAchatRepository commandeAchatRepo;
    @Autowired ReceptionLivraisonRepository receptionRepo;
    @Autowired FournisseurRepository fournisseurRepo;
    @Autowired ProduitRepository produitRepo;
    @Autowired EntrepotRepository entrepotRepo;
    @Autowired MouvementStockRepository mouvementRepo;
    @Autowired StockEntrepotRepository stockEntrepotRepo;
    @Autowired FactureRepository factureRepo;
    @Autowired CommandeRepository commandeRepo;
    @Autowired JavaMailSender mailSender;

    // ══════════════════════════════════════════════════════════
    // BONS DE COMMANDE ACHAT
    // ══════════════════════════════════════════════════════════

    @GetMapping("/commandes")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ACHAT') or hasRole('COMPTABLE') or hasRole('COMMERCIAL')")
    public List<CommandeAchat> getToutesCommandesAchat() {
        return commandeAchatRepo.findAll();
    }

    @GetMapping("/commandes/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ACHAT')")
    public ResponseEntity<?> getCommandeAchat(@PathVariable Long id) {
        return commandeAchatRepo.findById(id).map(cmd -> {
            Map<String, Object> result = new HashMap<>();
            result.put("id", cmd.getId());
            result.put("numeroCommande", cmd.getNumeroCommande());
            result.put("statut", cmd.getStatut());
            result.put("montantTotal", cmd.getMontantTotal());
            result.put("notes", cmd.getNotes());
            result.put("dateCommande", cmd.getDateCommande());
            if (cmd.getFournisseur() != null) {
                result.put("fournisseur", Map.of(
                    "id", cmd.getFournisseur().getId(),
                    "nom", cmd.getFournisseur().getNom()
                ));
            }
            return ResponseEntity.ok(result);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/commandes")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ACHAT')")
    public ResponseEntity<?> creerCommandeAchat(@RequestBody Map<String, Object> body) {
        try {
            Long fournisseurId = Long.valueOf(body.get("fournisseurId").toString());
            Fournisseur fournisseur = fournisseurRepo.findById(fournisseurId)
                    .orElseThrow(() -> new RuntimeException("Fournisseur introuvable"));

            String numero = "CA-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                    + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

            CommandeAchat cmd = CommandeAchat.builder()
                    .numeroCommande(numero)
                    .fournisseur(fournisseur)
                    .statut("EN_ATTENTE")
                    .montantTotal(BigDecimal.ZERO)
                    .notes(body.getOrDefault("notes", "").toString())
                    .build();

            // Lignes
            if (body.containsKey("lignes")) {
                List<Map<String, Object>> lignesData = (List<Map<String, Object>>) body.get("lignes");
                List<LigneCommandeAchat> lignes = new ArrayList<>();
                BigDecimal total = BigDecimal.ZERO;
                for (Map<String, Object> l : lignesData) {
                    LigneCommandeAchat ligne = new LigneCommandeAchat();
                    if (l.containsKey("produitId") && l.get("produitId") != null) {
                        produitRepo.findById(Long.valueOf(l.get("produitId").toString()))
                                .ifPresent(ligne::setProduit);
                    }
                    ligne.setDesignation(l.getOrDefault("designation", "").toString());
                    int qte = Integer.parseInt(l.getOrDefault("quantite", "1").toString());
                    BigDecimal pu = new BigDecimal(l.getOrDefault("prixUnitaire", "0").toString());
                    ligne.setQuantite(qte);
                    ligne.setPrixUnitaire(pu);
                    ligne.setMontantLigne(pu.multiply(BigDecimal.valueOf(qte)));
                    ligne.setCommandeAchat(cmd);
                    lignes.add(ligne);
                    total = total.add(ligne.getMontantLigne());
                }
                cmd.setLignes(lignes);
                cmd.setMontantTotal(total);
            }

            CommandeAchat saved = commandeAchatRepo.save(cmd);
            return ResponseEntity.ok(Map.of(
                "id", saved.getId(),
                "numeroCommande", saved.getNumeroCommande(),
                "statut", saved.getStatut(),
                "montantTotal", saved.getMontantTotal(),
                "message", "Bon de commande créé avec succès !"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Erreur : " + e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════
    // P3 — WORKFLOW MULTI-NIVEAUX
    // EN_ATTENTE → EN_VALIDATION → ENVOYEE → RECUE_PARTIELLE/TOTALE
    // ══════════════════════════════════════════════════════════

    @PutMapping("/commandes/{id}/statut")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ACHAT')")
    public ResponseEntity<?> changerStatutCommande(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return commandeAchatRepo.findById(id).map(cmd -> {
            String ancienStatut = cmd.getStatut();
            String nouveauStatut = body.get("statut");

            // Workflow ordonné avec validation intermédiaire
            List<String> workflow = List.of("EN_ATTENTE", "EN_VALIDATION", "ENVOYEE", "RECUE_PARTIELLE", "RECUE_TOTALE");
            int ancienIdx = workflow.indexOf(ancienStatut);
            int nouveauIdx = workflow.indexOf(nouveauStatut);

            // Interdire régression
            if (ancienIdx >= 0 && nouveauIdx >= 0 && nouveauIdx < ancienIdx) {
                return ResponseEntity.badRequest()
                    .body(Map.of("message", "Impossible de revenir à un statut précédent."));
            }
            if ("ANNULEE".equals(ancienStatut)) {
                return ResponseEntity.badRequest()
                    .body(Map.of("message", "Impossible de modifier une commande annulée."));
            }

            cmd.setStatut(nouveauStatut);
            commandeAchatRepo.save(cmd);
            return ResponseEntity.ok(Map.of(
                "message", "Statut mis à jour avec succès !",
                "statut", cmd.getStatut()
            ));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/commandes/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ACHAT')")
    public ResponseEntity<?> supprimerCommande(@PathVariable Long id) {
        return commandeAchatRepo.findById(id).map(cmd -> {
            if (!"EN_ATTENTE".equals(cmd.getStatut())) {
                return ResponseEntity.badRequest()
                    .body(Map.of("message", "Impossible de supprimer une commande déjà envoyée."));
            }
            commandeAchatRepo.delete(cmd);
            return ResponseEntity.ok(Map.of("message", "Commande supprimée avec succès !"));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ══════════════════════════════════════════════════════════
    // RÉCEPTIONS — P1 : Mise à jour stock automatique
    // ══════════════════════════════════════════════════════════

    @GetMapping("/receptions")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ACHAT') or hasRole('STOCK')")
    public List<ReceptionLivraison> getToutesReceptions() {
        return receptionRepo.findAll();
    }

    @PostMapping("/receptions")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ACHAT') or hasRole('STOCK')")
    public ResponseEntity<?> enregistrerReception(@RequestBody Map<String, Object> body) {
        try {
            Long commandeId = Long.valueOf(body.get("commandeAchatId").toString());
            CommandeAchat cmd = commandeAchatRepo.findById(commandeId)
                    .orElseThrow(() -> new RuntimeException("Commande achat introuvable"));

            String numero = "REC-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                    + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

            ReceptionLivraison reception = ReceptionLivraison.builder()
                    .numeroReception(numero)
                    .commandeAchat(cmd)
                    .statut(body.getOrDefault("statut", "CONFORME").toString())
                    .observations(body.getOrDefault("observations", "").toString())
                    .build();

            Produit produitRecu = null;
            Entrepot entrepotCible = null;

            if (body.containsKey("produitId") && body.get("produitId") != null) {
                produitRecu = produitRepo.findById(Long.valueOf(body.get("produitId").toString())).orElse(null);
                reception.setProduit(produitRecu);
            }
            if (body.containsKey("entrepotId") && body.get("entrepotId") != null) {
                entrepotCible = entrepotRepo.findById(Long.valueOf(body.get("entrepotId").toString())).orElse(null);
                reception.setEntrepot(entrepotCible);
            }

            int qteCmd = Integer.parseInt(body.getOrDefault("quantiteCommandee", "0").toString());
            int qteRec = Integer.parseInt(body.getOrDefault("quantiteRecue", "0").toString());
            reception.setQuantiteCommandee(qteCmd);
            reception.setQuantiteRecue(qteRec);

            ReceptionLivraison saved = receptionRepo.save(reception);

            // ── P1 : Mise à jour automatique du stock ──────────────
            boolean stockMisAJour = false;
            if (produitRecu != null && entrepotCible != null && qteRec > 0) {
                final Produit fp = produitRecu;
                final Entrepot fe = entrepotCible;

                // 1. Mettre à jour ou créer StockEntrepot
                StockEntrepot stock = stockEntrepotRepo
                        .findByProduitIdAndEntrepotId(fp.getId(), fe.getId())
                        .orElseGet(() -> StockEntrepot.builder()
                                .produit(fp).entrepot(fe).quantite(0).build());
                stock.setQuantite(stock.getQuantite() + qteRec);
                stockEntrepotRepo.save(stock);

                // 2. Créer un MouvementStock de type ENTREE
                MouvementStock mvt = MouvementStock.builder()
                        .produit(fp)
                        .entrepot(fe)
                        .typeMouvement(TypeMouvement.ENTREE)
                        .quantite(qteRec)
                        .description("Réception " + saved.getNumeroReception()
                                + " | BC " + cmd.getNumeroCommande())
                        .build();
                mouvementRepo.save(mvt);

                // 3. Mettre à jour quantiteStock global du produit
                fp.setQuantiteStock((fp.getQuantiteStock() == null ? 0 : fp.getQuantiteStock()) + qteRec);
                produitRepo.save(fp);

                stockMisAJour = true;
            }
            // ──────────────────────────────────────────────────────

            // Mise à jour statut commande
            if (qteRec >= qteCmd) {
                cmd.setStatut("RECUE_TOTALE");
            } else {
                cmd.setStatut("RECUE_PARTIELLE");
            }
            commandeAchatRepo.save(cmd);

            return ResponseEntity.ok(Map.of(
                "message", stockMisAJour
                    ? "Réception enregistrée et stock mis à jour automatiquement !"
                    : "Réception enregistrée avec succès !",
                "numeroReception", saved.getNumeroReception(),
                "statut", saved.getStatut(),
                "stockMisAJour", stockMisAJour
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Erreur : " + e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════
    // P5 — RELANCE EMAIL FOURNISSEUR
    // ══════════════════════════════════════════════════════════

    @PostMapping("/commandes/{id}/relance-email")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ACHAT')")
    public ResponseEntity<?> relancerFournisseur(@PathVariable Long id) {
        return commandeAchatRepo.findById(id).map(cmd -> {
            try {
                Fournisseur f = cmd.getFournisseur();
                if (f == null || f.getEmail() == null || f.getEmail().isBlank()) {
                    return ResponseEntity.badRequest()
                        .body(Map.of("message", "Ce fournisseur n'a pas d'adresse email renseignée."));
                }

                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom("ecoressourcesb2b@gmail.com");
                helper.setTo(f.getEmail());
                helper.setSubject("Relance commande " + cmd.getNumeroCommande() + " — BENJEDDOU ERP");
                helper.setText(buildEmailRelance(cmd, f), true); // true = HTML

                mailSender.send(message);

                return ResponseEntity.ok(Map.of(
                    "message", "Email de relance envoyé avec succès à " + f.getEmail(),
                    "destinataire", f.getEmail(),
                    "commande", cmd.getNumeroCommande()
                ));
            } catch (Exception e) {
                return ResponseEntity.badRequest()
                    .body(Map.of("message", "Erreur lors de l'envoi de l'email : " + e.getMessage()));
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    private String buildEmailRelance(CommandeAchat cmd, Fournisseur f) {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'></head>"
            + "<body style='font-family:Arial,sans-serif;background:#f8fafc;padding:20px;'>"
            + "<div style='max-width:600px;margin:0 auto;background:white;border-radius:16px;"
            + "padding:32px;box-shadow:0 4px 20px rgba(0,0,0,0.08);'>"
            + "<div style='background:linear-gradient(135deg,#f97316,#ea580c);border-radius:12px;"
            + "padding:20px 24px;margin-bottom:24px;'>"
            + "<h2 style='color:white;margin:0;font-size:1.2rem;'>⚡ BENJEDDOU ERP</h2>"
            + "<p style='color:rgba(255,255,255,0.8);margin:4px 0 0;font-size:0.85rem;'>Relance commande fournisseur</p>"
            + "</div>"
            + "<p style='color:#1e293b;font-size:0.95rem;'>Bonjour <strong>" + f.getNom() + "</strong>,</p>"
            + "<p style='color:#475569;line-height:1.7;'>Nous vous contactons au sujet du bon de commande "
            + "<strong style='color:#f97316;'>" + cmd.getNumeroCommande() + "</strong> "
            + "d'un montant de <strong>" + cmd.getMontantTotal() + " TND</strong>.</p>"
            + "<div style='background:#fff7ed;border:1px solid #fed7aa;border-radius:10px;padding:16px;margin:20px 0;'>"
            + "<p style='margin:0;color:#c2410c;font-size:0.88rem;'>"
            + "<strong>Statut actuel :</strong> " + cmd.getStatut() + "</p>"
            + "</div>"
            + "<p style='color:#475569;line-height:1.7;'>Merci de nous confirmer la date de livraison "
            + "prévue pour cette commande dans les meilleurs délais.</p>"
            + "<p style='color:#475569;margin-top:24px;'>Cordialement,<br>"
            + "<strong style='color:#0f172a;'>Équipe BENJEDDOU ERP</strong></p>"
            + "</div></body></html>";
    }

    // ══════════════════════════════════════════════════════════
    // KPIs ACHATS
    // ══════════════════════════════════════════════════════════

    @GetMapping("/kpis")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ACHAT')")
    public ResponseEntity<Map<String, Object>> getKpisAchats() {
        List<CommandeAchat> commandes = commandeAchatRepo.findAll();
        List<ReceptionLivraison> receptions = receptionRepo.findAll();
        List<Fournisseur> fournisseurs = fournisseurRepo.findAll();

        BigDecimal totalDepenses = commandes.stream()
                .filter(c -> !"ANNULEE".equals(c.getStatut()))
                .map(CommandeAchat::getMontantTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long bonsEnAttente = commandes.stream()
                .filter(c -> "EN_ATTENTE".equals(c.getStatut()) || "ENVOYEE".equals(c.getStatut())
                          || "EN_VALIDATION".equals(c.getStatut()))
                .count();

        long receptionsCeMois = receptions.stream()
                .filter(r -> r.getDateReception() != null &&
                        r.getDateReception().getMonth() == LocalDateTime.now().getMonth() &&
                        r.getDateReception().getYear() == LocalDateTime.now().getYear())
                .count();

        Map<String, Object> kpis = new HashMap<>();
        kpis.put("totalDepenses", totalDepenses);
        kpis.put("bonsEnAttente", bonsEnAttente);
        kpis.put("fournisseursActifs", fournisseurs.size());
        kpis.put("receptionsCeMois", receptionsCeMois);
        kpis.put("totalCommandes", commandes.size());
        kpis.put("totalReceptions", receptions.size());

        return ResponseEntity.ok(kpis);
    }
}
