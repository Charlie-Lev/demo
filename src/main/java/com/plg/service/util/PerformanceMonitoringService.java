// package com.plg.service.util;

// import com.plg.service.*;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.scheduling.annotation.Scheduled;
// import org.springframework.stereotype.Component;

// import lombok.Data;

// import java.time.LocalDateTime;
// import java.util.*;
// import java.util.concurrent.ConcurrentHashMap;
// import java.util.concurrent.atomic.AtomicLong;

// /**
//  * Servicio centralizado de monitoreo de performance
//  * Recolecta métricas de todos los componentes del sistema
//  */
// @Component
// public class PerformanceMonitoringService {
    
//     private static final Logger logger = LoggerFactory.getLogger(PerformanceMonitoringService.class);
    
//     @Autowired
//     private AEstrellaService aEstrellaService;
    
//     @Autowired
//     private OptimizadorSecuenciasService optimizadorSecuencias;
    
//     @Autowired
//     private CacheRutas cacheRutas;
    
//     @Autowired
//     private MemoryMonitoringService memoryMonitor;
    
//     @Autowired
//     private ResourceLeakDetector leakDetector;
    
//     @Autowired(required = false)
//     private BottleneckDetectionService bottleneckDetector;
    
//     @Autowired(required = false)
//     private AdaptiveConfigurationService adaptiveConfig;
    
//     @Autowired(required = false)
//     private PerformanceAlertingService alertingService;
    
//     // Métricas centralizadas
//     private final Map<String, MetricaComponente> metricas = new ConcurrentHashMap<>();
//     private final AtomicLong ciclosMonitoreo = new AtomicLong(0);
    
//     // Configuración
//     private ConfiguracionMonitoreo configuracion = new ConfiguracionMonitoreo();
    
//     /**
//      * Recolección de métricas cada 30 segundos
//      */
//     @Scheduled(fixedRate = 30000)
//     public void recolectarMetricas() {
//         try {
//             logger.debug("Iniciando recolección de métricas de performance");
//             long tiempoInicio = System.currentTimeMillis();
            
//             // Recolectar métricas de A*
//             recolectarMetricasAEstrella();
            
//             // Recolectar métricas de Optimizador
//             recolectarMetricasOptimizador();
            
//             // Recolectar métricas de Cache
//             recolectarMetricasCache();
            
//             // Recolectar métricas de Sistema
//             recolectarMetricasSistema();
            
//             // Detectar cuellos de botella
//             if (bottleneckDetector != null) {
//                 bottleneckDetector.analizarBottlenecks(metricas);
//             }
            
//             // Configuración adaptiva
//             if (adaptiveConfig != null) {
//                 adaptiveConfig.ajustarConfiguraciones(metricas);
//             }
            
//             // Verificar alertas
//             if (alertingService != null) {
//                 alertingService.verificarAlertas(metricas);
//             }
            
//             long tiempoTotal = System.currentTimeMillis() - tiempoInicio;
//             ciclosMonitoreo.incrementAndGet();
            
//             logger.debug("Recolección de métricas completada en {}ms", tiempoTotal);
            
//         } catch (Exception e) {
//             logger.error("Error en recolección de métricas: {}", e.getMessage(), e);
//         }
//     }
    
//     private void recolectarMetricasAEstrella() {
//         try {
//             AEstrellaService.EstadisticasAEstrella stats = aEstrellaService.obtenerEstadisticas();
            
//             MetricaComponente metrica = new MetricaComponente("AEstrella");
//             metrica.agregarValor("totalCalculos", (double) stats.getTotalCalculos());
//             metrica.agregarValor("tasaExito", stats.getTasaExito());
//             metrica.agregarValor("tiempoPromedioMs", stats.getTiempoPromedioMs());
//             metrica.agregarValor("tasaHitCache", stats.getTasaHitCache());
//             metrica.agregarValor("throughputCalculosPorSegundo", stats.getThroughputCalculosPorSegundo());
//             metrica.agregarValor("utilizacionPoolThreads", stats.getUtilizacionPromPoolThreads());
//             metrica.agregarDocumento("eficienciaParalelizacion", stats.getEficienciaParalelizacion());
//             metrica.setTimestamp(LocalDateTime.now());
            
