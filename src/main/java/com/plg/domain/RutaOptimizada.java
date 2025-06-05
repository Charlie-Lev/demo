package com.plg.domain;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.plg.domain.enumeration.EstadoRuta;
import com.plg.domain.enumeration.EstadoSegmento;
import com.plg.domain.enumeration.TipoSegmento;

/**
 * Representa una ruta optimizada completa para un camión específico
 * Incluye secuencia de movimientos, métricas y metadatos
 */
@Data
public class RutaOptimizada {
    
    private Camion camion;
    private List<SegmentoRuta> segmentos;
    private Almacen almacenOrigen;
    private Almacen almacenDestino;
    
    // Métricas de la ruta
    private double distanciaTotalKm;
    private double tiempoEstimadoHoras;
    private double combustibleNecesarioGalones;
    private double combustibleRestanteGalones;
    private double pesoInicialTon;
    private double pesoFinalTon;
    
    // Metadatos
    private LocalDateTime fechaHoraInicio;
    private LocalDateTime fechaHoraFinEstimada;
    private int numeroEntregas;
    private boolean rutaViable;
    private String observaciones;
    
    // Estado de ejecución
    private EstadoRuta estado;
    private double porcentajeCompletado;
    private SegmentoRuta segmentoActual;
    
    public RutaOptimizada() {
        this.segmentos = new ArrayList<>();
        this.estado = EstadoRuta.PLANIFICADA;
        this.porcentajeCompletado = 0.0;
        this.rutaViable = true;
    }
    
    public RutaOptimizada(Camion camion, Almacen almacenOrigen) {
        this();
        this.camion = camion;
        this.almacenOrigen = almacenOrigen;
        this.almacenDestino = almacenOrigen; // Por defecto retorna al mismo almacén
    }
    
    /**
     * Agrega un segmento a la ruta y actualiza métricas
     */
    public void agregarSegmento(SegmentoRuta segmento) {
        segmentos.add(segmento);
        actualizarMetricas();
    }
    
    /**
     * Obtiene la secuencia de puntos de la ruta completa
     */
    public List<Punto> obtenerSecuenciaPuntos() {
        List<Punto> puntos = new ArrayList<>();
        
        if (!segmentos.isEmpty()) {
            // Agregar punto de inicio del primer segmento
            puntos.add(segmentos.get(0).getOrigen());
            
            // Agregar punto de destino de cada segmento
            for (SegmentoRuta segmento : segmentos) {
                puntos.add(segmento.getDestino());
            }
        }
        
        return puntos;
    }
    
    /**
     * Obtiene todas las entregas de la ruta en orden
     */
    public List<Entrega> obtenerEntregasEnOrden() {
        return segmentos.stream()
            .filter(s -> s.getTipoSegmento() == TipoSegmento.ENTREGA)
            .map(SegmentoRuta::getEntrega)
            .toList();
    }
    
    /**
     * Verifica si la ruta es factible en términos de combustible
     */
    public boolean esRutaFactible() {
        return rutaViable && combustibleRestanteGalones >= 0;
    }
    
    /**
     * Calcula el porcentaje de utilización del camión
     */
    public double calcularPorcentajeUtilizacion() {
        if (camion == null || camion.getMaxCargaM3() == 0) {
            return 0.0;
        }
        
        double volumenTotal = obtenerEntregasEnOrden().stream()
            .mapToDouble(Entrega::getVolumenEntregadoM3)
            .sum();
            
        return (volumenTotal / camion.getMaxCargaM3()) * 100.0;
    }
    
    /**
     * Obtiene el siguiente segmento a ejecutar
     */
    public SegmentoRuta obtenerSiguienteSegmento() {
        return segmentos.stream()
            .filter(s -> s.getEstado() == EstadoSegmento.PENDIENTE)
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Marca un segmento como completado y actualiza el progreso
     */
    public void completarSegmento(SegmentoRuta segmento) {
        segmento.setEstado(EstadoSegmento.COMPLETADO);
        segmento.setFechaHoraFinReal(LocalDateTime.now());
        actualizarProgreso();
    }
    
    /**
     * Actualiza las métricas generales de la ruta
     */
    private void actualizarMetricas() {
        distanciaTotalKm = segmentos.stream()
            .mapToDouble(SegmentoRuta::getDistanciaKm)
            .sum();
            
        tiempoEstimadoHoras = segmentos.stream()
            .mapToDouble(SegmentoRuta::getTiempoEstimadoHoras)
            .sum();
            
        numeroEntregas = (int) segmentos.stream()
            .filter(s -> s.getTipoSegmento() == TipoSegmento.ENTREGA)
            .count();
    }
    
    /**
     * Actualiza el porcentaje de progreso de la ruta
     */
    private void actualizarProgreso() {
        if (segmentos.isEmpty()) {
            porcentajeCompletado = 0.0;
            return;
        }
        
        long segmentosCompletados = segmentos.stream()
            .filter(s -> s.getEstado() == EstadoSegmento.COMPLETADO)
            .count();
            
        porcentajeCompletado = (double) segmentosCompletados / segmentos.size() * 100.0;
        
        // Actualizar estado general
        if (porcentajeCompletado == 100.0) {
            estado = EstadoRuta.COMPLETADA;
        } else if (porcentajeCompletado > 0.0) {
            estado = EstadoRuta.EN_EJECUCION;
        }
    }
    
    /**
     * Crea una copia de la ruta para optimización
     */
    public RutaOptimizada clonar() {
        RutaOptimizada copia = new RutaOptimizada(camion, almacenOrigen);
        copia.setAlmacenDestino(almacenDestino);
        
        // Clonar segmentos
        for (SegmentoRuta segmento : segmentos) {
            copia.agregarSegmento(segmento.clonar());
        }
        
        return copia;
    }
}

