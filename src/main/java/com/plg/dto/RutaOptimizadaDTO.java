package com.plg.dto;

import com.plg.domain.Punto;
import lombok.Data;
import java.util.List;

@Data
public class RutaOptimizadaDTO {
    private Integer camionId;
    private String camionCodigo;
    private double distanciaTotalKm;
    private double tiempoEstimadoHoras;
    private int numeroEntregas;
    private int numeroSegmentos;
    private boolean rutaViable;
    private List<Punto> secuenciaPuntos;
    private List<SegmentoRutaDTO> segmentosDetallados; // Solo si incluirDebug = true
}