package com.benjeddou.erp.controller;

import com.benjeddou.erp.model.PeriodeTaux;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PeriodeTauxController — Administration des périodes et taux de référence.
 *
 * ╔═══════════════════════════════════════════════════════════════════════╗
 * ║  ARCHITECTURE CENTRALISÉE — Conformité Point 2 de l'encadrant        ║
 * ╠═══════════════════════════════════════════════════════════════════════╣
 * ║  • La table periodes_taux est EXCLUSIVEMENT dans benjeddou_erp        ║
 * ║  • Elle est gérée EXCLUSIVEMENT par le Super Admin SaaS              ║
 * ║  • Utilise JDBC DIRECT sur masterDataSource (pas JPA)                ║
 * ║  → Garantit l'accès à benjeddou_erp même si un tenant est actif     ║
 * ║  → Résout définitivement l'erreur 500 Http failure response          ║
 * ╚═══════════════════════════════════════════════════════════════════════╝
 *
 * Politique d'accès :
 *   - GET (lecture) : tous les utilisateurs authentifiés
 *   - POST / PUT / PATCH / DELETE : SUPERADMIN UNIQUEMENT
 */
@RestController
@RequestMapping("/api/periodes-taux")
@CrossOrigin(origins = "*")
@Slf4j
public class PeriodeTauxController {

    /** DataSource master (benjeddou_erp) — injecté directement, jamais routé */
    private final DataSource masterDataSource;

    public PeriodeTauxController(@Qualifier("masterDataSource") DataSource masterDataSource) {
        this.masterDataSource = masterDataSource;
    }

    // ── LECTURE ──────────────────────────────────────────────────────────────

