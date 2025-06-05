package com.plg.domain;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.plg.domain.enumeration.EstadoSegmento;
import com.plg.domain.enumeration.TipoSegmento;

/**
 * Representa un tramo individual de una ruta entre dos puntos específicos
 * Incluye pathfinding, métricas de consumo y metadatos de ejecución
 */
@Data
public class SegmentoRuta {
    
    private Punto origen;
    private Punto destino;
    private TipoSegmento tipoSegmento;
    
    // Asociaciones opcionales según el tipo de segmento
    private Entrega entrega;        // Si es un segmento de entrega
    private Almacen almacen;        // Si es movimiento hacia almacén
    
    // Pathfinding y navegación
    private List<Punto> rutaDetallada;  // Secuencia de puntos A* 
    private double distanciaKm;
    private int distanciaGrid;          // Distancia en unidades de grid
    
    // Métricas temporales y energéticas
    private double tiempoEstimadoHoras;
    private double velocidadPromedio;   // km/h
    private double pesoInicialTon;
    private double pesoFinalTon;
    private double combustibleConsumidoGalones;
    
    // Control de ejecución
    private EstadoSegmento estado;
    private LocalDateTime fechaHoraInicioPlaneada;
    private LocalDateTime fechaHoraFinPlaneada;
    private LocalDateTime fechaHoraInicioReal;
    private LocalDateTime fechaHoraFinReal;
    
    // Metadatos
    private int ordenEnRuta;
    private boolean requiereValidacionEspecial;
    private String observaciones;
    
    public SegmentoRuta() {
        this.rutaDetallada = new ArrayList<>();
        this.estado = EstadoSegmento.PENDIENTE;
        this.velocidadPromedio = 50.0; // km/h por defecto
    }
    
    public SegmentoRuta(Punto origen, Punto destino, TipoSegmento tipo) {
        this();
        this.origen = origen;
        this.destino = destino;
        this.tipoSegmento = tipo;
    }
    
    /**
     * Constructor para segmento de entrega
     */
    public static SegmentoRuta crearSegmentoEntrega(Punto origen, Entrega entrega, int orden) {
        Punto destino = new Punto(
            entrega.getPedido().getUbicacionX(), 
            entrega.getPedido().getUbicacionY()
        );
        
        SegmentoRuta segmento = new SegmentoRuta(origen, destino, TipoSegmento.ENTREGA);
        segmento.setEntrega(entrega);
        segmento.setOrdenEnRuta(orden);
        
        return segmento;
    }
    
    /**
     * Constructor para segmento de movimiento a almacén
     */
    public static SegmentoRuta crearSegmentoAlmacen(Punto origen, Almacen almacen, int orden) {
        Punto destino = new Punto(almacen.getX(), almacen.getY());
        
        SegmentoRuta segmento = new SegmentoRuta(origen, destino, TipoSegmento.RETORNO_ALMACEN);
        segmento.setAlmacen(almacen);
        segmento.setOrdenEnRuta(orden);
        
        return segmento;
    }
    
    /**
     * Constructor para segmento de movimiento simple
     */
    public static SegmentoRuta crearSegmentoMovimiento(Punto origen, Punto destino, int orden) {
        SegmentoRuta segmento = new SegmentoRuta(origen, destino, TipoSegmento.MOVIMIENTO);
        segmento.setOrdenEnRuta(orden);
        
        return segmento;
    }
    
    /**
     * Establece la ruta detallada calculada por A* y actualiza métricas
     */
    public void establecerRutaDetallada(List<Punto> rutaCalculada) {
        this.rutaDetallada = new ArrayList<>(rutaCalculada);
        this.distanciaGrid = calcularDistanciaGrid();
        actualizarMetricas();
    }
    
    /**
     * Calcula la distancia en unidades de grid (Manhattan distance)
     */
    private int calcularDistanciaGrid() {
        if (rutaDetallada.size() < 2) {
            // Fallback a distancia Manhattan directa
            return Math.abs(destino.getX() - origen.getX()) + 
                   Math.abs(destino.getY() - origen.getY());
        }
        
        int distanciaTotal = 0;
        for (int i = 0; i < rutaDetallada.size() - 1; i++) {
            Punto actual = rutaDetallada.get(i);
            Punto siguiente = rutaDetallada.get(i + 1);
            
            distanciaTotal += Math.abs(siguiente.getX() - actual.getX()) + 
                            Math.abs(siguiente.getY() - actual.getY());
        }
        
        return distanciaTotal;
    }
    
