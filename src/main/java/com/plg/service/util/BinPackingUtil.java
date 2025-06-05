package com.plg.service.util;

import com.plg.domain.AsignacionCamion;
import com.plg.domain.Pedido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Utilidades para algoritmos de empaquetado (Bin Packing) 
 * REESTRUCTURADO COMPLETAMENTE - Sin HashMap de objetos complejos
 */
public class BinPackingUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(BinPackingUtil.class);
    
    /**
     * Resultado de asignación simplificado
     */
    public static class ResultadoAsignacion {
        private final int camionId;
        private final List<Pedido> pedidosAsignados;
        private double capacidadUtilizada;
        private double capacidadDisponible;
        
        public ResultadoAsignacion(int camionId, double capacidadMaxima) {
            this.camionId = camionId;
            this.pedidosAsignados = new ArrayList<>();
            this.capacidadUtilizada = 0.0;
            this.capacidadDisponible = capacidadMaxima;
        }
        
        public boolean puedeAgregar(double volumen) {
            return volumen <= capacidadDisponible;
        }
        
        public void agregarPedido(Pedido pedido) {
            pedidosAsignados.add(pedido);
            capacidadUtilizada += pedido.getVolumenM3();
            capacidadDisponible -= pedido.getVolumenM3();
        }
        
        // Getters
        public int getCamionId() { return camionId; }
        public List<Pedido> getPedidosAsignados() { return new ArrayList<>(pedidosAsignados); }
        public double getCapacidadUtilizada() { return capacidadUtilizada; }
        public double getCapacidadDisponible() { return capacidadDisponible; }
        public boolean tieneAsignaciones() { return !pedidosAsignados.isEmpty(); }
        public double getPorcentajeUtilizacion(double capacidadMaxima) {
            return (capacidadUtilizada / capacidadMaxima) * 100.0;
        }
    }
    
    /**
     * Algoritmo First Fit: asigna cada pedido al primer camión donde quepa
     */
    public static List<ResultadoAsignacion> firstFit(List<Pedido> pedidos, List<AsignacionCamion> camiones) {
        logger.debug("Ejecutando First Fit para {} pedidos", pedidos.size());
        
        List<ResultadoAsignacion> resultados = inicializarResultados(camiones);
        
        for (Pedido pedido : pedidos) {
            boolean asignado = false;
            
            for (ResultadoAsignacion resultado : resultados) {
                if (resultado.puedeAgregar(pedido.getVolumenM3())) {
                    resultado.agregarPedido(pedido);
                    asignado = true;
                    break;
                }
            }
            
            if (!asignado) {
                logger.warn("Pedido {} no pudo ser asignado con First Fit", pedido.getId());
            }
        }
        
        return resultados;
    }
    
    /**
     * Algoritmo Best Fit: asigna cada pedido al camión con menor espacio libre donde aún quepa
     */
    public static List<ResultadoAsignacion> bestFit(List<Pedido> pedidos, List<AsignacionCamion> camiones) {
        logger.debug("Ejecutando Best Fit para {} pedidos", pedidos.size());
        
        List<ResultadoAsignacion> resultados = inicializarResultados(camiones);
        
        for (Pedido pedido : pedidos) {
            ResultadoAsignacion mejorOpcion = null;
            double menorEspacioLibre = Double.MAX_VALUE;
            
            for (ResultadoAsignacion resultado : resultados) {
                if (resultado.puedeAgregar(pedido.getVolumenM3())) {
                    double espacioRestante = resultado.getCapacidadDisponible() - pedido.getVolumenM3();
                    
                    if (espacioRestante < menorEspacioLibre) {
                        menorEspacioLibre = espacioRestante;
                        mejorOpcion = resultado;
                    }
                }
            }
            
            if (mejorOpcion != null) {
                mejorOpcion.agregarPedido(pedido);
            } else {
                logger.warn("Pedido {} no pudo ser asignado con Best Fit", pedido.getId());
            }
        }
        
        return resultados;
    }
    
    /**
     * Algoritmo Best Fit Decreasing: ordena pedidos por volumen descendente y aplica Best Fit
     */
    public static List<ResultadoAsignacion> bestFitDecreasing(List<Pedido> pedidos, List<AsignacionCamion> camiones) {
        logger.debug("Ejecutando Best Fit Decreasing para {} pedidos", pedidos.size());
        
        // Ordenar pedidos por volumen descendente
        List<Pedido> pedidosOrdenados = new ArrayList<>(pedidos);
        pedidosOrdenados.sort((p1, p2) -> Double.compare(p2.getVolumenM3(), p1.getVolumenM3()));
        
        return bestFit(pedidosOrdenados, camiones);
    }
    
    /**
     * Algoritmo Worst Fit: asigna cada pedido al camión con mayor espacio libre
     */
    public static List<ResultadoAsignacion> worstFit(List<Pedido> pedidos, List<AsignacionCamion> camiones) {
        logger.debug("Ejecutando Worst Fit para {} pedidos", pedidos.size());
        
        List<ResultadoAsignacion> resultados = inicializarResultados(camiones);
        
        for (Pedido pedido : pedidos) {
            ResultadoAsignacion mejorOpcion = null;
            double mayorEspacioLibre = -1;
            
            for (ResultadoAsignacion resultado : resultados) {
                if (resultado.puedeAgregar(pedido.getVolumenM3())) {
                    if (resultado.getCapacidadDisponible() > mayorEspacioLibre) {
                        mayorEspacioLibre = resultado.getCapacidadDisponible();
                        mejorOpcion = resultado;
                    }
                }
            }
            
            if (mejorOpcion != null) {
                mejorOpcion.agregarPedido(pedido);
            } else {
                logger.warn("Pedido {} no pudo ser asignado con Worst Fit", pedido.getId());
            }
        }
        
        return resultados;
    }
    
    /**
     * Encuentra la mejor asignación comparando múltiples algoritmos
     */
    public static Map<AsignacionCamion, List<Pedido>> encontrarMejorAsignacion(
            List<Pedido> pedidos, List<AsignacionCamion> camiones) {
        
        logger.info("Evaluando múltiples algoritmos de bin packing para {} pedidos", pedidos.size());
        
        Map<String, List<ResultadoAsignacion>> resultados = new HashMap<>();
        
        // Probar diferentes algoritmos
        resultados.put("FirstFit", firstFit(pedidos, camiones));
        resultados.put("BestFit", bestFit(pedidos, camiones));
        resultados.put("BestFitDecreasing", bestFitDecreasing(pedidos, camiones));
        resultados.put("WorstFit", worstFit(pedidos, camiones));
        
        // Evaluar cada resultado
        String mejorAlgoritmo = null;
        double mejorEficiencia = -1;
        
        for (Map.Entry<String, List<ResultadoAsignacion>> entry : resultados.entrySet()) {
            double eficiencia = calcularEficiencia(entry.getValue(), camiones);
            
            logger.debug("Algoritmo {}: eficiencia = {:.2f}%", entry.getKey(), eficiencia);
            
            if (eficiencia > mejorEficiencia) {
                mejorEficiencia = eficiencia;
                mejorAlgoritmo = entry.getKey();
            }
        }
        
        logger.info("Mejor algoritmo: {} con eficiencia de {:.2f}%", mejorAlgoritmo, mejorEficiencia);
        
        // Convertir el mejor resultado al formato esperado
        return convertirResultado(resultados.get(mejorAlgoritmo), camiones);
    }
    
    /**
     * Calcula la eficiencia de una asignación (0-100%)
     */
    public static double calcularEficiencia(List<ResultadoAsignacion> resultados, List<AsignacionCamion> camionesOriginales) {
        if (resultados == null || resultados.isEmpty() || camionesOriginales == null) {
            return 0.0;
        }
        
        List<ResultadoAsignacion> resultadosUsados = resultados.stream()
            .filter(ResultadoAsignacion::tieneAsignaciones)
            .collect(Collectors.toList());
        
        if (resultadosUsados.isEmpty()) {
            return 0.0;
        }
        
        double capacidadTotalUsada = 0.0;
        double capacidadUtilizadaTotal = 0.0;
        
        // Mapear resultados con camiones originales por ID
        Map<Integer, Integer> capacidadesPorId = camionesOriginales.stream()
            .collect(Collectors.toMap(
                c -> c.getCamion().getId(),
                c -> c.getCamion().getMaxCargaM3()
            ));
        
        for (ResultadoAsignacion resultado : resultadosUsados) {
            Integer capacidadMaxima = capacidadesPorId.get(resultado.getCamionId());
            if (capacidadMaxima != null) {
                capacidadTotalUsada += capacidadMaxima;
                capacidadUtilizadaTotal += resultado.getCapacidadUtilizada();
            }
        }
        
        return capacidadTotalUsada > 0 ? (capacidadUtilizadaTotal / capacidadTotalUsada) * 100 : 0;
    }
    
    /**
     * Calcula el desperdicio total de una asignación
     */
    public static double calcularDesperdicio(List<ResultadoAsignacion> resultados) {
        return resultados.stream()
            .filter(ResultadoAsignacion::tieneAsignaciones)
            .mapToDouble(ResultadoAsignacion::getCapacidadDisponible)
            .sum();
    }
    
    // Métodos auxiliares privados
    
    private static List<ResultadoAsignacion> inicializarResultados(List<AsignacionCamion> camiones) {
        return camiones.stream()
            .map(asignacion -> new ResultadoAsignacion(
                asignacion.getCamion().getId(),
                asignacion.getCamion().getMaxCargaM3().doubleValue()))
            .collect(Collectors.toList());
    }
    
    private static Map<AsignacionCamion, List<Pedido>> convertirResultado(
            List<ResultadoAsignacion> resultados, List<AsignacionCamion> camionesOriginales) {
        
        Map<AsignacionCamion, List<Pedido>> resultado = new HashMap<>();
        
        // Crear mapa de IDs a AsignacionCamion originales
        Map<Integer, AsignacionCamion> asignacionesPorId = camionesOriginales.stream()
            .collect(Collectors.toMap(
                c -> c.getCamion().getId(),
                c -> c
            ));
        
        for (ResultadoAsignacion res : resultados) {
            if (res.tieneAsignaciones()) {
                AsignacionCamion asignacionOriginal = asignacionesPorId.get(res.getCamionId());
                if (asignacionOriginal != null) {
                    // Crear nueva instancia para evitar modificar la original
                    AsignacionCamion nuevaAsignacion = new AsignacionCamion(asignacionOriginal.getCamion());
                    nuevaAsignacion.setCapacidadUtilizada(res.getCapacidadUtilizada());
                    nuevaAsignacion.setCapacidadDisponible(res.getCapacidadDisponible());
                    
                    resultado.put(nuevaAsignacion, res.getPedidosAsignados());
                }
            }
        }
        
        return resultado;
    }
}