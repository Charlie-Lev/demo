// package com.plg.service.util;

// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
// import org.springframework.stereotype.Component;

// import lombok.Data;

// import java.time.LocalDateTime;
// import java.util.*;
// import java.util.concurrent.ConcurrentHashMap;

// /**
//  * Sistema de alertas de performance
//  * Notifica cuando la performance degrada significativamente
//  */
// @Component
// public class PerformanceAlertingService {
    
//     private static final Logger logger = LoggerFactory.getLogger(PerformanceAlertingService.class);
    
//     // Configuración de alertas
//     private ConfiguracionAlertas configuracion = new ConfiguracionAlertas();
    
//     // Tracking de alertas activas
//     private final Map<String, AlertaActiva> alertasActivas = new ConcurrentHashMap<>();
    
//     // Historial de alertas
//     private final List<AlertaPerformance> historialAlertas = new ArrayList<>();
//     private final int MAX_HISTORIAL = 1000;
    
//     /**
//      * Verificar y generar alertas basado en métricas
//      */
//     public ResultadoAlertas verificarAlertas(Map<String, PerformanceMonitoringService.MetricaComponente> metricas) {
//         if (!configuracion.isHabilitado()) {
//             return new ResultadoAlertas();
//         }
        
//         ResultadoAlertas resultado = new ResultadoAlertas();
//         resultado.setTimestamp(LocalDateTime.now());
        
//         // Verificar alertas A*
//         verificarAlertasAEstrella(metricas, resultado);
        
//         // Verificar alertas de memoria
//         verificarAlertasMemoria(metricas, resultado);
        
//         // Verificar alertas de cache
//         verificarAlertasCache(metricas, resultado);
        
//         // Verificar alertas de sistema
//         verificarAlertasSistema(metricas, resultado);
        
//         // Procesar alertas nuevas
//         procesarAlertasNuevas(resultado);
        
//         // Resolver alertas obsoletas
//         resolverAlertasObsoletas(metricas);
        
//         return resultado;
//     }
    
//     private void verificarAlertasAEstrella(Map<String, PerformanceMonitoringService.MetricaComponente> metricas,
//                                          ResultadoAlertas resultado) {
//         PerformanceMonitoringService.MetricaComponente aEstrella = metricas.get("AEstrella");
//         if (aEstrella == null) return;
        
//         double tiempoPromedio = aEstrella.obtenerValor("tiempoPromedioMs");
//         double throughput = aEstrella.obtenerValor("throughputCalculosPorSegundo");
//         double tasaExito = aEstrella.obtenerValor("tasaExito");
        
//         // Tiempo excesivo
//         if (tiempoPromedio > configuracion.getUmbralTiempoAEstrellaMs()) {
//             AlertaPerformance alerta = new AlertaPerformance();
//             alerta.setId("ASTAR_TIEMPO_ALTO");
//             alerta.setComponente("AEstrella");
//             alerta.setTipoAlerta(TipoAlerta.PERFORMANCE_DEGRADADA);
//             alerta.setSeveridad(tiempoPromedio > 200 ? SeveridadAlerta.CRITICA : SeveridadAlerta.ALTA);
//             alerta.setMensaje(String.format("Tiempo A* excesivo: %.1fms (límite: %.1fms)", 
//                 tiempoPromedio, configuracion.getUmbralTiempoAEstrellaMs()));
//             alerta.setValorActual(tiempoPromedio);
//             alerta.setValorLimite(configuracion.getUmbralTiempoAEstrellaMs());
            
//             resultado.agregarAlerta(alerta);
//         }
        
//         // Throughput bajo
//         if (throughput < configuracion.getUmbralThroughputMinimo()) {
//             AlertaPerformance alerta = new AlertaPerformance();
//             alerta.setId("ASTAR_THROUGHPUT_BAJO");
//             alerta.setComponente("AEstrella");
//             alerta.setTipoAlerta(TipoAlerta.THROUGHPUT_BAJO);
//             alerta.setSeveridad(throughput < 5 ? SeveridadAlerta.CRITICA : SeveridadAlerta.MEDIA);
//             alerta.setMensaje(String.format("Throughput A* bajo: %.1f calc/s (mín: %.1f)", 
//                 throughput, configuracion.getUmbralThroughputMinimo()));
            
