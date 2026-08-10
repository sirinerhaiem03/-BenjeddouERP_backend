package com.benjeddou.erp.repository;

import com.benjeddou.erp.model.AuditLog;
import com.benjeddou.erp.model.AuditLog.ActionAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** Tous les logs paginés, triés du plus récent au plus ancien */
    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Logs d'un utilisateur précis */
    Page<AuditLog> findByUtilisateurIdOrderByCreatedAtDesc(Long utilisateurId, Pageable pageable);

    /** Logs par action */
    Page<AuditLog> findByActionOrderByCreatedAtDesc(ActionAudit action, Pageable pageable);

    /** Logs par IP (détection bruteforce) */
    List<AuditLog> findByAdresseIpAndCreatedAtAfterOrderByCreatedAtDesc(
        String adresseIp, LocalDateTime depuis);

    /** Compter les tentatives échouées depuis une IP dans la fenêtre glissante */
    @Query("""
        SELECT COUNT(a) FROM AuditLog a
        WHERE a.adresseIp = :ip
          AND a.action = 'LOGIN_ECHEC'
          AND a.createdAt > :depuis
        """)
    long countEchecsParIpDepuis(@Param("ip") String ip, @Param("depuis") LocalDateTime depuis);

    /** Recherche full-text sur nomUtilisateur, details, IP */
    @Query("""
        SELECT a FROM AuditLog a
        WHERE (:q IS NULL OR :q = ''
            OR LOWER(a.nomUtilisateur) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(a.adresseIp) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(a.details) LIKE LOWER(CONCAT('%', :q, '%')))
        ORDER BY a.createdAt DESC
        """)
    Page<AuditLog> rechercher(@Param("q") String q, Pageable pageable);

    /** Derniers logs de sécurité critiques (pour dashboard SuperAdmin) */
    @Query("""
        SELECT a FROM AuditLog a
        WHERE a.action IN ('LOGIN_ECHEC', 'RATE_LIMIT_BLOQUE', 'SESSION_REVOQUEE',
                           'COMPTE_BLOQUE', 'ROLE_MODIFIE')
        ORDER BY a.createdAt DESC
        """)
    List<AuditLog> findLogsCritiques(Pageable pageable);

    /** Stats par action pour graphique */
    @Query("""
        SELECT a.action, COUNT(a) FROM AuditLog a
        WHERE a.createdAt > :depuis
        GROUP BY a.action
        ORDER BY COUNT(a) DESC
        """)
    List<Object[]> statsParAction(@Param("depuis") LocalDateTime depuis);
}
