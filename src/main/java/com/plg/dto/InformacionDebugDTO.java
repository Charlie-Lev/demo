package com.plg.dto;

import com.plg.service.PlanificadorRutasService;
import lombok.Data;
import java.util.Map;

@Data
public class InformacionDebugDTO {
    private PlanificadorRutasService.EstadisticasPlanificacionRutas estadisticasAEstrella;
    private Map<String, Object> estadisticasPlanificadorPedidos;
    private int obstaculosActivos;
}