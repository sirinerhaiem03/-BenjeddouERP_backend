package com.benjeddou.erp.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

/**
 * ThemeConfig — Configuration visuelle globale de la plateforme.
 * Stockée dans la base MASTER (une seule ligne : id=1).
 * Chargée publiquement au démarrage de l'app Angular pour tous les utilisateurs.
 */
@Entity
@Table(name = "theme_config")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ThemeConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Couleur principale (hex) */
    @Column(name = "primary_color", length = 20)
    private String primaryColor;

    /** Couleur accentuation (hex) */
    @Column(name = "accent_color", length = 20)
    private String accentColor;

    /** Couleur fond sidebar (hex) */
    @Column(name = "sidebar_color", length = 20)
    private String sidebarColor;

    /** Police principale */
    @Column(name = "font_family", length = 100)
    private String fontFamily;

    /** Rayon des coins (px) */
    @Column(name = "border_radius", length = 20)
    private String borderRadius;

    /** Mode sombre (true/false) */
    @Column(name = "dark_mode")
    private Boolean darkMode;

    /** Mode compact */
    @Column(name = "compact_mode")
    private Boolean compactMode;

    /** Nom de la plateforme affiché */
    @Column(name = "logo_text", length = 100)
    private String logoText;

    /**
     * Jeu d'icones Material Symbols.
     * Valeurs possibles : "outlined", "rounded", "sharp"
     */
    @Column(name = "icon_set", length = 30)
    private String iconSet;

    /** URL du logo (base64 ou lien externe) */
    @Column(name = "logo_url", columnDefinition = "TEXT")
    private String logoUrl;

    /** Modules visibles dans la sidebar (JSON : ex: ["finance","stock","commercial"]) */
    @Column(name = "visible_modules", columnDefinition = "TEXT")
    private String visibleModules;

    /** Dernière mise à jour */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Nom du SuperAdmin qui a fait le dernier changement */
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    /** Retourne un thème par défaut (utilisé à l'initialisation) */
    public static ThemeConfig defaultTheme() {
        return ThemeConfig.builder()
                .primaryColor("#f97316")
                .accentColor("#a855f7")
                .sidebarColor("#0f172a")
                .fontFamily("Inter, sans-serif")
                .borderRadius("12px")
                .darkMode(false)        // Thème CLAIR par défaut (exigé par l'encadrant)
                .compactMode(false)
                .logoText("BENJEDDOU ERP")
                .iconSet("outlined")
                .visibleModules("[\"finance\",\"stock\",\"commercial\",\"rh\",\"rapports\",\"admin\"]")
                .build();
    }
}
