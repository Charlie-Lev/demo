package com.plg.service.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.Data;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Detector de leaks de recursos (threads, memoria, etc.)
 * Previene degradación del rendimiento por recursos no liberados
 */
@Component
public class ResourceLeakDetector {
    
    private static final Logger logger = LoggerFactory.getLogger(ResourceLeakDetector.class);
    
    private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    
    // Tracking de threads
    private final ConcurrentHashMap<Long, ThreadInfo> threadsTrackeados = new ConcurrentHashMap<>();
    private final AtomicLong threadsLeakDetectados = new AtomicLong(0);
    
    // Umbrales de detección
    private static final int MAX_THREADS_ESPERADOS = 50;
    private static final long TIEMPO_VIDA_THREAD_SOSPECHOSO_MS = 300000; // 5 minutos
    
    /**
     * Verificación cada 2 minutos
     */
    @Scheduled(fixedRate = 120000)
    public void detectarLeaks() {
        try {
            detectarThreadLeaks();
            limpiarThreadsObsoletos();
            
        } catch (Exception e) {
            logger.error("Error en detección de leaks: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Detectar threads que pueden ser leaks
     */
    private void detectarThreadLeaks() {
        long[] threadIds = threadBean.getAllThreadIds();
        int threadCount = threadIds.length;
        
        // Verificar threads nuevos
        for (long threadId : threadIds) {
            if (!threadsTrackeados.containsKey(threadId)) {
                ThreadInfo info = new ThreadInfo(threadId, System.currentTimeMillis());
                threadsTrackeados.put(threadId, info);
            }
        }
        
        // Alertar si hay demasiados threads
        if (threadCount > MAX_THREADS_ESPERADOS) {
            logger.warn("🔍 POSIBLE THREAD LEAK: {} threads activos (máximo esperado: {})", 
                threadCount, MAX_THREADS_ESPERADOS);
            
            // Analizar threads sospechosos
            analizarThreadsSospechosos();
        }
        
        logger.debug("Detector Leaks: {} threads activos, {} trackeados", 
            threadCount, threadsTrackeados.size());
    }
    
    /**
     * Analizar threads de larga duración (posibles leaks)
     */
    private void analizarThreadsSospechosos() {
        long tiempoActual = System.currentTimeMillis();
        
        threadsTrackeados.entrySet().removeIf(entry -> {
            Long threadId = entry.getKey();
            ThreadInfo info = entry.getValue();
            
            // Verificar si el thread sigue vivo
            java.lang.management.ThreadInfo threadInfo = threadBean.getThreadInfo(threadId);
            if (threadInfo == null) {
                // Thread ya murió, remover del tracking
                return true;
            }
            
            // Verificar si es sospechoso por tiempo de vida
            long tiempoVida = tiempoActual - info.getTiempoCreacion();
            if (tiempoVida > TIEMPO_VIDA_THREAD_SOSPECHOSO_MS) {
                
                logger.warn("🚨 THREAD SOSPECHOSO: ID={}, Nombre='{}', Tiempo vida={}ms, Estado={}", 
                    threadId, threadInfo.getThreadName(), tiempoVida, threadInfo.getThreadState());
                
                threadsLeakDetectados.incrementAndGet();
                
                // Verificar si es de nuestros pools
                if (threadInfo.getThreadName().contains("AEstrella-Worker") || 
                    threadInfo.getThreadName().contains("PlanificadorRuta")) {
                    
                    logger.error("🔴 LEAK CONFIRMADO: Thread de nuestro pool no liberado: '{}'", 
                        threadInfo.getThreadName());
                }
            }
            
            return false; // Mantener en tracking
        });
    }
    
    /**
     * Limpiar threads obsoletos del tracking
     */
    private void limpiarThreadsObsoletos() {
        long[] threadIdsActivos = threadBean.getAllThreadIds();
        java.util.Set<Long> idsActivosSet = new java.util.HashSet<>();
        
        for (long id : threadIdsActivos) {
            idsActivosSet.add(id);
        }
        
        // Remover threads que ya no existen
        int removidos = 0;
        var iterator = threadsTrackeados.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!idsActivosSet.contains(entry.getKey())) {
                iterator.remove();
                removidos++;
            }
        }
        
        if (removidos > 0) {
            logger.debug("Limpieza: {} threads obsoletos removidos del tracking", removidos);
        }
    }
    
    /**
     * Obtener estadísticas de leaks
     */
    public EstadisticasLeaks obtenerEstadisticas() {
        EstadisticasLeaks stats = new EstadisticasLeaks();
        
        long[] threadIds = threadBean.getAllThreadIds();
        stats.setThreadsActivos(threadIds.length);
        stats.setThreadsTrackeados(threadsTrackeados.size());
        stats.setThreadsLeakDetectados(threadsLeakDetectados.get());
        
        // Contar threads por tipo
        long threadsAEstrella = java.util.Arrays.stream(threadIds)
            .mapToObj(threadBean::getThreadInfo)
            .filter(java.util.Objects::nonNull)
            .filter(info -> info.getThreadName().contains("AEstrella"))
            .count();
        
        long threadsPlanificador = java.util.Arrays.stream(threadIds)
            .mapToObj(threadBean::getThreadInfo)
            .filter(java.util.Objects::nonNull)
            .filter(info -> info.getThreadName().contains("PlanificadorRuta"))
            .count();
        
        stats.setThreadsAEstrella(threadsAEstrella);
        stats.setThreadsPlanificador(threadsPlanificador);
        
        return stats;
    }
    
    /**
     * Información de thread trackeado
     */
    private static class ThreadInfo {
        private final long threadId;
        private final long tiempoCreacion;
        
        public ThreadInfo(long threadId, long tiempoCreacion) {
            this.threadId = threadId;
            this.tiempoCreacion = tiempoCreacion;
        }
        
        public long getThreadId() { return threadId; }
        public long getTiempoCreacion() { return tiempoCreacion; }
    }
    @Data
    public static class EstadisticasLeaks {
        private int threadsActivos;
        private int threadsTrackeados;
        private long threadsLeakDetectados;
        private long threadsAEstrella;
        private long threadsPlanificador;
        
        @Override
        public String toString() {
            return String.format("Leak Stats: %d threads activos, %d trackeados, %d leaks detectados", 
                threadsActivos, threadsTrackeados, threadsLeakDetectados);
        }
    }
}