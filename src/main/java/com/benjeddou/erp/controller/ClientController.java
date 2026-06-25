package com.benjeddou.erp.controller;

import com.benjeddou.erp.model.Client;
import com.benjeddou.erp.repository.ClientRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/clients")
public class ClientController {

    @Autowired
    ClientRepository clientRepository;

    @GetMapping("")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMMERCIAL') or hasRole('COMPTABLE')")
    public List<Client> getTousLesClients() {
        return clientRepository.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMMERCIAL') or hasRole('COMPTABLE')")
    public ResponseEntity<Client> getClientParId(@PathVariable Long id) {
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