//             resultado.agregarAlerta(alerta);
//         }
        
//         // Tasa de éxito baja
//         if (tasaExito < configuracion.getUmbralTasaExitoMinima()) {
//             AlertaPerformance alerta = new AlertaPerformance();
//             alerta.setId("ASTAR_TASA_EXITO_BAJA");
//             alerta.setComponente("AEstrella");
//             alerta.setTipoAlerta(TipoAlerta.CALIDAD_DEGRADADA);
//             alerta.setSeveridad(tasaExito < 80 ? SeveridadAlerta.ALTA : SeveridadAlerta.MEDIA);
//             alerta.setMensaje(String.format("Tasa éxito A* baja: %.1f%% (mín: %.1f%%)", 
//                 tasaExito, configuracion.getUmbralTasaExitoMinima()));
            
//             resultado.agregarAlerta(alerta);
//         }
//     }
    
//     private void verificarAlertasMemoria(Map<String, PerformanceMonitoringService.MetricaComponente> metricas,
//                                        ResultadoAlertas resultado) {
//         PerformanceMonitoringService.MetricaComponente memoria = metricas.get("Memoria");
//         if (memoria == null) return;
        
//         double usoHeap = memoria.obtenerValor("porcentajeUsoHeap");
        
//         if (usoHeap > configuracion.getUmbralUsoMemoriaMaximo()) {
//             AlertaPerformance alerta = new AlertaPerformance();
//             alerta.setId("MEMORIA_USO_ALTO");
//             alerta.setComponente("Memoria");
//             alerta.setTipoAlerta(TipoAlerta.RECURSO_AGOTANDOSE);
//             alerta.setSeveridad(usoHeap > 90 ? SeveridadAlerta.CRITICA : SeveridadAlerta.ALTA);
//             alerta.setMensaje(String.format("Uso memoria alto: %.1f%% (límite: %.1f%%)", 
//                 usoHeap, configuracion.getUmbralUsoMemoriaMaximo()));
            
//             resultado.agregarAlerta(alerta);
//         }
//     }
    
//     private void verificarAlertasCache(Map<String, PerformanceMonitoringService.MetricaComponente> metricas,
//                                      ResultadoAlertas resultado) {
//         PerformanceMonitoringService.MetricaComponente aEstrella = metricas.get("AEstrella");
//         if (aEstrella == null) return;
        
//         double tasaHit = aEstrella.obtenerValor("tasaHitCache");
        
//         if (tasaHit < configuracion.getUmbralTasaHitCacheMinima()) {
//             AlertaPerformance alerta = new AlertaPerformance();
//             alerta.setId("CACHE_TASA_HIT_BAJA");
//             alerta.setComponente("Cache");
//             alerta.setTipoAlerta(TipoAlerta.EFICIENCIA_BAJA);
//             alerta.setSeveridad(tasaHit < 30 ? SeveridadAlerta.ALTA : SeveridadAlerta.MEDIA);
//             alerta.setMensaje(String.format("Tasa hit cache baja: %.1f%% (mín: %.1f%%)", 
//                 tasaHit, configuracion.getUmbralTasaHitCacheMinima()));
            
//             resultado.agregarAlerta(alerta);
//         }
//     }
    
//     private void verificarAlertasSistema(Map<String, PerformanceMonitoringService.MetricaComponente> metricas,
//                                        ResultadoAlertas resultado) {
//         PerformanceMonitoringService.MetricaComponente leaks = metricas.get("ResourceLeaks");
//         if (leaks == null) return;
        
//         double threadsLeaks = leaks.obtenerValor("threadsLeakDetectados");
        
