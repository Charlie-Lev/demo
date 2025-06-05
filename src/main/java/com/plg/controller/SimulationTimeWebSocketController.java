package com.plg.controller;

import com.plg.dto.SimulationTimeDTO;
import com.plg.service.SimulationTimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller WebSocket para control en tiempo real del tiempo de simulación
 * Maneja comandos bidireccionales entre frontend y backend
 */
@Controller
public class SimulationTimeWebSocketController {
    
    private static final Logger logger = LoggerFactory.getLogger(SimulationTimeWebSocketController.class);
    
    @Autowired
    private SimulationTimeService simulationTimeService;
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    /**
     * Iniciar simulación vía WebSocket
     */
    @MessageMapping("/simulation/start")
    @SendTo("/topic/simulation-response")
    public Map<String, Object> startSimulation() {
        try {
            simulationTimeService.startSimulation();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("action", "START");
            response.put("message", "Simulación iniciada");
            response.put("timestamp", LocalDateTime.now());
            response.put("state", simulationTimeService.getCurrentState());
            
            logger.info("✅ Simulación iniciada vía WebSocket");
            return response;
            
        } catch (Exception e) {
            logger.error("Error iniciando simulación vía WebSocket: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("action", "START");
            response.put("message", "Error iniciando simulación: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            
            return response;
        }
    }
    
    /**
     * Pausar simulación vía WebSocket
     */
    @MessageMapping("/simulation/pause")
    @SendTo("/topic/simulation-response")
    public Map<String, Object> pauseSimulation() {
        try {
            simulationTimeService.pauseSimulation();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("action", "PAUSE");
            response.put("message", "Simulación pausada");
            response.put("timestamp", LocalDateTime.now());
            response.put("state", simulationTimeService.getCurrentState());
            
            logger.info("⏸️ Simulación pausada vía WebSocket");
            return response;
            
        } catch (Exception e) {
            logger.error("Error pausando simulación vía WebSocket: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("action", "PAUSE");
            response.put("message", "Error pausando simulación: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            
            return response;
        }
    }
    
    /**
     * Toggle start/pause vía WebSocket
     */
    @MessageMapping("/simulation/toggle")
    @SendTo("/topic/simulation-response")
    public Map<String, Object> toggleSimulation() {
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
            response.put("action", "TOGGLE");
            response.put("message", newState.isRunning() ? "Simulación iniciada" : "Simulación pausada");
            response.put("timestamp", LocalDateTime.now());
            response.put("previousState", currentState.isRunning());
            response.put("currentState", newState.isRunning());
            response.put("state", newState);
            
            logger.info("🔄 Toggle simulación vía WebSocket: {} → {}", 
                       currentState.isRunning(), newState.isRunning());
            
            return response;
            
        } catch (Exception e) {
            logger.error("Error en toggle vía WebSocket: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("action", "TOGGLE");
            response.put("message", "Error en toggle: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            
            return response;
        }
    }
    
    /**
     * Ajustar factor de aceleración vía WebSocket
     */
    @MessageMapping("/simulation/acceleration")
    @SendTo("/topic/simulation-response")
    public Map<String, Object> setAccelerationFactor(@Payload Map<String, Object> payload) {
        try {
            double factor = ((Number) payload.get("factor")).doubleValue();
            
            simulationTimeService.setAccelerationFactor(factor);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("action", "SET_ACCELERATION");
            response.put("message", String.format("Factor ajustado a %.1fx", factor));
            response.put("timestamp", LocalDateTime.now());
            response.put("newFactor", factor);
            response.put("state", simulationTimeService.getCurrentState());
            
            logger.info("⚡ Factor de aceleración ajustado a {}x vía WebSocket", factor);
            return response;
            
        } catch (Exception e) {
            logger.error("Error ajustando factor vía WebSocket: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("action", "SET_ACCELERATION");
            response.put("message", "Error ajustando factor: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            
            return response;
        }
    }
    
    /**
     * Establecer tiempo específico vía WebSocket
     */
    @MessageMapping("/simulation/settime")
    @SendTo("/topic/simulation-response")
    public Map<String, Object> setSimulationTime(@Payload Map<String, Object> payload) {
        try {
            String timeString = (String) payload.get("time");
            LocalDateTime time = LocalDateTime.parse(timeString);
            
            simulationTimeService.setSimulationTime(time);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("action", "SET_TIME");
            response.put("message", "Tiempo establecido");
            response.put("timestamp", LocalDateTime.now());
            response.put("newSimulationTime", time);
            response.put("state", simulationTimeService.getCurrentState());
            
            logger.info("🕐 Tiempo establecido a {} vía WebSocket", time);
            return response;
            
        } catch (Exception e) {
            logger.error("Error estableciendo tiempo vía WebSocket: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("action", "SET_TIME");
            response.put("message", "Error estableciendo tiempo: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            
            return response;
        }
    }
    
    /**
     * Reiniciar simulación vía WebSocket
     */
    @MessageMapping("/simulation/reset")
    @SendTo("/topic/simulation-response")
    public Map<String, Object> resetSimulation() {
        try {
            simulationTimeService.resetSimulation();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("action", "RESET");
            response.put("message", "Simulación reiniciada");
            response.put("timestamp", LocalDateTime.now());
            response.put("state", simulationTimeService.getCurrentState());
            
            logger.info("🔄 Simulación reiniciada vía WebSocket");
            return response;
            
        } catch (Exception e) {
            logger.error("Error reiniciando simulación vía WebSocket: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("action", "RESET");
            response.put("message", "Error reiniciando simulación: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            
            return response;
        }
    }
    
    /**
     * Aplicar preset de velocidad vía WebSocket
     */
    @MessageMapping("/simulation/preset")
    @SendTo("/topic/simulation-response")
    public Map<String, Object> setSpeedPreset(@Payload Map<String, Object> payload) {
        try {
            String preset = (String) payload.get("preset");
            
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
                    errorResponse.put("action", "SET_PRESET");
                    errorResponse.put("message", "Preset no válido");
                    errorResponse.put("timestamp", LocalDateTime.now());
                    errorResponse.put("validPresets", new String[]{"realtime", "slow", "fast", "veryfast", "ultrafast"});
                    
                    return errorResponse;
            }
            
            simulationTimeService.setAccelerationFactor(factor);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("action", "SET_PRESET");
            response.put("message", description + " activada");
            response.put("timestamp", LocalDateTime.now());
            response.put("preset", preset);
            response.put("factor", factor);
            response.put("description", description);
            response.put("state", simulationTimeService.getCurrentState());
            
            logger.info("🎯 Preset '{}' aplicado ({}x) vía WebSocket", preset, factor);
            return response;
            
        } catch (Exception e) {
            logger.error("Error aplicando preset vía WebSocket: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("action", "SET_PRESET");
            response.put("message", "Error aplicando preset: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            
            return response;
        }
    }
    
    /**
     * Solicitar estado actual vía WebSocket
     */
    @MessageMapping("/simulation/status")
    @SendTo("/topic/simulation-response")
    public Map<String, Object> getSimulationStatus() {
        try {
            SimulationTimeDTO currentState = simulationTimeService.getCurrentState();
            Map<String, Object> metrics = simulationTimeService.getMetrics();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("action", "GET_STATUS");
            response.put("timestamp", LocalDateTime.now());
            response.put("state", currentState);
            response.put("metrics", metrics);
            
            return response;
            
        } catch (Exception e) {
            logger.error("Error obteniendo estado vía WebSocket: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("action", "GET_STATUS");
            response.put("message", "Error obteniendo estado: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            
            return response;
        }
    }
    
    /**
     * Programar planificación automática vía WebSocket
     */
    // @MessageMapping("/simulation/schedule-planning")
    // @SendTo("/topic/simulation-response")
    // public Map<String, Object> schedulePlanning(@Payload Map<String, Object> payload) {
    //     try {
    //         String periodo = (String) payload.get("periodo");
            
    //         simulationTimeService.schedulePlanificacion(periodo);
            
    //         Map<String, Object> response = new HashMap<>();
    //         response.put("success", true);
    //         response.put("action", "SCHEDULE_PLANNING");
    //         response.put("message", "Planificación automática programada");
    //         response.put("timestamp", LocalDateTime.now());
    //         response.put("periodo", periodo);
            
    //         logger.info("📅 Planificación automática programada cada {} vía WebSocket", periodo);
    //         return response;
            
    //     } catch (Exception e) {
    //         logger.error("Error programando planificación vía WebSocket: {}", e.getMessage(), e);
            
    //         Map<String, Object> response = new HashMap<>();
    //         response.put("success", false);
    //         response.put("action", "SCHEDULE_PLANNING");
    //         response.put("message", "Error programando planificación: " + e.getMessage());
    //         response.put("timestamp", LocalDateTime.now());
            
    //         return response;
    //     }
    // }
    
    /**
     * Ping/heartbeat para mantener conexión activa
     */
    @MessageMapping("/simulation/ping")
    @SendTo("/topic/simulation-response")
    public Map<String, Object> ping(@Payload Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("action", "PONG");
        response.put("timestamp", LocalDateTime.now());
        response.put("clientId", payload.get("clientId"));
        response.put("state", simulationTimeService.getCurrentState());
        
        return response;
    }
    
    /**
     * Envío dirigido de notificaciones a clientes específicos
     */
    public void notifyClient(String clientId, String message, Object data) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "NOTIFICATION");
            notification.put("message", message);
            notification.put("data", data);
            notification.put("timestamp", LocalDateTime.now());
            notification.put("targetClient", clientId);
            
            messagingTemplate.convertAndSend("/topic/simulation-notifications/" + clientId, notification);
            
        } catch (Exception e) {
            logger.error("Error enviando notificación a cliente {}: {}", clientId, e.getMessage());
        }
    }
    
    /**
     * Broadcasting de alertas globales
     */
    public void broadcastAlert(String level, String message, Object data) {
        try {
            Map<String, Object> alert = new HashMap<>();
            alert.put("type", "ALERT");
            alert.put("level", level); // INFO, WARNING, ERROR, CRITICAL
            alert.put("message", message);
            alert.put("data", data);
            alert.put("timestamp", LocalDateTime.now());
            alert.put("simulationTime", simulationTimeService.getCurrentState().getSimulationTime());
            
            messagingTemplate.convertAndSend("/topic/simulation-alerts", alert);
            
            logger.info("📢 Alert broadcasted [{}]: {}", level, message);
            
        } catch (Exception e) {
            logger.error("Error broadcasting alert: {}", e.getMessage());
        }
    }
}