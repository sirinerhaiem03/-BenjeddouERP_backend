package com.benjeddou.erp.repository;

import com.benjeddou.erp.model.ConnexionLog;
import com.benjeddou.erp.model.ConnexionLog.StatutSession;
import com.benjeddou.erp.model.Utilisateur;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConnexionLogRepository extends JpaRepository<ConnexionLog, Long> {

    // ── Historique utilisateur ───────────────────────────────────
    List<ConnexionLog> findByUtilisateurOrderByDateConnexionDesc(Utilisateur utilisateur);
    List<ConnexionLog> findTop10ByUtilisateurOrderByDateConnexionDesc(Utilisateur utilisateur);

    // ── Sessions actives ─────────────────────────────────────────
    List<ConnexionLog> findByStatut(StatutSession statut);

    Optional<ConnexionLog> findBySessionToken(String sessionToken);

    List<ConnexionLog> findByUtilisateurAndStatut(Utilisateur utilisateur, StatutSession statut);

    // ── Toutes les sessions actives avec détails (SuperAdmin) ─────
    @Query("""
        SELECT c FROM ConnexionLog c
        LEFT JOIN FETCH c.utilisateur u
        WHERE c.statut = 'ACTIVE'
        ORDER BY c.dateConnexion DESC
        """)
    List<ConnexionLog> findAllSessionsActives();

    // ── Pagination pour SuperAdmin ────────────────────────────────
    @Query("""
        SELECT c FROM ConnexionLog c
        LEFT JOIN FETCH c.utilisateur u
        ORDER BY c.dateConnexion DESC
        """)
    List<ConnexionLog> findAllOrderByDateDesc(Pageable pageable);

    // ── Invalider toutes les sessions actives d'un utilisateur ────
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("""
        UPDATE ConnexionLog c
        SET c.statut = 'REVOQUEE', c.motifRevocation = :motif
        WHERE c.utilisateur = :user AND c.statut = 'ACTIVE'
        """)
    int revoquerSessionsActives(@Param("user") Utilisateur user,
                                @Param("motif") String motif);

    // ── Invalider une session par token ───────────────────────────
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("""
        UPDATE ConnexionLog c
        SET c.statut = 'REVOQUEE', c.motifRevocation = :motif
        WHERE c.sessionToken = :token
        """)
    int revoquerParToken(@Param("token") String token,
                         @Param("motif") String motif);

    // ── Nombre de sessions actives globales ───────────────────────
    long countByStatut(StatutSession statut);

    // ── Signalement connexion inconnue ───────────────────────────────────
    Optional<ConnexionLog> findBySignalementToken(String signalementToken);

    // ── Device Fingerprint : détecter si l'appareil est connu ───────────
    /** Retourne true si ce fingerprint a déjà été utilisé avec succès par cet utilisateur */
    boolean existsByUtilisateurAndDeviceFingerprintAndSucces(Utilisateur utilisateur,
                                                              String deviceFingerprint,
                                                              Boolean succes);

    // ── Signalement avec JOIN FETCH (évite le LazyInitializationException) ──
    @Query("""
        SELECT c FROM ConnexionLog c
        JOIN FETCH c.utilisateur u
        WHERE c.signalementToken = :token
        """)
    Optional<ConnexionLog> findBySignalementTokenAvecUtilisateur(@Param("token") String token);

    // ── Marquer directement en BDD sans charger l'entité (évite flush/lazy) ──
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("""
        UPDATE ConnexionLog c
        SET c.estSignale = true, c.dateSignalement = :now
        WHERE c.signalementToken = :token AND c.estSignale = false
        """)
    int marquerCommeSignale(@Param("token") String token,
                            @Param("now") java.time.LocalDateTime now);

    // ── Tous les signalements (SuperAdmin) ────────────────────────
    @Query("""
        SELECT c FROM ConnexionLog c
        LEFT JOIN FETCH c.utilisateur u
        WHERE c.estSignale = true
        ORDER BY c.dateSignalement DESC
        """)
    List<ConnexionLog> findAllSignalements();

    // ── Calcul du niveau de risque : vérification historique ──────────────────

    /** true si l'utilisateur a déjà connecté depuis ce pays */
    boolean existsByUtilisateurAndPaysAndSucces(Utilisateur utilisateur, String pays, Boolean succes);

    /** true si l'utilisateur a déjà connecté depuis cette IP */
    boolean existsByUtilisateurAndAdresseIpAndSucces(Utilisateur utilisateur, String adresseIp, Boolean succes);

    /** true si l'utilisateur a déjà connecté depuis cette ville */
    boolean existsByUtilisateurAndVilleAndSucces(Utilisateur utilisateur, String ville, Boolean succes);

    /** Détecte un voyage impossible : connexion depuis un pays différent dans la dernière heure */
    @Query("""
        SELECT COUNT(c) > 0 FROM ConnexionLog c
        WHERE c.utilisateur = :user
        AND c.succes = true
        AND c.pays IS NOT NULL
        AND c.pays != :pays
        AND c.dateConnexion >= :depuis
        """)
    boolean existsVoyageImpossible(
        @Param("user") Utilisateur user,
        @Param("pays") String pays,
        @Param("depuis") java.time.LocalDateTime depuis);
}
