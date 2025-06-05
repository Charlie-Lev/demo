// package com.plg.service.util;

// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
// import org.springframework.stereotype.Component;

// import lombok.Data;

// import java.time.LocalDateTime;
// import java.util.*;
// import java.util.stream.Collectors;

// /**
//  * Detector de cuellos de botella en performance
//  * Identifica componentes más lentos y patrones problemáticos
//  */
// @Component
// public class BottleneckDetectionService {
    
//     private static final Logger logger = LoggerFactory.getLogger(BottleneckDetectionService.class);
    
//     // Umbrales de detección
//     private static final double UMBRAL_TIEMPO_AESTREALLA_MS = 100.0;
//     private static final double UMBRAL_TIEMPO_OPTIMIZACION_MS = 500.0;
//     private static final double UMBRAL_USO_MEMORIA_PERCENT = 80.0;
//     private static final double UMBRAL_TASA_HIT_CACHE_MIN = 60.0;
//     private static final double UMBRAL_THROUGHPUT_MIN = 10.0;
    
//     // Historial para análisis de tendencias
//     private final List<AnalisisBottleneck> historialAnalisis = new ArrayList<>();
//     private final int MAX_HISTORIAL = 100;
    
//     /**
//      * Analizar bottlenecks basado en métricas actuales
//      */
//     public AnalisisBottleneck analizarBottlenecks(Map<String, PerformanceMonitoringService.MetricaComponente> metricas) {
//         logger.debug("Iniciando análisis de bottlenecks");
        
//         AnalisisBottleneck analisis = new AnalisisBottleneck();
//         analisis.setTimestamp(LocalDateTime.now());
        
//         // Analizar componente A*
//         analizarBottleneckAEstrella(metricas, analisis);
        
//         // Analizar optimizador
//         analizarBottleneckOptimizador(metricas, analisis);
        
//         // Analizar memoria
//         analizarBottleneckMemoria(metricas, analisis);
        
//         // Analizar cache
//         analizarBottleneckCache(metricas, analisis);
        
//         // Identificar bottleneck principal
//         identificarBottleneckPrincipal(analisis);
        
//         // Añadir al historial
//         agregarAlHistorial(analisis);
        
//         // Log de resultados
//         if (!analisis.getBottlenecksDetectados().isEmpty()) {
//             logger.warn("Bottlenecks detectados: {}", 
//                 analisis.getBottlenecksDetectados().stream()
//                     .map(BottleneckDetectado::getDescripcion)
//                     .collect(Collectors.joining(", ")));
//         }
        
//         return analisis;
//     }
    
//     private void analizarBottleneckAEstrella(Map<String, PerformanceMonitoringService.MetricaComponente> metricas, 
//                                            AnalisisBottleneck analisis) {
//         PerformanceMonitoringService.MetricaComponente aEstrella = metricas.get("AEstrella");
//         if (aEstrella == null) return;
        
//         double tiempoPromedio = aEstrella.obtenerValor("tiempoPromedioMs");
//         double throughput = aEstrella.obtenerValor("throughputCalculosPorSegundo");
//         double utilizacionPool = aEstrella.obtenerValor("utilizacionPoolThreads");
        
//         // Tiempo alto
//         if (tiempoPromedio > UMBRAL_TIEMPO_AESTREALLA_MS) {
//             BottleneckDetectado bottleneck = new BottleneckDetectado();
//             bottleneck.setComponente("AEstrella");
//             bottleneck.setTipoProblema("TIEMPO_ALTO");
//             bottleneck.setSeveridad(tiempoPromedio > 200 ? SeveridadBottleneck.CRITICO : SeveridadBottleneck.ALTO);
//             bottleneck.setDescripcion(String.format("Tiempo A* alto: %.1fms (umbral: %.1fms)", 
//                 tiempoPromedio, UMBRAL_TIEMPO_AESTREALLA_MS));
//             bottleneck.setValorActual(tiempoPromedio);
//             bottleneck.setValorEsperado(UMBRAL_TIEMPO_AESTREALLA_MS);
            
//             // Sugerencias
//             if (utilizacionPool > 80) {
//                 bottleneck.agregarSugerencia("Pool de threads saturado - incrementar número de threads");
//             }
//             if (throughput < UMBRAL_THROUGHPUT_MIN) {
//                 bottleneck.agregarSugerencia("Bajo throughput - optimizar algoritmo A* o usar cache más agresivo");
//             }
            
