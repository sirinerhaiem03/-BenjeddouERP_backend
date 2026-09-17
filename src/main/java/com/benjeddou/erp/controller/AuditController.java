package com.benjeddou.erp.controller;

import com.benjeddou.erp.model.AuditLog;
import com.benjeddou.erp.model.AuditLog.ActionAudit;
import com.benjeddou.erp.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AuditController — Consultation des logs d'audit (SuperAdmin / Admin)
 * Base URL : /api/audit
 */
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuditController {

    private final AuditLogRepository auditLogRepository;

    // ═════════════════════════════════════════════════════════════════════
    // GET /api/audit/logs — Liste paginée de tous les logs
    // ═════════════════════════════════════════════════════════════════════
    @GetMapping("/logs")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> getLogs(
            @RequestParam(defaultValue = "0")    int page,
            @RequestParam(defaultValue = "20")   int size,
            @RequestParam(required = false)      String q,
            @RequestParam(required = false)      String action) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<AuditLog> result;

        if (q != null && !q.isBlank()) {
            result = auditLogRepository.rechercher(q, pageable);
        } else if (action != null && !action.isBlank()) {
            try {
                ActionAudit a = ActionAudit.valueOf(action.toUpperCase());
                result = auditLogRepository.findByActionOrderByCreatedAtDesc(a, pageable);
            } catch (IllegalArgumentException e) {
                result = auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
            }
        } else {
            result = auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("content",       result.getContent());
        response.put("totalElements", result.getTotalElements());
        response.put("totalPages",    result.getTotalPages());
        response.put("currentPage",   result.getNumber());
        return ResponseEntity.ok(response);
    }

    // ═════════════════════════════════════════════════════════════════════
    // GET /api/audit/critiques — 20 derniers logs critiques (dashboard)
    // ═════════════════════════════════════════════════════════════════════
    @GetMapping("/critiques")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> getLogsCritiques() {
        List<AuditLog> logs = auditLogRepository.findLogsCritiques(
            PageRequest.of(0, 20)
        );
        return ResponseEntity.ok(logs);
    }

    // ═════════════════════════════════════════════════════════════════════
    // GET /api/audit/stats — Statistiques par action (dernières 24h)
    // ═════════════════════════════════════════════════════════════════════
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> getStats(@RequestParam(defaultValue = "24") int heures) {
        LocalDateTime depuis = LocalDateTime.now().minusHours(heures);
        List<Object[]> raw = auditLogRepository.statsParAction(depuis);

        List<Map<String, Object>> stats = raw.stream().map(row -> {
            Map<String, Object> m = new HashMap<>();
            m.put("action", row[0].toString());
            m.put("count",  row[1]);
            return m;
        }).collect(Collectors.toList());

        // Totaux rapides
        long totalLogs = auditLogRepository.count();
        long logsCritiques = stats.stream()
            .filter(s -> List.of("LOGIN_ECHEC", "RATE_LIMIT_BLOQUE", "COMPTE_BLOQUE")
                .contains(s.get("action")))
            .mapToLong(s -> (long)(Long)s.get("count"))
            .sum();

        Map<String, Object> resp = new HashMap<>();
        resp.put("statsParAction", stats);
        resp.put("totalLogs",      totalLogs);
        resp.put("logsCritiques",  logsCritiques);
        resp.put("periode",        heures + "h");
        return ResponseEntity.ok(resp);
    }

    // ═════════════════════════════════════════════════════════════════════
    // GET /api/audit/utilisateur/{id} — Logs d'un utilisateur
    // ═════════════════════════════════════════════════════════════════════
    @GetMapping("/utilisateur/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> getLogsUtilisateur(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "15") int size) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<AuditLog> result = auditLogRepository
            .findByUtilisateurIdOrderByCreatedAtDesc(id, pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("content",       result.getContent());
        response.put("totalElements", result.getTotalElements());
        response.put("totalPages",    result.getTotalPages());
        response.put("currentPage",   result.getNumber());
        return ResponseEntity.ok(response);
    }

    // ═════════════════════════════════════════════════════════════════════
    // GET /api/audit/actions — Liste des types d'actions (pour filtre UI)
    // ═════════════════════════════════════════════════════════════════════
    @GetMapping("/actions")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> getActions() {
        return ResponseEntity.ok(ActionAudit.values());
    }
}