//             metricas.put("AEstrella", metrica);
            
//         } catch (Exception e) {
//             logger.warn("Error recolectando métricas A*: {}", e.getMessage());
//         }
//     }
    
//     private void recolectarMetricasOptimizador() {
//         try {
//             OptimizadorSecuenciasService.EstadisticasOptimizador stats = 
//                 optimizadorSecuencias.obtenerEstadisticas();
            
//             MetricaComponente metrica = new MetricaComponente("OptimizadorSecuencias");
//             metrica.agregarValor("totalOptimizaciones", (double) stats.getTotalOptimizaciones());
//             metrica.agregarValor("tiempoPromedioMs", stats.getTiempoPromedioMs());
//             metrica.agregarValor("mejoraMedioPorcentaje", stats.getMejoraMedioaPorcentaje());
//             metrica.setTimestamp(LocalDateTime.now());
            
//             metricas.put("OptimizadorSecuencias", metrica);
            
//         } catch (Exception e) {
//             logger.warn("Error recolectando métricas Optimizador: {}", e.getMessage());
//         }
//     }
    
//     private void recolectarMetricasCache() {
//         try {
//             CacheRutas.EstadisticasCache stats = cacheRutas.obtenerEstadisticas();
            
//             MetricaComponente metrica = new MetricaComponente("Cache");
//             metrica.agregarValor("totalConsultas", (double) stats.getTotalConsultas());
//             metrica.agregarValor("tasaHit", stats.getTasaHit());
//             metrica.agregarValor("tamanoActual", (double) stats.getTamanoActual());
//             metrica.agregarValor("porcentajeUso", stats.getPorcentajeUso());
//             metrica.setTimestamp(LocalDateTime.now());
            
//             metricas.put("Cache", metrica);
            
//         } catch (Exception e) {
//             logger.warn("Error recolectando métricas Cache: {}", e.getMessage());
//         }
//     }
    
//     private void recolectarMetricasSistema() {
//         try {
//             // Memoria
//             MemoryMonitoringService.EstadisticasMemoria memStats = 
//                 memoryMonitor.obtenerEstadisticasMemoria();
            
//             MetricaComponente metricaMem = new MetricaComponente("Memoria");
//             metricaMem.agregarValor("heapUsadaMB", (double) memStats.getHeapUsada());
//             metricaMem.agregarValor("porcentajeUsoHeap", memStats.getPorcentajeUsoHeap());
//             metricaMem.agregarValor("nonHeapUsadaMB", (double) memStats.getNonHeapUsada());
//             metricaMem.setTimestamp(LocalDateTime.now());
            
//             metricas.put("Memoria", metricaMem);
            
//             // Leaks
//             ResourceLeakDetector.EstadisticasLeaks leakStats = 
//                 leakDetector.obtenerEstadisticas();
            
//             MetricaComponente metricaLeaks = new MetricaComponente("ResourceLeaks");
//             metricaLeaks.agregarValor("threadsActivos", (double) leakStats.getThreadsActivos());
//             metricaLeaks.agregarValor("threadsLeakDetectados", (double) leakStats.getThreadsLeakDetectados());
//             metricaLeaks.agregarValor("threadsAEstrella", (double) leakStats.getThreadsAEstrella());
//             metricaLeaks.setTimestamp(LocalDateTime.now());
            
//             metricas.put("ResourceLeaks", metricaLeaks);
            
//         } catch (Exception e) {
//             logger.warn("Error recolectando métricas Sistema: {}", e.getMessage());
//         }
//     }
    
//     /**
//      * Obtener resumen de performance actual
//      */
//     public ResumenPerformance obtenerResumenPerformance() {
//         ResumenPerformance resumen = new ResumenPerformance();
        
