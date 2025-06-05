package com.plg.dto;

import com.plg.domain.Punto;
import lombok.Data;
import java.util.List;

@Data
public class SegmentoRutaDTO {
    private Punto origen;
    private Punto destino;
    private String tipoSegmento;
    private double distanciaKm;
    private double tiempoEstimadoHoras;
    private List<Punto> rutaDetallada;
}