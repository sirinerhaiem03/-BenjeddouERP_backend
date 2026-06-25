package com.benjeddou.erp.repository;

import com.benjeddou.erp.model.DocumentKyc;
import com.benjeddou.erp.model.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DocumentKycRepository extends JpaRepository<DocumentKyc, Long> {
    List<DocumentKyc> findByUtilisateur(Utilisateur utilisateur);
    List<DocumentKyc> findByUtilisateurOrderByDateSoumissionDesc(Utilisateur utilisateur);
}
