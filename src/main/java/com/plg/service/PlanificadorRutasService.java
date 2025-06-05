package com.plg.service;

import com.plg.domain.*;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface PlanificadorRutasService {

    Map<Camion, RutaOptimizada> planificarRutas(
        Map<Camion, AsignacionCamion> asignaciones, 
        List<Almacen> almacenes);

    Map<Camion, RutaOptimizada> planificarRutas(
        Map<Camion, AsignacionCamion> asignaciones, 
        List<Almacen> almacenes,
        ConfiguracionPlanificadorRutas configuracion);

    RutaOptimizada planificarRutaCamion(
        Camion camion, 
        AsignacionCamion asignacion, 
        List<Almacen> almacenes);

    CompletableFuture<RutaOptimizada> planificarRutaCamionAsincrona(
        Camion camion, 
        AsignacionCamion asignacion, 
        List<Almacen> almacenes);


    List<Entrega> optimizarOrdenEntregas(
        List<Entrega> entregas, 
        Punto puntoInicio);


    List<Entrega> optimizarOrdenEntregas(
        List<Entrega> entregas, 
        Punto puntoInicio,
        ConfiguracionACO configuracionACO);
    List<SegmentoRuta> calcularSegmentosRuta(
        List<Punto> secuenciaPuntos, 
        List<Entrega> entregas);
    RutaOptimizada calcularRutaCompleta(
        Camion camion,
        Punto puntoActual,
        List<Entrega> entregas,
        Almacen almacenDestino);
    boolean esRutaFactible(RutaOptimizada ruta, Camion camion);
    MetricasCalidadRuta calcularMetricasCalidad(RutaOptimizada ruta);
    RutaOptimizada optimizarRutaLocal(RutaOptimizada rutaBase);
    Almacen encontrarAlmacenMasCercano(Punto punto, List<Almacen> almacenes);
    void precalcularRutasAlmacenes(List<Almacen> almacenes);
    EstadisticasPlanificacionRutas obtenerEstadisticas();
    void configurar(ConfiguracionPlanificadorRutas configuracion);
    void reiniciar();
    @Data
    class ConfiguracionPlanificadorRutas {
        // Configuración general
        private int dimensionMapaX = 70;
        private int dimensionMapaY = 50;
        private boolean habilitarParalelizacion = true;
        private int numeroThreads = 4;
        private long timeoutCalculoMs = 30000;

        // Configuración ACO
        private ConfiguracionACO configuracionACO = new ConfiguracionACO();
        
        // Configuración A*
        private AEstrellaService.ConfiguracionAEstrella configuracionAEstrella = 
            AEstrellaService.ConfiguracionAEstrella.rapida();

        // Optimizaciones
        private boolean usarCacheRutas = true;
        private boolean usarOptimizacionLocal = true;
        private boolean forzarRetornoAlmacen = true;
        private double margenCombustibleSeguridad = 0.15; // 15% de margen

        // Configuraciones predefinidas
        public static ConfiguracionPlanificadorRutas rapida() {
            ConfiguracionPlanificadorRutas config = new ConfiguracionPlanificadorRutas();
            config.configuracionACO = ConfiguracionACO.rapida();
            config.configuracionAEstrella = AEstrellaService.ConfiguracionAEstrella.rapida();
            config.timeoutCalculoMs = 10000;
            config.numeroThreads = 2;
            return config;
        }

        public static ConfiguracionPlanificadorRutas precisa() {
            ConfiguracionPlanificadorRutas config = new ConfiguracionPlanificadorRutas();
            config.configuracionACO = ConfiguracionACO.precisa();
            config.configuracionAEstrella = AEstrellaService.ConfiguracionAEstrella.precisa();
            config.timeoutCalculoMs = 60000;
            config.numeroThreads = 8;
            return config;
        }

        public static ConfiguracionPlanificadorRutas balanceada() {
            return new ConfiguracionPlanificadorRutas(); // Valores por defecto
        }
    }
    @Data
    class ConfiguracionACO {
        // Parámetros del algoritmo ACO
        private int numeroHormigas = 10;
        private int numeroIteraciones = 100;
        private double alfa = 1.0;      // Influencia de feromonas
        private double beta = 2.0;      // Influencia de visibilidad (distancia)
        private double rho = 0.1;       // Tasa de evaporación de feromonas
        private double q0 = 0.9;        // Probabilidad de explotación vs exploración
        private double feromonaInicial = 0.1;

        // Criterios de convergencia
        private int iteracionesSinMejora = 20;
        private double umbralMejoraMinima = 0.01; // 1% de mejora mínima

        // Configuraciones predefinidas
        public static ConfiguracionACO rapida() {
            ConfiguracionACO config = new ConfiguracionACO();
            config.numeroHormigas = 5;
            config.numeroIteraciones = 50;
            config.iteracionesSinMejora = 10;
            return config;
        }

        public static ConfiguracionACO precisa() {
            ConfiguracionACO config = new ConfiguracionACO();
            config.numeroHormigas = 20;
            config.numeroIteraciones = 200;
            config.iteracionesSinMejora = 40;
            return config;
        }
    }

    @Data
    class MetricasCalidadRuta {
        private double distanciaTotalKm;
        private double tiempoTotalHoras;
        private double combustibleTotalGalones;
        private double eficienciaDistancia;        // km por litro
        private double utilizacionCamion;          // % de capacidad usada
        private double indiceOptimizacion;         // 0-100, qué tan optimizada está
        private int numeroEntregas;
        private int numeroSegmentos;
        
        // Métricas de calidad ACO
        private double calidadOrdenEntregas;       // Qué tan bueno es el orden TSP
        private double distanciaVsOptima;          // Distancia real vs óptima estimada
        
        // Métricas de calidad A*
        private double calidadPathfinding;         // Calidad promedio de los A*
        private int numeroPuntosRuta;              // Total de puntos en la ruta
    }

    @Data
    class EstadisticasPlanificacionRutas {
        // Estadísticas generales
        private long totalCalculos = 0;
        private long calculosExitosos = 0;
        private long calculosFallidos = 0;
        private double tiempoPromedioMs = 0.0;

        // Estadísticas ACO
        private long totalOptimizacionesACO = 0;
        private double tiempoPromedioACOMs = 0.0;
        private double mejoraPromedioACO = 0.0;    // % de mejora vs ruta inicial

        // Estadísticas A*
        private long totalCalculosAEstrella = 0;
        private double tiempoPromedioAEstrellaMs = 0.0;

        // Estadísticas de paralelización
        private long calculosParalelos = 0;
        private double eficienciaParalelizacion = 0.0;

        // Estadísticas de calidad
        private double distanciaPromedioKm = 0.0;
        private double utilizacionPromedioCamiones = 0.0;
        private double indiceOptimizacionPromedio = 0.0;

        // Estado del sistema
        private int rutasEnCache = 0;
        private int threadPoolSize = 0;
        private long memoriaTotalUtilizadaKb = 0;

        public double getTasaExito() {
            return totalCalculos > 0 ? (double) calculosExitosos / totalCalculos * 100.0 : 0.0;
        }

        public double getPorcentajeParalelizacion() {
            return totalCalculos > 0 ? (double) calculosParalelos / totalCalculos * 100.0 : 0.0;
        }

        @Override
        public String toString() {
            return String.format(
                "PlanificadorRutas Stats: %d cálculos (%.1f%% éxito), %.1fms promedio, %.1f%% paralelo, %.1fkm promedio",
                totalCalculos, getTasaExito(), tiempoPromedioMs, 
                getPorcentajeParalelizacion(), distanciaPromedioKm);
        }
    }
}