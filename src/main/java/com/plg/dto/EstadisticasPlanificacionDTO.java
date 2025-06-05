package com.plg.dto;

import lombok.Data;

@Data
public class EstadisticasPlanificacionDTO {
    private int camionesUtilizados;
    private int totalEntregas;
    private double utilizacionPromedioVehiculos;
    private int rutasCalculadas;
    private double distanciaTotalKm;
    private double tiempoTotalEstimadoHoras;
}