    /**
     * Actualiza métricas basadas en distancia y parámetros
     */
    private void actualizarMetricas() {
        // Convertir distancia de grid a kilómetros (factor configurable)
        double factorConversionKm = 0.5; // 1 unidad grid = 0.5 km
        this.distanciaKm = distanciaGrid * factorConversionKm;
        
        // Calcular tiempo estimado
        this.tiempoEstimadoHoras = distanciaKm / velocidadPromedio;
        
        // Agregar tiempo de entrega si aplica
        if (tipoSegmento == TipoSegmento.ENTREGA) {
            this.tiempoEstimadoHoras += 0.25; // 15 minutos para descarga
        }
    }
    
    /**
     * Calcula el consumo de combustible para este segmento
     * Fórmula: Distancia[Km] × Peso[Ton] ÷ 180
     */
    public double calcularConsumoEstimado() {
        if (pesoInicialTon <= 0 || distanciaKm <= 0) {
            return 0.0;
        }
        
        // Usar peso promedio del segmento para mejor precisión
        double pesoProm = (pesoInicialTon + pesoFinalTon) / 2.0;
        return (distanciaKm * pesoProm) / 180.0;
    }
    
    /**
     * Verifica si el segmento es ejecutable con el combustible disponible
     */
    public boolean esEjecutableConCombustible(double combustibleDisponible) {
        double consumoEstimado = calcularConsumoEstimado();
        double margenSeguridad = consumoEstimado * 0.1; // 10% de margen
        
        return combustibleDisponible >= (consumoEstimado + margenSeguridad);
    }
    
    /**
     * Obtiene el punto intermedio del segmento (útil para averías)
     */
    public Punto obtenerPuntoIntermedio(double porcentaje) {
        if (rutaDetallada.isEmpty()) {
            // Interpolación simple entre origen y destino
            int x = origen.getX() + (int)((destino.getX() - origen.getX()) * porcentaje);
            int y = origen.getY() + (int)((destino.getY() - origen.getY()) * porcentaje);
            return new Punto(x, y);
        }
        
        // Encontrar punto en la ruta detallada según porcentaje
        int indiceObjetivo = (int)(rutaDetallada.size() * porcentaje);
        indiceObjetivo = Math.min(indiceObjetivo, rutaDetallada.size() - 1);
        indiceObjetivo = Math.max(indiceObjetivo, 0);
        
        return rutaDetallada.get(indiceObjetivo);
    }
    
    /**
     * Marca el segmento como iniciado
     */
    public void iniciarEjecucion() {
        this.estado = EstadoSegmento.EN_EJECUCION;
        this.fechaHoraInicioReal = LocalDateTime.now();
    }
    
    /**
     * Marca el segmento como completado
     */
    public void completarEjecucion() {
        this.estado = EstadoSegmento.COMPLETADO;
        this.fechaHoraFinReal = LocalDateTime.now();
        
        // Calcular combustible realmente consumido si se conocen pesos reales
        if (pesoInicialTon > 0 && pesoFinalTon > 0) {
            this.combustibleConsumidoGalones = calcularConsumoEstimado();
        }
    }
    
    /**
     * Verifica si este segmento tiene mayor prioridad que otro
     */
    public boolean tieneMayorPrioridadQue(SegmentoRuta otro) {
        if (this.tipoSegmento == TipoSegmento.ENTREGA && otro.tipoSegmento == TipoSegmento.ENTREGA) {
            // Comparar prioridades de pedidos
            return this.entrega.getPedido().getPrioridad() > otro.entrega.getPedido().getPrioridad();
        }
        
        // Orden por tipo: ENTREGA > MOVIMIENTO > RETORNO_ALMACEN
        return this.tipoSegmento.getPrioridad() > otro.tipoSegmento.getPrioridad();
    }
    
    /**
     * Crea una copia del segmento para optimización
     */
    public SegmentoRuta clonar() {
        SegmentoRuta copia = new SegmentoRuta();
        
        copia.setOrigen(new Punto(origen.getX(), origen.getY()));
        copia.setDestino(new Punto(destino.getX(), destino.getY()));
        copia.setTipoSegmento(tipoSegmento);
        copia.setEntrega(entrega);
        copia.setAlmacen(almacen);
        copia.setRutaDetallada(new ArrayList<>(rutaDetallada));
        copia.setDistanciaKm(distanciaKm);
        copia.setDistanciaGrid(distanciaGrid);
        copia.setTiempoEstimadoHoras(tiempoEstimadoHoras);
        copia.setPesoInicialTon(pesoInicialTon);
        copia.setPesoFinalTon(pesoFinalTon);
        copia.setOrdenEnRuta(ordenEnRuta);
        
        return copia;
    }
    
    @Override
    public String toString() {
        return String.format("SegmentoRuta[%s: (%d,%d)→(%d,%d) %.2fkm %s]",
            tipoSegmento,
            origen.getX(), origen.getY(),
            destino.getX(), destino.getY(),
            distanciaKm,
            estado);
    }
}



