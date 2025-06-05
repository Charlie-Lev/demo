package com.plg.service;

import com.plg.domain.*;
import java.util.List;
import java.util.Map;

/**
 * Servicio para planificar y asignar pedidos a camiones de manera óptima
 */
public interface PlanificadorPedidosService {

    Map<Camion, AsignacionCamion> planificarPedidos(List<Pedido> pedidos, List<Camion> camiones);
    List<Entrega> asignarPedido(Pedido pedido, List<AsignacionCamion> asignaciones);
    int calcularPrioridad(Pedido pedido);
    List<Entrega> fragmentarPedido(Pedido pedido, List<Double> volumenesDisponibles);
    List<AsignacionCamion> optimizarAsignaciones(List<AsignacionCamion> asignaciones);
    Map<String, Object> obtenerEstadisticasPlanificacion(List<AsignacionCamion> asignaciones);
    void reiniciar();
}