    /**
     * GET /api/periodes-taux — Liste toutes les périodes (actives + inactives).
     * Lit TOUJOURS depuis benjeddou_erp via JDBC direct.
     */
    @GetMapping
    public ResponseEntity<List<PeriodeTaux>> getAll() {
        try (Connection conn = masterDataSource.getConnection()) {
            String sql = "SELECT id, date_debut, date_fin, taux, libelle, actif, date_creation, date_modification " +
                         "FROM periodes_taux ORDER BY date_debut ASC";
            List<PeriodeTaux> result = new ArrayList<>();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
            return ResponseEntity.ok(result);
        } catch (SQLException e) {
            log.error("Erreur lecture periodes_taux : {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * GET /api/periodes-taux/actives — Seulement les périodes actives.
     */
    @GetMapping("/actives")
    public ResponseEntity<List<PeriodeTaux>> getActives() {
        try (Connection conn = masterDataSource.getConnection()) {
            String sql = "SELECT id, date_debut, date_fin, taux, libelle, actif, date_creation, date_modification " +
                         "FROM periodes_taux WHERE actif = TRUE ORDER BY date_debut ASC";
            List<PeriodeTaux> result = new ArrayList<>();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
            return ResponseEntity.ok(result);
        } catch (SQLException e) {
            log.error("Erreur lecture periodes_taux actives : {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    // ── ÉCRITURE (SUPERADMIN UNIQUEMENT) ────────────────────────────────────

    /**
     * POST /api/periodes-taux — Créer une nouvelle période.
     */
    @PostMapping
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> creer(@RequestBody Map<String, Object> body) {
        try {
            LocalDate dateDebut = LocalDate.parse(body.get("dateDebut").toString());
            LocalDate dateFin   = LocalDate.parse(body.get("dateFin").toString());
            BigDecimal taux     = new BigDecimal(body.get("taux").toString());
            String libelle      = body.getOrDefault("libelle", "").toString();

            if (dateFin.isBefore(dateDebut)) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false,
                        "message", "La date de fin doit être après la date de début"));
            }

            try (Connection conn = masterDataSource.getConnection()) {
                // Vérifier chevauchement
                if (existsChevauchement(conn, dateDebut, dateFin, -1L)) {
                    return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Une période avec taux existe déjà sur tout ou partie de cette plage de dates."
                    ));
                }

                String sql = "INSERT INTO periodes_taux (date_debut, date_fin, taux, libelle, actif) VALUES (?, ?, ?, ?, TRUE)";
                try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setDate(1, Date.valueOf(dateDebut));
                    ps.setDate(2, Date.valueOf(dateFin));
                    ps.setBigDecimal(3, taux);
                    ps.setString(4, libelle);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        Long newId = keys.next() ? keys.getLong(1) : null;
                        log.info("Période taux créée id={} ({} → {} @ {}%)", newId, dateDebut, dateFin, taux);
                        return ResponseEntity.ok(Map.of(
                            "success", true,
                            "id", newId != null ? newId : 0,
                            "message", "Période créée avec succès dans la base centralisée"
                        ));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Erreur création période taux : {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * PUT /api/periodes-taux/{id} — Modifier une période.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> modifier(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try (Connection conn = masterDataSource.getConnection()) {
            // Lire la période existante
            PeriodeTaux existing = findById(conn, id);
            if (existing == null) return ResponseEntity.notFound().build();

            LocalDate dateDebut = body.containsKey("dateDebut")
                ? LocalDate.parse(body.get("dateDebut").toString()) : existing.getDateDebut();
            LocalDate dateFin = body.containsKey("dateFin")
                ? LocalDate.parse(body.get("dateFin").toString()) : existing.getDateFin();
            BigDecimal taux = body.containsKey("taux")
                ? new BigDecimal(body.get("taux").toString()) : existing.getTaux();
            String libelle = body.containsKey("libelle")
                ? body.get("libelle").toString() : existing.getLibelle();
            boolean actif = body.containsKey("actif")
                ? Boolean.parseBoolean(body.get("actif").toString()) : existing.isActif();

            if (existsChevauchement(conn, dateDebut, dateFin, id)) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "message", "Chevauchement détecté avec une période existante"
                ));
            }

            String sql = "UPDATE periodes_taux SET date_debut=?, date_fin=?, taux=?, libelle=?, actif=? WHERE id=?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setDate(1, Date.valueOf(dateDebut));
                ps.setDate(2, Date.valueOf(dateFin));
                ps.setBigDecimal(3, taux);
                ps.setString(4, libelle);
                ps.setBoolean(5, actif);
                ps.setLong(6, id);
                ps.executeUpdate();
            }
            return ResponseEntity.ok(Map.of("success", true, "message", "Période mise à jour dans la base centralisée"));
        } catch (Exception e) {
            log.error("Erreur modification période taux id={} : {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * PATCH /api/periodes-taux/{id}/toggle — Activer/désactiver une période.
     */
    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> toggle(@PathVariable Long id) {
        try (Connection conn = masterDataSource.getConnection()) {
            PeriodeTaux existing = findById(conn, id);
            if (existing == null) return ResponseEntity.notFound().build();

            boolean newActif = !existing.isActif();
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE periodes_taux SET actif=? WHERE id=?")) {
                ps.setBoolean(1, newActif);
                ps.setLong(2, id);
                ps.executeUpdate();
            }
            return ResponseEntity.ok(Map.of(
                "success", true,
                "actif", newActif,
                "message", newActif ? "Période activée" : "Période désactivée"
            ));
        } catch (Exception e) {
            log.error("Erreur toggle période taux id={} : {}", id, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * DELETE /api/periodes-taux/{id} — Supprimer une période.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> supprimer(@PathVariable Long id) {
        try (Connection conn = masterDataSource.getConnection()) {
            if (findById(conn, id) == null) return ResponseEntity.notFound().build();
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM periodes_taux WHERE id=?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
            log.info("Période taux id={} supprimée de la base centralisée", id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Période supprimée de la base centralisée"));
        } catch (Exception e) {
            log.error("Erreur suppression période taux id={} : {}", id, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    // ── Utilitaires JDBC ────────────────────────────────────────────────────

    private PeriodeTaux mapRow(ResultSet rs) throws SQLException {
        PeriodeTaux p = new PeriodeTaux();
        p.setId(rs.getLong("id"));
        p.setDateDebut(rs.getDate("date_debut").toLocalDate());
        p.setDateFin(rs.getDate("date_fin").toLocalDate());
        p.setTaux(rs.getBigDecimal("taux"));
        p.setLibelle(rs.getString("libelle"));
        p.setActif(rs.getBoolean("actif"));
        Timestamp dc = rs.getTimestamp("date_creation");
        if (dc != null) p.setDateCreation(dc.toLocalDateTime());
        Timestamp dm = rs.getTimestamp("date_modification");
        if (dm != null) p.setDateModification(dm.toLocalDateTime());
        return p;
    }

    private PeriodeTaux findById(Connection conn, Long id) throws SQLException {
        String sql = "SELECT id, date_debut, date_fin, taux, libelle, actif, date_creation, date_modification " +
                     "FROM periodes_taux WHERE id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    private boolean existsChevauchement(Connection conn, LocalDate dateDebut, LocalDate dateFin, Long excludeId)
            throws SQLException {
        String sql = "SELECT COUNT(*) FROM periodes_taux WHERE actif=TRUE AND id<>? " +
                     "AND date_debut<=? AND date_fin>=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, excludeId < 0 ? Long.MAX_VALUE : excludeId);
            ps.setDate(2, Date.valueOf(dateFin));
            ps.setDate(3, Date.valueOf(dateDebut));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }
}
