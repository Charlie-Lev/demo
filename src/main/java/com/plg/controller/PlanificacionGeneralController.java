package com.plg.controller;

import com.plg.service.PlanificacionGeneralService;
import com.plg.service.SimulationTimeService;
import com.plg.dto.PlanificacionGeneralRequest;
import com.plg.dto.PlanificacionGeneralResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

/**
 * 🚛 Controller para Planificación General de Rutas con Tiempo de Simulación
 * 
 * Endpoints para ejecutar planificación completa considerando:
 * - Tiempo de simulación actual
 * - Bloqueos temporales vigentes
 * - Estado de almacenes, camiones y pedidos
 */
@RestController
@RequestMapping("/api/planificacion-general")
@CrossOrigin(origins = "*")
public class PlanificacionGeneralController {
    
    private static final Logger logger = LoggerFactory.getLogger(PlanificacionGeneralController.class);
    
    @Autowired
    private PlanificacionGeneralService planificacionGeneralService;
    
    @Autowired
    private SimulationTimeService simulationTimeService;
    
    /**
     * 🎯 Endpoint principal: Ejecutar planificación completa en tiempo específico
     * 
     * POST /api/planificacion-general/ejecutar
     * 
     * Body: {
     *   "tiempoSimulacion": "2025-01-15T14:30:00" (opcional, si no se envía usa tiempo actual)
     *   "configuracion": {
     *     "forzarRecalculo": false,
     *     "usarCacheRutas": true,
     *     "optimizacionNivel": "BALANCEADA"
     *   }
     * }
     */
    @PostMapping("/ejecutar")
    public ResponseEntity<PlanificacionGeneralResponse> ejecutarPlanificacionCompleta(
            @RequestBody(required = false) PlanificacionGeneralRequest request) {
        
        try {
            // 🕒 Determinar tiempo de simulación
            LocalDateTime tiempoSimulacion;
            if (request != null && request.getTiempoSimulacion() != null) {
                tiempoSimulacion = request.getTiempoSimulacion();
                logger.info("🕒 Usando tiempo específico: {}", tiempoSimulacion);
            } else {
                tiempoSimulacion = simulationTimeService.getCurrentSimulationTime();
                logger.info("🕒 Usando tiempo actual de simulación: {}", tiempoSimulacion);
            }
            
            logger.info("🚀 INICIANDO PLANIFICACIÓN GENERAL");
            logger.info("⏰ Tiempo de simulación: {}", 
                tiempoSimulacion.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
            
            // 🎯 Ejecutar planificación completa
            PlanificacionGeneralResponse response = planificacionGeneralService
                .ejecutarPlanificacionCompleta(tiempoSimulacion, request);
            
            logger.info("✅ PLANIFICACIÓN COMPLETADA");
            logger.info("📊 Resultados: {} camiones planificados, {} rutas calculadas", 
                response.getCamionesAsignados(), response.getRutasCalculadas());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("❌ Error en planificación general: {}", e.getMessage(), e);
            
            PlanificacionGeneralResponse errorResponse = new PlanificacionGeneralResponse();
            errorResponse.setExitoso(false);
            errorResponse.setMensajeError("Error ejecutando planificación: " + e.getMessage());
            errorResponse.setTimestamp(LocalDateTime.now());
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * 🕒 Endpoint para ejecutar con tiempo específico como parámetro
     * 
     * POST /api/planificacion-general/ejecutar-en/{tiempo}
     * 
     * @param tiempoStr Formato: "2025-01-15T14:30:00"
     */
    @PostMapping("/ejecutar-en/{tiempo}")
    public ResponseEntity<Map<String, Object>> ejecutarEnTiempoEspecifico(
            @PathVariable String tiempoStr,
            @RequestBody(required = false) PlanificacionGeneralRequest request) {
        
        try {
            // 🔍 Parsear tiempo
            LocalDateTime tiempoSimulacion = LocalDateTime.parse(tiempoStr, 
                DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            
            logger.info("🎯 Ejecutando planificación en tiempo específico: {}", tiempoSimulacion);
            
            // Crear request si no existe
            if (request == null) {
                request = new PlanificacionGeneralRequest();
            }
            request.setTiempoSimulacion(tiempoSimulacion);
            
            PlanificacionGeneralResponse response = planificacionGeneralService
                .ejecutarPlanificacionCompleta(tiempoSimulacion, request);
            
            // Convertir a map para response más flexible
            Map<String, Object> resultado = new HashMap<>();
            resultado.put("exitoso", response.isExitoso());
            resultado.put("tiempoSimulacion", tiempoSimulacion);
            resultado.put("camionesAsignados", response.getCamionesAsignados());
            resultado.put("rutasCalculadas", response.getRutasCalculadas());
            resultado.put("pedidosAtendidos", response.getPedidosAtendidos());
            resultado.put("distanciaTotalKm", response.getDistanciaTotalKm());
            resultado.put("tiempoTotalHoras", response.getTiempoTotalHoras());
            resultado.put("estadisticas", response.getEstadisticas());
            resultado.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(resultado);
            
        } catch (DateTimeParseException e) {
            logger.error("❌ Error parseando tiempo: {}", tiempoStr);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("exitoso", false);
            errorResponse.put("error", "Formato de tiempo inválido");
            errorResponse.put("formatoEsperado", "YYYY-MM-DDTHH:mm:ss");
            errorResponse.put("ejemplos", Map.of(
                "hoy_14_30", LocalDateTime.now().withHour(14).withMinute(30).withSecond(0),
                "manana_08_00", LocalDateTime.now().plusDays(1).withHour(8).withMinute(0).withSecond(0)
            ));
            errorResponse.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.badRequest().body(errorResponse);
            
        } catch (Exception e) {
            logger.error("❌ Error en planificación: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("exitoso", false);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * 🚛 Endpoint para obtener estado actual de recursos
     * 
     * GET /api/planificacion-general/estado-recursos
     */
    @GetMapping("/estado-recursos")
    public ResponseEntity<Map<String, Object>> obtenerEstadoRecursos() {
        try {
            LocalDateTime tiempoActual = simulationTimeService.getCurrentSimulationTime();
            
            Map<String, Object> estado = planificacionGeneralService
                .obtenerEstadoRecursos(tiempoActual);
            
            estado.put("tiempoSimulacion", tiempoActual);
            estado.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(estado);
            
        } catch (Exception e) {
            logger.error("❌ Error obteniendo estado de recursos: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("exitoso", false);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * 📊 Endpoint para obtener métricas de planificación
     * 
     * GET /api/planificacion-general/metricas
     */
    @GetMapping("/metricas")
    public ResponseEntity<Map<String, Object>> obtenerMetricasPlanificacion() {
        try {
            Map<String, Object> metricas = planificacionGeneralService.obtenerMetricas();
            metricas.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(metricas);
            
        } catch (Exception e) {
            logger.error("❌ Error obteniendo métricas: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("exitoso", false);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * 🧪 Endpoint para test rápido (igual que el JUnit)
     * 
     * POST /api/planificacion-general/test-escenario
     */
    @PostMapping("/test-escenario")
    public ResponseEntity<Map<String, Object>> ejecutarTestEscenario() {
        try {
            logger.info("🧪 Ejecutando test de escenario real");
            
            Map<String, Object> resultadoTest = planificacionGeneralService
                .ejecutarTestEscenarioCompleto();
            
            resultadoTest.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(resultadoTest);
            
        } catch (Exception e) {
            logger.error("❌ Error en test de escenario: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("exitoso", false);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * 🔄 Endpoint para limpiar cache y reiniciar servicios
     * 
     * POST /api/planificacion-general/reiniciar
     */
    @PostMapping("/reiniciar")
    public ResponseEntity<Map<String, Object>> reiniciarServicios() {
        try {
            logger.info("🔄 Reiniciando servicios de planificación");
            
            planificacionGeneralService.reiniciarServicios();
            
            Map<String, Object> response = new HashMap<>();
            response.put("exitoso", true);
            response.put("mensaje", "Servicios reiniciados correctamente");
            response.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("❌ Error reiniciando servicios: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("exitoso", false);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
}