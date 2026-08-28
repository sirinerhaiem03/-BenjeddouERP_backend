package com.benjeddou.erp.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SuggestionsController — Fournit des suggestions d'autocomplétion
 * depuis les données existantes de la base du tenant courant.
 *
 * Objectif : réduire les saisies répétitives, limiter les erreurs,
 * éviter les doublons et améliorer l'expérience utilisateur.
 *
 * Tous les endpoints sont en lecture seule, légers (max 10 résultats),
 * et nécessitent une authentification.
 */
@RestController
@RequestMapping("/api/suggestions")
@CrossOrigin(origins = "*")
@Slf4j
@PreAuthorize("isAuthenticated()")
public class SuggestionsController {

    private final DataSource dataSource;

    /** Injection du DataSource @Primary (TenantRoutingDataSource) — route automatiquement vers le tenant courant */
    public SuggestionsController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // ── Clients ──────────────────────────────────────────────────────

    @GetMapping("/clients")
    public ResponseEntity<List<Map<String, String>>> suggestClients(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "10") int max) {
        String sql = "SELECT DISTINCT nom, prenom, email FROM clients " +
                     "WHERE actif = TRUE AND (nom LIKE ? OR prenom LIKE ? OR email LIKE ?) " +
                     "ORDER BY nom ASC LIMIT ?";
        List<Map<String, String>> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String pattern = "%" + q + "%";
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setString(3, pattern);
            ps.setInt(4, max);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String nom = rs.getString("nom");
                    String prenom = rs.getString("prenom");
                    String email = rs.getString("email");
                    String label = ((prenom != null ? prenom + " " : "") + (nom != null ? nom : "")).trim();
                    results.add(Map.of(
                        "value", label,
                        "label", label,
                        "sub", email != null ? email : "",
                        "icon", "person"
                    ));
                }
            }
        } catch (SQLException e) {
            log.warn("[Suggestions/clients] {}", e.getMessage());
        }
        return ResponseEntity.ok(results);
    }

    // ── Produits ─────────────────────────────────────────────────────

    @GetMapping("/produits")
    public ResponseEntity<List<Map<String, String>>> suggestProduits(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "10") int max) {
        String sql = "SELECT DISTINCT nom, reference, categorie FROM produits " +
                     "WHERE (nom LIKE ? OR reference LIKE ?) " +
                     "ORDER BY nom ASC LIMIT ?";
        List<Map<String, String>> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String pattern = "%" + q + "%";
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setInt(3, max);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String nom = rs.getString("nom");
                    String ref = rs.getString("reference");
                    results.add(Map.of(
                        "value", nom != null ? nom : "",
                        "label", nom != null ? nom : "",
                        "sub", ref != null ? "Réf: " + ref : "",
                        "icon", "inventory_2"
                    ));
                }
            }
        } catch (SQLException e) {
            log.warn("[Suggestions/produits] {}", e.getMessage());
        }
        return ResponseEntity.ok(results);
    }

    // ── Fournisseurs ─────────────────────────────────────────────────

    @GetMapping("/fournisseurs")
    public ResponseEntity<List<Map<String, String>>> suggestFournisseurs(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "10") int max) {
        // Cherche dans factures_achat ET commandes_achat
        String sql = "SELECT DISTINCT fournisseur FROM " +
                     "(SELECT fournisseur FROM factures_achat WHERE fournisseur LIKE ? " +
                     " UNION " +
                     " SELECT fournisseur FROM commandes_achat WHERE fournisseur LIKE ?) AS f " +
                     "WHERE fournisseur IS NOT NULL AND fournisseur != '' " +
                     "ORDER BY fournisseur ASC LIMIT ?";
        List<Map<String, String>> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String pattern = "%" + q + "%";
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setInt(3, max);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String f = rs.getString("fournisseur");
                    if (f != null && !f.isBlank()) {
                        results.add(Map.of(
                            "value", f,
                            "label", f,
                            "icon", "local_shipping"
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            log.warn("[Suggestions/fournisseurs] {}", e.getMessage());
        }
        return ResponseEntity.ok(results);
    }

    // ── Catégories ────────────────────────────────────────────────────

    @GetMapping("/categories")
    public ResponseEntity<List<Map<String, String>>> suggestCategories(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "10") int max) {
        String sql = "SELECT DISTINCT categorie FROM produits " +
                     "WHERE categorie IS NOT NULL AND categorie LIKE ? " +
                     "ORDER BY categorie ASC LIMIT ?";
        List<Map<String, String>> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "%" + q + "%");
            ps.setInt(2, max);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String cat = rs.getString("categorie");
                    if (cat != null && !cat.isBlank()) {
                        results.add(Map.of(
                            "value", cat,
                            "label", cat,
                            "icon", "category"
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            log.warn("[Suggestions/categories] {}", e.getMessage());
        }
        return ResponseEntity.ok(results);
    }

    // ── Libellés (depuis factures, devis, mouvements) ────────────────

    @GetMapping("/libelles")
    public ResponseEntity<List<Map<String, String>>> suggestLibelles(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "10") int max) {
        String sql = "SELECT DISTINCT libelle FROM " +
                     "(SELECT libelle FROM factures WHERE libelle LIKE ? AND libelle IS NOT NULL " +
                     " UNION " +
                     " SELECT libelle FROM devis WHERE libelle LIKE ? AND libelle IS NOT NULL) AS l " +
                     "ORDER BY libelle ASC LIMIT ?";
        List<Map<String, String>> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String pattern = "%" + q + "%";
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setInt(3, max);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String lib = rs.getString("libelle");
                    if (lib != null && !lib.isBlank()) {
                        results.add(Map.of(
                            "value", lib,
                            "label", lib,
                            "icon", "label"
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            log.warn("[Suggestions/libelles] {}", e.getMessage());
        }
        return ResponseEntity.ok(results);
    }

    // ── Utilisateurs (pour assignation) ─────────────────────────────

    @GetMapping("/utilisateurs")
    public ResponseEntity<List<Map<String, String>>> suggestUtilisateurs(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "10") int max) {
        String sql = "SELECT DISTINCT nom, prenom, email FROM utilisateurs " +
                     "WHERE actif = TRUE AND (nom LIKE ? OR prenom LIKE ? OR email LIKE ?) " +
                     "ORDER BY nom ASC LIMIT ?";
        List<Map<String, String>> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String pattern = "%" + q + "%";
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setString(3, pattern);
            ps.setInt(4, max);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String nom = rs.getString("nom");
                    String prenom = rs.getString("prenom");
                    String email = rs.getString("email");
                    String label = ((prenom != null ? prenom + " " : "") + (nom != null ? nom : "")).trim();
                    results.add(Map.of(
                        "value", label,
                        "label", label,
                        "sub", email != null ? email : "",
                        "icon", "manage_accounts"
                    ));
                }
            }
        } catch (SQLException e) {
            log.warn("[Suggestions/utilisateurs] {}", e.getMessage());
        }
        return ResponseEntity.ok(results);
    }
}
