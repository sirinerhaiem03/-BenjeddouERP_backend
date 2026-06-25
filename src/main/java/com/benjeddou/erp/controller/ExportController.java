package com.benjeddou.erp.controller;

import com.benjeddou.erp.model.*;
import com.benjeddou.erp.repository.*;
import com.benjeddou.erp.security.services.UserDetailsImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Export des données utilisateur — accessible même si le trial est expiré.
 * GET /api/export/mes-donnees
 */
@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/export")
public class ExportController {

    @Autowired UtilisateurRepository  utilisateurRepository;
    @Autowired AbonnementRepository   abonnementRepository;

    /**
     * Exporte toutes les données du client connecté en JSON.
     * Accessible même si le trial est expiré (JWT valide requis).
     */
    @GetMapping("/mes-donnees")
    public ResponseEntity<?> exporterMesDonnees() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserDetailsImpl)) {
            return ResponseEntity.status(401).body(Map.of("error", "Non authentifié."));
        }

        UserDetailsImpl userDetails = (UserDetailsImpl) auth.getPrincipal();
        Long userId = userDetails.getId();

        Optional<Utilisateur> userOpt = utilisateurRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Utilisateur user = userOpt.get();

        // Construire le payload d'export
        Map<String, Object> export = new LinkedHashMap<>();
        export.put("exportDate",    LocalDateTime.now().toString());
        export.put("exportVersion", "1.0");
        export.put("plateforme",    "BENJEDDOU ERP");

        // ── Profil ──────────────────────────────────────────────────
        Map<String, Object> profil = new LinkedHashMap<>();
        profil.put("id",               user.getId());
        profil.put("nomUtilisateur",   user.getNomUtilisateur());
        profil.put("email",            user.getEmail());
        profil.put("prenom",           user.getPrenom());
        profil.put("nom",              user.getNom());
        profil.put("telephone",        user.getTelephone());
        profil.put("societe",          user.getSociete());
        profil.put("adresse",          user.getAdresse());
        profil.put("dateCreation",     user.getDateCreation() != null ? user.getDateCreation().toString() : null);
        profil.put("modeTrial",        user.getModeTrial());
        profil.put("nbUtilisations",   user.getNbUtilisations());
        profil.put("nbUtilisationsMax",user.getNbUtilisationsMax());
        profil.put("statutCompte",     user.getStatutCompte() != null ? user.getStatutCompte().name() : null);
        export.put("profil", profil);

        // ── Abonnements ──────────────────────────────────────────────
        try {
            List<Abonnement> abonnements = abonnementRepository.findByClientOrderByDateSoumissionDesc(user);
            List<Map<String, Object>> abList = new ArrayList<>();
            for (Abonnement a : abonnements) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id",              a.getId());
                m.put("typePlan",        a.getTypePlan() != null ? a.getTypePlan().name() : null);
                m.put("statut",          a.getStatut() != null ? a.getStatut().name() : null);
                m.put("dateDebut",       a.getDateDebut() != null ? a.getDateDebut().toString() : null);
                m.put("dateFin",         a.getDateFin() != null ? a.getDateFin().toString() : null);
                m.put("prix",            a.getPrix());
                m.put("methodePaiement", a.getMethodePaiement());
                m.put("dureeMois",       a.getDureeMois());
                abList.add(m);
            }
            export.put("abonnements", abList);
        } catch (Exception e) {
            export.put("abonnements", List.of());
        }

        // Message résumé
        export.put("message",
            "Export généré le " + LocalDateTime.now() + ". " +
            "Vos données sont conservées. Pour les récupérer complètement, " +
            "veuillez activer votre abonnement sur BENJEDDOU ERP.");

        return ResponseEntity.ok(export);
    }
}
