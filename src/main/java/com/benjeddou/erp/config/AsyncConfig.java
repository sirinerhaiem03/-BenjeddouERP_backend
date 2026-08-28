package com.benjeddou.erp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * AsyncConfig — Configuration du pool de threads @Async avec propagation du TenantContext.
 *
 * Ce bean remplace le SimpleAsyncTaskExecutor par défaut de Spring.
 *
 * Points clés :
 *  - TenantAwareTaskDecorator : propage le TenantContextHolder vers chaque thread async
 *  - Pool de threads calibré pour les tâches IO-bound (géolocalisation, email, audit)
 *  - AsyncUncaughtExceptionHandler : logge toutes les exceptions non capturées dans @Async
 *    (car dans un thread async les exceptions sont perdues silencieusement sans ce handler)
 *
 * Sans cette configuration :
 *  - SessionAlertService.enrichirEtAlerter() exécute connexionLogRepository.findById() dans
 *    un thread sans TenantContext → cherche dans MASTER → session introuvable → exception silencieuse
 *  - RefreshTokenService.creerRefreshToken() et SessionService.ouvrirSession() peuvent échouer
 *    pour la même raison si appelés via @Async indirectement
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Taille du pool : adapté aux tâches IO-bound (attente réseau, BDD, email)
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-tenant-");
        executor.setKeepAliveSeconds(60);

        // CLEF : propager le TenantContext vers chaque thread async
        executor.setTaskDecorator(new TenantAwareTaskDecorator());

        // Attendre la fin des tâches async avant l'arrêt du serveur (graceful shutdown)
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();
        log.info("[AsyncConfig] Pool de threads async initialisé avec TenantAwareTaskDecorator");
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        // Logger toutes les exceptions non capturées dans les méthodes @Async
        // Sans ce handler, les exceptions disparaissent silencieusement !
        return (ex, method, params) -> {
            log.error("[ASYNC] Exception non capturée dans {}.{}() : {} — {}",
                    method.getDeclaringClass().getSimpleName(),
                    method.getName(),
                    ex.getClass().getSimpleName(),
                    ex.getMessage(),
                    ex);
        };
    }
}
