package com.benjeddou.erp.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * GlobalExceptionHandler — Gestionnaire centralisé des erreurs HTTP.
 *
 * Intercepte :
 *  - MethodArgumentNotValidException : erreurs de validation Bean Validation (@Valid)
 *    → 400 Bad Request avec la liste des champs invalides
 *  - AccessDeniedException           : tentative d'accès non autorisée (RBAC)
 *    → 403 Forbidden
 *  - Exception générique             : toute erreur non gérée
 *    → 500 Internal Server Error (message générique, pas de stacktrace exposée)
 *
 * Ce handler est essentiel pour la protection anti–SQL Injection car il garantit
 * que les contraintes Bean Validation (@NotBlank, @Email, @Size, @Pattern, etc.)
 * produisent des réponses HTTP 400 claires avant que les données n'atteignent
 * la couche service ou la base de données.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Gère les erreurs de validation des requêtes (@Valid sur les @RequestBody).
     * Retourne un 400 avec la liste détaillée des champs invalides.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        List<Map<String, String>> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError -> {
                    Map<String, String> err = new HashMap<>();
                    err.put("champ", fieldError.getField());
                    err.put("message", fieldError.getDefaultMessage() != null
                            ? fieldError.getDefaultMessage()
                            : "Valeur invalide");
                    return err;
                })
                .collect(Collectors.toList());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("statut", 400);
        body.put("erreur", "Données de la requête invalides");
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("details", errors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Gère les accès non autorisés (RBAC @PreAuthorize refus).
     * Retourne un 403 générique sans révéler la structure interne.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("statut", 403);
        body.put("erreur", "Accès refusé : droits insuffisants pour cette opération");
        body.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    /**
     * Gère toutes les autres exceptions non capturées.
     * N'expose PAS le message d'erreur interne pour éviter la divulgation d'informations.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        // Log complet avec stack trace — visible dans la console Spring Boot pour débogage
        log.error("ERREUR NON CAPTURÉE — Type: {} — Message: {}", ex.getClass().getName(), ex.getMessage(), ex);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("statut", 500);
        body.put("erreur", "Une erreur interne s'est produite. Veuillez réessayer.");
        body.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
