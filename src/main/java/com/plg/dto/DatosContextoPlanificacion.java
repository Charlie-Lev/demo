package com.plg.dto;

import com.plg.domain.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class DatosContextoPlanificacion {
    private LocalDateTime tiempoSimulacion;
    private List<Almacen> almacenesDisponibles;
    private List<Camion> camionesDisponibles;
    private List<Pedido> pedidosPendientes;
    private List<Obstaculo> obstaculosActivos;
}