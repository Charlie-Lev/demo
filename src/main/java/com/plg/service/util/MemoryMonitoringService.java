package com.plg.service.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import lombok.Data;

import org.springframework.scheduling.annotation.Scheduled;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Servicio de monitoreo de memoria y alertas de uso
 * Previene OutOfMemory y optimiza rendimiento
 */
@Component
public class MemoryMonitoringService {
    
    private static final Logger logger = LoggerFactory.getLogger(MemoryMonitoringService.class);
    
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    
    // Umbrales de alerta (porcentajes)
    private static final double UMBRAL_ADVERTENCIA = 75.0; // 75%
    private static final double UMBRAL_CRITICO = 85.0;     // 85%
    private static final double UMBRAL_EMERGENCIA = 95.0;  // 95%
    
    // Contadores de alertas
    private final AtomicLong alertasAdvertencia = new AtomicLong(0);
    private final AtomicLong alertasCriticas = new AtomicLong(0);
    private final AtomicLong alertasEmergencia = new AtomicLong(0);
    
    private volatile boolean memoriaEnEstadoCritico = false;
    
    /**
     * Monitoreo cada 30 segundos
     */
    @Scheduled(fixedRate = 30000)
    public void monitorearMemoria() {
        try {
            EstadisticasMemoria stats = obtenerEstadisticasMemoria();
            
            // Verificar umbrales y generar alertas
            verificarUmbrales(stats);
            
            // Log periódico de estado
            if (stats.getPorcentajeUsoHeap() > UMBRAL_ADVERTENCIA) {
                logger.warn("Uso de memoria elevado: {:.1f}% heap, {:.1f}% non-heap", 
                    stats.getPorcentajeUsoHeap(), stats.getPorcentajeUsoNonHeap());
            }
            
        } catch (Exception e) {
            logger.error("Error en monitoreo de memoria: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Obtener estadísticas actuales de memoria
     */
    public EstadisticasMemoria obtenerEstadisticasMemoria() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
        
        EstadisticasMemoria stats = new EstadisticasMemoria();
        
        // Memoria heap
        stats.setHeapUsada(heapUsage.getUsed() / (1024 * 1024)); // MB
        stats.setHeapMaxima(heapUsage.getMax() / (1024 * 1024)); // MB
        stats.setHeapComprometida(heapUsage.getCommitted() / (1024 * 1024)); // MB
        
        if (heapUsage.getMax() > 0) {  // Evitar división por cero
            stats.setPorcentajeUsoHeap(
                (double) heapUsage.getUsed() / heapUsage.getMax() * 100.0);
        }
        
        // Memoria non-heap
        stats.setNonHeapUsada(nonHeapUsage.getUsed() / (1024 * 1024)); // MB
        stats.setNonHeapMaxima(nonHeapUsage.getMax() / (1024 * 1024)); // MB
        
        if (nonHeapUsage.getMax() > 0) {
            stats.setPorcentajeUsoNonHeap(
                (double) nonHeapUsage.getUsed() / nonHeapUsage.getMax() * 100.0);
        }
        
        return stats;
    }
    
    /**
     * Verificar umbrales y generar alertas
     */
    private void verificarUmbrales(EstadisticasMemoria stats) {
        double porcentajeHeap = stats.getPorcentajeUsoHeap();
        
        if (porcentajeHeap >= UMBRAL_EMERGENCIA) {
            alertasEmergencia.incrementAndGet();
            memoriaEnEstadoCritico = true;
            
            logger.error("🚨 EMERGENCIA MEMORIA: {:.1f}% heap usado - ACCIÓN INMEDIATA REQUERIDA", 
                porcentajeHeap);
            
            // Forzar garbage collection en emergencia
            System.gc();
            
        } else if (porcentajeHeap >= UMBRAL_CRITICO) {
            alertasCriticas.incrementAndGet();
            memoriaEnEstadoCritico = true;
            
            logger.error("🔴 MEMORIA CRÍTICA: {:.1f}% heap usado - Optimizar uso de memoria", 
                porcentajeHeap);
            
        } else if (porcentajeHeap >= UMBRAL_ADVERTENCIA) {
            alertasAdvertencia.incrementAndGet();
            memoriaEnEstadoCritico = false;
            
            logger.warn("⚠️ ADVERTENCIA MEMORIA: {:.1f}% heap usado - Supervisar uso", 
                porcentajeHeap);
            
        } else {
            memoriaEnEstadoCritico = false;
        }
    }
    
    /**
     * Verificar si sistema está en estado crítico de memoria
     */
    public boolean isMemoriaEnEstadoCritico() {
        return memoriaEnEstadoCritico;
    }
    
    /**
     * Obtener reporte completo de memoria
     */
    public ReporteMemoria generarReporte() {
        EstadisticasMemoria stats = obtenerEstadisticasMemoria();
        ReporteMemoria reporte = new ReporteMemoria();
        
        reporte.setEstadisticas(stats);
        reporte.setMemoriaEnEstadoCritico(memoriaEnEstadoCritico);
        reporte.setAlertasAdvertencia(alertasAdvertencia.get());
        reporte.setAlertasCriticas(alertasCriticas.get());
        reporte.setAlertasEmergencia(alertasEmergencia.get());
        
        return reporte;
    }
    @Data
    public static class EstadisticasMemoria {
        private long heapUsada;
        private long heapMaxima;
        private long heapComprometida;
        private double porcentajeUsoHeap;
        
        private long nonHeapUsada;
        private long nonHeapMaxima;
        private double porcentajeUsoNonHeap;
    
    }
    @Data
    public static class ReporteMemoria {
        private EstadisticasMemoria estadisticas;
        private boolean memoriaEnEstadoCritico;
        private long alertasAdvertencia;
        private long alertasCriticas;
        private long alertasEmergencia;
    }
}