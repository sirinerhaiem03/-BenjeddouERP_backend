package com.benjeddou.erp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ErpApplication — Point d'entrée principal de l'application BENJEDDOU ERP SaaS.
 *
 * Annotations actives :
 *  @EnableScheduling : Active les tâches planifiées (@Scheduled) — utilisé par BackupService
 *  @Async            : Configuré via AsyncConfig (TenantAwareTaskDecorator pour propagation du TenantContext)
 */
@SpringBootApplication
@EnableScheduling
public class ErpApplication {

    public static void main(String[] args) {
        SpringApplication.run(ErpApplication.class, args);
    }
}