//             analisis.agregarBottleneck(bottleneck);
//         }
        
//         // Throughput bajo
//         if (throughput < UMBRAL_THROUGHPUT_MIN) {
//             BottleneckDetectado bottleneck = new BottleneckDetectado();
//             bottleneck.setComponente("AEstrella");
//             bottleneck.setTipoProblema("THROUGHPUT_BAJO");
//             bottleneck.setSeveridad(throughput < 5 ? SeveridadBottleneck.CRITICO : SeveridadBottleneck.MEDIO);
//             bottleneck.setDescripcion(String.format("Throughput A* bajo: %.1f calc/s (mín: %.1f)", 
//                 throughput, UMBRAL_THROUGHPUT_MIN));
//             bottleneck.agregarSugerencia("Incrementar paralelización o cache hit rate");
            
//             analisis.agregarBottleneck(bottleneck);
//         }
//     }
    
//     private void analizarBottleneckOptimizador(Map<String, PerformanceMonitoringService.MetricaComponente> metricas, 
//                                              AnalisisBottleneck analisis) {
//         PerformanceMonitoringService.MetricaComponente optimizador = metricas.get("OptimizadorSecuencias");
//         if (optimizador == null) return;
        
//         double tiempoPromedio = optimizador.obtenerValor("tiempoPromedioMs");
        
//         if (tiempoPromedio > UMBRAL_TIEMPO_OPTIMIZACION_MS) {
//             BottleneckDetectado bottleneck = new BottleneckDetectado();
//             bottleneck.setComponente("OptimizadorSecuencias");
//             bottleneck.setTipoProblema("TIEMPO_ALTO");
//             bottleneck.setSeveridad(tiempoPromedio > 1000 ? SeveridadBottleneck.CRITICO : SeveridadBottleneck.ALTO);
//             bottleneck.setDescripcion(String.format("Optimización lenta: %.1fms (umbral: %.1fms)", 
//                 tiempoPromedio, UMBRAL_TIEMPO_OPTIMIZACION_MS));
//             bottleneck.agregarSugerencia("Reducir número de iteraciones Local Search o usar algoritmo más simple");
            
//             analisis.agregarBottleneck(bottleneck);
//         }
//     }
    
//     private void analizarBottleneckMemoria(Map<String, PerformanceMonitoringService.MetricaComponente> metricas, 
//                                          AnalisisBottleneck analisis) {
//         PerformanceMonitoringService.MetricaComponente memoria = metricas.get("Memoria");
//         if (memoria == null) return;
        
//         double usoHeap = memoria.obtenerValor("porcentajeUsoHeap");
        
//         if (usoHeap > UMBRAL_USO_MEMORIA_PERCENT) {
//             BottleneckDetectado bottleneck = new BottleneckDetectado();
//             bottleneck.setComponente("Memoria");
//             bottleneck.setTipoProblema("USO_ALTO");
//             bottleneck.setSeveridad(usoHeap > 90 ? SeveridadBottleneck.CRITICO : SeveridadBottleneck.ALTO);
//             bottleneck.setDescripcion(String.format("Uso memoria alto: %.1f%% (umbral: %.1f%%)", 
//                 usoHeap, UMBRAL_USO_MEMORIA_PERCENT));
//             bottleneck.agregarSugerencia("Incrementar heap size o optimizar uso de memoria");
//             bottleneck.agregarSugerencia("Verificar object pooling y liberación de recursos");
            
//             analisis.agregarBottleneck(bottleneck);
//         }
//     }
    
//     private void analizarBottleneckCache(Map<String, PerformanceMonitoringService.MetricaComponente> metricas, 
//                                        AnalisisBottleneck analisis) {
//         PerformanceMonitoringService.MetricaComponente aEstrella = metricas.get("AEstrella");
//         if (aEstrella == null) return;
        
//         double tasaHit = aEstrella.obtenerValor("tasaHitCache");
        
//         if (tasaHit < UMBRAL_TASA_HIT_CACHE_MIN) {
//             BottleneckDetectado bottleneck = new BottleneckDetectado();
//             bottleneck.setComponente("Cache");
//             bottleneck.setTipoProblema("TASA_HIT_BAJA");
//             bottleneck.setSeveridad(tasaHit < 30 ? SeveridadBottleneck.ALTO : SeveridadBottleneck.MEDIO);
//             bottleneck.setDescripcion(String.format("Tasa hit cache baja: %.1f%% (mín: %.1f%%)", 
//                 tasaHit, UMBRAL_TASA_HIT_CACHE_MIN));
//             bottleneck.agregarSugerencia("Incrementar tamaño de cache o mejorar algoritmo de pre-carga");
            
