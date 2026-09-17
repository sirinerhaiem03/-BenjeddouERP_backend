package com.benjeddou.erp.controller;

import com.benjeddou.erp.model.*;
import com.benjeddou.erp.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * PortailClientController
 * Endpoints dédiés aux clients externes (rôle CLIENT).
 * Solution : clientMatchesUser() relie les données à l'utilisateur connecté
 * par email, prénom, nom ou nomUtilisateur — sans dépendre d'un client_id fixe.
 */
@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/portail")
public class PortailClientController {

    @Autowired UtilisateurRepository utilisateurRepository;
    @Autowired ClientRepository clientRepository;
    @Autowired CommandeRepository commandeRepository;
    @Autowired FactureRepository factureRepository;
    @Autowired DevisRepository devisRepository;
    @Autowired LigneCommandeRepository ligneCommandeRepository;
    @Autowired LigneDevisRepository ligneDevisRepository;

    // ─────────────────────────────────────────────────────────────
    //  UTILITAIRE 1 : Récupérer l'utilisateur connecté via JWT
    // ─────────────────────────────────────────────────────────────
    private Optional<Utilisateur> getUtilisateurConnecte() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return Optional.empty();
        String nomUtilisateur = auth.getName();
        Optional<Utilisateur> byUsername = utilisateurRepository.findByNomUtilisateur(nomUtilisateur);
        if (byUsername.isPresent()) return byUsername;
        return utilisateurRepository.findByEmail(nomUtilisateur);
    }

    // ─────────────────────────────────────────────────────────────
    //  UTILITAIRE 2 : Vérifier si un Client correspond à l'utilisateur
    //  Résout le problème : clients dans 'utilisateurs' mais
    //  devis/commandes/factures référencent la table 'clients'.
    //  Cherche par email → prénom → nom → nomUtilisateur.
    // ─────────────────────────────────────────────────────────────
    private boolean clientMatchesUser(Client c, Utilisateur u) {
        if (c == null || u == null) return false;

        // 1. Email exact
        String email = u.getEmail();
        if (email != null && !email.isBlank() && email.equalsIgnoreCase(c.getEmail())) return true;

        String clientNom = c.getNom() != null ? c.getNom().toLowerCase().trim() : "";

        // 2. Prénom dans le nom du client (ex: "Karim" dans "Karim Belhadj")
        String prenom = u.getPrenom() != null ? u.getPrenom().toLowerCase().trim() : null;
        if (prenom != null && !prenom.isBlank() && clientNom.contains(prenom)) return true;

        // 3. Nom de famille dans le nom du client
        String nom = u.getNom() != null ? u.getNom().toLowerCase().trim() : null;
        if (nom != null && !nom.isBlank() && clientNom.contains(nom)) return true;

        // 4. NomUtilisateur dans le nom du client
        String username = u.getNomUtilisateur() != null ? u.getNomUtilisateur().toLowerCase().trim() : null;
        if (username != null && !username.isBlank() && clientNom.contains(username)) return true;

        // 5. Correspondance par ID numérique
        // L'admin a créé des données pour client_id=X et l'utilisateur a id=X dans utilisateurs
        if (c.getId() != null && u.getId() != null && c.getId().equals(u.getId())) return true;

        return false;
    }


    // ─────────────────────────────────────────────────────────────
    //  UTILITAIRE 3 : Récupérer ou créer le Client lié à l'utilisateur
    //  (utilisé pour les endpoints qui écrivent des données)
    // ─────────────────────────────────────────────────────────────
    private Optional<Client> getClientConnecte() {
        Optional<Utilisateur> userOpt = getUtilisateurConnecte();
        if (userOpt.isEmpty()) return Optional.empty();
        Utilisateur u = userOpt.get();
        if (u.getRole() != Role.CLIENT) return Optional.empty();

        // Cherche par email
        Optional<Client> byEmail = clientRepository.findByEmail(u.getEmail());
        if (byEmail.isPresent()) return byEmail;

        // Cherche par prénom (cas où admin a créé le client sous le prénom)
        if (u.getPrenom() != null && !u.getPrenom().isBlank()) {
            List<Client> byPrenom = clientRepository.findByNomContainingIgnoreCase(u.getPrenom().trim());
            if (!byPrenom.isEmpty()) {
                Client c = byPrenom.get(0);
                c.setEmail(u.getEmail()); // lien permanent
                return Optional.of(clientRepository.save(c));
            }
        }

        // Cherche par nom
        if (u.getNom() != null && !u.getNom().isBlank()) {
            List<Client> byNom = clientRepository.findByNomContainingIgnoreCase(u.getNom().trim());
            if (!byNom.isEmpty()) {
                Client c = byNom.get(0);
                c.setEmail(u.getEmail());
                return Optional.of(clientRepository.save(c));
            }
        }

        // Auto-créer si aucune correspondance
        String nomClient = (u.getPrenom() != null && !u.getPrenom().isBlank())
                ? u.getPrenom() + (u.getNom() != null ? " " + u.getNom() : "")
                : (u.getNomUtilisateur() != null ? u.getNomUtilisateur() : "Client");
        Client c = Client.builder()
                .nom(nomClient).email(u.getEmail())
                .telephone(u.getTelephone()).adresse(u.getAdresse())
                .build();
        return Optional.of(clientRepository.save(c));
    }

    // ─────────────────────────────────────────────────────────────
    //  DASHBOARD — KPIs du client connecté
    // ─────────────────────────────────────────────────────────────
    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard() {
        Optional<Utilisateur> userOpt = getUtilisateurConnecte();
        if (userOpt.isEmpty()) return ResponseEntity.status(403).build();
        Utilisateur u = userOpt.get();

        List<Facture> factures = factureRepository.findAll().stream()
                .filter(f -> f.getCommande() != null && clientMatchesUser(f.getCommande().getClient(), u))
                .collect(Collectors.toList());

        List<Commande> commandes = commandeRepository.findAll().stream()
                .filter(c -> clientMatchesUser(c.getClient(), u))
                .collect(Collectors.toList());

        List<Devis> devis = devisRepository.findAll().stream()
                .filter(d -> clientMatchesUser(d.getClient(), u))
                .filter(d -> !"DEMANDE_CLIENT".equals(d.getStatut()))
                .collect(Collectors.toList());

        long facturesImpayees = factures.stream()
                .filter(f -> "EN_ATTENTE".equals(f.getStatut()) || "IMPAYEE".equals(f.getStatut())).count();
        BigDecimal montantDu = factures.stream()
                .filter(f -> "EN_ATTENTE".equals(f.getStatut()) || "IMPAYEE".equals(f.getStatut()))
                .map(f -> f.getMontantTotal() != null ? f.getMontantTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long devisEnAttente = devis.stream().filter(d -> "ENVOYE".equals(d.getStatut())).count();
        long commandesEnCours = commandes.stream().filter(c -> "EN_ATTENTE".equals(c.getStatut())).count();
        long facturesPayees = factures.stream().filter(f -> "PAYEE".equals(f.getStatut())).count();

        String nomAffiche = (u.getPrenom() != null ? u.getPrenom() + " " : "")
                + (u.getNom() != null ? u.getNom() : u.getNomUtilisateur());

        return ResponseEntity.ok(Map.of(
                "client", Map.of("id", u.getId(), "nom", nomAffiche.trim(), "email", u.getEmail()),
                "kpis", Map.of(
                        "facturesImpayees", facturesImpayees,
                        "montantDu", montantDu,
                        "devisEnAttente", devisEnAttente,
                        "commandesEnCours", commandesEnCours,
                        "facturesPayees", facturesPayees,
                        "totalFactures", factures.size(),
                        "totalCommandes", commandes.size(),
                        "totalDevis", devis.size()
                )
        ));
    }

    // ─────────────────────────────────────────────────────────────
    //  FACTURES
    // ─────────────────────────────────────────────────────────────
    @GetMapping("/factures")
    public ResponseEntity<?> getFactures() {
        Optional<Utilisateur> userOpt = getUtilisateurConnecte();
        if (userOpt.isEmpty()) return ResponseEntity.ok(Collections.emptyList());
        Utilisateur u = userOpt.get();

        List<Facture> factures = factureRepository.findAll().stream()
                .filter(f -> f.getCommande() != null && clientMatchesUser(f.getCommande().getClient(), u))
                .collect(Collectors.toList());
        return ResponseEntity.ok(factures);
    }

    @GetMapping("/factures/{id}")
    public ResponseEntity<?> getFactureDetail(@PathVariable Long id) {
        Optional<Utilisateur> userOpt = getUtilisateurConnecte();
        if (userOpt.isEmpty()) return ResponseEntity.status(403).body("Accès refusé");
        Utilisateur u = userOpt.get();

        return factureRepository.findById(id)
                .filter(f -> f.getCommande() != null && clientMatchesUser(f.getCommande().getClient(), u))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ─────────────────────────────────────────────────────────────
    //  DEVIS
    // ─────────────────────────────────────────────────────────────
    @GetMapping("/debug-profil")
    public ResponseEntity<?> debugProfil() {
        Optional<Utilisateur> userOpt = getUtilisateurConnecte();
        if (userOpt.isEmpty()) return ResponseEntity.status(403).body("Non connecté");
        Utilisateur u = userOpt.get();

        Map<String, Object> debug = new LinkedHashMap<>();
        debug.put("utilisateur_id",    u.getId());
        debug.put("utilisateur_prenom", u.getPrenom());
        debug.put("utilisateur_nom",   u.getNom());
        debug.put("utilisateur_email", u.getEmail());
        debug.put("utilisateur_role",  u.getRole());

        long nbDevis = devisRepository.findAll().stream()
                .filter(d -> clientMatchesUser(d.getClient(), u)).count();
        long nbCommandes = commandeRepository.findAll().stream()
                .filter(c -> clientMatchesUser(c.getClient(), u)).count();
        long nbFactures = factureRepository.findAll().stream()
                .filter(f -> f.getCommande() != null && clientMatchesUser(f.getCommande().getClient(), u)).count();

        debug.put("nb_devis_lies",    nbDevis);
        debug.put("nb_commandes_lies", nbCommandes);
        debug.put("nb_factures_lies",  nbFactures);

        return ResponseEntity.ok(debug);
    }

    @GetMapping("/devis")
    public ResponseEntity<?> getDevis() {
        Optional<Utilisateur> userOpt = getUtilisateurConnecte();
        if (userOpt.isEmpty()) return ResponseEntity.ok(Collections.emptyList());
        Utilisateur u = userOpt.get();

        List<Devis> devisList = devisRepository.findAll().stream()
                .filter(d -> clientMatchesUser(d.getClient(), u))
                .filter(d -> !"DEMANDE_CLIENT".equals(d.getStatut()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(devisList);
    }

    @GetMapping("/devis/{id}")
    public ResponseEntity<?> getDevisDetail(@PathVariable Long id) {
        Optional<Utilisateur> userOpt = getUtilisateurConnecte();
        if (userOpt.isEmpty()) return ResponseEntity.status(403).body("Accès refusé");
        Utilisateur u = userOpt.get();

        return devisRepository.findById(id)
                .filter(d -> clientMatchesUser(d.getClient(), u))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/devis/{id}/reponse")
    public ResponseEntity<?> repondreDevis(@PathVariable Long id,
                                           @RequestBody Map<String, String> body) {
        Optional<Utilisateur> userOpt = getUtilisateurConnecte();
        if (userOpt.isEmpty()) return ResponseEntity.status(403).body("Accès refusé");
        Utilisateur u = userOpt.get();
        String action = body.get("action");

        if (!"ACCEPTE".equals(action) && !"REFUSE".equals(action)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Action invalide. Valeurs acceptées : ACCEPTE, REFUSE"));
        }

        return devisRepository.findById(id)
                .filter(d -> clientMatchesUser(d.getClient(), u))
                .map(d -> {
                    if (!"ENVOYE".equals(d.getStatut())) {
                        return ResponseEntity.badRequest()
                                .body((Object) Map.of("error", "Ce devis ne peut plus être modifié (statut : " + d.getStatut() + ")"));
                    }
                    d.setStatut(action);
                    devisRepository.save(d);
                    String msg = "ACCEPTE".equals(action)
                            ? "✅ Devis accepté ! Le service commercial a été notifié."
                            : "❌ Devis refusé. Le service commercial a été notifié.";
                    return ResponseEntity.ok((Object) Map.of("message", msg, "statut", action));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ─────────────────────────────────────────────────────────────
    //  COMMANDES
    // ─────────────────────────────────────────────────────────────
    @GetMapping("/commandes")
    public ResponseEntity<?> getCommandes() {
        Optional<Utilisateur> userOpt = getUtilisateurConnecte();
        if (userOpt.isEmpty()) return ResponseEntity.ok(Collections.emptyList());
        Utilisateur u = userOpt.get();

        List<Commande> commandes = commandeRepository.findAll().stream()
                .filter(c -> clientMatchesUser(c.getClient(), u))
                .collect(Collectors.toList());
        return ResponseEntity.ok(commandes);
    }

    @GetMapping("/commandes/{id}")
    public ResponseEntity<?> getCommandeDetail(@PathVariable Long id) {
        Optional<Utilisateur> userOpt = getUtilisateurConnecte();
        if (userOpt.isEmpty()) return ResponseEntity.status(403).body("Accès refusé");
        Utilisateur u = userOpt.get();

        return commandeRepository.findById(id)
                .filter(c -> clientMatchesUser(c.getClient(), u))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ─────────────────────────────────────────────────────────────
    //  PROFIL CLIENT
    // ─────────────────────────────────────────────────────────────
    @GetMapping("/profil")
    public ResponseEntity<?> getProfil() {
        Optional<Utilisateur> userOpt = getUtilisateurConnecte();
        if (userOpt.isEmpty()) return ResponseEntity.status(403).build();
        Utilisateur u = userOpt.get();
        Map<String, Object> profil = new LinkedHashMap<>();
        profil.put("id",             u.getId());
        profil.put("nom",            u.getNom() != null ? u.getNom() : u.getNomUtilisateur());
        profil.put("prenom",         u.getPrenom());
        profil.put("email",          u.getEmail());
        profil.put("telephone",      u.getTelephone());
        profil.put("adresse",        u.getAdresse());
        profil.put("societe",        u.getSociete());
        profil.put("dateCreation",   u.getDateCreation() != null ? u.getDateCreation().toString() : null);
        profil.put("nomUtilisateur", u.getNomUtilisateur());
        return ResponseEntity.ok(profil);
    }

    @PutMapping("/profil")
    public ResponseEntity<?> updateProfil(@RequestBody Map<String, String> body) {
        Optional<Utilisateur> userOpt = getUtilisateurConnecte();
        if (userOpt.isEmpty()) return ResponseEntity.status(403).build();
        Utilisateur u = userOpt.get();

        if (body.containsKey("nom")       && body.get("nom")       != null && !body.get("nom").isBlank())
            u.setNom(body.get("nom").trim());
        if (body.containsKey("prenom")    && body.get("prenom")    != null) u.setPrenom(body.get("prenom"));
        if (body.containsKey("telephone")) u.setTelephone(body.get("telephone"));
        if (body.containsKey("adresse"))   u.setAdresse(body.get("adresse"));
        if (body.containsKey("societe"))   u.setSociete(body.get("societe"));
        utilisateurRepository.save(u);

        Map<String, Object> profil = new LinkedHashMap<>();
        profil.put("id",             u.getId());
        profil.put("nom",            u.getNom() != null ? u.getNom() : u.getNomUtilisateur());
        profil.put("prenom",         u.getPrenom());
        profil.put("email",          u.getEmail());
        profil.put("telephone",      u.getTelephone());
        profil.put("adresse",        u.getAdresse());
        profil.put("societe",        u.getSociete());
        profil.put("dateCreation",   u.getDateCreation() != null ? u.getDateCreation().toString() : null);
        profil.put("nomUtilisateur", u.getNomUtilisateur());
        return ResponseEntity.ok(profil);
    }

    // ─────────────────────────────────────────────────────────────
    //  DEMANDE DE DEVIS — soumise par le client
    // ─────────────────────────────────────────────────────────────
    @PostMapping("/devis-request")
    public ResponseEntity<?> soumettreDemandeDevis(@RequestBody Map<String, Object> body) {
        Optional<Client> clientOpt = getClientConnecte();
        if (clientOpt.isEmpty()) return ResponseEntity.status(403).body(Map.of("error", "Client introuvable"));
        Client client = clientOpt.get();

        String ref      = "DR-" + System.currentTimeMillis() % 1_000_000;
        String objet    = body.getOrDefault("objet",    "Demande sans objet").toString();
        String urgence  = body.getOrDefault("urgence",  "NORMAL").toString();
        String remarques = body.getOrDefault("remarques", "").toString();
        String lignesJson = body.get("lignes") != null ? body.get("lignes").toString() : "[]";

        String notes = "[DEMANDE_CLIENT]\nObjet: " + objet + "\nUrgence: " + urgence
                + "\nRemarques: " + remarques + "\nLignes: " + lignesJson;

        Devis devis = Devis.builder()
                .numeroDevis(ref).statut("DEMANDE_CLIENT")
                .montantTotal(BigDecimal.ZERO)
                .notes(notes.length() > 1999 ? notes.substring(0, 1999) : notes)
                .client(client).build();
        devisRepository.save(devis);

        return ResponseEntity.ok(Map.of("reference", ref, "message", "Demande envoyée avec succès", "id", devis.getId()));
    }

    @GetMapping("/devis-requests")
    public ResponseEntity<?> getMesDemandesDevis() {
        Optional<Utilisateur> userOpt = getUtilisateurConnecte();
        if (userOpt.isEmpty()) return ResponseEntity.ok(Collections.emptyList());
        Utilisateur u = userOpt.get();

        List<Map<String, Object>> demandes = devisRepository.findAll().stream()
                .filter(d -> clientMatchesUser(d.getClient(), u) && "DEMANDE_CLIENT".equals(d.getStatut()))
                .sorted(Comparator.comparing(Devis::getDateCreation,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(d -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id",     d.getId());
                    m.put("ref",    d.getNumeroDevis());
                    m.put("date",   d.getDateCreation() != null ? d.getDateCreation().toLocalDate().toString() : "");
                    m.put("statut", "ENVOYEE");
                    String objt = "", urg = "NORMAL";
                    if (d.getNotes() != null) {
                        for (String line : d.getNotes().split("\n")) {
                            if (line.startsWith("Objet: "))   objt = line.substring(7).trim();
                            if (line.startsWith("Urgence: ")) urg  = line.substring(9).trim();
                        }
                    }
                    m.put("objet", objt); m.put("urgence", urg);
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(demandes);
    }

    // ─────────────────────────────────────────────────────────────
    //  RELEVÉ DE COMPTE
    // ─────────────────────────────────────────────────────────────
    @GetMapping("/releve")
    public ResponseEntity<?> getReleve() {
        Optional<Utilisateur> userOpt = getUtilisateurConnecte();
        if (userOpt.isEmpty()) return ResponseEntity.ok(Collections.emptyList());
        Utilisateur u = userOpt.get();

        List<Facture> factures = factureRepository.findAll().stream()
                .filter(f -> f.getCommande() != null && clientMatchesUser(f.getCommande().getClient(), u))
                .sorted(Comparator.comparing(Facture::getDateEmission, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        List<Map<String, Object>> lignes = new ArrayList<>();
        BigDecimal solde = BigDecimal.ZERO;

        for (Facture f : factures) {
            BigDecimal montant = f.getMontantTotal() != null ? f.getMontantTotal() : BigDecimal.ZERO;
            solde = solde.add(montant);

            Map<String, Object> ligneDebit = new LinkedHashMap<>();
            ligneDebit.put("date",      f.getDateEmission() != null ? f.getDateEmission().toLocalDate().toString() : "");
            ligneDebit.put("reference", f.getNumeroFacture());
            ligneDebit.put("libelle",   "Facture " + f.getNumeroFacture());
            ligneDebit.put("type",      "FACTURE");
            ligneDebit.put("debit",     montant);
            ligneDebit.put("credit",    BigDecimal.ZERO);
            ligneDebit.put("solde",     solde);
            lignes.add(ligneDebit);

            if ("PAYEE".equals(f.getStatut())) {
                solde = solde.subtract(montant);
                Map<String, Object> ligneCredit = new LinkedHashMap<>();
                ligneCredit.put("date",      f.getDateModification() != null
                        ? f.getDateModification().toLocalDate().toString()
                        : f.getDateEmission().toLocalDate().toString());
                ligneCredit.put("reference", "PAY-" + f.getNumeroFacture());
                ligneCredit.put("libelle",   "Paiement facture " + f.getNumeroFacture());
                ligneCredit.put("type",      "PAIEMENT");
                ligneCredit.put("debit",     BigDecimal.ZERO);
                ligneCredit.put("credit",    montant);
                ligneCredit.put("solde",     solde);
                lignes.add(ligneCredit);
            }
        }
        return ResponseEntity.ok(lignes);
    }

    // ─────────────────────────────────────────────────────────────
    //  PAYER UNE FACTURE (Client - Sauvegarde BDD permanente)
    // ─────────────────────────────────────────────────────────────
    @PostMapping("/factures/{id}/payer")
    public ResponseEntity<?> payerFacture(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        Optional<Utilisateur> userOpt = getUtilisateurConnecte();
        if (userOpt.isEmpty()) return ResponseEntity.badRequest().body(Map.of("message", "Utilisateur non authentifié"));

        Optional<Facture> factureOpt = factureRepository.findById(id);
        if (factureOpt.isEmpty()) return ResponseEntity.notFound().build();

        Facture f = factureOpt.get();
        f.setStatut("PAYEE");
        factureRepository.save(f);

        // Mettre à jour la commande associée et le client si disponible
        if (f.getCommande() != null) {
            f.getCommande().setStatut("PAYEE");
            commandeRepository.save(f.getCommande());
            if (f.getCommande().getClient() != null) {
                Client c = f.getCommande().getClient();
                clientRepository.save(c);
            }
        }

        return ResponseEntity.ok(Map.of(
            "message", "Facture enregistrée comme PAYÉE avec succès",
            "statut", "PAYEE",
            "factureId", id
        ));
    }
}
