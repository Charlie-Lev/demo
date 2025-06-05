// package com.plg.service.util;

// import com.plg.service.*;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.stereotype.Component;

// import lombok.Data;

// import java.time.LocalDateTime;
// import java.util.*;

// /**
//  * Configuración adaptiva basada en métricas de performance
//  * Ajusta parámetros automáticamente para optimizar rendimiento
//  */
// @Component
// public class AdaptiveConfigurationService {
    
//     private static final Logger logger = LoggerFactory.getLogger(AdaptiveConfigurationService.class);
    
//     @Autowired
//     private AEstrellaService aEstrellaService;
    
//     @Autowired
//     private CacheRutas cacheRutas;
    
//     // Configuración adaptiva
//     private ConfiguracionAdaptiva configuracion = new ConfiguracionAdaptiva();
//     private final List<AjusteConfiguracion> historialAjustes = new ArrayList<>();
    
//     /**
//      * Ajustar configuraciones basado en métricas actuales
//      */
//     public ResultadoAjuste ajustarConfiguraciones(Map<String, PerformanceMonitoringService.MetricaComponente> metricas) {
//         if (!configuracion.isHabilitado()) {
//             return new ResultadoAjuste();
//         }
        
//         logger.debug("Iniciando ajustes adaptativos de configuración");
        
//         ResultadoAjuste resultado = new ResultadoAjuste();
//         resultado.setTimestamp(LocalDateTime.now());
        
//         // Ajustar A*
//         ajustarConfiguracionAEstrella(metricas, resultado);
        
//         // Ajustar Cache
//         ajustarConfiguracionCache(metricas, resultado);
        
//         // Log de ajustes realizados
//         if (!resultado.getAjustesRealizados().isEmpty()) {
//             logger.info("Configuración adaptiva aplicada: {} ajustes realizados", 
//                 resultado.getAjustesRealizados().size());
            
//             for (AjusteConfiguracion ajuste : resultado.getAjustesRealizados()) {
//                 logger.info("  - {}: {} -> {} (razón: {})", 
//                     ajuste.getParametro(), ajuste.getValorAnterior(), 
//                     ajuste.getValorNuevo(), ajuste.getRazon());
//             }
//         }
        
//         return resultado;
//     }
    
//     private void ajustarConfiguracionAEstrella(Map<String, PerformanceMonitoringService.MetricaComponente> metricas,
//                                              ResultadoAjuste resultado) {
//         PerformanceMonitoringService.MetricaComponente aEstrella = metricas.get("AEstrella");
//         if (aEstrella == null) return;
        
//         double tiempoPromedio = aEstrella.obtenerValor("tiempoPromedioMs");
//         double throughput = aEstrella.obtenerValor("throughputCalculosPorSegundo");
//         double utilizacionPool = aEstrella.obtenerValor("utilizacionPoolThreads");
        
//         // Ajustar timeout según rendimiento
//         if (tiempoPromedio > 150) {
//             // Tiempo alto - reducir timeout para evitar bloqueos
//             AEstrellaService.ConfiguracionAEstrella configActual = new AEstrellaService.ConfiguracionAEstrella();
//             long timeoutAnterior = configActual.getTimeoutMs();
//             long nuevoTimeout = Math.max(1000, (long) (timeoutAnterior * 0.8));
            
//             if (nuevoTimeout != timeoutAnterior) {
//                 configActual.setTimeoutMs(nuevoTimeout);
//                 aEstrellaService.configurar(configActual);
                
//                 AjusteConfiguracion ajuste = new AjusteConfiguracion();
//                 ajuste.setComponente("AEstrella");
//                 ajuste.setParametro("timeoutMs");
//                 ajuste.setValorAnterior(String.valueOf(timeoutAnterior));
//                 ajuste.setValorNuevo(String.valueOf(nuevoTimeout));
//                 ajuste.setRazon("Tiempo promedio alto: " + tiempoPromedio + "ms");
                
//                 resultado.agregarAjuste(ajuste);
//             }
//         }
        
//         // Ajustar número de threads según utilización
//         if (utilizacionPool > 90 && throughput < 20) {
//             // Pool saturado y throughput bajo - incrementar threads
//             AEstrellaService.ConfiguracionAEstrella configActual = new AEstrellaService.ConfiguracionAEstrella();
//             int threadsAnterior = configActual.getNumeroThreads();
//             int nuevoThreads = Math.min(16, threadsAnterior + 2);
            
//             if (nuevoThreads != threadsAnterior) {
//                 configActual.setNumeroThreads(nuevoThreads);
//                 aEstrellaService.configurar(configActual);
                
//                 AjusteConfiguracion ajuste = new AjusteConfiguracion();
//                 ajuste.setComponente("AEstrella");
//                 ajuste.setParametro("numeroThreads");
//                 ajuste.setValorAnterior(String.valueOf(threadsAnterior));
//                 ajuste.setValorNuevo(String.valueOf(nuevoThreads));
//                 ajuste.setRazon("Pool saturado: " + utilizacionPool + "% utilización");
                
