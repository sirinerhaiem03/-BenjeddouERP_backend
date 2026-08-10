package com.benjeddou.erp.repository;

import com.benjeddou.erp.model.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {
    Optional<Client> findByEmail(String email);
    Optional<Client> findByNom(String nom);
    Boolean existsByEmail(String email);
    java.util.List<Client> findByNomContainingIgnoreCase(String nom);

}
