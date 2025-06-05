// // NUEVO ARCHIVO: src/main/java/com/plg/controller/RutasVisualizacionController.java
// package com.plg.controller;

// import com.plg.domain.*;
// import com.plg.dto.RutaVisualizacionDTO;
// import com.plg.service.PlanificacionIntegradorService;
// import com.plg.service.SerializadorVisualizacionService;
// import com.plg.service.PlanificadorRutasService;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.http.ResponseEntity;
// import org.springframework.web.bind.annotation.*;

// import java.util.*;

// /**
//  * Controller REST para APIs de visualización de rutas
//  * Proporciona endpoints para consultar rutas optimizadas
//  */
// @RestController
// @RequestMapping("/api")
// @CrossOrigin(origins = "*")
// public class RutasVisualizacionController {
    
//     private static final Logger logger = LoggerFactory.getLogger(RutasVisualizacionController.class);
    
//     @Autowired
//     private PlanificacionIntegradorService planificacionIntegrador;
    
//     @Autowired
//     private SerializadorVisualizacionService serializadorVisualizacion;
    
//     @Autowired
//     private PlanificadorRutasService planificadorRutas;
    
//     /**
//      * V1: Obtener todas las rutas optimizadas
//      */
//     @GetMapping("/v1/rutas")
//     public ResponseEntity<List<RutaVisualizacionDTO>> obtenerRutas() {
//         try {
//             // Simular obtención de rutas desde el servicio
//             //Map<Camion, RutaOptimizada> rutas = new HashMap<>(); // En implementación real, obtener desde servicio
            
//             Map<Camion, RutaOptimizada> rutas = planificacionIntegrador.ejecutarPlanificacionCompleta();

//             List<RutaVisualizacionDTO> rutasDTO = planificacionIntegrador.prepararParaVisualizacion(rutas);
            
//             return ResponseEntity.ok(rutasDTO);
            
//         } catch (Exception e) {
//             logger.error("Error obteniendo rutas: {}", e.getMessage(), e);
//             return ResponseEntity.internalServerError().build();
//         }
//     }
    
//     /**
//      * V1: Obtener ruta específica por camión
//      */
//     @GetMapping("/v1/rutas/camion/{camionId}")
//     public ResponseEntity<RutaVisualizacionDTO> obtenerRutaPorCamion(@PathVariable Integer camionId) {
//         try {
//             // En implementación real, buscar ruta específica del camión
//             logger.info("Obteniendo ruta para camión: {}", camionId);
            
//             // TODO: Implementar búsqueda específica
//             return ResponseEntity.notFound().build();
            
//         } catch (Exception e) {
//             logger.error("Error obteniendo ruta para camión {}: {}", camionId, e.getMessage(), e);
//             return ResponseEntity.internalServerError().build();
//         }
//     }
    
//     /**
//      * V1: Obtener payload completo para dashboard
//      */
//     @GetMapping("/v1/dashboard")
//     public ResponseEntity<SerializadorVisualizacionService.PayloadVisualizacionCompleto> obtenerDashboard(
//             @RequestParam(defaultValue = "false") boolean incluirObstaculos,
//             @RequestParam(defaultValue = "true") boolean incluirTimeline,
//             @RequestParam(defaultValue = "1") int nivelOptimizacion) {
        
//         try {
//             logger.info("Generando dashboard completo - optimización nivel: {}", nivelOptimizacion);
            
//             // Configurar visualización
//             SerializadorVisualizacionService.ConfiguracionVisualizacion config = 
//                 new SerializadorVisualizacionService.ConfiguracionVisualizacion();
//             config.setIncluirObstaculos(incluirObstaculos);
//             config.setIncluirTimeline(incluirTimeline);
            
//             // En implementación real, obtener datos desde servicios
//             Map<Camion, RutaOptimizada> rutas = new HashMap<>();
//             Map<String, Object> metricas = new HashMap<>();
            
//             SerializadorVisualizacionService.PayloadVisualizacionCompleto payload = 
//                 serializadorVisualizacion.generarPayloadCompleto(rutas, metricas, config);
            
//             // Aplicar optimización
//             SerializadorVisualizacionService.PayloadVisualizacionCompleto payloadOptimizado = 
//                 serializadorVisualizacion.optimizarPayload(payload, nivelOptimizacion);
            
//             return ResponseEntity.ok(payloadOptimizado);
            
//         } catch (Exception e) {
//             logger.error("Error generando dashboard: {}", e.getMessage(), e);
//             return ResponseEntity.internalServerError().build();
//         }
//     }
    
//     /**
//      * V2: Endpoints con versionado para backward compatibility
//      */
//     @GetMapping("/v2/rutas")
//     public ResponseEntity<Map<String, Object>> obtenerRutasV2() {
//         try {
//             List<RutaVisualizacionDTO> rutas = obtenerRutas().getBody();
            
//             // Formato V2 con metadatos adicionales
//             Map<String, Object> response = new HashMap<>();
//             response.put("version", "2.0");
//             response.put("timestamp", java.time.LocalDateTime.now());
//             response.put("rutas", rutas);
//             response.put("totalRutas", rutas != null ? rutas.size() : 0);
            
//             return ResponseEntity.ok(response);
            
//         } catch (Exception e) {
//             logger.error("Error en API v2: {}", e.getMessage(), e);
//             return ResponseEntity.internalServerError().build();
//         }
//     }

//     /**
//      * Forzar ejecución del planificador con datos de BD
//      */
//     @PostMapping("/v1/planificar")
//     public ResponseEntity<Map<String, Object>> forzarPlanificacion() {
//         try {
//             logger.info("🚀 Iniciando planificación forzada...");
            
//             // Obtener datos desde BD automáticamente
//             Map<Camion, RutaOptimizada> rutasCalculadas = planificacionIntegrador.ejecutarPlanificacionCompleta();
            
//             // Preparar respuesta
//             Map<String, Object> response = new HashMap<>();
//             response.put("success", true);
//             response.put("timestamp", java.time.LocalDateTime.now());
//             response.put("totalRutas", rutasCalculadas.size());
//             response.put("camionesAsignados", rutasCalculadas.keySet().size());
            
//             logger.info("✅ Planificación completada: {} rutas generadas", rutasCalculadas.size());
            
//             return ResponseEntity.ok(response);
            
//         } catch (Exception e) {
//             logger.error("❌ Error en planificación forzada: {}", e.getMessage(), e);
            
//             Map<String, Object> errorResponse = new HashMap<>();
//             errorResponse.put("success", false);
//             errorResponse.put("error", e.getMessage());
//             errorResponse.put("timestamp", java.time.LocalDateTime.now());
            
//             return ResponseEntity.internalServerError().body(errorResponse);
//         }
//     }
// }