//                 resultado.agregarAjuste(ajuste);
//             }
//         } else if (utilizacionPool < 30 && throughput > 50) {
//             // Pool sub-utilizado - reducir threads para eficiencia
//             AEstrellaService.ConfiguracionAEstrella configActual = new AEstrellaService.ConfiguracionAEstrella();
//             int threadsAnterior = configActual.getNumeroThreads();
//             int nuevoThreads = Math.max(2, threadsAnterior - 1);
            
//             if (nuevoThreads != threadsAnterior) {
//                 configActual.setNumeroThreads(nuevoThreads);
//                 aEstrellaService.configurar(configActual);
                
//                 AjusteConfiguracion ajuste = new AjusteConfiguracion();
//                 ajuste.setComponente("AEstrella");
//                 ajuste.setParametro("numeroThreads");
//                 ajuste.setValorAnterior(String.valueOf(threadsAnterior));
//                 ajuste.setValorNuevo(String.valueOf(nuevoThreads));
//                 ajuste.setRazon("Pool sub-utilizado: " + utilizacionPool + "% utilización");
                
//                 resultado.agregarAjuste(ajuste);
//             }
//         }
//     }
    
//     private void ajustarConfiguracionCache(Map<String, PerformanceMonitoringService.MetricaComponente> metricas,
//                                          ResultadoAjuste resultado) {
//         // Cache metrics
//         PerformanceMonitoringService.MetricaComponente cache = metricas.get("Cache");
//         PerformanceMonitoringService.MetricaComponente aEstrella = metricas.get("AEstrella");
        
//         if (cache == null || aEstrella == null) return;
        
//         double tasaHit = aEstrella.obtenerValor("tasaHitCache");
//         double porcentajeUso = cache.obtenerValor("porcentajeUso");
        
//         // Ajustar tamaño de cache
//         if (tasaHit < 50 && porcentajeUso > 80) {
//             // Tasa hit baja y cache casi lleno - incrementar tamaño
//             CacheRutas.EstadisticasCache statsCache = cacheRutas.obtenerEstadisticas();
//             int capacidadAnterior = statsCache.getCapacidadMaxima();
//             int nuevaCapacidad = Math.min(10000, (int) (capacidadAnterior * 1.5));
            
//             if (nuevaCapacidad != capacidadAnterior) {
//                 cacheRutas.configurarCapacidad(nuevaCapacidad);
                
//                 AjusteConfiguracion ajuste = new AjusteConfiguracion();
//                 ajuste.setComponente("Cache");
//                 ajuste.setParametro("capacidadMaxima");
//                 ajuste.setValorAnterior(String.valueOf(capacidadAnterior));
//                 ajuste.setValorNuevo(String.valueOf(nuevaCapacidad));
//                 ajuste.setRazon("Tasa hit baja: " + tasaHit + "%, uso: " + porcentajeUso + "%");
                
//                 resultado.agregarAjuste(ajuste);
//             }
//         } else if (tasaHit > 90 && porcentajeUso < 30) {
//             // Tasa hit muy alta y cache poco usado - reducir tamaño para eficiencia
//             CacheRutas.EstadisticasCache statsCache = cacheRutas.obtenerEstadisticas();
//             int capacidadAnterior = statsCache.getCapacidadMaxima();
//             int nuevaCapacidad = Math.max(500, (int) (capacidadAnterior * 0.8));
            
//             if (nuevaCapacidad != capacidadAnterior) {
//                 cacheRutas.configurarCapacidad(nuevaCapacidad);
                
//                 AjusteConfiguracion ajuste = new AjusteConfiguracion();
//                 ajuste.setComponente("Cache");
//                 ajuste.setParametro("capacidadMaxima");
//                 ajuste.setValorAnterior(String.valueOf(capacidadAnterior));
//                 ajuste.setValorNuevo(String.valueOf(nuevaCapacidad));
//                 ajuste.setRazon("Cache sub-utilizado: " + porcentajeUso + "% uso");
                
//                 resultado.agregarAjuste(ajuste);
//             }
//         }
//     }
    
//     // Clases auxiliares
    
//     @Data
//     public static class ConfiguracionAdaptiva {
//         private boolean habilitado = true;
//         private double umbralCambioMinimo = 5.0; // % mínimo de cambio para aplicar ajuste
//         private int maxAjustesPorCiclo = 3; // Máximo ajustes por ciclo para evitar oscilaciones
//     }
    
//     @Data
//     public static class ResultadoAjuste {
//         private LocalDateTime timestamp;
//         private List<AjusteConfiguracion> ajustesRealizados = new ArrayList<>();
        
//         public void agregarAjuste(AjusteConfiguracion ajuste) {
//             ajustesRealizados.add(ajuste);
//         }
//     }
    
//     @Data
//     public static class AjusteConfiguracion {
//         private String componente;
//         private String parametro;
//         private String valorAnterior;
//         private String valorNuevo;
//         private String razon;
//         private LocalDateTime timestamp = LocalDateTime.now();
//     }
// }