package com.benjeddou.erp.repository;

import com.benjeddou.erp.model.ConnexionLog;
import com.benjeddou.erp.model.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ConnexionLogRepository extends JpaRepository<ConnexionLog, Long> {
    List<ConnexionLog> findByUtilisateurOrderByDateConnexionDesc(Utilisateur utilisateur);
    List<ConnexionLog> findTop10ByUtilisateurOrderByDateConnexionDesc(Utilisateur utilisateur);
}