//         if (threadsLeaks > 0) {
//             AlertaPerformance alerta = new AlertaPerformance();
//             alerta.setId("RESOURCE_LEAKS_DETECTADOS");
//             alerta.setComponente("Sistema");
//             alerta.setTipoAlerta(TipoAlerta.LEAK_RECURSOS);
//             alerta.setSeveridad(threadsLeaks > 5 ? SeveridadAlerta.CRITICA : SeveridadAlerta.ALTA);
//             alerta.setMensaje(String.format("Leaks de threads detectados: %.0f", threadsLeaks));
            
//             resultado.agregarAlerta(alerta);
//         }
//     }
    
//     private void procesarAlertasNuevas(ResultadoAlertas resultado) {
//         for (AlertaPerformance alerta : resultado.getAlertasGeneradas()) {
//             String id = alerta.getId();
            
//             // Verificar si ya existe alerta activa
//             AlertaActiva existente = alertasActivas.get(id);
//             if (existente == null) {
//                 // Nueva alerta
//                 AlertaActiva nueva = new AlertaActiva();
//                 nueva.setAlerta(alerta);
//                 nueva.setPrimeraOcurrencia(LocalDateTime.now());
//                 nueva.setUltimaOcurrencia(LocalDateTime.now());
//                 nueva.setContadorOcurrencias(1);
                
//                 alertasActivas.put(id, nueva);
                
//                 // Log crítico para alertas nuevas
//                 logAlerta(alerta, true);
                
//                 // Añadir al historial
//                 agregarAlHistorial(alerta);
                
//             } else {
//                 // Alerta existente - actualizar
//                 existente.setUltimaOcurrencia(LocalDateTime.now());
//                 existente.incrementarContador();
//                 existente.setAlerta(alerta); // Actualizar valores
                
//                 // Log de seguimiento cada 10 ocurrencias
//                 if (existente.getContadorOcurrencias() % 10 == 0) {
//                     logAlerta(alerta, false);
//                 }
//             }
//         }
//     }
    
//     private void resolverAlertasObsoletas(Map<String, PerformanceMonitoringService.MetricaComponente> metricas) {
//         List<String> alertasAResolver = new ArrayList<>();
        
//         for (Map.Entry<String, AlertaActiva> entry : alertasActivas.entrySet()) {
//             String alertaId = entry.getKey();
//             AlertaActiva alertaActiva = entry.getValue();
            
//             // Verificar si la condición ya no se cumple
//             if (esAlertaResuelta(alertaId, metricas)) {
//                 alertasAResolver.add(alertaId);
                
//                 logger.info("✅ ALERTA RESUELTA: {} - Duración: {} min", 
//                     alertaActiva.getAlerta().getMensaje(),
//                     java.time.Duration.between(alertaActiva.getPrimeraOcurrencia(), LocalDateTime.now()).toMinutes());
//             }
//         }
        
//         // Remover alertas resueltas
//         for (String alertaId : alertasAResolver) {
//             alertasActivas.remove(alertaId);
//         }
//     }
    
//     private boolean esAlertaResuelta(String alertaId, Map<String, PerformanceMonitoringService.MetricaComponente> metricas) {
//         // Verificar si las condiciones que causaron la alerta ya no existen
//         switch (alertaId) {
//             case "ASTAR_TIEMPO_ALTO":
//                 PerformanceMonitoringService.MetricaComponente aEstrella = metricas.get("AEstrella");
//                 return aEstrella != null && aEstrella.obtenerValor("tiempoPromedioMs") <= configuracion.getUmbralTiempoAEstrellaMs();
                
//             case "MEMORIA_USO_ALTO":
//                 PerformanceMonitoringService.MetricaComponente memoria = metricas.get("Memoria");
//                 return memoria != null && memoria.obtenerValor("porcentajeUsoHeap") <= configuracion.getUmbralUsoMemoriaMaximo();
                
//             // Agregar más casos según sea necesario
//             default:
//                 return false;
//         }
//     }
    
//     private void logAlerta(AlertaPerformance alerta, boolean esNueva) {
//         String prefijo = esNueva ? "🚨 NUEVA ALERTA" : "🔄 ALERTA PERSISTENTE";
//         String severidad = obtenerEmojiSeveridad(alerta.getSeveridad());
        
