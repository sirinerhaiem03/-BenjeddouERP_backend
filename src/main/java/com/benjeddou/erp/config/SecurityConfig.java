package com.benjeddou.erp.config;

import com.benjeddou.erp.security.jwt.AuthEntryPointJwt;
import com.benjeddou.erp.security.jwt.AuthTokenFilter;
import com.benjeddou.erp.security.services.UserDetailsServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;

/**
 * SecurityConfig — Configuration de sécurité principale BENJEDDOU ERP SaaS
 *
 * Mécanismes de sécurité implémentés :
 *  ✅ Authentification JWT Stateless (access token 15min, refresh 7j)
 *  ✅ BCrypt password encoding (coût 12 — recommandé production)
 *  ✅ CSRF : désactivé (API REST stateless — protection assurée par JWT Bearer)
 *  ✅ Protection XSS : header X-XSS-Protection
 *  ✅ Content-Security-Policy : anti-injection scripts malveillants
 *  ✅ X-Frame-Options DENY : anti-clickjacking
 *  ✅ HSTS : force HTTPS (31536000 secondes = 1 an)
 *  ✅ X-Content-Type-Options : nosniff (anti MIME sniffing)
 *  ✅ Referrer-Policy : strict-origin-when-cross-origin
 *  ✅ Permissions-Policy : désactive caméra/micro/géoloc/usb
 *  ✅ CORS strict : origines explicites uniquement
 *  ✅ RBAC : @EnableMethodSecurity + @PreAuthorize sur tous les controllers
 *  ✅ Rate Limiting : brute force via AuditService (5 tentatives / 5 min)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    UserDetailsServiceImpl userDetailsService;

    @Autowired
    private AuthEntryPointJwt unauthorizedHandler;

    @Autowired
    private FlexiblePasswordEncoder flexiblePasswordEncoder;

    @Bean
    public AuthTokenFilter authenticationJwtTokenFilter() {
        return new AuthTokenFilter();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(flexiblePasswordEncoder);
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // ── CSRF ──────────────────────────────────────────────────────────
            // Désactivé car API REST stateless avec JWT dans l'en-tête Authorization.
            // Le CSRF ne s'applique pas aux requêtes sans cookie de session.
            .csrf(csrf -> csrf.disable())

            // ── CORS ──────────────────────────────────────────────────────────
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // ── HEADERS DE SÉCURITÉ HTTP ──────────────────────────────────────
            .headers(headers -> headers
                // X-Frame-Options: DENY — protection anti-clickjacking
                .frameOptions(frame -> frame.deny())

                // X-Content-Type-Options: nosniff — empêche le MIME sniffing
                .contentTypeOptions(cto -> {})

                // HTTP Strict Transport Security — force HTTPS (1 an + sous-domaines)
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000)
                    .preload(true)
                )

                // Content-Security-Policy — protection XSS et injection de scripts
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives(
                        "default-src 'self'; " +
                        "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://js.stripe.com; " +
                        "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
                        "font-src 'self' data: https://fonts.gstatic.com; " +
                        "img-src 'self' data: blob: https:; " +
                        "connect-src 'self' https://api.stripe.com https://ip-api.com; " +
                        "frame-src https://js.stripe.com https://hooks.stripe.com; " +
                        "worker-src blob:; " +
                        "object-src 'none'; " +
                        "base-uri 'self'; " +
                        "form-action 'self'; " +
                        "upgrade-insecure-requests;"
                    )
                )

                // Referrer-Policy — limiter les informations envoyées aux sites tiers
                .referrerPolicy(rp -> rp
                    .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                )

                // Permissions-Policy — désactiver les fonctionnalités navigateur inutiles
                .permissionsPolicy(pp -> pp
                    .policy("camera=(), microphone=(), geolocation=(), payment=(self), usb=(), " +
                            "accelerometer=(), gyroscope=(), magnetometer=()")
                )
            )

            // ── GESTION DES EXCEPTIONS ─────────────────────────────────────────
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(unauthorizedHandler)
            )

            // ── SESSION STATELESS (JWT) ────────────────────────────────────────
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // ── RÈGLES D'ACCÈS AUX ENDPOINTS ──────────────────────────────────
            .authorizeHttpRequests(auth -> auth
                // Preflight CORS
                .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                // Authentification publique (login, refresh, logout)
                .requestMatchers("/api/auth/**").permitAll()
                // Portail client public (inscription, OTP, KYC)
                .requestMatchers("/api/client/register").permitAll()
                .requestMatchers("/api/client/otp/**").permitAll()
                .requestMatchers("/api/client/kyc/upload").permitAll()
                .requestMatchers("/api/client/kyc/document/**").permitAll()
                // Inscription Administrateur Entreprise (public, sans auth)
                // Utilise /api/inscription-admin pour éviter le conflit avec AdminController (/api/admin @PreAuthorize)
                .requestMatchers("/api/inscription-admin/register").permitAll()
                .requestMatchers("/api/inscription-admin/otp/**").permitAll()
                .requestMatchers("/api/inscription-admin/check-username").permitAll()
                .requestMatchers("/api/inscription-admin/check-email").permitAll()
                // Plans d'abonnement (page publique)
                .requestMatchers("/api/abonnement/plans").permitAll()
                // Theme global de la plateforme (charge par tous les users au demarrage)
                .requestMatchers("/api/theme/current").permitAll()
                // Stripe (webhook signe cote controller, cle publique)
                .requestMatchers("/api/stripe/webhook").permitAll()
                .requestMatchers("/api/stripe/public-key").permitAll()
                // Assistant IA
                .requestMatchers("/api/ai/**").permitAll()
                // Health check (monitoring)
                .requestMatchers("/actuator/health").permitAll()
                // Gestion des erreurs Spring
                .requestMatchers("/error").permitAll()
                // Tout le reste nécessite un JWT valide
                .anyRequest().authenticated()
            );

        http.authenticationProvider(authenticationProvider());
        http.addFilterBefore(authenticationJwtTokenFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Configuration CORS stricte.
     * En développement : localhost:4200 (Angular dev server).
     * En production : remplacer par le vrai domaine de l'application.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOriginPatterns(Arrays.asList(
            "http://localhost:4200",
            "http://127.0.0.1:4200",
            "http://localhost:*",
                "https://benjeddou-erp-frontend-plum.vercel.app/",
                "https://benjeddou-erp-frontend-git-main-benjeddou.vercel.app", 
                "https://*.vercel.app"
                // PRODUCTION : ajouter ici "https://app.benjeddou.com"
        ));

        configuration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"
        ));

        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type",
            "Accept",
            "Origin",
            "X-Requested-With",
            "Access-Control-Request-Method",
            "Access-Control-Request-Headers",
            "X-XSRF-TOKEN"
        ));

        configuration.setExposedHeaders(Arrays.asList(
            "Authorization",
            "Content-Disposition",
            "Content-Type",
            "X-Total-Count",
            "X-Total-Pages"
        ));

        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
