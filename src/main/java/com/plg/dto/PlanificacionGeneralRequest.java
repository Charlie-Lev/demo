package com.plg.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 📥 DTO para solicitud de planificación general
 */
@Data
public class PlanificacionGeneralRequest {
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime tiempoSimulacion;
    
    private ConfiguracionPlanificacion configuracion;
    
    @Data
    public static class ConfiguracionPlanificacion {
        private boolean forzarRecalculo = false;
        private boolean usarCacheRutas = true;
        private String optimizacionNivel = "BALANCEADA"; // RAPIDA, BALANCEADA, PRECISA
        private boolean habilitarParalelizacion = true;
        private int numeroThreads = 4;
        private boolean incluirMetricasDetalladas = false;
    }
}