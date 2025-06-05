package com.plg.service;

import com.plg.domain.*;
import com.plg.dto.RutaVisualizacionDTO;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Servicio especializado para preparar datos optimizados para visualización frontend
 * Garantiza JSON serializable y estructura consistente
 */
public interface SerializadorVisualizacionService {
    
    List<RutaVisualizacionDTO> convertirRutasParaVisualizacion(Map<Camion, RutaOptimizada> rutas);

    RutaVisualizacionDTO convertirRutaIndividual(Camion camion, RutaOptimizada ruta);
    
    PayloadVisualizacionCompleto generarPayloadCompleto(
        Map<Camion, RutaOptimizada> rutas,
        Map<String, Object> metricas,
        ConfiguracionVisualizacion configuracion
    );

    List<ObstaculoVisualizacionDTO> convertirObstaculosParaVisualizacion();

    List<AlmacenVisualizacionDTO> convertirAlmacenesParaVisualizacion(List<Almacen> almacenes);
    
    List<AsignacionVisualizacionDTO> convertirAsignacionesParaVisualizacion(
        Map<Camion, AsignacionCamion> asignaciones);
    

    TimelineEjecucionDTO generarTimelineEjecucion(Map<Camion, RutaOptimizada> rutas);

    List<WidgetMetricaDTO> convertirMetricasParaWidgets(Map<String, Object> metricas);
    

    PayloadVisualizacionCompleto optimizarPayload(
        PayloadVisualizacionCompleto payload, 
        int nivelOptimizacion
    );
    
    void configurarSerializacion(ConfiguracionSerializacion configuracion);
    @Data
    class PayloadVisualizacionCompleto {
        private List<RutaVisualizacionDTO> rutas;
        private List<AlmacenVisualizacionDTO> almacenes;
        private List<ObstaculoVisualizacionDTO> obstaculos;
        private List<AsignacionVisualizacionDTO> asignaciones;
        private TimelineEjecucionDTO timeline;
        private List<WidgetMetricaDTO> widgets;
        private MetadatosVisualizacion metadatos;
        
        public PayloadVisualizacionCompleto() {
            this.rutas = new java.util.ArrayList<>();
            this.almacenes = new java.util.ArrayList<>();
            this.obstaculos = new java.util.ArrayList<>();
            this.asignaciones = new java.util.ArrayList<>();
            this.widgets = new java.util.ArrayList<>();
        }
    }
    @Data
    class ObstaculoVisualizacionDTO {
        private String tipo; // "PUNTO", "LINEA_H", "LINEA_V", "POLIGONO"
        private java.util.List<int[]> coordenadas;
        private String color;
        private Map<String, Object> propiedades;
        
        public ObstaculoVisualizacionDTO() {
            this.coordenadas = new java.util.ArrayList<>();
            this.propiedades = new java.util.HashMap<>();
        }
    }   
    @Data
    class AlmacenVisualizacionDTO {
        private int id;
        private int x;
        private int y;
        private double capacidad;
        private double cantidad;
        private String tipo;
        private boolean esPrincipal;
        private String estado; // "OPERATIVO", "MANTENIMIENTO", "FUERA_SERVICIO"
        private String icono;
        private String color;
    }
    @Data
    class AsignacionVisualizacionDTO {
        private int camionId;
        private String codigoCamion;
        private String tipoCamion;
        private List<EntregaResumenDTO> entregas;
        private double capacidadUtilizada;
        private double capacidadDisponible;
        private double porcentajeUtilizacion;
        private String estado;
        
        public AsignacionVisualizacionDTO() {
            this.entregas = new java.util.ArrayList<>();
        }
    }
    @Data
    class EntregaResumenDTO {
        private int pedidoId;
        private int x;
        private int y;
        private double volumen;
        private int prioridad;
        private String tipoEntrega;
    }
    @Data
    class TimelineEjecucionDTO {
        private String fechaInicio;
        private String fechaFinEstimada;
        private List<EventoTimelineDTO> eventos;
        private double duracionTotalHoras;
        
        public TimelineEjecucionDTO() {
            this.eventos = new java.util.ArrayList<>();
        }
    }
    @Data
    class EventoTimelineDTO {
        private String tipo; // "INICIO", "ENTREGA", "RETORNO"
        private String descripcion;
        private String horaEstimada;
        private int camionId;
        private int[] coordenadas;
        private String estado; // "PENDIENTE", "EN_PROGRESO", "COMPLETADO"
    }
    @Data
    class WidgetMetricaDTO {
        private String id;
        private String titulo;
        private String tipo; // "NUMERO", "GRAFICO", "GAUGE", "TABLA"
        private Object valor;
        private String unidad;
        private String color;
        private String icono;
        private Map<String, Object> configuracion;
        
        public WidgetMetricaDTO() {
            this.configuracion = new java.util.HashMap<>();
        }
    }
    @Data
    class MetadatosVisualizacion {
        private String fechaGeneracion;
        private String version;
        private Map<String, Object> configuracionGrid;
        private Map<String, Object> estadisticasGenerales;
        private int nivelOptimizacion;
        
        public MetadatosVisualizacion() {
            this.configuracionGrid = new java.util.HashMap<>();
            this.estadisticasGenerales = new java.util.HashMap<>();
            this.fechaGeneracion = java.time.LocalDateTime.now().toString();
            this.version = "1.0";
        }
    }
    @Data
    class ConfiguracionVisualizacion {
        private boolean incluirRutasDetalladas = true;
        private boolean incluirObstaculos = true;
        private boolean incluirTimeline = true;
        private boolean incluirWidgets = true;
        private int maxPuntosRuta = 1000;
        private String formatoFecha = "yyyy-MM-dd HH:mm:ss";
    }
    @Data
    class ConfiguracionSerializacion {
        private boolean habilitarCompresion = true;
        private boolean excluirCamposNulos = true;
        private boolean formatearFechas = true;
        private int precisionDecimales = 2;
    }
}