//         // Performance A*
//         MetricaComponente aEstrella = metricas.get("AEstrella");
//         if (aEstrella != null) {
//             resumen.setTiempoPromedioAEstrella(aEstrella.obtenerValor("tiempoPromedioMs"));
//             resumen.setThroughputAEstrella(aEstrella.obtenerValor("throughputCalculosPorSegundo"));
//             resumen.setTasaHitCache(aEstrella.obtenerValor("tasaHitCache"));
//         }
        
//         // Performance Optimizador
//         MetricaComponente optimizador = metricas.get("OptimizadorSecuencias");
//         if (optimizador != null) {
//             resumen.setTiempoPromedioOptimizacion(optimizador.obtenerValor("tiempoPromedioMs"));
//             resumen.setMejoraPromedio(optimizador.obtenerValor("mejoraMedioPorcentaje"));
//         }
        
//         // Performance Sistema
//         MetricaComponente memoria = metricas.get("Memoria");
//         if (memoria != null) {
//             resumen.setUsoMemoriaHeap(memoria.obtenerValor("porcentajeUsoHeap"));
//         }
        
//         // Calcular score global
//         resumen.setScorePerformanceGlobal(calcularScoreGlobal());
//         resumen.setTimestamp(LocalDateTime.now());
        
//         return resumen;
//     }
    
//     private double calcularScoreGlobal() {
//         double score = 100.0;
        
//         // Penalizar por tiempo alto de A*
//         MetricaComponente aEstrella = metricas.get("AEstrella");
//         if (aEstrella != null) {
//             double tiempoAEstrella = aEstrella.obtenerValor("tiempoPromedioMs");
//             if (tiempoAEstrella > 100) {
//                 score -= Math.min(30, (tiempoAEstrella - 100) / 10);
//             }
//         }
        
//         // Penalizar por uso alto de memoria
//         MetricaComponente memoria = metricas.get("Memoria");
//         if (memoria != null) {
//             double usoMemoria = memoria.obtenerValor("porcentajeUsoHeap");
//             if (usoMemoria > 80) {
//                 score -= Math.min(20, (usoMemoria - 80) * 2);
//             }
//         }
        
//         // Bonificar por alta tasa de cache hit
//         if (aEstrella != null) {
//             double tasaHit = aEstrella.obtenerValor("tasaHitCache");
//             if (tasaHit > 70) {
//                 score += Math.min(10, (tasaHit - 70) / 3);
//             }
//         }
        
//         return Math.max(0, Math.min(100, score));
//     }
    
//     public Map<String, MetricaComponente> obtenerTodasMetricas() {
//         return new HashMap<>(metricas);
//     }
    
//     @Data
//     public static class MetricaComponente {
//         private String nombreComponente;
//         private Map<String, Double> valores;
//         private LocalDateTime timestamp;
        
//         public MetricaComponente(String nombreComponente) {
//             this.nombreComponente = nombreComponente;
//             this.valores = new HashMap<>();
//         }
        
//         public void agregarValor(String clave, Double valor) {
//             if (valor != null && !valor.isNaN() && !valor.isInfinite()) {
//                 valores.put(clave, valor);
//             }
//         }
        
//         public void agregarDocumento(String clave, Double valor) {
//             agregarValor(clave, valor);
//         }
        
//         public Double obtenerValor(String clave) {
//             return valores.getOrDefault(clave, 0.0);
//         }
//     }
    
//     @Data
//     public static class ResumenPerformance {
//         private double tiempoPromedioAEstrella;
//         private double throughputAEstrella;
//         private double tasaHitCache;
//         private double tiempoPromedioOptimizacion;
//         private double mejoraPromedio;
//         private double usoMemoriaHeap;
//         private double scorePerformanceGlobal;
//         private LocalDateTime timestamp;
//     }
    
//     @Data
//     public static class ConfiguracionMonitoreo {
//         private boolean habilitado = true;
//         private long intervaloRecoleccionMs = 30000;
//         private boolean habilitarAlertas = true;
//         private boolean habilitarConfiguracionAdaptiva = true;
//     }
// }