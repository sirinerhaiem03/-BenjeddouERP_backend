package com.benjeddou.erp.controller;

import com.benjeddou.erp.model.Client;
import com.benjeddou.erp.model.Role;
import com.benjeddou.erp.model.Utilisateur;
import com.benjeddou.erp.repository.ClientRepository;
import com.benjeddou.erp.repository.UtilisateurRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/clients")
public class ClientController {

    @Autowired
    ClientRepository clientRepository;

    @Autowired
    UtilisateurRepository utilisateurRepository;

    /**
     * Retourne les utilisateurs ayant le rôle CLIENT depuis la table utilisateurs.
     * Format retourné compatible avec le frontend (id, nom, email, telephone, adresse).
     */
    @GetMapping("")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMMERCIAL') or hasRole('COMPTABLE')")
    public List<Map<String, Object>> getTousLesClients() {
        return utilisateurRepository.findAll().stream()
            .filter(u -> u.getRole() == Role.CLIENT)
            .map(u -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", u.getId());
                map.put("nom", buildNomComplet(u));
                map.put("prenom", u.getPrenom() != null ? u.getPrenom() : "");
                map.put("email", u.getEmail());
                map.put("telephone", u.getTelephone() != null ? u.getTelephone() : "");
                map.put("adresse", u.getAdresse() != null ? u.getAdresse() : "");
                map.put("societe", u.getSociete() != null ? u.getSociete() : "");
                map.put("nomUtilisateur", u.getNomUtilisateur());
                map.put("source", "utilisateur"); // pour distinguer si besoin
                return map;
            })
            .collect(Collectors.toList());
    }

    private String buildNomComplet(Utilisateur u) {
        String prenom = u.getPrenom() != null ? u.getPrenom().trim() : "";
        String nom    = u.getNom()    != null ? u.getNom().trim()    : "";
        String societe = u.getSociete() != null ? u.getSociete().trim() : "";
        // Priorité : société → prénom+nom → nomUtilisateur
        if (!societe.isBlank()) return societe;
        String full = (prenom + " " + nom).trim();
        return full.isBlank() ? u.getNomUtilisateur() : full;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMMERCIAL') or hasRole('COMPTABLE')")
    public ResponseEntity<?> getClientParId(@PathVariable Long id) {
        // Cherche d'abord dans utilisateurs (ROLE_CLIENT)
        Optional<Utilisateur> userOpt = utilisateurRepository.findById(id);
        if (userOpt.isPresent() && userOpt.get().getRole() == Role.CLIENT) {
            Utilisateur u = userOpt.get();
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", u.getId());
            map.put("nom", buildNomComplet(u));
            map.put("prenom", u.getPrenom() != null ? u.getPrenom() : "");
            map.put("email", u.getEmail());
            map.put("telephone", u.getTelephone() != null ? u.getTelephone() : "");
            map.put("adresse", u.getAdresse() != null ? u.getAdresse() : "");
            map.put("societe", u.getSociete() != null ? u.getSociete() : "");
            return ResponseEntity.ok(map);
        }
        // Fallback sur la table clients
        return clientRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMMERCIAL')")
    public ResponseEntity<?> creerClient(@Valid @RequestBody Client client) {
        if (clientRepository.existsByEmail(client.getEmail())) {
            return ResponseEntity.badRequest().body("Erreur : Un client avec cet email existe déjà !");
        }
        Client nouveau = clientRepository.save(client);
        return ResponseEntity.ok(nouveau);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMMERCIAL')")
    public ResponseEntity<?> modifierClient(@PathVariable Long id, @Valid @RequestBody Client clientDetails) {
        return clientRepository.findById(id)
                .map(client -> {
                    client.setNom(clientDetails.getNom());
                    client.setEmail(clientDetails.getEmail());
                    client.setTelephone(clientDetails.getTelephone());
                    client.setAdresse(clientDetails.getAdresse());
                    client.setMatriculeFiscale(clientDetails.getMatriculeFiscale());
                    return ResponseEntity.ok(clientRepository.save(client));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMMERCIAL')")
    public ResponseEntity<?> supprimerClient(@PathVariable Long id) {
        return clientRepository.findById(id)
                .map(client -> {
                    clientRepository.delete(client);
                    return ResponseEntity.ok().body("Client supprimé avec succès !");
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
