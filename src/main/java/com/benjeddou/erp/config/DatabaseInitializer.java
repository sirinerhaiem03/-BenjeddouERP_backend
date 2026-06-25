package com.benjeddou.erp.config;

import com.benjeddou.erp.model.Role;
import com.benjeddou.erp.model.Utilisateur;
import com.benjeddou.erp.repository.UtilisateurRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DatabaseInitializer implements CommandLineRunner {

    @Autowired
    UtilisateurRepository utilisateurRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // Initialiser l'Utilisateur Admin par défaut
        if (utilisateurRepository.findByNomUtilisateur("admin").isEmpty()) {
            
            Utilisateur admin = Utilisateur.builder()
                    .nomUtilisateur("admin")
                    .email("admin@benjeddou.com")
                    .motDePasse(passwordEncoder.encode("admin123"))
                    .prenom("Admin")
                    .nom("Benjeddou")
                    .actif(true)
                    .languePreferee("fr")
                    .role(Role.ADMIN)
                    .build();
            
            utilisateurRepository.save(admin);
            System.out.println(">>> Utilisateur Admin ('admin' / 'admin123') cree automatiquement avec le role ADMIN !");
        }
    }
}
