package com.benjeddou.erp.controller;

import com.benjeddou.erp.model.*;
import com.benjeddou.erp.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/finance")
public class FinanceController {

    @Autowired FactureRepository factureRepo;
    @Autowired CommandeRepository commandeRepo;
    @Autowired CommandeAchatRepository commandeAchatRepo;
    @Autowired EcritureComptableRepository ecritureRepo;

    // ══════════════════════════════════════════════════════════
    // KPIs FINANCIERS
    // ══════════════════════════════════════════════════════════

    @GetMapping("/kpis")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPTABLE')")
    public ResponseEntity<Map<String, Object>> getKpisFinanciers() {
        List<Facture> factures = factureRepo.findAll();
        List<CommandeAchat> achats = commandeAchatRepo.findAll();

        // Produits (ventes facturées)
        BigDecimal totalProduits = factures.stream()
                .filter(f -> "PAYEE".equals(f.getStatut()))
                .map(Facture::getMontantTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Charges (achats reçus)
        BigDecimal totalCharges = achats.stream()
                .filter(a -> "RECUE_TOTALE".equals(a.getStatut()) || "RECUE_PARTIELLE".equals(a.getStatut()))
                .map(CommandeAchat::getMontantTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal beneficeNet = totalProduits.subtract(totalCharges);

        // Factures impayées
        BigDecimal montantImpayes = factures.stream()
                .filter(f -> "EN_ATTENTE".equals(f.getStatut()) || "IMPAYEE".equals(f.getStatut()))
                .map(Facture::getMontantTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long facturesEnAttente = factures.stream()
                .filter(f -> "EN_ATTENTE".equals(f.getStatut()) || "IMPAYEE".equals(f.getStatut()))
                .count();

        // TVA collectée (19% sur ventes payées)
        BigDecimal tvaCollectee = totalProduits.multiply(new BigDecimal("0.19")).setScale(3, RoundingMode.HALF_UP);
        // TVA déductible (19% sur achats)
        BigDecimal tvaDeductible = totalCharges.multiply(new BigDecimal("0.19")).setScale(3, RoundingMode.HALF_UP);
        BigDecimal tvaAVerser = tvaCollectee.subtract(tvaDeductible);

        Map<String, Object> kpis = new HashMap<>();
        kpis.put("totalProduits", totalProduits);
        kpis.put("totalCharges", totalCharges);
        kpis.put("beneficeNet", beneficeNet);
        kpis.put("montantImpayes", montantImpayes);
        kpis.put("facturesEnAttente", facturesEnAttente);
        kpis.put("tvaCollectee", tvaCollectee);
        kpis.put("tvaDeductible", tvaDeductible);
        kpis.put("tvaAVerser", tvaAVerser.compareTo(BigDecimal.ZERO) > 0 ? tvaAVerser : BigDecimal.ZERO);
        kpis.put("totalFactures", factures.size());

        return ResponseEntity.ok(kpis);
    }

    // ══════════════════════════════════════════════════════════
    // TRÉSORERIE — Flux mensuels
    // ══════════════════════════════════════════════════════════

    @GetMapping("/tresorerie")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPTABLE')")
    public ResponseEntity<Map<String, Object>> getTresorerie() {
        List<Facture> factures = factureRepo.findAll();
        List<CommandeAchat> achats = commandeAchatRepo.findAll();

        // Flux des 6 derniers mois
        List<String> labels = new ArrayList<>();
        List<BigDecimal> entrees = new ArrayList<>();
        List<BigDecimal> sorties = new ArrayList<>();

        String[] mois = {"Jan","Fév","Mar","Avr","Mai","Juin","Juil","Aoû","Sep","Oct","Nov","Déc"};
        LocalDateTime now = LocalDateTime.now();

        for (int i = 5; i >= 0; i--) {
            LocalDateTime moisCible = now.minusMonths(i);
            int m = moisCible.getMonthValue();
            int y = moisCible.getYear();
            labels.add(mois[m - 1] + " " + y);

            BigDecimal entree = factures.stream()
                    .filter(f -> "PAYEE".equals(f.getStatut()) && f.getDateCreation() != null
                            && f.getDateCreation().getMonthValue() == m
                            && f.getDateCreation().getYear() == y)
                    .map(Facture::getMontantTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal sortie = achats.stream()
                    .filter(a -> !"ANNULEE".equals(a.getStatut()) && a.getDateCommande() != null
                            && a.getDateCommande().getMonthValue() == m
                            && a.getDateCommande().getYear() == y)
                    .map(CommandeAchat::getMontantTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            entrees.add(entree);
            sorties.add(sortie);
        }

        // Solde courant = somme entrées - somme sorties
        BigDecimal soldeCourant = entrees.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .subtract(sorties.stream().reduce(BigDecimal.ZERO, BigDecimal::add));

        Map<String, Object> result = new HashMap<>();
        result.put("labels", labels);
        result.put("entrees", entrees);
        result.put("sorties", sorties);
        result.put("soldeCourant", soldeCourant);

        return ResponseEntity.ok(result);
    }

    // ══════════════════════════════════════════════════════════
    // PIÈCES COMPTABLES / ÉCRITURES
    // ══════════════════════════════════════════════════════════

    @GetMapping("/ecritures")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPTABLE')")
    public List<EcritureComptable> getToutesEcritures() {
        return ecritureRepo.findAllByOrderByDateEcritureDesc();
    }

    @PostMapping("/ecritures")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPTABLE')")
    public ResponseEntity<?> creerEcriture(@RequestBody Map<String, Object> body) {
        try {
            String numero = "EC-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                    + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

            EcritureComptable ecriture = EcritureComptable.builder()
                    .numeroEcriture(numero)
                    .dateEcriture(LocalDate.now())
                    .libelle(body.get("libelle").toString())
                    .typeEcriture(body.getOrDefault("typeEcriture", "AUTRE").toString())
                    .sens(body.getOrDefault("sens", "DEBIT").toString())
                    .montant(new BigDecimal(body.getOrDefault("montant", "0").toString()))
                    .compteComptable(body.getOrDefault("compteComptable", "").toString())
                    .referencePiece(body.getOrDefault("referencePiece", "").toString())
                    .statut("BROUILLON")
                    .build();

            if (body.containsKey("factureId") && body.get("factureId") != null) {
                factureRepo.findById(Long.valueOf(body.get("factureId").toString()))
                        .ifPresent(ecriture::setFacture);
            }

            EcritureComptable saved = ecritureRepo.save(ecriture);
            return ResponseEntity.ok(Map.of(
                "id", saved.getId(),
                "numeroEcriture", saved.getNumeroEcriture(),
                "statut", saved.getStatut(),
                "message", "Écriture enregistrée avec succès !"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Erreur : " + e.getMessage()));
        }
    }

    @PutMapping("/ecritures/{id}/valider")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPTABLE')")
    public ResponseEntity<?> validerEcriture(@PathVariable Long id) {
        return ecritureRepo.findById(id).map(e -> {
            e.setStatut("VALIDE");
            ecritureRepo.save(e);
            return ResponseEntity.ok(Map.of(
                "message", "Écriture validée avec succès !",
                "statut", "VALIDE"
            ));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/ecritures/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPTABLE')")
    public ResponseEntity<?> supprimerEcriture(@PathVariable Long id) {
        return ecritureRepo.findById(id).map(e -> {
            if ("VALIDE".equals(e.getStatut())) {
                return ResponseEntity.badRequest()
                    .body(Map.of("message", "Impossible de supprimer une écriture validée."));
            }
            ecritureRepo.delete(e);
            return ResponseEntity.ok(Map.of("message", "Écriture supprimée avec succès !"));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ══════════════════════════════════════════════════════════
    // DÉCLARATION TVA (Législation Tunisienne)
    // ══════════════════════════════════════════════════════════

    @GetMapping("/declaration-tva")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPTABLE')")
    public ResponseEntity<Map<String, Object>> getDeclarationTva(
            @RequestParam(defaultValue = "0") int mois,
            @RequestParam(defaultValue = "0") int annee) {

        LocalDateTime now = LocalDateTime.now();
        int m = mois > 0 ? mois : now.getMonthValue();
        int y = annee > 0 ? annee : now.getYear();

        List<Facture> factures = factureRepo.findAll();
        List<CommandeAchat> achats = commandeAchatRepo.findAll();

        // Ventes du mois
        BigDecimal chiffreAffairesMois = factures.stream()
                .filter(f -> "PAYEE".equals(f.getStatut()) && f.getDateCreation() != null
                        && f.getDateCreation().getMonthValue() == m && f.getDateCreation().getYear() == y)
                .map(Facture::getMontantTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Achats du mois
        BigDecimal achatsMois = achats.stream()
                .filter(a -> !"ANNULEE".equals(a.getStatut()) && a.getDateCommande() != null
                        && a.getDateCommande().getMonthValue() == m && a.getDateCommande().getYear() == y)
                .map(CommandeAchat::getMontantTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal taux = new BigDecimal("0.19"); // TVA 19% Tunisie
        BigDecimal tvaCollectee = chiffreAffairesMois.multiply(taux).setScale(3, RoundingMode.HALF_UP);
        BigDecimal tvaDeductible = achatsMois.multiply(taux).setScale(3, RoundingMode.HALF_UP);
        BigDecimal tvaNet = tvaCollectee.subtract(tvaDeductible);

        String[] nomsMois = {"Janvier","Février","Mars","Avril","Mai","Juin",
                "Juillet","Août","Septembre","Octobre","Novembre","Décembre"};

        Map<String, Object> decl = new HashMap<>();
        decl.put("periode", nomsMois[m - 1] + " " + y);
        decl.put("mois", m);
        decl.put("annee", y);
        decl.put("chiffreAffairesMois", chiffreAffairesMois);
        decl.put("achatsMois", achatsMois);
        decl.put("tauxTva", "19%");
        decl.put("tvaCollectee", tvaCollectee);
        decl.put("tvaDeductible", tvaDeductible);
        decl.put("tvaAVerser", tvaNet.compareTo(BigDecimal.ZERO) > 0 ? tvaNet : BigDecimal.ZERO);
        decl.put("tvaAReporter", tvaNet.compareTo(BigDecimal.ZERO) < 0 ? tvaNet.abs() : BigDecimal.ZERO);

        return ResponseEntity.ok(decl);
    }

    // ══════════════════════════════════════════════════════════
    // P2 — EXPORT DÉCLARATION TVA FORMAT XML (DGI Tunisie)
    // ══════════════════════════════════════════════════════════

    @GetMapping("/declaration-tva/export-xml")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPTABLE')")
    public ResponseEntity<byte[]> exportTvaXml(
            @RequestParam(defaultValue = "0") int mois,
            @RequestParam(defaultValue = "0") int annee) {

        LocalDateTime now = LocalDateTime.now();
        int m = mois > 0 ? mois : now.getMonthValue();
        int y = annee > 0 ? annee : now.getYear();

        List<Facture> factures = factureRepo.findAll();
        List<CommandeAchat> achats = commandeAchatRepo.findAll();

        BigDecimal chiffreAffairesMois = factures.stream()
                .filter(f -> "PAYEE".equals(f.getStatut()) && f.getDateCreation() != null
                        && f.getDateCreation().getMonthValue() == m && f.getDateCreation().getYear() == y)
                .map(Facture::getMontantTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal achatsMois = achats.stream()
                .filter(a -> !"ANNULEE".equals(a.getStatut()) && a.getDateCommande() != null
                        && a.getDateCommande().getMonthValue() == m && a.getDateCommande().getYear() == y)
                .map(CommandeAchat::getMontantTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal taux = new BigDecimal("0.19");
        BigDecimal tvaCollectee  = chiffreAffairesMois.multiply(taux).setScale(3, RoundingMode.HALF_UP);
        BigDecimal tvaDeductible = achatsMois.multiply(taux).setScale(3, RoundingMode.HALF_UP);
        BigDecimal tvaNet        = tvaCollectee.subtract(tvaDeductible);
        BigDecimal tvaAVerser    = tvaNet.compareTo(BigDecimal.ZERO) > 0 ? tvaNet : BigDecimal.ZERO;
        BigDecimal tvaAReporter  = tvaNet.compareTo(BigDecimal.ZERO) < 0 ? tvaNet.abs() : BigDecimal.ZERO;

        String[] nomsMois = {"Janvier","Février","Mars","Avril","Mai","Juin",
                "Juillet","Août","Septembre","Octobre","Novembre","Décembre"};
        String dateGeneration = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        // ── Construction du XML format DGI Tunisie ──────────────
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<DeclarationTVA xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
            + "  <Entete>\n"
            + "    <Emetteur>BENJEDDOU Technologie Services</Emetteur>\n"
            + "    <MatriculeFiscale>XXXXXXXXX/A/M/000</MatriculeFiscale>\n"
            + "    <Periode>\n"
            + "      <Mois>" + m + "</Mois>\n"
            + "      <Annee>" + y + "</Annee>\n"
            + "      <Libelle>" + nomsMois[m - 1] + " " + y + "</Libelle>\n"
            + "    </Periode>\n"
            + "    <DateGeneration>" + dateGeneration + "</DateGeneration>\n"
            + "    <Devise>TND</Devise>\n"
            + "    <TauxTVA>19%</TauxTVA>\n"
            + "  </Entete>\n"
            + "  <ChiffreAffaires>\n"
            + "    <BaseImposable>" + chiffreAffairesMois.toPlainString() + "</BaseImposable>\n"
            + "    <TVACollectee>" + tvaCollectee.toPlainString() + "</TVACollectee>\n"
            + "  </ChiffreAffaires>\n"
            + "  <Achats>\n"
            + "    <BaseDeductible>" + achatsMois.toPlainString() + "</BaseDeductible>\n"
            + "    <TVADeductible>" + tvaDeductible.toPlainString() + "</TVADeductible>\n"
            + "  </Achats>\n"
            + "  <LiquidationTVA>\n"
            + "    <TVANette>" + tvaNet.toPlainString() + "</TVANette>\n"
            + "    <TVAAVerser>" + tvaAVerser.toPlainString() + "</TVAAVerser>\n"
            + "    <TVAAReporter>" + tvaAReporter.toPlainString() + "</TVAAReporter>\n"
            + "  </LiquidationTVA>\n"
            + "</DeclarationTVA>\n";
        // ────────────────────────────────────────────────────────

        String filename = "DeclarationTVA_" + String.format("%02d", m) + "_" + y + ".xml";
        byte[] xmlBytes = xml.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_XML)
                .body(xmlBytes);
    }

    // ══════════════════════════════════════════════════════════
    // ÉTAT FINANCIER — Compte de Résultat simplifié
    // ══════════════════════════════════════════════════════════

    @GetMapping("/compte-resultat")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPTABLE')")
    public ResponseEntity<Map<String, Object>> getCompteResultat(@RequestParam(defaultValue = "0") int annee) {
        int y = annee > 0 ? annee : LocalDateTime.now().getYear();

        List<Facture> factures = factureRepo.findAll();
        List<CommandeAchat> achats = commandeAchatRepo.findAll();

        BigDecimal produits = factures.stream()
                .filter(f -> "PAYEE".equals(f.getStatut()) && f.getDateCreation() != null
                        && f.getDateCreation().getYear() == y)
                .map(Facture::getMontantTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal charges = achats.stream()
                .filter(a -> !"ANNULEE".equals(a.getStatut()) && a.getDateCommande() != null
                        && a.getDateCommande().getYear() == y)
                .map(CommandeAchat::getMontantTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal resultatNet = produits.subtract(charges);
        BigDecimal tauxMarge = produits.compareTo(BigDecimal.ZERO) > 0
                ? resultatNet.divide(produits, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
                : BigDecimal.ZERO;

        Map<String, Object> cr = new HashMap<>();
        cr.put("annee", y);
        cr.put("produits", produits);
        cr.put("charges", charges);
        cr.put("resultatNet", resultatNet);
        cr.put("tauxMarge", tauxMarge.setScale(2, RoundingMode.HALF_UP));
        cr.put("positif", resultatNet.compareTo(BigDecimal.ZERO) > 0);

        return ResponseEntity.ok(cr);
    }
}
