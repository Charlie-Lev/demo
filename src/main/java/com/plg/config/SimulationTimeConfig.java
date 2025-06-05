package com.plg.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Configuración para el sistema de tiempo de simulación
 * Habilita scheduling, WebSocket y configuración de threads
 */
@Configuration
@EnableScheduling
@EnableWebSocketMessageBroker
public class SimulationTimeConfig {
    
    /**
     * Configurar TaskScheduler específico para simulación
     * Pool separado para no interferir con otras tareas programadas
     */
    @Bean(name = "simulationTaskScheduler")
    public TaskScheduler simulationTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        
        // Configuración del pool de threads
        scheduler.setPoolSize(4); // 4 threads para tareas de simulación
        scheduler.setThreadNamePrefix("SimulationScheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(10);
        
        // Configuración de manejo de errores
        scheduler.setRejectedExecutionHandler((r, executor) -> {
            System.err.println("⚠️ Tarea de simulación rechazada: " + r.toString());
        });
        
        scheduler.initialize();
        return scheduler;
    }
    
    /**
     * Executor para operaciones asíncronas de simulación
     */
    @Bean(name = "simulationExecutor")
    public Executor simulationExecutor() {
        return Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "SimulationAsync-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * Configuración específica para broadcasting de WebSocket
     */
    @Bean(name = "webSocketBroadcastExecutor")
    public Executor webSocketBroadcastExecutor() {
        ThreadPoolTaskScheduler executor = new ThreadPoolTaskScheduler();
        executor.setPoolSize(2);
        executor.setThreadNamePrefix("WSBroadcast-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        executor.initialize();
        return executor;
    }
}