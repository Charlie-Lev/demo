package com.plg.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.plg.domain.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 📤 DTO para respuesta de planificación general
 */
@Data
public class PlanificacionGeneralResponse {
    
    // Estado general
    private boolean exitoso;
    private String mensajeError;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime tiempoSimulacion;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;
    
    private long tiempoEjecucionMs;
    
    // Métricas principales
    private int camionesAsignados;
    private int rutasCalculadas;
    private int pedidosAtendidos;
    private double distanciaTotalKm;
    private double tiempoTotalHoras;
    
    // Estadísticas detalladas
    private Map<String, Object> estadisticas;
    
    // Resultados detallados (opcional - solo si se requieren)
    private Map<Camion, AsignacionCamion> asignaciones;
    private Map<Camion, RutaOptimizada> rutas;
}
