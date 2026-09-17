package com.benjeddou.erp.controller;

import com.benjeddou.erp.model.ThemeConfig;
import com.benjeddou.erp.repository.ThemeConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * ThemeConfigController — Gestion de la configuration visuelle globale.
 *
 * GET  /api/theme/current      — PUBLIC  : retourne le theme actuel (chargé par tous les users)
 * POST /api/theme/save         — SUPERADMIN uniquement : sauvegarde la configuration
 * POST /api/theme/reset        — SUPERADMIN uniquement : remet le theme par defaut
 */
@RestController
@RequestMapping("/api/theme")
@RequiredArgsConstructor
public class ThemeConfigController {

    private final ThemeConfigRepository themeConfigRepository;

    // ════════════════════════════════════════════════════════════════
    // GET /api/theme/current — PUBLIC (pas besoin d'etre connecte)
    // Charge le theme au demarrage de l'application Angular
    // ════════════════════════════════════════════════════════════════
    @GetMapping("/current")
    public ResponseEntity<?> getCurrentTheme() {
        ThemeConfig config = themeConfigRepository.findById(1L)
                .orElseGet(() -> {
                    // Premiere fois : creer et sauvegarder le theme par defaut
                    ThemeConfig defaultTheme = ThemeConfig.defaultTheme();
                    defaultTheme.setId(null); // laisser auto-increment
                    return themeConfigRepository.save(defaultTheme);
                });
        return ResponseEntity.ok(toMap(config));
    }

    // ════════════════════════════════════════════════════════════════
    // POST /api/theme/save — SUPERADMIN uniquement
    // ════════════════════════════════════════════════════════════════
    @PostMapping("/save")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> saveTheme(@RequestBody Map<String, Object> body,
                                        Authentication auth) {
        ThemeConfig config = themeConfigRepository.findById(1L)
                .orElseGet(ThemeConfig::new);

        // Appliquer les champs recus
        if (body.containsKey("primaryColor"))   config.setPrimaryColor((String) body.get("primaryColor"));
        if (body.containsKey("accentColor"))    config.setAccentColor((String) body.get("accentColor"));
        if (body.containsKey("sidebarColor"))   config.setSidebarColor((String) body.get("sidebarColor"));
        if (body.containsKey("fontFamily"))     config.setFontFamily((String) body.get("fontFamily"));
        if (body.containsKey("borderRadius"))   config.setBorderRadius((String) body.get("borderRadius"));
        if (body.containsKey("darkMode"))       config.setDarkMode((Boolean) body.get("darkMode"));
        if (body.containsKey("compactMode"))    config.setCompactMode((Boolean) body.get("compactMode"));
        if (body.containsKey("logoText"))       config.setLogoText((String) body.get("logoText"));
        if (body.containsKey("iconSet"))        config.setIconSet((String) body.get("iconSet"));
        if (body.containsKey("logoUrl"))        config.setLogoUrl((String) body.get("logoUrl"));
        if (body.containsKey("visibleModules")) config.setVisibleModules((String) body.get("visibleModules"));

        // Tracer qui a fait le changement
        config.setUpdatedBy(auth != null ? auth.getName() : "superadmin");

        ThemeConfig saved = themeConfigRepository.save(config);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Theme sauvegarde avec succes",
                "theme", toMap(saved)
        ));
    }

    // ════════════════════════════════════════════════════════════════
    // POST /api/theme/reset — SUPERADMIN uniquement
    // ════════════════════════════════════════════════════════════════
    @PostMapping("/reset")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> resetTheme(Authentication auth) {
        ThemeConfig config = themeConfigRepository.findById(1L)
                .orElseGet(ThemeConfig::new);

        ThemeConfig defaults = ThemeConfig.defaultTheme();
        config.setPrimaryColor(defaults.getPrimaryColor());
        config.setAccentColor(defaults.getAccentColor());
        config.setSidebarColor(defaults.getSidebarColor());
        config.setFontFamily(defaults.getFontFamily());
        config.setBorderRadius(defaults.getBorderRadius());
        config.setDarkMode(defaults.getDarkMode());
        config.setCompactMode(defaults.getCompactMode());
        config.setLogoText(defaults.getLogoText());
        config.setIconSet(defaults.getIconSet());
        config.setVisibleModules(defaults.getVisibleModules());
        config.setLogoUrl(null);
        config.setUpdatedBy(auth != null ? auth.getName() : "superadmin");

        ThemeConfig saved = themeConfigRepository.save(config);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Theme reinitialise aux valeurs par defaut",
                "theme", toMap(saved)
        ));
    }

    // ── Helper : convertit l'entite en Map pour la reponse JSON ────
    private Map<String, Object> toMap(ThemeConfig c) {
        return Map.ofEntries(
                Map.entry("primaryColor",   c.getPrimaryColor()   != null ? c.getPrimaryColor()   : "#f97316"),
                Map.entry("accentColor",    c.getAccentColor()    != null ? c.getAccentColor()    : "#a855f7"),
                Map.entry("sidebarColor",   c.getSidebarColor()   != null ? c.getSidebarColor()   : "#080e1a"),
                Map.entry("fontFamily",     c.getFontFamily()     != null ? c.getFontFamily()     : "Inter, sans-serif"),
                Map.entry("borderRadius",   c.getBorderRadius()   != null ? c.getBorderRadius()   : "12px"),
                Map.entry("darkMode",       c.getDarkMode()       != null ? c.getDarkMode()       : false),
                Map.entry("compactMode",    c.getCompactMode()    != null ? c.getCompactMode()    : false),
                Map.entry("logoText",       c.getLogoText()       != null ? c.getLogoText()       : "BENJEDDOU ERP"),
                Map.entry("iconSet",        c.getIconSet()        != null ? c.getIconSet()        : "outlined"),
                Map.entry("logoUrl",        c.getLogoUrl()        != null ? c.getLogoUrl()        : ""),
                Map.entry("visibleModules", c.getVisibleModules() != null ? c.getVisibleModules() : "[]"),
                Map.entry("updatedBy",      c.getUpdatedBy()      != null ? c.getUpdatedBy()      : ""),
                Map.entry("updatedAt",      c.getUpdatedAt()      != null ? c.getUpdatedAt().toString() : "")
        );
    }
}
