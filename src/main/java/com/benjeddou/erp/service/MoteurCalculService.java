package com.benjeddou.erp.service;

import com.benjeddou.erp.config.MasterTenantContext;
import com.benjeddou.erp.model.*;
import com.benjeddou.erp.model.CalculMoteur.TypeCalcul;
import com.benjeddou.erp.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MoteurCalculService — Service central du moteur de calcul basé sur les périodes et les taux.
 *
 * Formule universelle :
 *   Résultat = Montant × (Taux / 100) × (Nombre de jours / 365)
 *
 * Base de calcul : 365 jours (fixe — non composé)
 * Arrondi : 2 décimales (RoundingMode.HALF_UP — standard comptable)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MoteurCalculService {

    private static final BigDecimal BASE_JOURS = new BigDecimal("365");
    private static final int ECHELLE_RESULTAT = 2;
    private static final RoundingMode ARRONDI = RoundingMode.HALF_UP;

    private final PeriodeTauxRepository periodeTauxRepository;
    private final CalculMoteurRepository calculMoteurRepository;
    private final LigneCalculRepository ligneCalculRepository;
    private final UtilisateurRepository utilisateurRepository;

    // ═══════════════════════════════════════════════════════════════
    // MODE 1 — TAUX UNIQUE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Calcule avec un taux unique sur toute la période.
     * Sauvegarde automatiquement dans l'historique.
     *
     * @param montant   Montant de base
     * @param dateDebut Date de début (inclusive)
     * @param dateFin   Date de fin (inclusive)
     * @param taux      Taux en % (ex: 9.75 pour 9,75%)
     * @param moduleErp Module ERP d'origine (ex: "RH", "FINANCE", "GENERAL")
     * @param libelle   Description libre du calcul
     * @param userId    ID de l'utilisateur effectuant le calcul
     * @return CalculMoteur sauvegardé avec le résultat
     */
    @Transactional
    public CalculMoteur calculerTauxUnique(BigDecimal montant, LocalDate dateDebut,
            LocalDate dateFin, BigDecimal taux, String moduleErp, String libelle, Long userId) {

        long nombreJours = calculerNombreJours(dateDebut, dateFin);
        BigDecimal resultat = calculerMontantPeriode(montant, taux, nombreJours);

        log.info("Calcul taux unique — Montant: {} | Taux: {}% | Jours: {} | Résultat: {}",
                montant, taux, nombreJours, resultat);

        CalculMoteur calcul = CalculMoteur.builder()
                .reference(genererReference())
                .typeCalcul(TypeCalcul.TAUX_UNIQUE)
                .montant(montant)
                .dateDebut(dateDebut)
                .dateFin(dateFin)
                .nombreJours(nombreJours)
                .tauxUnique(taux)
                .resultatTotal(resultat)
                .moduleErp(moduleErp != null ? moduleErp : "GENERAL")
                .libelle(libelle)
                .creePar(userId != null ? utilisateurRepository.findById(userId).orElse(null) : null)
                .build();

        return calculMoteurRepository.save(calcul);
    }

    // ═══════════════════════════════════════════════════════════════
    // MODE 2 — TAUX VARIABLES PAR PÉRIODE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Calcule avec des taux variables — découpage automatique selon la base de référence.
     * Le moteur récupère les périodes applicables depuis la BD et calcule chaque segment.
     *
     * @param montant       Montant de base (identique pour chaque période)
     * @param dateDebut     Début de la période globale
     * @param dateFin       Fin de la période globale
     * @param moduleErp     Module ERP d'origine
     * @param libelle       Description libre
     * @param userId        ID utilisateur
     * @return CalculMoteur avec les lignes de détail
     */
    @Transactional
    public CalculMoteur calculerTauxVariables(BigDecimal montant, LocalDate dateDebut,
            LocalDate dateFin, String moduleErp, String libelle, Long userId) {

        // Lire les taux depuis la BASE CENTRALE (benjeddou_erp) — partagée entre tous les tenants
        List<PeriodeTaux> periodesApplicables = MasterTenantContext.run(() ->
                periodeTauxRepository.findPeriodesApplicables(dateDebut, dateFin));

        if (periodesApplicables.isEmpty()) {
            throw new IllegalStateException(
                "Aucune période avec taux définie pour la plage " + dateDebut + " → " + dateFin +
                ". Veuillez configurer les périodes dans l'interface d'administration."
            );
        }

        List<LigneCalcul> lignes = new ArrayList<>();
        BigDecimal resultatTotal = BigDecimal.ZERO;
        int numeroLigne = 1;

        for (PeriodeTaux periode : periodesApplicables) {
            // Intersection entre la période de référence et la période demandée
            LocalDate segmentDebut = periode.getDateDebut().isBefore(dateDebut)
                    ? dateDebut : periode.getDateDebut();
            LocalDate segmentFin = periode.getDateFin().isAfter(dateFin)
                    ? dateFin : periode.getDateFin();

            long joursSegment = calculerNombreJours(segmentDebut, segmentFin);
            BigDecimal resultatLigne = calculerMontantPeriode(montant, periode.getTaux(), joursSegment);

            lignes.add(LigneCalcul.builder()
                    .numeroLigne(numeroLigne++)
                    .dateDebut(segmentDebut)
                    .dateFin(segmentFin)
                    .nombreJours(joursSegment)
                    .taux(periode.getTaux())
                    .montantBase(montant)
                    .resultatLigne(resultatLigne)
                    .libellePeriode(periode.getLibelle())
                    .build());

            resultatTotal = resultatTotal.add(resultatLigne);
        }

        resultatTotal = resultatTotal.setScale(ECHELLE_RESULTAT, ARRONDI);
        long joursTotal = calculerNombreJours(dateDebut, dateFin);

        log.info("Calcul taux variable — Montant: {} | Périodes: {} | Jours: {} | Résultat total: {}",
                montant, lignes.size(), joursTotal, resultatTotal);

        CalculMoteur calcul = CalculMoteur.builder()
                .reference(genererReference())
                .typeCalcul(TypeCalcul.TAUX_VARIABLE)
                .montant(montant)
                .dateDebut(dateDebut)
                .dateFin(dateFin)
                .nombreJours(joursTotal)
                .tauxUnique(null)
                .resultatTotal(resultatTotal)
                .moduleErp(moduleErp != null ? moduleErp : "GENERAL")
                .libelle(libelle)
                .creePar(userId != null ? utilisateurRepository.findById(userId).orElse(null) : null)
                .build();

        CalculMoteur sauvegarde = calculMoteurRepository.save(calcul);

        // Associer les lignes au calcul sauvegardé
        for (LigneCalcul ligne : lignes) {
            ligne.setCalcul(sauvegarde);
        }
        List<LigneCalcul> lignesSauvegardees = ligneCalculRepository.saveAll(lignes);
        sauvegarde.getLignes().addAll(lignesSauvegardees);

        return sauvegarde;
    }

    // ═══════════════════════════════════════════════════════════════
    // SIMULATION (sans sauvegarde) — pour aperçu temps réel côté front
    // ═══════════════════════════════════════════════════════════════

    /**
     * Simule un calcul taux variables sans sauvegarder — pour l'aperçu temps réel.
     */
    public List<LigneCalcul> simulerTauxVariables(BigDecimal montant,
            LocalDate dateDebut, LocalDate dateFin) {

        // Lire les taux depuis la BASE CENTRALE pour la simulation
        List<PeriodeTaux> periodesApplicables = MasterTenantContext.run(() ->
                periodeTauxRepository.findPeriodesApplicables(dateDebut, dateFin));

        List<LigneCalcul> lignes = new ArrayList<>();
        int numeroLigne = 1;

        for (PeriodeTaux periode : periodesApplicables) {
            LocalDate segmentDebut = periode.getDateDebut().isBefore(dateDebut)
                    ? dateDebut : periode.getDateDebut();
            LocalDate segmentFin = periode.getDateFin().isAfter(dateFin)
                    ? dateFin : periode.getDateFin();

            long joursSegment = calculerNombreJours(segmentDebut, segmentFin);
            BigDecimal resultatLigne = calculerMontantPeriode(montant, periode.getTaux(), joursSegment);

            lignes.add(LigneCalcul.builder()
                    .numeroLigne(numeroLigne++)
                    .dateDebut(segmentDebut)
                    .dateFin(segmentFin)
                    .nombreJours(joursSegment)
                    .taux(periode.getTaux())
                    .montantBase(montant)
                    .resultatLigne(resultatLigne)
                    .libellePeriode(periode.getLibelle())
                    .build());
        }
        return lignes;
    }

    // ═══════════════════════════════════════════════════════════════
    // HISTORIQUE
    // ═══════════════════════════════════════════════════════════════

    public Page<CalculMoteur> getHistorique(Pageable pageable) {
        return calculMoteurRepository.findAllByOrderByDateCreationDesc(pageable);
    }

    /** Historique filtré par type de calcul : TAUX_UNIQUE ou TAUX_VARIABLE */
    public Page<CalculMoteur> getHistoriqueParType(String typeStr, Pageable pageable) {
        try {
            TypeCalcul type = TypeCalcul.valueOf(typeStr);
            return calculMoteurRepository.findByTypeCalculOrderByDateCreationDesc(type, pageable);
        } catch (IllegalArgumentException e) {
            return calculMoteurRepository.findAllByOrderByDateCreationDesc(pageable);
        }
    }

    public Page<CalculMoteur> rechercherHistorique(String q, Pageable pageable) {
        return calculMoteurRepository.rechercher(q, pageable);
    }

    /** Recherche dans l'historique filtrée par type */
    public Page<CalculMoteur> rechercherHistoriqueParType(String q, String typeStr, Pageable pageable) {
        try {
            TypeCalcul type = TypeCalcul.valueOf(typeStr);
            return calculMoteurRepository.rechercherParType(q, type, pageable);
        } catch (IllegalArgumentException e) {
            return calculMoteurRepository.rechercher(q, pageable);
        }
    }

    public Optional<CalculMoteur> getById(Long id) {
        return calculMoteurRepository.findById(id);
    }

    /**
     * Historique paginé filtré par utilisateur (isolation multi-tenant).
     * Chaque utilisateur ne voit que SES propres calculs.
     */
    public Page<CalculMoteur> getHistoriqueParUtilisateur(Long userId, String typeStr, Pageable pageable) {
        if (typeStr != null && !typeStr.isBlank()) {
            try {
                TypeCalcul type = TypeCalcul.valueOf(typeStr);
                return calculMoteurRepository.findByCreePar_IdAndTypeCalculOrderByDateCreationDesc(userId, type, pageable);
            } catch (IllegalArgumentException e) {
                return calculMoteurRepository.findByCreePar_IdOrderByDateCreationDesc(userId, pageable);
            }
        }
        return calculMoteurRepository.findByCreePar_IdOrderByDateCreationDesc(userId, pageable);
    }

    /**
     * Recherche dans l'historique filtrée par utilisateur (isolation multi-tenant).
     */
    public Page<CalculMoteur> rechercherHistoriqueParUtilisateur(String q, Long userId, String typeStr, Pageable pageable) {
        if (typeStr != null && !typeStr.isBlank()) {
            try {
                TypeCalcul type = TypeCalcul.valueOf(typeStr);
                return calculMoteurRepository.rechercherParUtilisateurEtType(q, userId, type, pageable);
            } catch (IllegalArgumentException e) {
                return calculMoteurRepository.rechercherParUtilisateur(q, userId, pageable);
            }
        }
        return calculMoteurRepository.rechercherParUtilisateur(q, userId, pageable);
    }

    public List<LigneCalcul> getLignes(Long calculId) {
        return ligneCalculRepository.findByCalculIdOrderByNumeroLigneAsc(calculId);
    }

    @Transactional
    public void supprimer(Long id) {
        ligneCalculRepository.deleteByCalculId(id);
        calculMoteurRepository.deleteById(id);
    }

    // ═══════════════════════════════════════════════════════════════
    // UTILITAIRES
    // ═══════════════════════════════════════════════════════════════

    /**
     * Calcule le nombre de jours entre deux dates (inclusif : fin - début + 1).
     * Conformément au cahier des charges : 18/02 → 09/03 = 20 jours.
     */
    public long calculerNombreJours(LocalDate debut, LocalDate fin) {
        if (debut == null || fin == null || fin.isBefore(debut)) return 0;
        return fin.toEpochDay() - debut.toEpochDay() + 1;
    }

    /**
     * Applique la formule : Montant × (Taux / 100) × (Jours / 365)
     * Résultat arrondi à 2 décimales (RoundingMode.HALF_UP).
     */
    public BigDecimal calculerMontantPeriode(BigDecimal montant, BigDecimal taux, long jours) {
        if (montant == null || taux == null || jours <= 0) return BigDecimal.ZERO;
        BigDecimal tauxDecimal = taux.divide(new BigDecimal("100"), 10, ARRONDI);
        BigDecimal fractionJours = new BigDecimal(jours).divide(BASE_JOURS, 10, ARRONDI);
        return montant.multiply(tauxDecimal).multiply(fractionJours)
                .setScale(ECHELLE_RESULTAT, ARRONDI);
    }

    /**
     * Génère une référence unique : CM-YYYYMMDD-XXXX
     * ex: CM-20260711-0001
     */
    private String genererReference() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long seq = calculMoteurRepository.countByDateStr(dateStr) + 1;
        return String.format("CM-%s-%04d", dateStr, seq);
    }

    /** Récupère les périodes actives applicables depuis la base centrale */
    public List<PeriodeTaux> getPeriodesApplicables(LocalDate debut, LocalDate fin) {
        return MasterTenantContext.run(() ->
                periodeTauxRepository.findPeriodesApplicables(debut, fin));
    }
}