//             analisis.agregarBottleneck(bottleneck);
//         }
//     }
    
//     private void identificarBottleneckPrincipal(AnalisisBottleneck analisis) {
//         if (analisis.getBottlenecksDetectados().isEmpty()) {
//             return;
//         }
        
//         // Identificar bottleneck más crítico
//         BottleneckDetectado principal = analisis.getBottlenecksDetectados().stream()
//             .max(Comparator.comparing(b -> b.getSeveridad().ordinal()))
//             .orElse(null);
        
//         if (principal != null) {
//             analisis.setBottleneckPrincipal(principal);
//         }
//     }
    
//     private void agregarAlHistorial(AnalisisBottleneck analisis) {
//         historialAnalisis.add(analisis);
        
//         // Mantener tamaño máximo
//         while (historialAnalisis.size() > MAX_HISTORIAL) {
//             historialAnalisis.remove(0);
//         }
//     }
    
//     /**
//      * Obtener tendencias de bottlenecks
//      */
//     public TendenciasBottleneck analizarTendencias() {
//         if (historialAnalisis.size() < 5) {
//             return new TendenciasBottleneck(); // Datos insuficientes
//         }
        
//         TendenciasBottleneck tendencias = new TendenciasBottleneck();
        
//         // Análisis de los últimos 10 análisis
//         List<AnalisisBottleneck> recientes = historialAnalisis.stream()
//             .skip(Math.max(0, historialAnalisis.size() - 10))
//             .collect(Collectors.toList());
        
//         // Contar frecuencia de cada tipo de problema
//         Map<String, Long> frecuenciaProblemas = recientes.stream()
//             .flatMap(a -> a.getBottlenecksDetectados().stream())
//             .collect(Collectors.groupingBy(
//                 b -> b.getComponente() + ":" + b.getTipoProblema(),
//                 Collectors.counting()
//             ));
        
//         tendencias.setProblemasRecurrentes(frecuenciaProblemas);
        
//         // Tendencia general (empeorando/mejorando)
//         if (recientes.size() >= 5) {
//             int problemasInicio = recientes.subList(0, 2).stream()
//                 .mapToInt(a -> a.getBottlenecksDetectados().size())
//                 .sum();
            
//             int problemasFinal = recientes.subList(recientes.size() - 2, recientes.size()).stream()
//                 .mapToInt(a -> a.getBottlenecksDetectados().size())
//                 .sum();
            
//             if (problemasFinal > problemasInicio) {
//                 tendencias.setTendenciaGeneral("EMPEORANDO");
//             } else if (problemasFinal < problemasInicio) {
//                 tendencias.setTendenciaGeneral("MEJORANDO");
//             } else {
//                 tendencias.setTendenciaGeneral("ESTABLE");
//             }
//         }
        
//         return tendencias;
//     }
    
//     // Clases auxiliares
    
//     @Data
//     public static class AnalisisBottleneck {
//         private LocalDateTime timestamp;
//         private List<BottleneckDetectado> bottlenecksDetectados = new ArrayList<>();
//         private BottleneckDetectado bottleneckPrincipal;
        
//         public void agregarBottleneck(BottleneckDetectado bottleneck) {
//             bottlenecksDetectados.add(bottleneck);
//         }
//     }
    
//     @Data
//     public static class BottleneckDetectado {
//         private String componente;
//         private String tipoProblema;
//         private SeveridadBottleneck severidad;
//         private String descripcion;
//         private double valorActual;
//         private double valorEsperado;
//         private List<String> sugerencias = new ArrayList<>();
        
//         public void agregarSugerencia(String sugerencia) {
//             sugerencias.add(sugerencia);
//         }
//     }
    
//     public enum SeveridadBottleneck {
//         BAJO, MEDIO, ALTO, CRITICO
//     }
    
//     @Data
//     public static class TendenciasBottleneck {
//         private Map<String, Long> problemasRecurrentes = new HashMap<>();
//         private String tendenciaGeneral = "DESCONOCIDO";
//     }
    
// }