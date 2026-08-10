package com.benjeddou.erp.service;

import com.benjeddou.erp.model.Facture;
import com.benjeddou.erp.model.LigneCommande;
import com.benjeddou.erp.repository.LigneCommandeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class EmailService {

    @Autowired private JavaMailSender mailSender;
    @Autowired private LigneCommandeRepository ligneCommandeRepository;

    @Value("${spring.mail.username}")
    private String fromEmail;

    private static final DateTimeFormatter FMT      = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_LONG = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // ════════════════════════════════════════════════════════════
    // API PUBLIQUE
    // ════════════════════════════════════════════════════════════

    public void envoyerFactureParEmail(Facture facture) throws Exception {
        List<LigneCommande> lignes = ligneCommandeRepository.findByCommandeId(facture.getCommande().getId());
        String sujet = "[BENJEDDOU ERP] Facture " + facture.getNumeroFacture();
        send(facture.getCommande().getClient().getEmail(), sujet, buildEmail(facture, lignes, false));
    }

    public void envoyerRappelImpayee(Facture facture) throws Exception {
        List<LigneCommande> lignes = ligneCommandeRepository.findByCommandeId(facture.getCommande().getId());
        String sujet = "[RAPPEL] Facture " + facture.getNumeroFacture() + " — paiement en retard";
        send(facture.getCommande().getClient().getEmail(), sujet, buildEmail(facture, lignes, true));
    }

    /**
     * Email simple en texte brut — utilisé par SessionService, OtpService, etc.
     */
    public void envoyerEmailSimple(String destinataire, String sujet, String corps) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(destinataire);
            helper.setSubject(sujet);
            helper.setText(corps, false);
            mailSender.send(msg);
        } catch (Exception e) {
            // Log sans faire rater la requête principale
            System.err.println("[EmailService] Erreur envoi email simple vers " + destinataire + " : " + e.getMessage());
        }
    }

    /**
     * Email envoyé au client quand son dossier KYC est validé par l'admin.
     * Invite le client à se connecter et choisir son abonnement.
     */
    public void envoyerNotificationValidationKyc(String email, String prenom) {
        try {
            String sujet = "✅ Votre dossier a été validé — BENJEDDOU ERP";
            String html = buildEmailValidationKyc(prenom);
            send(email, sujet, html);
        } catch (Exception e) {
            System.err.println("[EmailService] Erreur envoi notification KYC : " + e.getMessage());
        }
    }

    /**
     * Email envoyé au client quand son abonnement est validé et son compte activé.
     */
    public void envoyerNotificationActivationCompte(String email, String prenom, String typePlan, String dateFin) {
        try {
            String sujet = "🎉 Votre compte est activé — BENJEDDOU ERP";
            String html = buildEmailActivationCompte(prenom, typePlan, dateFin);
            send(email, sujet, html);
        } catch (Exception e) {
            System.err.println("[EmailService] Erreur envoi notification activation : " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════
    // ENVOI
    // ════════════════════════════════════════════════════════════

    private void send(String to, String subject, String html) throws Exception {
        MimeMessage msg = mailSender.createMimeMessage();
        MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");
        h.setFrom(fromEmail, "BENJEDDOU ERP");
        h.setTo(to);
        h.setSubject(subject);
        h.setText(html, true);
        mailSender.send(msg);
    }

    // ════════════════════════════════════════════════════════════
    // CONSTRUCTEUR HTML PRINCIPAL
    // ════════════════════════════════════════════════════════════

    private String buildEmail(Facture facture, List<LigneCommande> lignes, boolean rappel) {
        var client   = facture.getCommande().getClient();
        var commande = facture.getCommande();

        // ── Valeurs ───────────────────────────────────────────
        BigDecimal ht  = facture.getMontantTotal().subtract(facture.getMontantTva());
        BigDecimal tva = facture.getMontantTva();
        BigDecimal ttc = facture.getMontantTotal();

        String dateEm  = facture.getDateEmission() != null ? facture.getDateEmission().format(FMT) : "—";
        String dateEch = facture.getDateEcheance() != null ? facture.getDateEcheance().format(FMT) : "—";
        String dateCmd = commande.getDateCommande() != null ? commande.getDateCommande().format(FMT_LONG) : "—";

        // ── Couleurs thème ────────────────────────────────────
        String headerTop    = rappel ? "#b91c1c" : "#0f172a";
        String headerBot    = rappel ? "#dc2626" : "#1e3a8a";
        String accent       = rappel ? "#dc2626" : "#f97316";
        String accentLight  = rappel ? "#fee2e2" : "#fff7ed";
        String accentBorder = rappel ? "#fca5a5" : "#fed7aa";

        // ── Statut ────────────────────────────────────────────
        String[] stat = getStatutStyle(facture.getStatut(), rappel);
        String statutLabel = stat[0], statutBg = stat[1], statutFg = stat[2];

        // ── Lignes produits ───────────────────────────────────
        String lignesRows = buildLignesRows(lignes);

        // ── Alerte rappel ─────────────────────────────────────
        String rappelBanner = rappel ? tr(
            "<table width='100%' cellpadding='0' cellspacing='0' border='0' style='background:#fef2f2;border:2px solid #fca5a5;border-radius:12px;'>"
            + "<tr><td style='padding:16px 24px;text-align:center;'>"
            + "<p style='margin:0 0 4px;font-size:18px;font-weight:800;color:#dc2626;font-family:Arial,sans-serif;'>⚠️ RAPPEL DE PAIEMENT</p>"
            + "<p style='margin:0;font-size:13px;color:#991b1b;font-family:Arial,sans-serif;'>"
            + "La date d&apos;&eacute;ch&eacute;ance du <strong>" + dateEch + "</strong> est d&eacute;pass&eacute;e.<br>"
            + "Merci de proc&eacute;der au r&egrave;glement dans les plus brefs d&eacute;lais."
            + "</p></td></tr></table>"
        ) : "";

        // ════════════════════════════════════════════════════════════
        // TEMPLATE HTML (table-based pour compatibilité email)
        // ════════════════════════════════════════════════════════════
        return "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\""
            + " \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">"
            + "<html xmlns='http://www.w3.org/1999/xhtml' lang='fr'>"
            + "<head><meta http-equiv='Content-Type' content='text/html; charset=UTF-8'/>"
            + "<meta name='viewport' content='width=device-width,initial-scale=1'/>"
            + "<title>Facture " + facture.getNumeroFacture() + "</title></head>"
            + "<body style='margin:0;padding:0;background-color:#eef2f7;font-family:Arial,Helvetica,sans-serif;-webkit-font-smoothing:antialiased;'>"

            // Wrapper
            + "<table width='100%' cellpadding='0' cellspacing='0' border='0' style='background-color:#eef2f7;'><tr><td align='center' style='padding:32px 16px;'>"
            + "<table width='620' cellpadding='0' cellspacing='0' border='0' style='max-width:620px;width:100%;'>"

            // ── En-tête dégradé ───────────────────────────────
            + "<tr><td style='background:" + headerTop + ";border-radius:16px 16px 0 0;padding:0;'>"
            + "<!--[if gte mso 9]><v:rect xmlns:v='urn:schemas-microsoft-com:vml' fill='true' stroke='false' style='width:620px;'><v:fill type='gradient' color='" + headerTop + "' color2='" + headerBot + "' angle='135'/><v:textbox inset='0,0,0,0'><![endif]-->"
            + "<table width='100%' cellpadding='0' cellspacing='0' border='0'><tr>"
            + "<td style='padding:32px 36px 28px;background:linear-gradient(135deg," + headerTop + "," + headerBot + ");border-radius:16px 16px 0 0;'>"
            + "<table width='100%' cellpadding='0' cellspacing='0' border='0'><tr>"
            // Logo gauche
            + "<td valign='middle'>"
            + "<p style='margin:0;font-size:22px;font-weight:900;color:" + accent + ";letter-spacing:-0.5px;line-height:1;'>&#9899; BENJEDDOU</p>"
            + "<p style='margin:4px 0 0;font-size:11px;color:rgba(255,255,255,0.55);letter-spacing:2px;text-transform:uppercase;'>ERP &bull; Plateforme Intelligente</p>"
            + "</td>"
            // Numéro droite
            + "<td valign='middle' align='right'>"
            + "<p style='margin:0;font-size:11px;font-weight:700;color:rgba(255,255,255,0.55);text-transform:uppercase;letter-spacing:2px;'>" + (rappel ? "&#9888; Rappel" : "Facture") + "</p>"
            + "<p style='margin:4px 0 0;font-size:20px;font-weight:900;color:white;font-family:\"Courier New\",monospace;'>" + facture.getNumeroFacture() + "</p>"
            + "</td>"
            + "</tr></table>"
            + "</td></tr></table>"
            + "<!--[if gte mso 9]></v:textbox></v:rect><![endif]-->"
            + "</td></tr>"

            // ── Bande accent ─────────────────────────────────
            + "<tr><td style='background:" + accent + ";padding:0;height:4px;'>&nbsp;</td></tr>"

            // ── Corps blanc ───────────────────────────────────
            + "<tr><td style='background:#ffffff;border-left:1px solid #e2e8f0;border-right:1px solid #e2e8f0;padding:36px;'>"
            + "<table width='100%' cellpadding='0' cellspacing='0' border='0'>"

            // Rappel banner
            + rappelBanner

            // ── Salutation ────────────────────────────────────
            + tr("<p style='margin:0 0 4px;font-size:14px;color:#475569;font-family:Arial,sans-serif;'>Bonjour,</p>"
                + "<p style='margin:0 0 24px;font-size:15px;color:#0f172a;font-family:Arial,sans-serif;'>"
                + "Veuillez trouver ci-dessous la facture &eacute;mise &agrave; l&apos;attention de "
                + "<strong style='color:#0f172a;'>" + client.getNom() + "</strong>.</p>")

            // ── Bloc client + commande ────────────────────────
            + "<tr><td style='padding-bottom:24px;'>"
            + "<table width='100%' cellpadding='0' cellspacing='0' border='0'><tr>"
            // Client
            + "<td width='48%' valign='top' style='background:#f8fafc;border:1px solid #e2e8f0;border-radius:10px;padding:18px;'>"
            + "<p style='margin:0 0 10px;font-size:10px;font-weight:700;text-transform:uppercase;letter-spacing:1.5px;color:#94a3b8;font-family:Arial,sans-serif;'>Factur&eacute; &agrave;</p>"
            + "<p style='margin:0 0 4px;font-size:15px;font-weight:700;color:#0f172a;font-family:Arial,sans-serif;'>" + client.getNom() + "</p>"
            + "<p style='margin:0 0 2px;font-size:12px;color:#64748b;font-family:Arial,sans-serif;'>" + client.getEmail() + "</p>"
            + (client.getTelephone() != null ? "<p style='margin:0 0 2px;font-size:12px;color:#64748b;font-family:Arial,sans-serif;'>&#128222; " + client.getTelephone() + "</p>" : "")
            + (client.getAdresse() != null ? "<p style='margin:0 0 2px;font-size:11px;color:#94a3b8;font-family:Arial,sans-serif;'>" + client.getAdresse() + "</p>" : "")
            + (client.getMatriculeFiscale() != null ? "<p style='margin:0;font-size:10px;color:#94a3b8;font-family:\"Courier New\",monospace;'>MF : " + client.getMatriculeFiscale() + "</p>" : "")
            + "</td>"
            // Espace
            + "<td width='4%'>&nbsp;</td>"
            // Commande
            + "<td width='48%' valign='top' style='background:#f8fafc;border:1px solid #e2e8f0;border-radius:10px;padding:18px;'>"
            + "<p style='margin:0 0 10px;font-size:10px;font-weight:700;text-transform:uppercase;letter-spacing:1.5px;color:#94a3b8;font-family:Arial,sans-serif;'>D&eacute;tails commande</p>"
            + infoLine("N&deg; Commande", commande.getNumeroCommande(), "#7c3aed", true)
            + infoLine("Date commande", dateCmd, "#0f172a", false)
            + infoLine("Statut commande", commande.getStatut(), "#059669", false)
            + "</td>"
            + "</tr></table>"
            + "</td></tr>"

            // ── Dates facture (3 colonnes) ────────────────────
            + "<tr><td style='padding-bottom:24px;'>"
            + "<table width='100%' cellpadding='0' cellspacing='0' border='0'><tr>"
            + datePill("Date d&apos;&eacute;mission", dateEm, "#0f172a", "#f8fafc", "#e2e8f0")
            + "<td width='2%'>&nbsp;</td>"
            + datePill("Date d&apos;&eacute;ch&eacute;ance", dateEch, rappel ? "#dc2626" : "#0f172a", rappel ? "#fef2f2" : "#f8fafc", rappel ? "#fca5a5" : "#e2e8f0")
            + "<td width='2%'>&nbsp;</td>"
            + datePill("Statut", statutLabel, statutFg, statutBg, statutBg)
            + "</tr></table>"
            + "</td></tr>"

            // ── Titre tableau produits ────────────────────────
            + tr("<p style='margin:0 0 12px;font-size:11px;font-weight:700;text-transform:uppercase;"
                + "letter-spacing:1.5px;color:" + accent + ";font-family:Arial,sans-serif;'>"
                + "&#128230; D&eacute;tail des produits command&eacute;s</p>")

            // ── Tableau produits ──────────────────────────────
            + "<tr><td style='padding-bottom:24px;'>"
            + "<table width='100%' cellpadding='0' cellspacing='0' border='0' style='border-collapse:collapse;border:1px solid #e2e8f0;border-radius:10px;overflow:hidden;'>"
            // En-tête tableau
            + "<tr style='background:#f1f5f9;'>"
            + th("Produit", "left")
            + th("Qt&eacute;", "center")
            + th("Prix unit.", "right")
            + th("Remise", "center")
            + th("Total HT", "right")
            + "</tr>"
            // Lignes
            + lignesRows
            + "</table>"
            + "</td></tr>"

            // ── Bloc totaux ───────────────────────────────────
            + "<tr><td style='padding-bottom:28px;'>"
            + "<table width='100%' cellpadding='0' cellspacing='0' border='0'>"
            + "<tr>"
            + "<td width='45%' valign='top' style='padding-right:20px;'>"
            + "<p style='margin:0;font-size:12px;color:#94a3b8;line-height:1.6;font-family:Arial,sans-serif;'>"
            + "Ce document tient lieu de facture officielle.<br>"
            + "Conservez-le pour vos dossiers comptables."
            + "</p>"
            + "</td>"
            + "<td width='55%' valign='top' style='background:#f8fafc;border:1px solid #e2e8f0;border-radius:12px;padding:18px 20px;'>"
            + "<table width='100%' cellpadding='0' cellspacing='0' border='0'>"
            + totalRow("Sous-total HT", String.format("%.3f TND", ht), false, accent)
            + totalRow("TVA (19%)", String.format("%.3f TND", tva), false, accent)
            + "<tr><td colspan='2' style='padding:8px 0 0;'><hr style='border:none;border-top:2px solid #e2e8f0;margin:0;'/></td></tr>"
            + totalRow("TOTAL TTC", String.format("%.3f TND", ttc), true, accent)
            + "</table>"
            + "</td></tr></table>"
            + "</td></tr>"

            // ── Badge statut centré ───────────────────────────
            + "<tr><td style='text-align:center;padding-bottom:24px;'>"
            + "<span style='display:inline-block;background:" + statutBg + ";color:" + statutFg + ";"
            + "border:1.5px solid " + accentBorder + ";padding:10px 28px;"
            + "border-radius:50px;font-size:14px;font-weight:700;font-family:Arial,sans-serif;'>"
            + statutLabel + "</span>"
            + "</td></tr>"

            // ── Signature numérique ───────────────────────────
            + (facture.getSignatureNumerique() != null
                ? "<tr><td style='padding-bottom:20px;'>"
                  + "<table width='100%' cellpadding='0' cellspacing='0' border='0'><tr>"
                  + "<td style='background:#faf5ff;border:1px solid #ddd6fe;border-radius:8px;padding:10px 16px;text-align:center;'>"
                  + "<span style='font-size:11px;color:#94a3b8;font-family:Arial,sans-serif;'>&#128274; Signature num&eacute;rique : </span>"
                  + "<span style='font-family:\"Courier New\",monospace;font-size:10px;color:#7c3aed;word-break:break-all;'>"
                  + facture.getSignatureNumerique() + "</span>"
                  + "</td></tr></table></td></tr>"
                : "")

            + "</table>" // fin table corps
            + "</td></tr>" // fin td blanc

            // ── Pied de page ──────────────────────────────────
            + "<tr><td style='background:" + accent + ";padding:0;height:3px;'>&nbsp;</td></tr>"
            + "<tr><td style='background:#0f172a;border-radius:0 0 16px 16px;padding:24px 36px;'>"
            + "<table width='100%' cellpadding='0' cellspacing='0' border='0'><tr>"
            + "<td valign='middle'>"
            + "<p style='margin:0;font-size:13px;font-weight:700;color:" + accent + ";font-family:Arial,sans-serif;'>BENJEDDOU ERP</p>"
            + "<p style='margin:2px 0 0;font-size:11px;color:rgba(255,255,255,0.4);font-family:Arial,sans-serif;'>Plateforme ERP Intelligente</p>"
            + "</td>"
            + "<td align='right' valign='middle'>"
            + "<p style='margin:0;font-size:11px;color:rgba(255,255,255,0.5);font-family:Arial,sans-serif;'>Email g&eacute;n&eacute;r&eacute; automatiquement</p>"
            + "<p style='margin:2px 0 0;font-size:11px;font-family:Arial,sans-serif;'>"
            + "<a href='mailto:" + fromEmail + "' style='color:" + accent + ";text-decoration:none;'>" + fromEmail + "</a>"
            + "</p>"
            + "</td>"
            + "</tr></table>"
            + "</td></tr>"

            + "</table>" // fin table 620px
            + "</td></tr></table>" // fin wrapper
            + "</body></html>";
    }

    // ════════════════════════════════════════════════════════════
    // HELPERS HTML
    // ════════════════════════════════════════════════════════════

    /** Ligne de tableau basique */
    private String tr(String content) {
        return "<tr><td style='padding-bottom:0;'>" + content + "</td></tr>";
    }

    /** En-tête de colonne du tableau produits */
    private String th(String label, String align) {
        return "<th style='padding:11px 14px;text-align:" + align + ";"
            + "font-size:10px;font-weight:700;color:#64748b;text-transform:uppercase;"
            + "letter-spacing:1px;border-bottom:2px solid #e2e8f0;"
            + "font-family:Arial,sans-serif;'>" + label + "</th>";
    }

    /** Ligne d'info label/valeur dans la partie commande */
    private String infoLine(String label, String value, String valueColor, boolean mono) {
        return "<p style='margin:0 0 6px;font-family:Arial,sans-serif;'>"
            + "<span style='display:block;font-size:10px;color:#94a3b8;'>" + label + "</span>"
            + "<span style='font-size:13px;font-weight:700;color:" + valueColor + ";"
            + (mono ? "font-family:\"Courier New\",monospace;" : "") + "'>" + value + "</span>"
            + "</p>";
    }

    /** Pilule de date (en-tête facture) */
    private String datePill(String label, String value, String valueColor, String bg, String border) {
        return "<td width='32%' style='background:" + bg + ";border:1px solid " + border + ";"
            + "border-radius:10px;padding:14px;'>"
            + "<p style='margin:0 0 4px;font-size:10px;font-weight:700;text-transform:uppercase;"
            + "letter-spacing:1px;color:#94a3b8;font-family:Arial,sans-serif;'>" + label + "</p>"
            + "<p style='margin:0;font-size:14px;font-weight:700;color:" + valueColor + ";font-family:Arial,sans-serif;'>" + value + "</p>"
            + "</td>";
    }

    /** Ligne dans le bloc totaux */
    private String totalRow(String label, String value, boolean isTotal, String accent) {
        if (isTotal) {
            return "<tr>"
                + "<td style='padding:10px 0 0;font-size:14px;font-weight:700;color:#0f172a;font-family:Arial,sans-serif;'>" + label + "</td>"
                + "<td style='padding:10px 0 0;text-align:right;font-size:20px;font-weight:900;color:" + accent + ";font-family:Arial,sans-serif;'>" + value + "</td>"
                + "</tr>";
        }
        return "<tr>"
            + "<td style='padding:5px 0;font-size:13px;color:#64748b;font-family:Arial,sans-serif;'>" + label + "</td>"
            + "<td style='padding:5px 0;text-align:right;font-size:13px;font-weight:600;color:#0f172a;font-family:Arial,sans-serif;'>" + value + "</td>"
            + "</tr>";
    }

    /** Construction des lignes du tableau produits avec couleurs alternées */
    private String buildLignesRows(List<LigneCommande> lignes) {
        if (lignes.isEmpty()) {
            return "<tr><td colspan='5' style='padding:24px;text-align:center;color:#94a3b8;"
                + "font-size:13px;font-family:Arial,sans-serif;font-style:italic;'>"
                + "Aucun d&eacute;tail de produit disponible.</td></tr>";
        }

        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (LigneCommande l : lignes) {
            String rowBg = (i % 2 == 0) ? "#ffffff" : "#f8fafc";
            BigDecimal pu     = l.getPrixUnitaire();
            BigDecimal remise = l.getRemise() != null ? l.getRemise() : BigDecimal.ZERO;
            BigDecimal qty    = BigDecimal.valueOf(l.getQuantite());
            BigDecimal coeff  = BigDecimal.ONE.subtract(remise.divide(BigDecimal.valueOf(100)));
            BigDecimal htLine = pu.multiply(qty).multiply(coeff).setScale(3, RoundingMode.HALF_UP);

            String nomProduit = l.getProduit() != null ? l.getProduit().getNom() : "—";
            String refProduit = (l.getProduit() != null && l.getProduit().getReference() != null)
                ? "<br><span style='font-family:\"Courier New\",monospace;font-size:10px;color:#94a3b8;'>"
                  + l.getProduit().getReference() + "</span>"
                : "";

            String remiseTxt = remise.compareTo(BigDecimal.ZERO) > 0
                ? "<span style='background:#fee2e2;color:#ef4444;font-size:10px;font-weight:700;"
                  + "padding:2px 7px;border-radius:4px;'>-" + remise.stripTrailingZeros().toPlainString() + "%</span>"
                : "<span style='color:#cbd5e1;font-size:13px;'>—</span>";

            sb.append("<tr style='background:" + rowBg + ";'>")
              .append("<td style='padding:13px 14px;border-bottom:1px solid #f1f5f9;font-size:13px;color:#0f172a;font-family:Arial,sans-serif;'>")
              .append("<strong>" + nomProduit + "</strong>" + refProduit)
              .append("</td>")
              .append("<td style='padding:13px 14px;border-bottom:1px solid #f1f5f9;text-align:center;font-size:13px;font-weight:700;color:#0f172a;font-family:Arial,sans-serif;'>")
              .append(l.getQuantite())
              .append("</td>")
              .append("<td style='padding:13px 14px;border-bottom:1px solid #f1f5f9;text-align:right;font-size:13px;color:#475569;font-family:Arial,sans-serif;white-space:nowrap;'>")
              .append(String.format("%.3f", pu)).append(" TND")
              .append("</td>")
              .append("<td style='padding:13px 14px;border-bottom:1px solid #f1f5f9;text-align:center;font-family:Arial,sans-serif;'>")
              .append(remiseTxt)
              .append("</td>")
              .append("<td style='padding:13px 14px;border-bottom:1px solid #f1f5f9;text-align:right;font-size:14px;font-weight:800;color:#0f172a;font-family:Arial,sans-serif;white-space:nowrap;'>")
              .append(String.format("%.3f", htLine)).append(" TND")
              .append("</td>")
              .append("</tr>");
            i++;
        }
        return sb.toString();
    }

    /** Statut → [label, bg, fg] */
    private String[] getStatutStyle(String statut, boolean rappel) {
        return switch (statut) {
            case "PAYEE"   -> new String[]{"✅ Pay&eacute;e",   "#d1fae5", "#059669"};
            case "ANNULEE" -> new String[]{"&#10060; Annul&eacute;e", "#fee2e2", "#dc2626"};
            case "IMPAYEE" -> new String[]{"&#128680; Impay&eacute;e", "#fee2e2", "#dc2626"};
            default        -> rappel
                ? new String[]{"&#9888; En retard", "#fef2f2", "#dc2626"}
                : new String[]{"&#9203; En attente", "#fef3c7", "#d97706"};
        };
    }

    // ════════════════════════════════════════════════════════════
    // EMAIL : VALIDATION KYC
    // ════════════════════════════════════════════════════════════

    private String buildEmailValidationKyc(String prenom) {
        String nom = prenom != null && !prenom.isBlank() ? prenom : "Cher client";
        return """
<!DOCTYPE html>
<html lang="fr">
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
<body style="margin:0;padding:0;background:#f1f5f9;font-family:'Segoe UI',Arial,sans-serif;">
  <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f1f5f9;padding:32px 16px;">
    <tr><td align="center">
      <table width="600" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:20px;overflow:hidden;box-shadow:0 8px 32px rgba(0,0,0,0.10);">

        <!-- Header -->
        <tr><td style="background:linear-gradient(135deg,#0f172a 0%%,#1e3a5f 60%%,#1e40af 100%%);padding:36px 40px;text-align:center;">
          <div style="display:inline-block;background:linear-gradient(135deg,#f97316,#ea580c);width:56px;height:56px;border-radius:14px;line-height:56px;font-size:28px;margin-bottom:16px;">📊</div>
          <h1 style="color:#ffffff;font-size:22px;font-weight:900;margin:0 0 6px;letter-spacing:-0.02em;">BENJEDDOU ERP</h1>
          <p style="color:rgba(255,255,255,0.5);font-size:12px;margin:0;">Plateforme de gestion d'entreprise</p>
        </td></tr>

        <!-- Badge succès -->
        <tr><td style="background:#f0fdf4;border-bottom:2px solid #dcfce7;padding:24px 40px;text-align:center;">
          <div style="font-size:48px;margin-bottom:8px;">✅</div>
          <h2 style="color:#15803d;font-size:18px;font-weight:800;margin:0;">Dossier validé avec succès !</h2>
        </td></tr>

        <!-- Corps -->
        <tr><td style="padding:36px 40px;">
          <p style="color:#1e293b;font-size:16px;font-weight:600;margin:0 0 12px;">Bonjour %s,</p>
          <p style="color:#475569;font-size:14px;line-height:1.7;margin:0 0 20px;">
            Nous avons le plaisir de vous informer que votre dossier de vérification d'identité (KYC)
            a été <strong style="color:#15803d;">examiné et validé</strong> par notre équipe.
          </p>

          <!-- Étapes -->
          <div style="background:#f8fafc;border:1px solid #f1f5f9;border-radius:12px;padding:20px;margin-bottom:24px;">
            <p style="color:#64748b;font-size:12px;font-weight:700;text-transform:uppercase;letter-spacing:0.06em;margin:0 0 14px;">Prochaines étapes</p>
            <div style="display:flex;align-items:center;gap:12px;margin-bottom:10px;">
              <span style="background:#22c55e;color:#fff;width:24px;height:24px;border-radius:50%%;display:inline-block;text-align:center;line-height:24px;font-size:12px;font-weight:800;flex-shrink:0;">✓</span>
              <span style="color:#475569;font-size:13px;">Inscription et vérification email</span>
            </div>
            <div style="display:flex;align-items:center;gap:12px;margin-bottom:10px;">
              <span style="background:#22c55e;color:#fff;width:24px;height:24px;border-radius:50%%;display:inline-block;text-align:center;line-height:24px;font-size:12px;font-weight:800;flex-shrink:0;">✓</span>
              <span style="color:#475569;font-size:13px;">Soumission et validation des documents KYC</span>
            </div>
            <div style="display:flex;align-items:center;gap:12px;margin-bottom:10px;">
              <span style="background:#f97316;color:#fff;width:24px;height:24px;border-radius:50%%;display:inline-block;text-align:center;line-height:24px;font-size:12px;font-weight:800;flex-shrink:0;">→</span>
              <span style="color:#f97316;font-weight:700;font-size:13px;">Choisir votre plan d'abonnement</span>
            </div>
            <div style="display:flex;align-items:center;gap:12px;">
              <span style="background:#e2e8f0;color:#94a3b8;width:24px;height:24px;border-radius:50%%;display:inline-block;text-align:center;line-height:24px;font-size:12px;font-weight:800;flex-shrink:0;">4</span>
              <span style="color:#94a3b8;font-size:13px;">Activation de votre compte ERP complet</span>
            </div>
          </div>

          <!-- CTA -->
          <div style="text-align:center;margin:28px 0;">
            <a href="http://localhost:4200/abonnement"
               style="display:inline-block;background:linear-gradient(135deg,#f97316,#ea580c);color:#ffffff;text-decoration:none;padding:16px 40px;border-radius:12px;font-size:15px;font-weight:800;box-shadow:0 8px 24px rgba(249,115,22,0.4);">
              💳 Choisir mon abonnement →
            </a>
          </div>

          <p style="color:#94a3b8;font-size:12px;text-align:center;margin:0 0 4px;">
            Ou connectez-vous sur <a href="http://localhost:4200/login" style="color:#f97316;">http://localhost:4200</a>
          </p>
        </td></tr>

        <!-- Footer -->
        <tr><td style="background:#0f172a;padding:20px 40px;text-align:center;">
          <p style="color:rgba(255,255,255,0.4);font-size:11px;margin:0 0 4px;">BENJEDDOU TECHNOLOGIE SERVICES</p>
          <p style="color:rgba(255,255,255,0.25);font-size:10px;margin:0;">Cet email est envoyé automatiquement. Merci de ne pas y répondre.</p>
        </td></tr>

      </table>
    </td></tr>
  </table>
</body>
</html>
""".formatted(nom);
    }

    // ════════════════════════════════════════════════════════════
    // EMAIL : ACTIVATION DU COMPTE
    // ════════════════════════════════════════════════════════════

    private String buildEmailActivationCompte(String prenom, String typePlan, String dateFin) {
        String nom       = prenom  != null && !prenom.isBlank()  ? prenom  : "Cher client";
        String planLabel = typePlan != null ? switch(typePlan) {
            case "MENSUEL"      -> "Mensuel (1 mois)";
            case "TRIMESTRIEL"  -> "Trimestriel (3 mois)";
            case "ANNUEL"       -> "Annuel (12 mois)";
            default             -> typePlan;
        } : "—";
        String expiration = dateFin != null && !dateFin.isBlank() ? dateFin.substring(0, 10).replace("-", "/") : "—";

        return """
<!DOCTYPE html>
<html lang="fr">
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
<body style="margin:0;padding:0;background:#f1f5f9;font-family:'Segoe UI',Arial,sans-serif;">
  <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f1f5f9;padding:32px 16px;">
    <tr><td align="center">
      <table width="600" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:20px;overflow:hidden;box-shadow:0 8px 32px rgba(0,0,0,0.10);">

        <!-- Header -->
        <tr><td style="background:linear-gradient(135deg,#0f172a 0%%,#1e3a5f 60%%,#1e40af 100%%);padding:36px 40px;text-align:center;">
          <div style="display:inline-block;background:linear-gradient(135deg,#f97316,#ea580c);width:56px;height:56px;border-radius:14px;line-height:56px;font-size:28px;margin-bottom:16px;">📊</div>
          <h1 style="color:#ffffff;font-size:22px;font-weight:900;margin:0 0 6px;letter-spacing:-0.02em;">BENJEDDOU ERP</h1>
          <p style="color:rgba(255,255,255,0.5);font-size:12px;margin:0;">Plateforme de gestion d'entreprise</p>
        </td></tr>

        <!-- Badge félicitations -->
        <tr><td style="background:linear-gradient(135deg,#fff7ed,#fef9c3);border-bottom:2px solid #fed7aa;padding:28px 40px;text-align:center;">
          <div style="font-size:52px;margin-bottom:10px;">🎉</div>
          <h2 style="color:#c2410c;font-size:20px;font-weight:900;margin:0 0 6px;">Bienvenue sur BENJEDDOU ERP !</h2>
          <p style="color:#92400e;font-size:13px;margin:0;">Votre compte est maintenant actif et opérationnel.</p>
        </td></tr>

        <!-- Corps -->
        <tr><td style="padding:36px 40px;">
          <p style="color:#1e293b;font-size:16px;font-weight:600;margin:0 0 12px;">Bonjour %s,</p>
          <p style="color:#475569;font-size:14px;line-height:1.7;margin:0 0 24px;">
            Votre paiement a été <strong style="color:#15803d;">confirmé</strong> et votre compte ERP est désormais
            <strong style="color:#f97316;">entièrement activé</strong>. Vous avez maintenant accès à toutes les
            fonctionnalités de la plateforme.
          </p>

          <!-- Détails abonnement -->
          <div style="background:#f8fafc;border:1.5px solid #e2e8f0;border-radius:14px;padding:20px;margin-bottom:24px;">
            <p style="color:#64748b;font-size:11px;font-weight:700;text-transform:uppercase;letter-spacing:0.06em;margin:0 0 14px;">Détails de votre abonnement</p>
            <table width="100%%" cellpadding="0" cellspacing="0">
              <tr>
                <td style="color:#94a3b8;font-size:13px;padding:6px 0;">Plan souscrit</td>
                <td style="color:#0f172a;font-size:13px;font-weight:700;text-align:right;">%s</td>
              </tr>
              <tr>
                <td style="color:#94a3b8;font-size:13px;padding:6px 0;border-top:1px solid #f1f5f9;">Date d'activation</td>
                <td style="color:#0f172a;font-size:13px;font-weight:700;text-align:right;border-top:1px solid #f1f5f9;">Aujourd'hui</td>
              </tr>
              <tr>
                <td style="color:#94a3b8;font-size:13px;padding:6px 0;border-top:1px solid #f1f5f9;">Expire le</td>
                <td style="color:#f97316;font-size:13px;font-weight:700;text-align:right;border-top:1px solid #f1f5f9;">%s</td>
              </tr>
            </table>
          </div>

          <!-- Modules disponibles -->
          <div style="background:#f0fdf4;border:1px solid #dcfce7;border-radius:12px;padding:16px 20px;margin-bottom:24px;">
            <p style="color:#15803d;font-size:12px;font-weight:700;text-transform:uppercase;letter-spacing:0.05em;margin:0 0 10px;">✅ Modules inclus dans votre abonnement</p>
            <div style="display:grid;grid-template-columns:1fr 1fr;gap:6px;">
              <span style="color:#475569;font-size:12px;">📦 Gestion de stock</span>
              <span style="color:#475569;font-size:12px;">🛒 Module commercial</span>
              <span style="color:#475569;font-size:12px;">💰 Facturation</span>
              <span style="color:#475569;font-size:12px;">📊 Tableau de bord</span>
              <span style="color:#475569;font-size:12px;">👥 Gestion utilisateurs</span>
              <span style="color:#475569;font-size:12px;">📈 Rapports & KPIs</span>
            </div>
          </div>

          <!-- CTA -->
          <div style="text-align:center;margin:28px 0 16px;">
            <a href="http://localhost:4200/login"
               style="display:inline-block;background:linear-gradient(135deg,#f97316,#ea580c);color:#ffffff;text-decoration:none;padding:16px 48px;border-radius:12px;font-size:15px;font-weight:800;box-shadow:0 8px 24px rgba(249,115,22,0.4);">
              🚀 Accéder à mon espace ERP →
            </a>
          </div>
        </td></tr>

        <!-- Footer -->
        <tr><td style="background:#0f172a;padding:20px 40px;text-align:center;">
          <p style="color:rgba(255,255,255,0.4);font-size:11px;margin:0 0 4px;">BENJEDDOU TECHNOLOGIE SERVICES</p>
          <p style="color:rgba(255,255,255,0.25);font-size:10px;margin:0;">Cet email est envoyé automatiquement. Merci de ne pas y répondre.</p>
        </td></tr>

      </table>
    </td></tr>
  </table>
</body>
</html>
""".formatted(nom, planLabel, expiration);
    }
}
