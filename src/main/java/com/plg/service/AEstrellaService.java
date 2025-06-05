package com.plg.service;

import com.plg.domain.Punto;
import com.plg.domain.ResultadoAEstrella;

import lombok.Data;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Servicio para pathfinding A* entre dos puntos específicos
 * Optimizado para grid cartesiano con movimientos Manhattan (arriba, abajo, izquierda, derecha)
 */
public interface AEstrellaService {

    ResultadoAEstrella calcularRuta(Punto origen, Punto destino);

    ResultadoAEstrella calcularRuta(Punto origen, Punto destino, ConfiguracionAEstrella configuracion);
    
    List<ResultadoAEstrella> calcularRutasParalelo(List<ParOrigenDestino> pares);
    
    CompletableFuture<ResultadoAEstrella> calcularRutaAsincrona(Punto origen, Punto destino);

    boolean existeRuta(Punto origen, Punto destino);

    int calcularDistanciaEstimada(Punto origen, Punto destino);

    void precalcularRutasFrecuentes(List<Punto> puntosImportantes);

    void optimizarConfiguracion();

    EstadisticasAEstrella obtenerEstadisticas();
    
    void configurar(ConfiguracionAEstrella configuracion);

    void reiniciar();

    class ParOrigenDestino {
        private final Punto origen;
        private final Punto destino;
        private final String identificador;
        
        public ParOrigenDestino(Punto origen, Punto destino) {
            this(origen, destino, null);
        }
        
        public ParOrigenDestino(Punto origen, Punto destino, String identificador) {
            this.origen = origen.clonar();
            this.destino = destino.clonar();
            this.identificador = identificador;
        }
        
        public Punto getOrigen() { return origen; }
        public Punto getDestino() { return destino; }
        public String getIdentificador() { return identificador; }
        
        @Override
        public String toString() {
            return String.format("%s: %s -> %s", 
                identificador != null ? identificador : "Ruta", origen, destino);
        }
    }

    @Data
    class ConfiguracionAEstrella {
        private long timeoutMs = 5000;           // Timeout máximo por cálculo
        private int maxNodosExplorados = 10000;  // Límite de nodos a explorar
        private boolean usarCache = true;        // Usar cache de rutas
        private boolean modoDebug = false;       // Activar debugging detallado
        private double factorHeuristica = 1.0;  // Factor de peso de la heurística
        private boolean permitirDiagonales = false; // Solo Manhattan por defecto
        private boolean usarBusquedaBidireccional = false;
        private int numeroThreads = 4;              // Número de threads del pool
        private boolean habilitarParalelizacion = true;  // Habilitar/deshabilitar paralelización
        private String prefijosNombreThread = "AEstrella-Worker";  // Prefijo para nombres de threads
        private long timeoutShutdownMs = 5000;     // Timeout para shutdown graceful
        public void setNumeroThreads(int numeroThreads) { 
            this.numeroThreads = Math.max(1, Math.min(numeroThreads, 16)); // Límite 1-16 threads
        }

        public static ConfiguracionAEstrella rapida() {
            ConfiguracionAEstrella config = new ConfiguracionAEstrella();
            config.setTimeoutMs(1000);
            config.setMaxNodosExplorados(5000);
            config.setFactorHeuristica(1.2);
            config.setNumeroThreads(2); // ✅ NUEVO - Menos threads para cálculos rápidos
            return config;
        }

        public static ConfiguracionAEstrella precisa() {
            ConfiguracionAEstrella config = new ConfiguracionAEstrella();
            config.setTimeoutMs(10000);
            config.setMaxNodosExplorados(50000);
            config.setFactorHeuristica(1.0);
            config.setNumeroThreads(8);
            return config;
        }

        public static ConfiguracionAEstrella debug() {
            ConfiguracionAEstrella config = new ConfiguracionAEstrella();
            config.setModoDebug(true);
            config.setTimeoutMs(30000);
            return config;
        }
    }

    @Data
    class EstadisticasAEstrella {
        private long totalCalculos = 0;
        private long calculosExitosos = 0;
        private long calculosFallidos = 0;
        private long calculosDesdeCache = 0;
        private double tiempoPromedioMs = 0.0;
        private double nodosPromedioExplorados = 0.0;
        private double tasaHitCache = 0.0;
        private int rutasEnCache = 0;
        private long memoriaTotalUtilizadaKb = 0;
        private long calculosParalelos = 0;         // Total de cálculos paralelos
        private double utilizacionPromPoolThreads = 0.0;  // Utilización promedio del pool (0-100%)
        private long threadPoolTareasCompletadas = 0;     // Tareas completadas por el pool
        private long threadPoolTareasEnCola = 0;          // Tareas pendientes en cola
        private int threadPoolTamanoActual = 0;           // Tamaño actual del pool
        private double throughputCalculosPorSegundo = 0.0; // Throughput de cálculos/segundo

        public double getEficienciaParalelizacion() {
            return totalCalculos > 0 ? (double) calculosParalelos / totalCalculos * 100.0 : 0.0;
        }
        
        public double getTasaExito() {
            return totalCalculos > 0 ? (double) calculosExitosos / totalCalculos * 100.0 : 0.0;
        }

        public double getPorcentajeCache() {
            return totalCalculos > 0 ? (double) calculosDesdeCache / totalCalculos * 100.0 : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format("A* Stats: %d cálculos (%.1f%% éxito, %.1f%% cache, %.1f%% paralelo), %.1fms promedio, %.1f cálc/s",
                totalCalculos, getTasaExito(), getPorcentajeCache(), getEficienciaParalelizacion(), 
                tiempoPromedioMs, throughputCalculosPorSegundo);
        }
    }
}