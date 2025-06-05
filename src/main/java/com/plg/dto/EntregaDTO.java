package com.plg.dto;

import lombok.Data;

@Data
public class EntregaDTO {
    private Integer pedidoId;
    private int ubicacionX;
    private int ubicacionY;
    private double volumenEntregadoM3;
    private int prioridad;
}
