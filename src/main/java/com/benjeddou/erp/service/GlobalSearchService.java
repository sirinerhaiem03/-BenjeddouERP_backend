package com.benjeddou.erp.service;

import com.benjeddou.erp.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service de recherche globale cross-modules.
 * Cherche dans toutes les entités par mot-clé : nom, email, téléphone, matricule, etc.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GlobalSearchService {

    private final UtilisateurRepository utilisateurRepo;
    private final ClientRepository clientRepo;
    private final FournisseurRepository fournisseurRepo;
    private final ProduitRepository produitRepo;

    /**
     * Résultat de recherche unifié multi-entités.
     */
    public record ResultatRecherche(
        String type,         // UTILISATEUR, CLIENT, FOURNISSEUR, PRODUIT
        Long id,
        String titre,        // nom principal affiché
        String sousTitre,    // info secondaire (email, téléphone...)
        String detail,       // info tertiaire (adresse, matricule...)
        String route,        // route Angular cible
        String icone         // Material Symbol
    ) {}

    /**
     * Recherche globale dans toutes les entités.
     * @param motCle  terme à chercher (nom, email, téléphone, matricule...)
     * @param limite  nombre max de résultats par catégorie (défaut 5)
     */
    public Map<String, List<ResultatRecherche>> rechercherTout(String motCle, int limite) {
        if (motCle == null || motCle.trim().length() < 2) {
            return Collections.emptyMap();
        }
        String q = motCle.trim().toLowerCase();
        Map<String, List<ResultatRecherche>> resultats = new LinkedHashMap<>();

        // ── Utilisateurs ────────────────────────────────────────────────
        List<ResultatRecherche> utilisateurs = utilisateurRepo.findAll().stream()
            .filter(u -> matches(q,
                u.getNomUtilisateur(), u.getNom(), u.getPrenom(),
                u.getEmail(), u.getTelephone(), u.getSociete()))
            .limit(limite)
            .map(u -> new ResultatRecherche(
                "UTILISATEUR", u.getId(),
                (u.getPrenom() != null ? u.getPrenom() + " " : "") + (u.getNom() != null ? u.getNom() : u.getNomUtilisateur()),
                u.getEmail(),
                u.getRole() != null ? u.getRole().name() : "",
                "admin-users",
                "manage_accounts"
            ))
            .toList();
        if (!utilisateurs.isEmpty()) resultats.put("Utilisateurs", utilisateurs);

        // ── Clients ──────────────────────────────────────────────────────
        List<ResultatRecherche> clients = clientRepo.findAll().stream()
            .filter(c -> matches(q, c.getNom(), c.getEmail(), c.getTelephone(),
                                    c.getAdresse(), c.getMatriculeFiscale()))
            .limit(limite)
            .map(c -> new ResultatRecherche(
                "CLIENT", c.getId(),
                c.getNom(),
                c.getEmail(),
                c.getTelephone() != null ? "Tél: " + c.getTelephone() : c.getMatriculeFiscale(),
                "clients",
                "person"
            ))
            .toList();
        if (!clients.isEmpty()) resultats.put("Clients", clients);

        // ── Fournisseurs ──────────────────────────────────────────────────
        List<ResultatRecherche> fournisseurs = fournisseurRepo.findAll().stream()
            .filter(f -> matches(q, f.getNom(), f.getEmail(), f.getTelephone(),
                                    f.getAdresse(), f.getMatriculeFiscale()))
            .limit(limite)
            .map(f -> new ResultatRecherche(
                "FOURNISSEUR", f.getId(),
                f.getNom(),
                f.getEmail(),
                f.getTelephone() != null ? "Tél: " + f.getTelephone() : f.getMatriculeFiscale(),
                "achats",
                "local_shipping"
            ))
            .toList();
        if (!fournisseurs.isEmpty()) resultats.put("Fournisseurs", fournisseurs);

        // ── Produits ─────────────────────────────────────────────────────
        List<ResultatRecherche> produits = produitRepo.findAll().stream()
            .filter(p -> matches(q, p.getNom(), p.getDescription(),
                                    p.getCategorie(), p.getReference()))
            .limit(limite)
            .map(p -> new ResultatRecherche(
                "PRODUIT", p.getId(),
                p.getNom(),
                p.getCategorie() != null ? p.getCategorie() : "",
                p.getReference() != null ? "Réf: " + p.getReference() : "",
                "products",
                "inventory_2"
            ))
            .toList();
        if (!produits.isEmpty()) resultats.put("Produits", produits);

        log.debug("Recherche '{}' → {} catégories, {} résultats total", motCle,
            resultats.size(), resultats.values().stream().mapToLong(List::size).sum());

        return resultats;
    }

    /** Vérifie si le mot-clé est présent dans l'un des champs (null-safe) */
    private boolean matches(String q, String... champs) {
        for (String champ : champs) {
            if (champ != null && champ.toLowerCase().contains(q)) return true;
        }
        return false;
    }

    /**
     * Auto-complétion rapide : retourne les 8 premiers résultats combinés
     * pour l'affichage dans la barre de recherche.
     */
    public List<ResultatRecherche> autocomplete(String motCle) {
        Map<String, List<ResultatRecherche>> tous = rechercherTout(motCle, 3);
        return tous.values().stream()
            .flatMap(List::stream)
            .limit(8)
            .toList();
    }
}
