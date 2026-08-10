package com.benjeddou.erp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ErpApplication — Point d'entrée principal de l'application BENJEDDOU ERP SaaS.
 *
 * Annotations actives :
 *  @EnableAsync      : Active les méthodes asynchrones (@Async) — utilisé par AuditService
 *  @EnableScheduling : Active les tâches planifiées (@Scheduled) — utilisé par BackupService
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class ErpApplication {

    public static void main(String[] args) {
        SpringApplication.run(ErpApplication.class, args);
    }
}
