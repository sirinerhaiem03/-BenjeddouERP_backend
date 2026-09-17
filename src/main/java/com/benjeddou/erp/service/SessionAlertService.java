package com.benjeddou.erp.service;

import com.benjeddou.erp.model.ConnexionLog;
import com.benjeddou.erp.model.Utilisateur;
import com.benjeddou.erp.repository.ConnexionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;


@Slf4j
@Service
@RequiredArgsConstructor
public class SessionAlertService {

    private final ConnexionLogRepository connexionLogRepository;
    private final EmailService           emailService;


    /**
     * Méthode principale appelée après la création de session.
     * S'exécute dans un thread séparé (pool async Spring).
     *
     * @param sessionId           ID de la session à enrichir
     * @param emailUtilisateur    email de l'utilisateur (eager — pas de lazy)
     * @param prenomUtilisateur   prénom de l'utilisateur (peut être null)
     * @param ip                  adresse IP de la connexion
     * @param appareilConnu       true si le fingerprint est déjà connu
     * @param sessionPreexistante true si une session était déjà active
     * @param signalementToken    token unique pour le lien "Ce n'est pas moi"
     * @param utilisateur         objet utilisateur pour les vérifications BDD
     */
    @Async
    @Transactional
    public void enrichirEtAlerter(Long sessionId,
                                   String emailUtilisateur,
                                   String prenomUtilisateur,
                                   String nomUtilisateur,
                                   String ip,
                                   boolean appareilConnu,
                                   boolean sessionPreexistante,
                                   String signalementToken,
                                   Utilisateur utilisateur) {

        log.info("[ALERT-ASYNC] Démarrage enrichissement async pour sessionId={}, user={}",
                sessionId, nomUtilisateur);

        try {
            // ── Étape 1 : Géolocalisation via ip-api.com ─────────────────────
            String pays = null, region = null, ville = null, fai = null;
            Double lat = null, lon = null;

            if (ip != null && !ip.isBlank()
                    && !ip.equals("0:0:0:0:0:0:0:1")
                    && !ip.equals("127.0.0.1")) {
                try {
                    RestTemplate rt = new RestTemplate();
                    String url = "http://ip-api.com/json/" + ip
                               + "?fields=status,country,regionName,city,lat,lon,isp&lang=fr";
                    @SuppressWarnings("unchecked")
                    Map<String, Object> geo = rt.getForObject(url, Map.class);
                    if (geo != null && "success".equals(geo.get("status"))) {
                        pays   = (String) geo.get("country");
                        region = (String) geo.get("regionName");
                        ville  = (String) geo.get("city");
                        fai    = (String) geo.get("isp");
                        Object latObj = geo.get("lat");
                        Object lonObj = geo.get("lon");
                        if (latObj instanceof Number) lat = ((Number) latObj).doubleValue();
                        if (lonObj instanceof Number) lon = ((Number) lonObj).doubleValue();
                        log.info("[GEO] {} → {}, {}, {}", ip, ville, region, pays);
                    }
                } catch (Exception e) {
                    log.warn("[GEO] Échec géolocalisation pour {} : {}", ip, e.getMessage());
                }
            } else {
                // IP locale → développement
                pays  = "Tunisie (local)";
                ville = "Localhost";
                log.debug("[GEO] IP locale détectée ({}), géolocalisation ignorée", ip);
            }

            // ── Étape 2 : Calcul du score de risque (0–100) ──────────────────
            int score = 0;
            StringBuilder raisons = new StringBuilder();

            // +30 : appareil jamais vu
            if (!appareilConnu) {
                score += 30;
                raisons.append("appareil inconnu (+30); ");
            }

            // +25 : pays jamais vu
            if (pays != null && !pays.isBlank() && !pays.startsWith("Tunisie (local)")) {
                try {
                    boolean paysConnu = connexionLogRepository
                            .existsByUtilisateurAndPaysAndSucces(utilisateur, pays, true);
                    if (!paysConnu) {
                        score += 25;
                        raisons.append("nouveau pays: ").append(pays).append(" (+25); ");
                    }
                } catch (Exception e) {
                    log.warn("[RISQUE] Impossible de vérifier le pays : {}", e.getMessage());
                }
            }

            // +20 : heure suspecte (2h – 5h du matin)
            int heure = LocalDateTime.now().getHour();
            if (heure >= 2 && heure <= 5) {
                score += 20;
                raisons.append("heure suspecte (").append(heure).append("h) (+20); ");
            }

            // +15 : IP jamais vue
            if (ip != null && !ip.isBlank()) {
                try {
                    boolean ipConnue = connexionLogRepository
                            .existsByUtilisateurAndAdresseIpAndSucces(utilisateur, ip, true);
                    if (!ipConnue) {
                        score += 15;
                        raisons.append("nouvelle IP (+15); ");
                    }
                } catch (Exception e) {
                    log.warn("[RISQUE] Impossible de vérifier l'IP : {}", e.getMessage());
                }
            }

            // +10 : ville jamais vue
            if (ville != null && !ville.isBlank() && !ville.equals("Localhost")) {
                try {
                    boolean villeConnue = connexionLogRepository
                            .existsByUtilisateurAndVilleAndSucces(utilisateur, ville, true);
                    if (!villeConnue) {
                        score += 10;
                        raisons.append("nouvelle ville: ").append(ville).append(" (+10); ");
                    }
                } catch (Exception e) {
                    log.warn("[RISQUE] Impossible de vérifier la ville : {}", e.getMessage());
                }
            }

            score = Math.min(score, 100);

            // ── Étape 3 : Détection voyage impossible ─────────────────────────
            boolean connexionInhabituelle = score >= 50;
            if (pays != null && !pays.isBlank() && !pays.startsWith("Tunisie (local)")) {
                try {
                    boolean voyageImpossible = connexionLogRepository.existsVoyageImpossible(
                            utilisateur, pays, LocalDateTime.now().minusHours(1));
                    if (voyageImpossible) {
                        score = Math.min(score + 20, 100);
                        connexionInhabituelle = true;
                        raisons.append("voyage impossible (<1h) (+20); ");
                        log.warn("[SECURITE] VOYAGE IMPOSSIBLE détecté pour {} depuis {}", nomUtilisateur, pays);
                    }
                } catch (Exception e) {
                    log.warn("[RISQUE] Impossible de vérifier le voyage impossible : {}", e.getMessage());
                }
            }

            if (score > 0) {
                log.info("[RISQUE] Score={} pour {} | Raisons: {}", score, nomUtilisateur, raisons);
            }

            // ── Étape 4 : Mettre à jour la session en BDD ────────────────────
            final String finalPays    = pays;
            final String finalRegion  = region;
            final String finalVille   = ville;
            final String finalFai     = fai;
            final Double finalLat     = lat;
            final Double finalLon     = lon;
            final int    finalScore   = score;
            final boolean finalInhabituelle = connexionInhabituelle;

            connexionLogRepository.findById(sessionId).ifPresent(s -> {
                s.setPays(finalPays);
                s.setRegion(finalRegion);
                s.setVille(finalVille);
                s.setFournisseurInternet(finalFai);
                s.setLatitude(finalLat);
                s.setLongitude(finalLon);
                s.setNiveauRisque(finalScore);
                s.setConnexionInhabituelle(finalInhabituelle);
                connexionLogRepository.save(s);
                log.info("[ALERT-ASYNC] Session {} enrichie : score={}, inhabit={}",
                        sessionId, finalScore, finalInhabituelle);
            });

            // ── Étape 5 : Envoi email d'alerte ───────────────────────────────
            // On envoie si : session déjà active OU appareil inconnu OU connexion inhabituelle
            boolean doitAlerter = sessionPreexistante || !appareilConnu || connexionInhabituelle;
            log.info("[ALERT-ASYNC] doitAlerter={} (sessionPreexistante={}, appareilConnu={}, connexionInhabituelle={})",
                    doitAlerter, sessionPreexistante, appareilConnu, connexionInhabituelle);

            if (doitAlerter && emailUtilisateur != null && !emailUtilisateur.isBlank()) {
                // Récupérer les infos de session pour l'email
                connexionLogRepository.findById(sessionId).ifPresent(session -> {
                    envoyerEmailAlerte(
                            emailUtilisateur,
                            prenomUtilisateur != null ? prenomUtilisateur : nomUtilisateur,
                            ip,
                            session.getOs(),
                            session.getNavigateur(),
                            session.getTypeAppareil(),
                            finalPays,
                            finalVille,
                            finalScore,
                            appareilConnu,
                            sessionPreexistante,
                            finalInhabituelle,
                            signalementToken
                    );
                });
            }

        } catch (Exception e) {
            log.error("[ALERT-ASYNC] Erreur inattendue dans enrichirEtAlerter : {}", e.getMessage(), e);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Email d'alerte enrichi — Nouvelle connexion détectée
    // ══════════════════════════════════════════════════════════════
    private void envoyerEmailAlerte(String email,
                                     String prenom,
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
        try {
            String lienSignalement = "http://localhost:4200/signaler-connexion?token=" + signalementToken;

            String niveauLabel = niveauRisque >= 70 ? "🔴 RISQUE ÉLEVÉ"
                               : niveauRisque >= 40 ? "🟠 RISQUE MOYEN"
                               : "🟢 RISQUE FAIBLE";

            String alerteType = connexionInhabituelle  ? "⚠️ CONNEXION INHABITUELLE détectée"
                              : !appareilConnu          ? "📱 Connexion depuis un appareil inconnu"
                              : sessionPreexistante     ? "🔄 Session précédente déconnectée"
                              : "Nouvelle connexion";

            String sujet = "🔐 [" + niveauLabel + "] " + alerteType + " — BENJEDDOU ERP";
            String localisation = (ville != null ? ville : "?") + (pays != null ? ", " + pays : "");
            String dateHeure = LocalDateTime.now().toString().replace("T", " ").substring(0, 19);

            String corps = String.format("""
                Bonjour %s,

                %s sur votre compte BENJEDDOU ERP.

                ─── Détails de la connexion ───
                • Adresse IP       : %s
                • Localisation     : %s
                • Appareil         : %s
                • Système          : %s
                • Navigateur       : %s
                • Date/Heure       : %s
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
                    prenom,
                    alerteType,
                    ip != null ? ip : "Inconnue",
                    localisation,
                    typeAppareil != null ? typeAppareil : "Inconnu",
                    os != null ? os : "Inconnu",
                    navigateur != null ? navigateur : "Inconnu",
                    dateHeure,
                    niveauLabel, niveauRisque,
                    appareilConnu ? "✅ Appareil connu" : "🟠 Appareil INCONNU (première connexion)",
                    lienSignalement
            );

            emailService.envoyerEmailSimple(email, sujet, corps);
            log.info("[ALERT-ASYNC] ✅ Email d'alerte envoyé à {} — risque={}", email, niveauRisque);

        } catch (Exception e) {
            log.warn("[ALERT-ASYNC] ❌ Impossible d'envoyer l'email d'alerte à {} : {}", email, e.getMessage());
        }
    }
}