//         logger.error("{} {}: [{}] {} - {}", 
//             prefijo, severidad, alerta.getComponente(), alerta.getTipoAlerta(), alerta.getMensaje());
//     }
    
//     private String obtenerEmojiSeveridad(SeveridadAlerta severidad) {
//         return switch (severidad) {
//             case CRITICA -> "🔴";
//             case ALTA -> "🟠";
//             case MEDIA -> "🟡";
//             case BAJA -> "🟢";
//         };
//     }
    
//     private void agregarAlHistorial(AlertaPerformance alerta) {
//         historialAlertas.add(alerta);
        
//         // Mantener tamaño máximo
//         while (historialAlertas.size() > MAX_HISTORIAL) {
//             historialAlertas.remove(0);
//         }
//     }
    
//     /**
//      * Obtener alertas activas
//      */
//     public Map<String, AlertaActiva> obtenerAlertasActivas() {
//         return new HashMap<>(alertasActivas);
//     }
    
//     /**
//      * Obtener estadísticas de alertas
//      */
//     public EstadisticasAlertas obtenerEstadisticas() {
//         EstadisticasAlertas stats = new EstadisticasAlertas();
//         stats.setAlertasActivas(alertasActivas.size());
//         stats.setTotalHistorialAlertas(historialAlertas.size());
        
//         // Contar por severidad
//         Map<SeveridadAlerta, Long> porSeveridad = alertasActivas.values().stream()
//             .collect(java.util.stream.Collectors.groupingBy(
//                 a -> a.getAlerta().getSeveridad(),
//                 java.util.stream.Collectors.counting()
//             ));
        
//         stats.setDistribucionSeveridad(porSeveridad);
        
//         return stats;
//     }
    
//     // Clases auxiliares
    
//     @Data
//     public static class ConfiguracionAlertas {
//         private boolean habilitado = true;
//         private double umbralTiempoAEstrellaMs = 100.0;
//         private double umbralThroughputMinimo = 10.0;
//         private double umbralTasaExitoMinima = 90.0;
//         private double umbralUsoMemoriaMaximo = 80.0;
//         private double umbralTasaHitCacheMinima = 60.0;
//     }
    
//     @Data
//     public static class ResultadoAlertas {
//         private LocalDateTime timestamp;
//         private List<AlertaPerformance> alertasGeneradas = new ArrayList<>();
        
//         public void agregarAlerta(AlertaPerformance alerta) {
//             alerta.setTimestamp(LocalDateTime.now());
//             alertasGeneradas.add(alerta);
//         }
//     }
    
//     @Data
//     public static class AlertaPerformance {
//         private String id;
//         private String componente;
//         private TipoAlerta tipoAlerta;
//         private SeveridadAlerta severidad;
//         private String mensaje;
//         private double valorActual;
//         private double valorLimite;
//         private LocalDateTime timestamp;
//     }
    
//     @Data
//     public static class AlertaActiva {
//         private AlertaPerformance alerta;
//         private LocalDateTime primeraOcurrencia;
//         private LocalDateTime ultimaOcurrencia;
//         private int contadorOcurrencias = 0;
        
//         public void incrementarContador() {
//             contadorOcurrencias++;
//         }
//     }
    
//     public enum TipoAlerta {
//         PERFORMANCE_DEGRADADA,
//         THROUGHPUT_BAJO,
//         CALIDAD_DEGRADADA,
//         RECURSO_AGOTANDOSE,
//         EFICIENCIA_BAJA,
//         LEAK_RECURSOS
//     }
    
//     public enum SeveridadAlerta {
//         BAJA, MEDIA, ALTA, CRITICA
//     }
    
//     @Data
//     public static class EstadisticasAlertas {
//         private int alertasActivas;
//         private int totalHistorialAlertas;
//         private Map<SeveridadAlerta, Long> distribucionSeveridad;
//     }
// }