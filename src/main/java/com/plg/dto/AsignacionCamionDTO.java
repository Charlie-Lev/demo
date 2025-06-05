package com.plg.dto;

import lombok.Data;
import java.util.List;

@Data
public class AsignacionCamionDTO {
    private Integer camionId;
    private String camionCodigo;
    private int numeroEntregas;
    private double capacidadUtilizada;
    private double capacidadTotal;
    private double porcentajeUtilizacion;
    private List<EntregaDTO> entregas;
}