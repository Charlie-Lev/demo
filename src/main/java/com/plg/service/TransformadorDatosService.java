package com.plg.service;

import com.plg.domain.*;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Servicio para transformación y conversión entre formatos de datos
 * Mantiene integridad mientras adapta estructuras
 */
public interface TransformadorDatosService {

    Map<Camion, List<Punto>> convertirAsignacionesAPuntos(Map<Camion, AsignacionCamion> asignaciones);
    
    List<Coordenada> convertirRutaACoordenadas(RutaOptimizada ruta);

    List<Punto> convertirEntregasAPuntos(List<Entrega> entregas);

    Map<String, List<Entrega>> agruparEntregas(List<Entrega> entregas, CriterioAgrupacion criterio);

    CoordenadaGeografica convertirGridAGeografica(Punto punto);

    DatosNormalizados normalizarDatos(DatosOriginales datos);

    EstructuraJerarquicaRutas crearEstructuraJerarquica(Map<Camion, RutaOptimizada> rutas);

    MetricasAgregadas calcularMetricasAgregadas(Map<Camion, RutaOptimizada> rutas);

    ResumenEjecutivo generarResumenEjecutivo(
        Map<Camion, AsignacionCamion> asignaciones,
        Map<Camion, RutaOptimizada> rutas
    );
    @Data
    class Coordenada {
        private int x;
        private int y;
        private String tipo; // "ORIGEN", "ENTREGA", "RETORNO"
        private String descripcion;
        private Map<String, Object> metadatos;
        
        public Coordenada(int x, int y, String tipo) {
            this.x = x;
            this.y = y;
            this.tipo = tipo;
            this.metadatos = new java.util.HashMap<>();
        }
    }
    @Data
    class CoordenadaGeografica {
        private double latitud;
        private double longitud;
        private double altitud;
        
        public CoordenadaGeografica(double latitud, double longitud) {
            this.latitud = latitud;
            this.longitud = longitud;
            this.altitud = 0.0;
        }
    }
    @Data
    class DatosOriginales {
        private List<Pedido> pedidos;
        private List<Camion> camiones;
        private List<Almacen> almacenes;
        private Map<String, Object> configuracion;
    }
    
    @Data
    class DatosNormalizados {
        private List<Pedido> pedidosNormalizados;
        private List<Camion> camionesNormalizados;
        private List<Almacen> almacenesNormalizados;
        private Map<String, Object> transformacionesAplicadas;
        
        public DatosNormalizados() {
            this.transformacionesAplicadas = new java.util.HashMap<>();
        }
    }
    @Data
    class EstructuraJerarquicaRutas {
        private Map<Camion, NodoRuta> nodosPorCamion;
        private MetricasGlobales metricasGlobales;
        
        public EstructuraJerarquicaRutas() {
            this.nodosPorCamion = new java.util.HashMap<>();
        }
    }
    @Data
    class NodoRuta {
        private Camion camion;
        private List<NodoSegmento> segmentos;
        private Map<String, Object> metricas;
        
        public NodoRuta(Camion camion) {
            this.camion = camion;
            this.segmentos = new java.util.ArrayList<>();
            this.metricas = new java.util.HashMap<>();
        }
    }
    @Data
    class NodoSegmento {
        private Punto origen;
        private Punto destino;
        private String tipoSegmento;
        private List<Punto> rutaDetallada;
        private Map<String, Object> propiedades;
        
        public NodoSegmento() {
            this.propiedades = new java.util.HashMap<>();
        }
    }
    @Data
    class MetricasAgregadas {
        private double distanciaTotalKm;
        private double tiempoTotalHoras;
        private double combustibleTotalGalones;
        private int totalEntregas;
        private int totalCamiones;
        private double eficienciaPromedio;
        private Map<String, Object> metricasDetalladas;
        
        public MetricasAgregadas() {
            this.metricasDetalladas = new java.util.HashMap<>();
        }
    }
    @Data
    class MetricasGlobales {
        private double utilizacionPromedioFlota;
        private double densidadRutas;
        private double scoreOptimizacion;
        private Map<String, Object> indicadoresKPI;
        
        public MetricasGlobales() {
            this.indicadoresKPI = new java.util.HashMap<>();
        }
    }
    @Data
    class ResumenEjecutivo {
        private String fechaGeneracion;
        private int totalPedidosAtendidos;
        private int totalCamionesUtilizados;
        private double costeTotalEstimado;
        private double tiempoTotalOperacion;
        private String estadoGeneral;
        private List<String> recomendaciones;
        private Map<String, Object> kpisClaves;
        
        public ResumenEjecutivo() {
            this.recomendaciones = new java.util.ArrayList<>();
            this.kpisClaves = new java.util.HashMap<>();
            this.fechaGeneracion = java.time.LocalDateTime.now().toString();
        }
    }

    enum CriterioAgrupacion {
        POR_PRIORIDAD,
        POR_VOLUMEN,
        POR_UBICACION,
        POR_TIPO_ENTREGA,
        POR_CAMION_ASIGNADO
    }
}