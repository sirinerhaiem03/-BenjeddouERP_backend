package com.benjeddou.erp.service;

import com.benjeddou.erp.model.ConnexionLog;
import com.benjeddou.erp.model.ConnexionLog.StatutSession;
import com.benjeddou.erp.model.StatutCompte;
import com.benjeddou.erp.model.Utilisateur;
import com.benjeddou.erp.repository.ConnexionLogRepository;
import com.benjeddou.erp.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;


/**
 * SessionService — Gestion des sessions uniques et du cycle de vie des connexions.
 *
 * Fonctionnalités :
 * - Garantit qu'un utilisateur n'a qu'UNE seule session active simultanée.
 * - Enregistre chaque connexion avec les informations appareil et réseau.
 * - Permet la révocation à distance (SuperAdmin ou détection de conflit).
 * - Envoie un email d'alerte en cas de nouvelle connexion détectée.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final ConnexionLogRepository  connexionLogRepository;
    private final UtilisateurRepository   utilisateurRepository;
    private final EmailService            emailService;
    /** Bean séparé pour @Async — évite le problème de self-invocation Spring AOP */
    private final SessionAlertService     sessionAlertService;

    // ══════════════════════════════════════════════════════════════
    // Créer une nouvelle session et invalider les anciennes
    // ══════════════════════════════════════════════════════════════
    @Transactional
    public ConnexionLog ouvrirSession(Utilisateur utilisateur,
                                      String jwt,
                                      String ip,
                                      String userAgent,
                                      String typeAppareil,
                                      String os,
                                      String navigateur,
                                      String resolution,
                                      String langue,
                                      String fuseauHoraire,
                                      String deviceFingerprint,
                                      String typeReseau) {

        // 1. Vérifier s'il existe déjà une session active
        List<ConnexionLog> sessionsActives = connexionLogRepository
                .findByUtilisateurAndStatut(utilisateur, StatutSession.ACTIVE);

        boolean sessionPreexistante = !sessionsActives.isEmpty();

        // 2. Vérifier si l'appareil est déjà connu (fingerprint vu avant pour cet utilisateur)
        boolean appareilConnu = false;
        if (deviceFingerprint != null && !deviceFingerprint.isBlank()) {
            try {
                appareilConnu = connexionLogRepository
                        .existsByUtilisateurAndDeviceFingerprintAndSucces(
                                utilisateur, deviceFingerprint, true);
            } catch (Exception e) {
                log.warn("[FINGERPRINT] Impossible de vérifier l'appareil : {}", e.getMessage());
            }
        }

        if (appareilConnu) {
            log.info("[FINGERPRINT] Appareil CONNU pour {} — fingerprint={}",
                    utilisateur.getNomUtilisateur(),
                    deviceFingerprint != null ? deviceFingerprint.substring(0, 8) + "..." : "null");
        } else {
            log.warn("[FINGERPRINT] Appareil INCONNU pour {} — fingerprint={}",
                    utilisateur.getNomUtilisateur(),
                    deviceFingerprint != null ? deviceFingerprint.substring(0, 8) + "..." : "null");
        }

        // 3. Révoquer toutes les anciennes sessions actives
        if (sessionPreexistante) {
            int revoquees = connexionLogRepository.revoquerSessionsActives(
                    utilisateur,
                    "Nouvelle connexion détectée depuis " + ip
            );
            log.info("[SESSION] {} ancienne(s) session(s) révoquée(s) pour {}",
                    revoquees, utilisateur.getNomUtilisateur());
        }

        // 4. Créer la nouvelle session (sans géolocalisation pour ne pas bloquer le login)
        ConnexionLog nouvSession = ConnexionLog.builder()
                .utilisateur(utilisateur)
                .sessionToken(jwt)
                .statut(StatutSession.ACTIVE)
                .adresseIp(ip)
                .userAgent(userAgent != null ? userAgent.substring(0, Math.min(userAgent.length(), 500)) : "inconnu")
                .typeAppareil(typeAppareil)
                .os(os)
                .navigateur(navigateur)
                .resolution(resolution)
                .langue(langue)
                .fuseauHoraire(fuseauHoraire)
                .deviceFingerprint(deviceFingerprint)
                .appareilConnu(appareilConnu)
                .typeReseau(typeReseau != null ? typeReseau : "Inconnu")
                .niveauRisque(0)           // sera mis à jour après géolocalisation
                .connexionInhabituelle(false)
                .succes(true)
                .build();

        ConnexionLog sauvegardee = connexionLogRepository.save(nouvSession);

        // 5. Enrichissement géographique + calcul du niveau de risque + email (vrai @Async)
        //    Délégué à SessionAlertService (bean séparé) pour que @Async fonctionne via le proxy Spring.
        sessionAlertService.enrichirEtAlerter(
                sauvegardee.getId(),
                utilisateur.getEmail(),
                utilisateur.getPrenom(),
                utilisateur.getNomUtilisateur(),
                ip,
                appareilConnu,
                sessionPreexistante,
                sauvegardee.getSignalementToken(),
                utilisateur
        );

        return sauvegardee;
    }



    // ══════════════════════════════════════════════════════════════
    // Fermer une session (lors du logout)
    // ══════════════════════════════════════════════════════════════
    @Transactional
    public void fermerSession(String jwt) {
        connexionLogRepository.findBySessionToken(jwt).ifPresent(session -> {
            session.setStatut(StatutSession.TERMINEE);
            session.setDateDeconnexion(LocalDateTime.now());
            connexionLogRepository.save(session);
            log.info("[SESSION] Session fermée pour l'utilisateur {}",
                    session.getUtilisateur().getNomUtilisateur());
        });
    }

    // ══════════════════════════════════════════════════════════════
    // SuperAdmin : forcer la déconnexion d'une session
    // ══════════════════════════════════════════════════════════════
    @Transactional
    public boolean revoquerSession(Long sessionId, String motif) {
        return connexionLogRepository.findById(sessionId).map(session -> {
            session.setStatut(StatutSession.REVOQUEE);
            session.setDateDeconnexion(LocalDateTime.now());
            session.setMotifRevocation(motif != null ? motif : "Révocations par SuperAdmin");
            connexionLogRepository.save(session);
            log.info("[SESSION] Session {} révoquée par SuperAdmin — motif: {}", sessionId, motif);
            return true;
        }).orElse(false);
    }

    // ══════════════════════════════════════════════════════════════
    // Révoquer toutes les sessions actives d'un utilisateur
    // ══════════════════════════════════════════════════════════════
    @Transactional
    public int revoquerToutesSessionsUtilisateur(Utilisateur utilisateur, String motif) {
        return connexionLogRepository.revoquerSessionsActives(utilisateur, motif);
    }

    // ══════════════════════════════════════════════════════════════
    // Enregistrer un échec de connexion
    // ══════════════════════════════════════════════════════════════
    @Transactional
    public void enregistrerEchec(Utilisateur utilisateur, String ip, String userAgent) {
        if (utilisateur == null) return;
        ConnexionLog echec = ConnexionLog.builder()
                .utilisateur(utilisateur)
                .statut(StatutSession.TERMINEE)
                .adresseIp(ip)
                .userAgent(userAgent != null ? userAgent.substring(0, Math.min(userAgent.length(), 500)) : "inconnu")
                .succes(false)
                .build();
        connexionLogRepository.save(echec);
    }

    // ══════════════════════════════════════════════════════════════
    // Signaler une connexion inconnue + verrouiller le compte
    // ══════════════════════════════════════════════════════════════
    public String signalerConnexion(String signalementToken) {

        // ── Étape 1 : Charger la session avec l'utilisateur en JOIN FETCH ─
        ConnexionLog session = null;
        try {
            session = connexionLogRepository
                    .findBySignalementTokenAvecUtilisateur(signalementToken)
                    .orElse(null);
        } catch (Exception e) {
            log.error("[SECURITE] Erreur findAvecUtilisateur : {}", e.getMessage());
            try {
                session = connexionLogRepository
                        .findBySignalementToken(signalementToken)
                        .orElse(null);
            } catch (Exception e2) {
                log.error("[SECURITE] Erreur findByToken fallback : {}", e2.getMessage());
                return "TOKEN_INVALIDE";
            }
        }

        if (session == null) {
            log.warn("[SECURITE] Token signalement introuvable : {}", signalementToken);
            return "TOKEN_INVALIDE";
        }

        if (Boolean.TRUE.equals(session.getEstSignale())) {
            log.info("[SECURITE] Connexion déjà signalée : {}", signalementToken);
            return "DEJA_SIGNALE";
        }

        // ── Étape 2 : UPDATE direct sans save/flush (évite LazyInitializationException) ─
        try {
            int updated = connexionLogRepository.marquerCommeSignale(
                    signalementToken, LocalDateTime.now());
            log.info("[SECURITE] {} ligne(s) marquée(s) comme signalée(s)", updated);
        } catch (Exception e) {
            log.error("[SECURITE] Impossible de marquer le signalement : {}", e.getMessage(), e);
            // On continue quand même pour tenter les autres étapes
        }

        // ── Étape 3 : Récupérer l'utilisateur ──────────────────────
        Utilisateur utilisateur = null;
        try {
            utilisateur = session.getUtilisateur();
        } catch (Exception e) {
            log.error("[SECURITE] Impossible de charger l'utilisateur : {}", e.getMessage());
        }

        // ── Étape 4 : Verrouiller le compte (optionnel) ────────────
        if (utilisateur != null) {
            try {
                utilisateur.setStatutCompte(StatutCompte.VERROUILLE);
                utilisateurRepository.save(utilisateur);
                log.warn("[SECURITE] Compte VERROUILLE : {}", utilisateur.getNomUtilisateur());
            } catch (Exception e) {
                log.error("[SECURITE] Impossible de verrouiller le compte : {}", e.getMessage());
            }
        }

        // ── Étape 5 : Révoquer les sessions actives (optionnel) ─────
        if (utilisateur != null) {
            try {
                int n = connexionLogRepository.revoquerSessionsActives(
                        utilisateur, "Compte verrouillé — signalement connexion suspecte");
                log.info("[SECURITE] {} session(s) révoquée(s)", n);
            } catch (Exception e) {
                log.error("[SECURITE] Impossible de révoquer les sessions : {}", e.getMessage());
            }
        }

        // ── Étape 6 : Email SuperAdmin (optionnel) ─────────────────
        try { envoyerAlertSignalementSuperAdmin(session); }
        catch (Exception e) { log.warn("[SECURITE] Email SuperAdmin non envoyé : {}", e.getMessage()); }

        // ── Étape 7 : Email utilisateur (optionnel) ────────────────
        if (utilisateur != null) {
            try { envoyerEmailVerrouillage(utilisateur, session); }
            catch (Exception e) { log.warn("[SECURITE] Email utilisateur non envoyé : {}", e.getMessage()); }
        }

        return "OK";
    }


    // ══════════════════════════════════════════════════════════════
    // SuperAdmin : déverrouiller un compte
    // ══════════════════════════════════════════════════════════════
    @Transactional
    public boolean deverrouillerCompte(Long utilisateurId) {
        return utilisateurRepository.findById(utilisateurId).map(u -> {
            u.setStatutCompte(StatutCompte.ACTIF);
            utilisateurRepository.save(u);
            log.info("[SECURITE] Compte {} déverrouillé par SuperAdmin", u.getNomUtilisateur());
            return true;
        }).orElse(false);
    }

    // ══════════════════════════════════════════════════════════════
    // Email d'alerte — Nouvelle connexion détectée (avec lien signalement)
    // ══════════════════════════════════════════════════════════════
    private void envoyerAlertNouvelleConnexionEnrichie(Utilisateur utilisateur,
                                                        String ip,
                                                        String os,
                                                        String navigateur,
                                                        String typeAppareil,
                                                        String pays,
                                                        String ville,
                                                        int niveauRisque,
                                                        boolean appareilConnu,
                                                        boolean sessionPreexistante,
                                                        boolean connexionInhabituelle,
                                                        String signalementToken) {
        if (utilisateur.getEmail() == null || utilisateur.getEmail().isBlank()) return;
        try {
            String lienSignalement = "http://localhost:4200/signaler-connexion?token=" + signalementToken;

            // Déterminer le type d'alerte
            String niveauLabel = niveauRisque >= 70 ? "🔴 RISQUE ÉLEVÉ"
                               : niveauRisque >= 40 ? "🟠 RISQUE MOYEN"
                               : "🟢 RISQUE FAIBLE";

            String alerteType = connexionInhabituelle  ? "⚠️ CONNEXION INHABITUELLE détectée"
                              : !appareilConnu          ? "📱 Connexion depuis un appareil inconnu"
                              : sessionPreexistante     ? "🔄 Session précédente déconnectée"
                              : "Nouvelle connexion";

            String sujet = "🔐 [" + niveauLabel + "] " + alerteType + " — BENJEDDOU ERP";
            String localisation = (ville != null ? ville : "?") + (pays != null ? ", " + pays : "");

            String corps = String.format("""
                Bonjour %s,

                %s sur votre compte BENJEDDOU ERP.

                ─── Détails de la connexion ───
                • Adresse IP      : %s
                • Localisation    : %s
                • Appareil        : %s
                • Système         : %s
                • Navigateur      : %s
                • Date/Heure      : %s
                • Niveau de risque : %s (%d/100)
                • Statut appareil  : %s

                ──────────────────────────────────────────
                ❓ Ce n'était pas vous ?
                ──────────────────────────────────────────
                Cliquez immédiatement sur le lien ci-dessous :

                👉 %s

                Ce lien est à usage unique et valable 24h.

                ⚠️  Mesures immédiates :
                1. Cliquez sur le lien pour signaler et verrouiller le compte.
                2. Changez votre mot de passe depuis l'espace Sécurité.
                3. Contactez votre administrateur BENJEDDOU ERP.

                Cordialement,
                L'équipe Sécurité BENJEDDOU ERP
                """,
                    utilisateur.getPrenom() != null ? utilisateur.getPrenom() : utilisateur.getNomUtilisateur(),
                    alerteType,
                    ip,
                    localisation,
                    typeAppareil != null ? typeAppareil : "Inconnu",
                    os != null ? os : "Inconnu",
                    navigateur != null ? navigateur : "Inconnu",
                    LocalDateTime.now().toString().replace("T", " ").substring(0, 19),
                    niveauLabel, niveauRisque,
                    appareilConnu ? "✅ Appareil connu" : "🟠 Appareil INCONNU (première connexion)",
                    lienSignalement
            );
            emailService.envoyerEmailSimple(utilisateur.getEmail(), sujet, corps);
            log.info("[SESSION] Email d'alerte enrichi envoyé à {} — risque={}",
                    utilisateur.getEmail(), niveauRisque);
        } catch (Exception e) {
            log.warn("[SESSION] Impossible d'envoyer l'email d'alerte : {}", e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Email d'alerte SuperAdmin — Signalement connexion suspecte
    // ══════════════════════════════════════════════════════════════
    private void envoyerAlertSignalementSuperAdmin(ConnexionLog session) {
        try {
            // Récupérer l'email SuperAdmin depuis les propriétés ou utiliser un email par défaut
            String emailSuperAdmin = "admin@benjeddou.tn";
            String sujet = "🚨 [SÉCURITÉ] Connexion suspecte signalée — " + session.getUtilisateur().getNomUtilisateur();
            String corps = String.format("""
                ⚠️  ALERTE SÉCURITÉ — Connexion Suspecte Signalée

                Un utilisateur a signalé une connexion non autorisée sur son compte.

                ─── Détails du signalement ───
                • Utilisateur  : %s (%s)
                • Email        : %s
                • Adresse IP   : %s
                • Appareil     : %s
                • Système      : %s
                • Navigateur   : %s
                • Date connexion signalée : %s
                • Date du signalement     : %s

                ─── Actions recommandées ───
                1. Vérifier les sessions actives dans la console SuperAdmin.
                2. Bloquer l'adresse IP suspecte si nécessaire.
                3. Contacter l'utilisateur pour confirmer.
                4. Envisager la suspension temporaire du compte.

                → Accéder à la console SuperAdmin : http://localhost:4200/superadmin/sessions

                BENJEDDOU ERP — Système de Sécurité Automatisé
                """,
                    session.getUtilisateur().getNomUtilisateur(),
                    session.getUtilisateur().getRole() != null ? session.getUtilisateur().getRole().name() : "USER",
                    session.getUtilisateur().getEmail(),
                    session.getAdresseIp() != null ? session.getAdresseIp() : "Inconnue",
                    session.getTypeAppareil() != null ? session.getTypeAppareil() : "Inconnu",
                    session.getOs() != null ? session.getOs() : "Inconnu",
                    session.getNavigateur() != null ? session.getNavigateur() : "Inconnu",
                    session.getDateConnexion() != null ? session.getDateConnexion().toString().replace("T", " ").substring(0, 19) : "?",
                    LocalDateTime.now().toString().replace("T", " ").substring(0, 19)
            );
            emailService.envoyerEmailSimple(emailSuperAdmin, sujet, corps);
            log.info("[SECURITE] Email d'alerte SuperAdmin envoyé pour signalement de {}",
                    session.getUtilisateur().getNomUtilisateur());
        } catch (Exception e) {
            log.warn("[SESSION] Impossible d'envoyer l'alerte SuperAdmin : {}", e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Email de confirmation de verrouillage à l'utilisateur
    // ══════════════════════════════════════════════════════════════
    private void envoyerEmailVerrouillage(Utilisateur utilisateur, ConnexionLog session) {
        if (utilisateur.getEmail() == null || utilisateur.getEmail().isBlank()) return;
        try {
            String sujet = "🔒 Votre compte BENJEDDOU ERP a été verrouillé";
            String corps = String.format("""
                Bonjour %s,

                Suite à votre signalement, votre compte a été immédiatement verrouillé
                pour protéger vos données.

                ─── Détails du signalement ───
                • Connexion suspecte depuis : %s
                • Appareil suspect          : %s (%s)
                • Date du signalement       : %s

                ─── Ce qui a été fait ───
                ✅ Votre compte est maintenant VERROUILLÉ.
                ✅ Toutes les sessions actives ont été révoquées.
                ✅ L'administrateur a été alerté automatiquement.

                ─── Prochaine étape ───
                Contactez votre administrateur pour déverrouiller votre compte :
                → http://localhost:4200/superadmin/sessions

                ⚠️  Si vous avez reconnu cette connexion comme étant la vôtre,
                contactez aussi votre administrateur pour rétablir l'accès.

                Cordialement,
                L'équipe Sécurité BENJEDDOU ERP
                """,
                    utilisateur.getPrenom() != null ? utilisateur.getPrenom() : utilisateur.getNomUtilisateur(),
                    session.getAdresseIp() != null ? session.getAdresseIp() : "Inconnue",
                    session.getTypeAppareil() != null ? session.getTypeAppareil() : "Inconnu",
                    session.getNavigateur() != null ? session.getNavigateur() : "Inconnu",
                    LocalDateTime.now().toString().replace("T", " ").substring(0, 19)
            );
            emailService.envoyerEmailSimple(utilisateur.getEmail(), sujet, corps);
            log.info("[SECURITE] Email de verrouillage envoyé à {}", utilisateur.getEmail());
        } catch (Exception e) {
            log.warn("[SESSION] Impossible d'envoyer l'email de verrouillage : {}", e.getMessage());
        }
    }
}
