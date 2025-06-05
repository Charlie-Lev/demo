package com.plg.domain;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class AsignacionCamion {
    
    private Camion camion;
    private List<Entrega> entregas;
    private double capacidadUtilizada;
    private double capacidadDisponible;
    private int prioridadPromedio;
    
    public AsignacionCamion(Camion camion) {
        this.camion = camion;
        this.entregas = new ArrayList<>();
        this.capacidadUtilizada = 0.0;
        this.capacidadDisponible = camion.getMaxCargaM3();
        this.prioridadPromedio = 0;
    }
    
    /**
     * Verifica si se puede agregar un volumen específico al camión
     */
    public boolean puedeAgregar(double volumen) {
        return volumen <= capacidadDisponible;
    }
    
    /**
     * Agrega una entrega al camión y actualiza las métricas
     */
    public boolean agregarEntrega(Entrega entrega) {
        if (!puedeAgregar(entrega.getVolumenEntregadoM3())) {
            return false;
        }
        
        entregas.add(entrega);
        capacidadUtilizada += entrega.getVolumenEntregadoM3();
        capacidadDisponible -= entrega.getVolumenEntregadoM3();
        actualizarPrioridadPromedio();
        
        return true;
    }
    
    /**
     * Calcula el mayor volumen que puede ser agregado
     */
    public double obtenerMaximoVolumenDisponible() {
        return capacidadDisponible;
    }
    
    /**
     * Calcula el porcentaje de utilización del camión
     */
    public double obtenerPorcentajeUtilizacion() {
        return (capacidadUtilizada / camion.getMaxCargaM3()) * 100;
    }
    
    /**
     * Determina si el camión está casi lleno (>80%)
     */
    public boolean estaCasiLleno() {
        return obtenerPorcentajeUtilizacion() > 80.0;
    }
    
    /**
     * Actualiza la prioridad promedio basada en las entregas asignadas
     */
    private void actualizarPrioridadPromedio() {
        if (entregas.isEmpty()) {
            prioridadPromedio = 0;
            return;
        }
        
        int sumaPrioridades = entregas.stream()
            .mapToInt(entrega -> entrega.getPedido().getPrioridad())
            .sum();
        
        prioridadPromedio = sumaPrioridades / entregas.size();
    }
    
    /**
     * Verifica si el camión tiene entregas asignadas
     */
    public boolean tieneEntregas() {
        return !entregas.isEmpty();
    }
    
    /**
     * Obtiene el número total de entregas asignadas
     */
    public int obtenerNumeroEntregas() {
        return entregas.size();
    }
}