package com.plg.domain;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.plg.domain.enumeration.TipoError;

/**
 * Resultado del algoritmo A* para pathfinding individual
 * Contiene la ruta calculada, métricas de rendimiento y metadatos
 */
@Data
public class ResultadoAEstrella {
    
    private Punto origen;
    private Punto destino;
    private List<Punto> rutaEncontrada;
    private boolean rutaExiste;
    private boolean calculoExitoso;
    
    // Métricas de la ruta
    private int distanciaGrid;           // Distancia en unidades de grid
    private double distanciaKm;          // Distancia en kilómetros
    private int numeroMovimientos;       // Cantidad de pasos en la ruta
    
    // Métricas de rendimiento del algoritmo
    private long tiempoCalculoMs;        // Tiempo de cálculo en millisegundos
    private int nodosExplorados;         // Nodos visitados durante la búsqueda
    private int nodosEnCola;             // Nodos que quedaron en cola al finalizar
    private int memoriaUtilizadaKb;      // Memoria aproximada utilizada
    
    // Control de calidad
    private boolean rutaOptima;         // Si la ruta encontrada es óptima
    private double factorOptimalidad;   // Qué tan cerca está del óptimo (1.0 = óptimo)
    private boolean timeoutAlcanzado;   // Si se alcanzó el timeout
    private boolean limiteProfundidadAlcanzado;
    
    // Metadatos del cálculo
    private LocalDateTime fechaHoraCalculo;
    private String algoritmoUtilizado;  // "A*", "A* con cache", etc.
    private boolean resultadoDesdeCache;
    private String version;             // Versión del algoritmo
    
    // Información de error (si aplicable)
    private String mensajeError;
    private TipoError tipoError;
    private Punto puntoProblematico;
    
    // Información adicional para debugging
    private List<Punto> nodosExploradosDetalle;
    private String trazaEjecucion;
    
    public ResultadoAEstrella() {
        this.rutaEncontrada = new ArrayList<>();
        this.nodosExploradosDetalle = new ArrayList<>();
        this.calculoExitoso = false;
        this.rutaExiste = false;
        this.rutaOptima = false;
        this.resultadoDesdeCache = false;
        this.fechaHoraCalculo = LocalDateTime.now();
        this.algoritmoUtilizado = "A*";
        this.version = "1.0";
    }
    
    public ResultadoAEstrella(Punto origen, Punto destino) {
        this();
        this.origen = origen.clonar();
        this.destino = destino.clonar();
    }
    
    /**
     * Constructor para resultado exitoso
     */
    public static ResultadoAEstrella exitoso(Punto origen, Punto destino, List<Punto> ruta) {
        ResultadoAEstrella resultado = new ResultadoAEstrella(origen, destino);
        resultado.setRutaEncontrada(new ArrayList<>(ruta));
        resultado.setRutaExiste(true);
        resultado.setCalculoExitoso(true);
        resultado.calcularMetricasRuta();
        
        return resultado;
    }
    
    /**
     * Constructor para resultado fallido
     */
    public static ResultadoAEstrella fallido(Punto origen, Punto destino, TipoError error, String mensaje) {
        ResultadoAEstrella resultado = new ResultadoAEstrella(origen, destino);
        resultado.setRutaExiste(false);
        resultado.setCalculoExitoso(false);
        resultado.setTipoError(error);
        resultado.setMensajeError(mensaje);
        
        return resultado;
    }
    
    /**
     * Constructor para resultado desde cache
     */
    public static ResultadoAEstrella desdeCache(Punto origen, Punto destino, List<Punto> ruta) {
        ResultadoAEstrella resultado = exitoso(origen, destino, ruta);
        resultado.setResultadoDesdeCache(true);
        resultado.setAlgoritmoUtilizado("A* con cache");
        resultado.setTiempoCalculoMs(0); // Cache hit es instantáneo
        
        return resultado;
    }
    
    /**
     * Establece la ruta encontrada y calcula métricas automáticamente
     */
    public void establecerRutaEncontrada(List<Punto> ruta) {
        this.rutaEncontrada = new ArrayList<>(ruta);
        this.rutaExiste = !ruta.isEmpty();
        calcularMetricasRuta();
    }
    
    /**
     * Calcula automáticamente las métricas de la ruta
     */
    private void calcularMetricasRuta() {
        if (rutaEncontrada.isEmpty()) {
            distanciaGrid = 0;
            distanciaKm = 0.0;
            numeroMovimientos = 0;
            return;
        }
        
        // Calcular distancia total
        distanciaGrid = 0;
        numeroMovimientos = rutaEncontrada.size() - 1;
        
        for (int i = 0; i < rutaEncontrada.size() - 1; i++) {
            Punto actual = rutaEncontrada.get(i);
            Punto siguiente = rutaEncontrada.get(i + 1);
            distanciaGrid += actual.distanciaManhattanHasta(siguiente);
        }
        
        // Convertir a kilómetros (factor configurable)
        double factorConversionKm = 0.5; // 1 unidad grid = 0.5 km
        distanciaKm = distanciaGrid * factorConversionKm;
        
        // Evaluar optimalidad (distancia Manhattan directa como referencia)
        int distanciaOptima = origen.distanciaManhattanHasta(destino);
        factorOptimalidad = distanciaOptima > 0 ? (double) distanciaOptima / distanciaGrid : 1.0;
        rutaOptima = factorOptimalidad >= 0.95; // Considerar óptimo si está dentro del 5%
    }
    
