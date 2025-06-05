package com.plg.controller;

import com.plg.dto.SimulationTimeDTO;
import com.plg.service.SimulationTimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller REST para control del tiempo de simulación
 * Proporciona endpoints para pausar, reanudar, acelerar y configurar
 */
@RestController
@RequestMapping("/api/simulation")
@CrossOrigin(origins = "*")
public class SimulationTimeController {
    
    private static final Logger logger = LoggerFactory.getLogger(SimulationTimeController.class);
    
    @Autowired
    private SimulationTimeService simulationTimeService;
    
    /**
     * Obtener estado actual del tiempo de simulación
     */
    @GetMapping("/time")
    public ResponseEntity<SimulationTimeDTO> getCurrentTime() {
        try {
            SimulationTimeDTO currentState = simulationTimeService.getCurrentState();
            return ResponseEntity.ok(currentState);
            
        } catch (Exception e) {
            logger.error("Error obteniendo tiempo actual: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Iniciar simulación
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startSimulation() {
        try {
            simulationTimeService.startSimulation();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Simulación iniciada");
            response.put("timestamp", LocalDateTime.now());
            response.put("state", simulationTimeService.getCurrentState());
            
            logger.info("✅ Simulación iniciada vía REST API");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error iniciando simulación: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error iniciando simulación: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * Pausar simulación
     */
    @PostMapping("/pause")
    public ResponseEntity<Map<String, Object>> pauseSimulation() {
        try {
            simulationTimeService.pauseSimulation();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Simulación pausada");
            response.put("timestamp", LocalDateTime.now());
            response.put("state", simulationTimeService.getCurrentState());
            
            logger.info("⏸️ Simulación pausada vía REST API");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error pausando simulación: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error pausando simulación: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * Reiniciar simulación
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetSimulation() {
        try {
            simulationTimeService.resetSimulation();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Simulación reiniciada");
            response.put("timestamp", LocalDateTime.now());
            response.put("state", simulationTimeService.getCurrentState());
            
            logger.info("🔄 Simulación reiniciada vía REST API");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error reiniciando simulación: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error reiniciando simulación: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * Ajustar factor de aceleración
     */
    @PutMapping("/acceleration/{factor}")
    public ResponseEntity<Map<String, Object>> setAccelerationFactor(@PathVariable double factor) {
        try {
            simulationTimeService.setAccelerationFactor(factor);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", String.format("Factor de aceleración ajustado a %.1fx", factor));
            response.put("timestamp", LocalDateTime.now());
            response.put("newFactor", factor);
            response.put("state", simulationTimeService.getCurrentState());
            
            logger.info("⚡ Factor de aceleración ajustado a {}x vía REST API", factor);
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            logger.warn("Factor de aceleración inválido: {}", factor);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Factor de aceleración inválido: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            response.put("requestedFactor", factor);
            
            return ResponseEntity.badRequest().body(response);
            
        } catch (Exception e) {
            logger.error("Error ajustando factor de aceleración: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error ajustando factor: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * Establecer tiempo específico de simulación
     */
    @PutMapping("/time")
    public ResponseEntity<Map<String, Object>> setSimulationTime(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime time) {
        try {
            simulationTimeService.setSimulationTime(time);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Tiempo de simulación establecido");
            response.put("timestamp", LocalDateTime.now());
            response.put("newSimulationTime", time);
            response.put("state", simulationTimeService.getCurrentState());
            
            logger.info("🕐 Tiempo de simulación establecido a {} vía REST API", time);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error estableciendo tiempo de simulación: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error estableciendo tiempo: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    // /**
    //  * Programar planificación automática
    //  */
    // @PostMapping("/schedule/planificacion")
    // public ResponseEntity<Map<String, Object>> schedulePlanificacion(@RequestParam String periodo) {
    //     try {
    //         simulationTimeService.schedulePlanificacion(periodo);
            
    //         Map<String, Object> response = new HashMap<>();
    //         response.put("success", true);
    //         response.put("message", "Planificación automática programada");
    //         response.put("timestamp", LocalDateTime.now());
    //         response.put("periodo", periodo);
            
    //         logger.info("📅 Planificación automática programada cada {} vía REST API", periodo);
    //         return ResponseEntity.ok(response);
            
    //     } catch (Exception e) {
    //         logger.error("Error programando planificación: {}", e.getMessage(), e);
            
    //         Map<String, Object> response = new HashMap<>();
    //         response.put("success", false);
    //         response.put("message", "Error programando planificación: " + e.getMessage());
    //         response.put("timestamp", LocalDateTime.now());
            
    //         return ResponseEntity.internalServerError().body(response);
    //     }
    // }
    
    /**
     * Obtener métricas de rendimiento
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        try {
            Map<String, Object> metrics = simulationTimeService.getMetrics();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("timestamp", LocalDateTime.now());
            response.put("metrics", metrics);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error obteniendo métricas: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error obteniendo métricas: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * Obtener estado completo del sistema de simulación
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSimulationStatus() {
        try {
            SimulationTimeDTO currentState = simulationTimeService.getCurrentState();
            Map<String, Object> metrics = simulationTimeService.getMetrics();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("timestamp", LocalDateTime.now());
            response.put("simulationState", currentState);
            response.put("metrics", metrics);
            response.put("version", "1.0");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error obteniendo estado de simulación: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error obteniendo estado: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * Control rápido - Toggle start/pause
     */
    @PostMapping("/toggle")
    public ResponseEntity<Map<String, Object>> toggleSimulation() {
        try {
            SimulationTimeDTO currentState = simulationTimeService.getCurrentState();
            
            if (currentState.isRunning()) {
                simulationTimeService.pauseSimulation();
            } else {
                simulationTimeService.startSimulation();
            }
            
            SimulationTimeDTO newState = simulationTimeService.getCurrentState();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", newState.isRunning() ? "Simulación iniciada" : "Simulación pausada");
            response.put("timestamp", LocalDateTime.now());
            response.put("previousState", currentState.isRunning());
            response.put("currentState", newState.isRunning());
            response.put("state", newState);
            
            logger.info("🔄 Toggle simulación: {} → {} vía REST API", 
                       currentState.isRunning(), newState.isRunning());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error en toggle simulación: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error en toggle: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * Configurar velocidades preestablecidas
     */
    @PostMapping("/speed/{preset}")
    public ResponseEntity<Map<String, Object>> setSpeedPreset(@PathVariable String preset) {
        try {
            double factor;
            String description;
            
            switch (preset.toLowerCase()) {
                case "realtime":
                    factor = 1.0;
                    description = "Tiempo real";
                    break;
                case "fast":
                    factor = 10.0;
                    description = "Velocidad rápida (10x)";
                    break;
                case "veryfast":
                    factor = 60.0;
                    description = "Velocidad muy rápida (60x)";
                    break;
                case "ultrafast":
                    factor = 360.0;
                    description = "Velocidad ultra rápida (360x)";
                    break;
                case "slow":
                    factor = 0.5;
                    description = "Velocidad lenta (0.5x)";
                    break;
                default:
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("success", false);
                    errorResponse.put("message", "Preset no válido. Opciones: realtime, slow, fast, veryfast, ultrafast");
                    errorResponse.put("timestamp", LocalDateTime.now());
                    
                    return ResponseEntity.badRequest().body(errorResponse);
            }
            
            simulationTimeService.setAccelerationFactor(factor);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", description + " activada");
            response.put("timestamp", LocalDateTime.now());
            response.put("preset", preset);
            response.put("factor", factor);
            response.put("state", simulationTimeService.getCurrentState());
            
            logger.info("🎯 Preset de velocidad '{}' aplicado ({}x) vía REST API", preset, factor);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error aplicando preset de velocidad: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error aplicando preset: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }
    @PutMapping("/set-start-time")
    public ResponseEntity<Map<String, Object>> setSimulationStartTime(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime) {
        
        try {
            logger.info("🕐 Estableciendo tiempo de inicio de simulación: {}", startTime);
            
            simulationTimeService.setSimulationStartTime(startTime);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Tiempo de inicio establecido exitosamente");
            response.put("timestamp", LocalDateTime.now());
            response.put("startTime", startTime);
            response.put("state", simulationTimeService.getCurrentState());
            
            logger.info("✅ Tiempo de inicio establecido a {} vía REST API", startTime);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error estableciendo tiempo de inicio: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error estableciendo tiempo de inicio: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            response.put("requestedStartTime", startTime);
            
            return ResponseEntity.badRequest().body(response);
        }
    }
    @PostMapping("/start-with-time")
    public ResponseEntity<Map<String, Object>> startSimulationWithTime(
            @RequestBody Map<String, Object> request) {
        
        try {
            // Parsear startTime del request
            String startTimeStr = (String) request.get("startTime");
            if (startTimeStr == null || startTimeStr.trim().isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Campo 'startTime' es requerido en formato YYYY-MM-DDTHH:mm:ss");
                errorResponse.put("timestamp", LocalDateTime.now());
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            LocalDateTime startTime = LocalDateTime.parse(startTimeStr);
            
            // Factor de aceleración opcional (default 1.0)
            Double accelerationFactor = request.containsKey("accelerationFactor") ? 
                ((Number) request.get("accelerationFactor")).doubleValue() : 1.0;
            
            logger.info("🚀 Iniciando simulación con tiempo personalizado: {} y factor: {}", 
                    startTime, accelerationFactor);
            
            // Establecer tiempo y factor
            simulationTimeService.setSimulationStartTime(startTime);
            if (accelerationFactor != 1.0) {
                simulationTimeService.setAccelerationFactor(accelerationFactor);
            }
            
            // Iniciar simulación
            simulationTimeService.startSimulation();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Simulación iniciada con configuración personalizada");
            response.put("timestamp", LocalDateTime.now());
            response.put("startTime", startTime);
            response.put("accelerationFactor", accelerationFactor);
            response.put("state", simulationTimeService.getCurrentState());
            
            logger.info("✅ Simulación iniciada con tiempo {} y factor {}x vía REST API", 
                    startTime, accelerationFactor);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error iniciando simulación con tiempo específico: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error iniciando simulación con tiempo específico: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.status(400).body(response);
        }
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getSimulationConfig() {
        try {
            SimulationTimeDTO currentState = simulationTimeService.getCurrentState();
            Map<String, Object> metrics = simulationTimeService.getMetrics();
            
            Map<String, Object> config = new HashMap<>();
            config.put("success", true);
            config.put("timestamp", LocalDateTime.now());
            config.put("isRunning", currentState.isRunning());
            config.put("simulationTime", currentState.getSimulationTime());
            config.put("accelerationFactor", currentState.getAccelerationFactor());
            config.put("realTime", currentState.getRealTime());
            config.put("uptime", currentState.getUptime());
            config.put("tickCount", currentState.getTickCount());
            config.put("metrics", metrics);
            
            return ResponseEntity.ok(config);
            
        } catch (Exception e) {
            logger.error("Error obteniendo configuración: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error obteniendo configuración: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    @PutMapping("/set-start-time-flexible")
    public ResponseEntity<Map<String, Object>> setSimulationStartTimeFlexible(
            @RequestBody Map<String, Object> request) {
        
        try {
            LocalDateTime startTime;
            
            // Intentar diferentes formatos
            if (request.containsKey("startTime")) {
                String startTimeStr = (String) request.get("startTime");
                startTime = LocalDateTime.parse(startTimeStr);
                
            } else if (request.containsKey("fecha") && request.containsKey("hora")) {
                // Formato separado: fecha="2025-01-15", hora="08:30:00"
                String fecha = (String) request.get("fecha");
                String hora = (String) request.get("hora");
                startTime = LocalDateTime.parse(fecha + "T" + hora);
                
            } else if (request.containsKey("year") && request.containsKey("month") && 
                    request.containsKey("day") && request.containsKey("hour") && 
                    request.containsKey("minute")) {
                // Formato por componentes separados
                int year = ((Number) request.get("year")).intValue();
                int month = ((Number) request.get("month")).intValue();
                int day = ((Number) request.get("day")).intValue();
                int hour = ((Number) request.get("hour")).intValue();
                int minute = ((Number) request.get("minute")).intValue();
                int second = request.containsKey("second") ? 
                        ((Number) request.get("second")).intValue() : 0;
                
                startTime = LocalDateTime.of(year, month, day, hour, minute, second);
                
            } else {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Formato de fecha no válido. Use: 'startTime', 'fecha+hora', o componentes separados");
                errorResponse.put("timestamp", LocalDateTime.now());
                errorResponse.put("ejemplos", Map.of(
                    "formato1", "{ \"startTime\": \"2025-01-15T08:30:00\" }",
                    "formato2", "{ \"fecha\": \"2025-01-15\", \"hora\": \"08:30:00\" }",
                    "formato3", "{ \"year\": 2025, \"month\": 1, \"day\": 15, \"hour\": 8, \"minute\": 30 }"
                ));
                
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            simulationTimeService.setSimulationStartTime(startTime);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Tiempo de inicio establecido con formato flexible");
            response.put("timestamp", LocalDateTime.now());
            response.put("startTime", startTime);
            response.put("parsedFormat", startTime.toString());
            response.put("state", simulationTimeService.getCurrentState());
            
            logger.info("✅ Tiempo de inicio establecido (flexible) a {} vía REST API", startTime);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error estableciendo tiempo de inicio (flexible): {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error estableciendo tiempo de inicio: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            response.put("requestReceived", request);
            
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/start-preset/{preset}")
    public ResponseEntity<Map<String, Object>> startWithPreset(@PathVariable String preset) {
        try {
            LocalDateTime startTime;
            double accelerationFactor = 1.0;
            String description;
            
            switch (preset.toLowerCase()) {
                case "hoy-madrugada":
                    startTime = LocalDateTime.now().withHour(6).withMinute(0).withSecond(0);
                    accelerationFactor = 10.0;
                    description = "Hoy a las 6:00 AM con velocidad 10x";
                    break;
                    
                case "hoy-manana":
                    startTime = LocalDateTime.now().withHour(8).withMinute(0).withSecond(0);
                    accelerationFactor = 5.0;
                    description = "Hoy a las 8:00 AM con velocidad 5x";
                    break;
                    
                case "manana-madrugada":
                    startTime = LocalDateTime.now().plusDays(1).withHour(6).withMinute(0).withSecond(0);
                    accelerationFactor = 60.0;
                    description = "Mañana a las 6:00 AM con velocidad 60x";
                    break;
                    
                case "lunes-proximo":
                    startTime = LocalDateTime.now().plusDays(7 - LocalDateTime.now().getDayOfWeek().getValue() + 1)
                            .withHour(8).withMinute(0).withSecond(0);
                    accelerationFactor = 120.0;
                    description = "Próximo lunes a las 8:00 AM con velocidad 120x";
                    break;
                    
                case "primer-enero":
                    startTime = LocalDateTime.of(LocalDateTime.now().getYear() + 1, 1, 1, 0, 0, 0);
                    accelerationFactor = 360.0;
                    description = "1 de enero próximo año con velocidad 360x";
                    break;
                    
                default:
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("success", false);
                    errorResponse.put("message", "Preset no válido");
                    errorResponse.put("timestamp", LocalDateTime.now());
                    errorResponse.put("presetsDisponibles", List.of(
                        "hoy-madrugada", "hoy-manana", "manana-madrugada", 
                        "lunes-proximo", "primer-enero"
                    ));
                    
                    return ResponseEntity.badRequest().body(errorResponse);
            }
            
            // Aplicar configuración
            simulationTimeService.setSimulationStartTime(startTime);
            simulationTimeService.setAccelerationFactor(accelerationFactor);
            simulationTimeService.startSimulation();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", description + " - Simulación iniciada");
            response.put("timestamp", LocalDateTime.now());
            response.put("preset", preset);
            response.put("startTime", startTime);
            response.put("accelerationFactor", accelerationFactor);
            response.put("state", simulationTimeService.getCurrentState());
            
            logger.info("🎯 Simulación iniciada con preset '{}': {} a {}x velocidad", 
                    preset, startTime, accelerationFactor);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error iniciando simulación con preset: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error iniciando simulación con preset: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.status(500).body(response);
        }
    }
}