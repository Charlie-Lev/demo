package com.plg.service;

import com.plg.domain.*;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Validador final que garantiza que ruta es ejecutable en mundo real
 * Verificación exhaustiva antes de entregar ruta al sistema
 */
public interface ValidadorFinalService {
 
    ResultadoValidacionFinal validarRutaFinal(RutaOptimizada ruta);
    
    ResultadoValidacionMultipleFinal validarMultiplesRutasFinal(Map<Camion, RutaOptimizada> rutas);
    
    ResultadoValidacionPrioridades validarPrioridadesSecuencia(RutaOptimizada ruta);
 
    ResultadoValidacionVentanasTiempo validarVentanasTiempo(RutaOptimizada ruta);

    MetricasCalidadRuta calcularMetricasCalidad(RutaOptimizada ruta);
    
    ReporteValidacionAuditoria generarReporteAuditoria(RutaOptimizada ruta, ResultadoValidacionFinal resultado);
    
    @Data
    class ResultadoValidacionFinal {
        private boolean rutaEjecutable;
        private double scoreCalidadGlobal; // 0-100
        private List<ProblemaValidacion> problemasDetectados;
        private List<String> advertenciasCalidad;
        private MetricasCalidadRuta metricas;
        private Map<String, Object> validacionesDetalladas;
        private boolean requiereIntervencionOperador;
        private String resumenEjecutivo;
    }
    @Data
    class ResultadoValidacionPrioridades {
        private boolean secuenciaPrioridadesCorrecta;
        private double scoreDistribucionPrioridades;
        private List<ProblemaOrdenPrioridad> problemasOrden;
        private int entregasUrgentesAlFinal;
        private double tiempoPromedioPrioridadAlta;
    }
    @Data
    class ResultadoValidacionVentanasTiempo {
        private boolean todasEntregasDentroVentana;
        private int entregasFueraVentana;
        private List<EntregaTardia> entregasTardias;
        private double tiempoTotalRuta;
        private String ventanaMasCritica;
    }
    @Data
    class MetricasCalidadRuta {
        private double scoreCombustible; // 0-100
        private double scoreEficiencia; // 0-100
        private double scorePrioridades; // 0-100
        private double scoreTiempos; // 0-100
        private double scoreAccesibilidad; // 0-100
        private double scoreViabilidadGeneral; // 0-100
        private Map<String, Double> metricasDetalladas;
        private double distanciaTotal;           // Distancia total (km)
        private double tiempoEstimado;          // Tiempo estimado (horas)
        private double consumoTotal;            // Consumo total (galones)
        private double eficienciaUtilizacion;   // Eficiencia de utilización (%)
        private double scorePrioridad;          // Score de prioridad (alias)
    }
    @Data
    class ProblemaValidacion {
        private TipoProblemaValidacion tipo;
        private SeveridadProblema severidad;
        private String descripcion;
        private String solucionSugerida;
        private SegmentoRuta segmentoAfectado;
    }
    @Data
    class ProblemaOrdenPrioridad {
        private Entrega entregaProblematica;
        private int posicionActual;
        private int posicionRecomendada;
        private String razonProblema;
    }
    @Data
    class EntregaTardia {
        private Entrega entrega;
        private double tiempoEstimadoEntrega;
        private double tiempoLimite;
        private double minutosRetraso;
    }
    
    enum TipoProblemaValidacion {
        COMBUSTIBLE_CRITICO,
        SECUENCIA_PRIORIDADES_INCORRECTA,
        VENTANA_TIEMPO_VIOLADA,
        PUNTO_INACCESIBLE,
        PESO_EXCEDIDO,
        EFICIENCIA_SUBOPTIMA
    }
    @Data
    class ResultadoValidacionMultipleFinal {
        private Map<Camion, ResultadoValidacionFinal> resultadosPorCamion;
        private double scoreCalidadPromedio;
        private int rutasEjecutables;
        private int rutasProblematicas;
        private List<String> recomendacionesGlobales;
    }
    @Data
    class ReporteValidacionAuditoria {
        private String fechaValidacion;
        private String identificadorRuta;
        private Map<String, Object> resumenValidaciones;
        private List<String> checklistCompletado;
        private String firmaValidacion;
    }
    enum SeveridadProblema {
        CRITICO("Impide ejecución de ruta"),
        ALTO("Problemas serios pero ruta ejecutable"),
        MEDIO("Advertencias importantes"),
        BAJO("Observaciones menores");
        private final String descripcion;
        SeveridadProblema(String descripcion) { this.descripcion = descripcion; }
        public String getDescripcion() { return descripcion; }
    }
}