    /**
     * Marca el cálculo como completado y registra métricas de rendimiento
     */
    public void completarCalculo(long tiempoInicio, int nodosExplorados, int nodosEnCola) {
        this.tiempoCalculoMs = System.currentTimeMillis() - tiempoInicio;
        this.nodosExplorados = nodosExplorados;
        this.nodosEnCola = nodosEnCola;
        this.calculoExitoso = true;
        
        // Estimar memoria utilizada (aproximación)
        this.memoriaUtilizadaKb = (nodosExplorados * 32 + rutaEncontrada.size() * 16) / 1024;
    }
    
    /**
     * Verifica si el resultado es válido para uso
     */
    public boolean esResultadoValido() {
        return calculoExitoso && rutaExiste && !rutaEncontrada.isEmpty() && 
               rutaEncontrada.get(0).equals(origen) && 
               rutaEncontrada.get(rutaEncontrada.size() - 1).equals(destino);
    }
    
    /**
     * Obtiene un resumen del rendimiento del cálculo
     */
    public String obtenerResumenRendimiento() {
        if (resultadoDesdeCache) {
            return "Cache HIT (instantáneo)";
        }
        
        return String.format("A*: %dms, %d nodos, %.1f%% óptimo", 
            tiempoCalculoMs, nodosExplorados, factorOptimalidad * 100);
    }
    
    /**
     * Obtiene estadísticas detalladas como mapa
     */
    public java.util.Map<String, Object> obtenerEstadisticasDetalladas() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        
        stats.put("origen", origen.toString());
        stats.put("destino", destino.toString());
        stats.put("rutaExiste", rutaExiste);
        stats.put("calculoExitoso", calculoExitoso);
        stats.put("distanciaGrid", distanciaGrid);
        stats.put("distanciaKm", distanciaKm);
        stats.put("numeroMovimientos", numeroMovimientos);
        stats.put("tiempoCalculoMs", tiempoCalculoMs);
        stats.put("nodosExplorados", nodosExplorados);
        stats.put("memoriaUtilizadaKb", memoriaUtilizadaKb);
        stats.put("rutaOptima", rutaOptima);
        stats.put("factorOptimalidad", factorOptimalidad);
        stats.put("resultadoDesdeCache", resultadoDesdeCache);
        stats.put("algoritmoUtilizado", algoritmoUtilizado);
        
        if (tipoError != null) {
            stats.put("error", tipoError.toString());
            stats.put("mensajeError", mensajeError);
        }
        
        return stats;
    }
    
    /**
     * Verifica si la ruta es eficiente en términos de rendimiento
     */
    public boolean esCalculoEficiente() {
        // Criterios de eficiencia
        boolean tiempoRazonable = tiempoCalculoMs < 100; // Menos de 100ms
        boolean memoriaRazonable = memoriaUtilizadaKb < 1024; // Menos de 1MB
        boolean exploracionRazonable = nodosExplorados < 1000; // Menos de 1000 nodos
        
        return tiempoRazonable && memoriaRazonable && exploracionRazonable;
    }
    
    /**
     * Agrega información de debugging
     */
    public void agregarTrazaDebug(String traza) {
        if (this.trazaEjecucion == null) {
            this.trazaEjecucion = traza;
        } else {
            this.trazaEjecucion += "\n" + traza;
        }
    }
    
    /**
     * Agrega nodo explorado para debugging
     */
    public void agregarNodoExplorado(Punto nodo) {
        if (nodosExploradosDetalle.size() < 100) { // Limitar para evitar memory overflow
            nodosExploradosDetalle.add(nodo.clonar());
        }
    }
    
    /**
     * Crea una copia del resultado
     */
    public ResultadoAEstrella clonar() {
        ResultadoAEstrella copia = new ResultadoAEstrella(origen, destino);
        
        copia.setRutaEncontrada(new ArrayList<>(rutaEncontrada));
        copia.setRutaExiste(rutaExiste);
        copia.setCalculoExitoso(calculoExitoso);
        copia.setDistanciaGrid(distanciaGrid);
        copia.setDistanciaKm(distanciaKm);
        copia.setNumeroMovimientos(numeroMovimientos);
        copia.setTiempoCalculoMs(tiempoCalculoMs);
        copia.setNodosExplorados(nodosExplorados);
        copia.setNodosEnCola(nodosEnCola);
        copia.setMemoriaUtilizadaKb(memoriaUtilizadaKb);
        copia.setRutaOptima(rutaOptima);
        copia.setFactorOptimalidad(factorOptimalidad);
        copia.setTimeoutAlcanzado(timeoutAlcanzado);
        copia.setFechaHoraCalculo(fechaHoraCalculo);
        copia.setAlgoritmoUtilizado(algoritmoUtilizado);
        copia.setResultadoDesdeCache(resultadoDesdeCache);
        copia.setMensajeError(mensajeError);
        copia.setTipoError(tipoError);
        
        return copia;
    }
    
    @Override
    public String toString() {
        if (!rutaExiste) {
            return String.format("A* FAIL: %s -> %s (%s)", 
                origen, destino, mensajeError != null ? mensajeError : "Sin ruta");
        }
        
        return String.format("A* OK: %s -> %s (dist=%d, tiempo=%dms, nodos=%d%s)", 
            origen, destino, distanciaGrid, tiempoCalculoMs, nodosExplorados,
            resultadoDesdeCache ? ", CACHE" : "");
    }
}

