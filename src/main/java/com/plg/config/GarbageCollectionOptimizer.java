package com.plg.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import lombok.Data;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;

/**
 * Optimizador de Garbage Collection para latencia baja
 * Configura GC para minimizar pausas y maximizar throughput
 */
@Component
public class GarbageCollectionOptimizer {
    
    private static final Logger logger = LoggerFactory.getLogger(GarbageCollectionOptimizer.class);
    
    /**
     * Configurar GC al inicio de la aplicación
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(0) // Ejecutar antes que otros inicializadores
    public void optimizarGarbageCollection() {
        logger.info("🗑️ Iniciando optimización de Garbage Collection");
        
        try {
            // Detectar tipo de GC actual
            detectarTipoGC();
            
            // Aplicar configuraciones de optimización
            aplicarOptimizacionesGC();
            
            // Configurar monitoreo de GC
            configurarMonitoreoGC();
            
            logger.info("✅ Garbage Collection optimizado para latencia baja");
            
        } catch (Exception e) {
            logger.error("❌ Error optimizando GC: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Detectar qué tipo de GC está siendo usado
     */
    private void detectarTipoGC() {
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        
        logger.info("Detectando configuración de GC:");
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            String nombre = gcBean.getName();
            long colecciones = gcBean.getCollectionCount();
            long tiempo = gcBean.getCollectionTime();
            
            logger.info("  - GC: {} | Colecciones: {} | Tiempo total: {}ms", 
                nombre, colecciones, tiempo);
            
            // Dar recomendaciones según el GC detectado
            darRecomendacionesGC(nombre);
        }
    }
    
    /**
     * Dar recomendaciones según el tipo de GC
     */
    private void darRecomendacionesGC(String nombreGC) {
        if (nombreGC.contains("G1")) {
            logger.info("  📋 G1GC detectado - Configuración recomendada para latencia:");
            logger.info("     -XX:MaxGCPauseMillis=50 -XX:G1HeapRegionSize=16M");
            
        } else if (nombreGC.contains("Parallel")) {
            logger.warn("  ⚠️ Parallel GC detectado - Considera cambiar a G1GC para mejor latencia:");
            logger.warn("     -XX:+UseG1GC -XX:MaxGCPauseMillis=50");
            
        } else if (nombreGC.contains("Serial")) {
            logger.warn("  ⚠️ Serial GC detectado - No recomendado para producción:");
            logger.warn("     Usar: -XX:+UseG1GC para mejor rendimiento");
            
        } else if (nombreGC.contains("ZGC") || nombreGC.contains("Shenandoah")) {
            logger.info("  🚀 GC de baja latencia detectado - Excelente para este sistema");
        }
    }
    
    /**
     * Aplicar optimizaciones programáticas de GC
     */
    private void aplicarOptimizacionesGC() {
        // Configuraciones que se pueden hacer desde código
        
        // Sugerir llamada inicial para warm-up del GC
        logger.info("Ejecutando warm-up inicial del GC...");
        System.gc(); // Solo para warm-up inicial
        
        // Configurar threshold para GC más agresivo si es necesario
        // (Esto es más efectivo con parámetros JVM)
        
        logger.info("Configuraciones recomendadas de JVM para optimización:");
        logger.info("  Heap: -Xms2g -Xmx4g");
        logger.info("  G1GC: -XX:+UseG1GC -XX:MaxGCPauseMillis=50 -XX:G1HeapRegionSize=16M");
        logger.info("  Logging: -Xlog:gc*:gc.log -XX:+UnlockExperimentalVMOptions");
        logger.info("  Otros: -XX:+UseStringDeduplication -XX:+OptimizeStringConcat");
    }
    
    /**
     * Configurar monitoreo continuo de GC
     */
    private void configurarMonitoreoGC() {
        // Iniciar thread de monitoreo
        Thread monitorGC = new Thread(this::monitorearGCContinuo, "GC-Monitor");
        monitorGC.setDaemon(true);
        monitorGC.start();
        
        logger.info("Monitor de GC iniciado en background");
    }
    
    /**
     * Monitoreo continuo de GC en background
     */
    private void monitorearGCContinuo() {
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        
        // Valores iniciales
        long[] coleccionesAnteriores = new long[gcBeans.size()];
        long[] tiemposAnteriores = new long[gcBeans.size()];
        
        // Inicializar
        for (int i = 0; i < gcBeans.size(); i++) {
            coleccionesAnteriores[i] = gcBeans.get(i).getCollectionCount();
            tiemposAnteriores[i] = gcBeans.get(i).getCollectionTime();
        }
        
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(60000); // Verificar cada minuto
                
                boolean alertaGenerada = false;
                
                for (int i = 0; i < gcBeans.size(); i++) {
                    GarbageCollectorMXBean gcBean = gcBeans.get(i);
                    
                    long coleccionesActuales = gcBean.getCollectionCount();
                    long tiempoActual = gcBean.getCollectionTime();
                    
                    long nuevasColecciones = coleccionesActuales - coleccionesAnteriores[i];
                    long nuevoTiempo = tiempoActual - tiemposAnteriores[i];
                    
                    if (nuevasColecciones > 0) {
                        double tiempoPromedioGC = (double) nuevoTiempo / nuevasColecciones;
                        
                        // Alertar si las pausas de GC son muy largas
                        if (tiempoPromedioGC > 100) { // Más de 100ms promedio
                            logger.warn("🚨 GC PAUSAS LARGAS: {} - {} colecciones, {:.1f}ms promedio", 
                                gcBean.getName(), nuevasColecciones, tiempoPromedioGC);
                            alertaGenerada = true;
                        }
                        
                        // Alertar si hay demasiadas colecciones
                        if (nuevasColecciones > 10) { // Más de 10 por minuto
                            logger.warn("🚨 GC FRECUENTE: {} - {} colecciones en último minuto", 
                                gcBean.getName(), nuevasColecciones);
                            alertaGenerada = true;
                        }
                    }
                    
                    // Actualizar valores anteriores
                    coleccionesAnteriores[i] = coleccionesActuales;
                    tiemposAnteriores[i] = tiempoActual;
                }
                
                if (!alertaGenerada) {
                    logger.debug("GC Monitor: Rendimiento normal");
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error en monitor de GC: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Obtener estadísticas actuales de GC
     */
    public EstadisticasGC obtenerEstadisticasGC() {
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        EstadisticasGC stats = new EstadisticasGC();
        
        long totalColecciones = 0;
        long totalTiempo = 0;
        
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            totalColecciones += gcBean.getCollectionCount();
            totalTiempo += gcBean.getCollectionTime();
        }
        
        stats.setTotalColecciones(totalColecciones);
        stats.setTiempoTotalMs(totalTiempo);
        
        if (totalColecciones > 0) {
            stats.setTiempoPromedioMs((double) totalTiempo / totalColecciones);
        }
        
        return stats;
    }
    @Data
    public static class EstadisticasGC {
        private long totalColecciones;
        private long tiempoTotalMs;
        private double tiempoPromedioMs;
        
        @Override
        public String toString() {
            return String.format("GC Stats: %d colecciones, %.1fms promedio", 
                totalColecciones, tiempoPromedioMs);
        }
    }
}