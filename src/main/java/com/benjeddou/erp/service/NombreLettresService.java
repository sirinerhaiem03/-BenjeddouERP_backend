package com.benjeddou.erp.service;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Service de conversion de montants numériques en toutes lettres.
 * Supporte : Français (fr), Anglais (en), Arabe (ar).
 * Gère : montants HT, TVA, TTC, devises (TND, EUR, USD, MAD).
 */
@Service
public class NombreLettresService {

    // ═══════════════════════════════════════════════════════════════════
    //  FRANÇAIS
    // ═══════════════════════════════════════════════════════════════════

    private static final String[] UNITES_FR = {
        "", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf",
        "dix", "onze", "douze", "treize", "quatorze", "quinze", "seize",
        "dix-sept", "dix-huit", "dix-neuf"
    };
    private static final String[] DIZAINES_FR = {
        "", "", "vingt", "trente", "quarante", "cinquante",
        "soixante", "soixante", "quatre-vingt", "quatre-vingt"
    };

    private String centiersFr(long n) {
        if (n == 0) return "zéro";
        StringBuilder sb = new StringBuilder();
        if (n < 0) { sb.append("moins "); n = -n; }
        if (n >= 1_000_000_000L) {
            long milliards = n / 1_000_000_000L;
            sb.append(centiersFr(milliards)).append(milliards == 1 ? " milliard" : " milliards");
            n %= 1_000_000_000L;
            if (n > 0) sb.append(" ");
        }
        if (n >= 1_000_000L) {
            long millions = n / 1_000_000L;
            sb.append(centiersFr(millions)).append(millions == 1 ? " million" : " millions");
            n %= 1_000_000L;
            if (n > 0) sb.append(" ");
        }
        if (n >= 1000L) {
            long milliers = n / 1000L;
            if (milliers == 1) sb.append("mille");
            else sb.append(centiersFr(milliers)).append(" mille");
            n %= 1000L;
            if (n > 0) sb.append(" ");
        }
        if (n >= 100L) {
            long cents = n / 100L;
            if (cents == 1) sb.append("cent");
            else sb.append(UNITES_FR[(int) cents]).append(" cent");
            n %= 100L;
            if (n > 0) sb.append(" ");
            else if (cents > 1) sb.append("s");
        }
        if (n > 0) {
            if (n < 20) {
                sb.append(UNITES_FR[(int) n]);
            } else {
                int dizaine = (int) (n / 10);
                int unite = (int) (n % 10);
                if (dizaine == 7 || dizaine == 9) {
                    sb.append(DIZAINES_FR[dizaine]);
                    sb.append(unite == 1 ? "-et-" : "-");
                    sb.append(UNITES_FR[(int) (n - dizaine * 10L + 10)]);
                } else {
                    sb.append(DIZAINES_FR[dizaine]);
                    if (unite > 0) {
                        sb.append(unite == 1 ? "-et-" : "-");
                        sb.append(UNITES_FR[unite]);
                    } else if (dizaine == 8) {
                        sb.append("s");
                    }
                }
            }
        }
        return sb.toString().trim();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  ANGLAIS
    // ═══════════════════════════════════════════════════════════════════

    private static final String[] UNITES_EN = {
        "", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
        "seventeen", "eighteen", "nineteen"
    };
    private static final String[] DIZAINES_EN = {
        "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"
    };

    private String centiersEn(long n) {
        if (n == 0) return "zero";
        StringBuilder sb = new StringBuilder();
        if (n < 0) { sb.append("minus "); n = -n; }
        if (n >= 1_000_000_000L) {
            sb.append(centiersEn(n / 1_000_000_000L)).append(" billion");
            n %= 1_000_000_000L;
            if (n > 0) sb.append(" ");
        }
        if (n >= 1_000_000L) {
            sb.append(centiersEn(n / 1_000_000L)).append(" million");
            n %= 1_000_000L;
            if (n > 0) sb.append(" ");
        }
        if (n >= 1000L) {
            sb.append(centiersEn(n / 1000L)).append(" thousand");
            n %= 1000L;
            if (n > 0) sb.append(" ");
        }
        if (n >= 100L) {
            sb.append(UNITES_EN[(int) (n / 100L)]).append(" hundred");
            n %= 100L;
            if (n > 0) sb.append(" ");
        }
        if (n > 0) {
            if (n < 20) {
                sb.append(UNITES_EN[(int) n]);
            } else {
                sb.append(DIZAINES_EN[(int) (n / 10)]);
                if (n % 10 > 0) sb.append("-").append(UNITES_EN[(int) (n % 10)]);
            }
        }
        return sb.toString().trim();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  ARABE
    // ═══════════════════════════════════════════════════════════════════

    private static final String[] UNITES_AR = {
        "", "واحد", "اثنان", "ثلاثة", "أربعة", "خمسة", "ستة", "سبعة", "ثمانية", "تسعة",
        "عشرة", "أحد عشر", "اثنا عشر", "ثلاثة عشر", "أربعة عشر", "خمسة عشر",
        "ستة عشر", "سبعة عشر", "ثمانية عشر", "تسعة عشر"
    };
    private static final String[] DIZAINES_AR = {
        "", "", "عشرون", "ثلاثون", "أربعون", "خمسون", "ستون", "سبعون", "ثمانون", "تسعون"
    };
    private static final String[] CENTAINES_AR = {
        "", "مئة", "مئتان", "ثلاثمئة", "أربعمئة", "خمسمئة",
        "ستمئة", "سبعمئة", "ثمانمئة", "تسعمئة"
    };

    private String centiersAr(long n) {
        if (n == 0) return "صفر";
        StringBuilder sb = new StringBuilder();
        if (n >= 1_000_000_000L) {
            sb.append(centiersAr(n / 1_000_000_000L)).append(" مليار");
            n %= 1_000_000_000L;
            if (n > 0) sb.append(" و");
        }
        if (n >= 1_000_000L) {
            sb.append(centiersAr(n / 1_000_000L)).append(" مليون");
            n %= 1_000_000L;
            if (n > 0) sb.append(" و");
        }
        if (n >= 1000L) {
            long milliers = n / 1000L;
            if (milliers == 1) sb.append("ألف");
            else if (milliers == 2) sb.append("ألفان");
            else if (milliers <= 10) sb.append(UNITES_AR[(int) milliers]).append(" آلاف");
            else sb.append(centiersAr(milliers)).append(" ألف");
            n %= 1000L;
            if (n > 0) sb.append(" و");
        }
        if (n >= 100L) {
            sb.append(CENTAINES_AR[(int) (n / 100L)]);
            n %= 100L;
            if (n > 0) sb.append(" و");
        }
        if (n > 0) {
            if (n < 20) {
                sb.append(UNITES_AR[(int) n]);
            } else {
                int unite = (int) (n % 10);
                if (unite > 0) {
                    sb.append(UNITES_AR[unite]).append(" و");
                }
                sb.append(DIZAINES_AR[(int) (n / 10)]);
            }
        }
        return sb.toString().trim();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  API PUBLIQUE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Convertit un montant en lettres avec devise et centimes.
     *
     * @param montant  Montant numérique (ex: 1234.75)
     * @param devise   Code devise : TND, EUR, USD, MAD (défaut TND)
     * @param langue   Code langue : fr, en, ar (défaut fr)
     * @return         Texte complet (ex: "mille deux-cent-trente-quatre dinars et soixante-quinze millimes")
     */
    public String convertir(BigDecimal montant, String devise, String langue) {
        if (montant == null) return "";
        montant = montant.setScale(3, RoundingMode.HALF_UP);

        long partieEntiere = montant.longValue();
        long partieCentimes = Math.round((montant.subtract(BigDecimal.valueOf(partieEntiere)).doubleValue()) * 1000);
        // Arrondi à 3 décimales pour TND (millimes), 2 pour EUR/USD
        boolean troisdecimales = "TND".equalsIgnoreCase(devise) || "MAD".equalsIgnoreCase(devise);
        if (!troisdecimales) {
            partieCentimes = Math.round(partieCentimes / 10.0);
        }

        String lang = langue != null ? langue.toLowerCase() : "fr";
        return switch (lang) {
            case "en" -> convertirEn(partieEntiere, partieCentimes, devise, troisdecimales);
            case "ar" -> convertirAr(partieEntiere, partieCentimes, devise, troisdecimales);
            default  -> convertirFr(partieEntiere, partieCentimes, devise, troisdecimales);
        };
    }

    private String convertirFr(long entier, long centimes, String devise, boolean troisdecimales) {
        String[] noms = nomDeviseFr(devise);
        String resultat = capitaliser(centiersFr(entier)) + " " + (entier > 1 ? noms[1] : noms[0]);
        if (centimes > 0) {
            String uniteCentimes = troisdecimales ? (centimes > 1 ? "millimes" : "millime") : (centimes > 1 ? "centimes" : "centime");
            resultat += " et " + centiersFr(centimes) + " " + uniteCentimes;
        }
        return resultat;
    }

    private String convertirEn(long entier, long centimes, String devise, boolean troisdecimales) {
        String[] noms = nomDeviseEn(devise);
        String resultat = capitaliser(centiersEn(entier)) + " " + (entier > 1 ? noms[1] : noms[0]);
        if (centimes > 0) {
            String uniteCentimes = troisdecimales ? (centimes > 1 ? "millimes" : "millime") : (centimes > 1 ? "cents" : "cent");
            resultat += " and " + centiersEn(centimes) + " " + uniteCentimes;
        }
        return resultat;
    }

    private String convertirAr(long entier, long centimes, String devise, boolean troisdecimales) {
        String[] noms = nomDeviseAr(devise);
        String resultat = centiersAr(entier) + " " + (entier > 1 ? noms[1] : noms[0]);
        if (centimes > 0) {
            String uniteCentimes = troisdecimales ? "مليم" : "سنتيم";
            resultat += " و" + centiersAr(centimes) + " " + uniteCentimes;
        }
        return resultat;
    }

    /** Noms de devises FR : [singulier, pluriel] */
    private String[] nomDeviseFr(String devise) {
        return switch (devise != null ? devise.toUpperCase() : "TND") {
            case "EUR" -> new String[]{"euro", "euros"};
            case "USD" -> new String[]{"dollar", "dollars"};
            case "MAD" -> new String[]{"dirham", "dirhams"};
            default    -> new String[]{"dinar", "dinars"};
        };
    }

    private String[] nomDeviseEn(String devise) {
        return switch (devise != null ? devise.toUpperCase() : "TND") {
            case "EUR" -> new String[]{"euro", "euros"};
            case "USD" -> new String[]{"dollar", "dollars"};
            case "MAD" -> new String[]{"dirham", "dirhams"};
            default    -> new String[]{"dinar", "dinars"};
        };
    }

    private String[] nomDeviseAr(String devise) {
        return switch (devise != null ? devise.toUpperCase() : "TND") {
            case "EUR" -> new String[]{"يورو", "يورو"};
            case "USD" -> new String[]{"دولار", "دولارات"};
            case "MAD" -> new String[]{"درهم", "دراهم"};
            default    -> new String[]{"دينار", "دنانير"};
        };
    }

    /**
     * Convertit un montant financier complet avec HT, TVA et TTC.
     * Retourne une map avec les 3 montants en lettres.
     */
    public java.util.Map<String, String> convertirMontantComplet(
            BigDecimal montantHt, double tauxTva, String devise, String langue) {
        BigDecimal tva = montantHt.multiply(BigDecimal.valueOf(tauxTva / 100))
                                   .setScale(3, RoundingMode.HALF_UP);
        BigDecimal ttc = montantHt.add(tva).setScale(3, RoundingMode.HALF_UP);

        return java.util.Map.of(
            "montantHtLettres",  convertir(montantHt, devise, langue),
            "montantTvaLettres", convertir(tva, devise, langue),
            "montantTtcLettres", convertir(ttc, devise, langue),
            "montantHt",  montantHt.toString(),
            "montantTva", tva.toString(),
            "montantTtc", ttc.toString()
        );
    }

    private String capitaliser(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
