package com.plg.service;

import com.plg.domain.*;
import com.plg.dto.RutaVisualizacionDTO;

import lombok.Data;

import java.util.List;
import java.util.Map;

public interface PlanificacionIntegradorService {

    Map<Camion, RutaOptimizada> ejecutarPlanificacionCompleta();
    Map<Camion, RutaOptimizada> ejecutarPlanificacionPedidosCriticos();
    Map<Camion, RutaOptimizada> obtenerRutasConPedidosCriticos();

    ResultadoPlanificacionCompleta planificarCompleto(
        List<Pedido> pedidosPendientes,
        List<Camion> camionesDisponibles, 
        List<Almacen> almacenesOperativos
    );

    Map<Camion, RutaOptimizada> integrarDesdeAsignaciones(
        Map<Camion, AsignacionCamion> asignacionesExistentes,
        List<Almacen> almacenesDisponibles
    );

    ResultadoValidacionEntrada validarDatosEntrada(
        List<Pedido> pedidos,
        List<Camion> camiones,
        List<Almacen> almacenes
    );

    List<RutaVisualizacionDTO> prepararParaVisualizacion(
        Map<Camion, RutaOptimizada> rutas
    );

    ResultadoReplanificacion replanificarPorEventos(
        Map<Camion, RutaOptimizada> rutasActuales,
        List<EventoAveria> eventosAverias,
        DatosActualizacion nuevosDatos
    );

    MetricasPlanificacionCompleta obtenerMetricasConsolidadas(
        ResultadoPlanificacionCompleta resultado
    );
    
    void configurarIntegracion(ConfiguracionIntegracion configuracion);
    
    @Data
    class ResultadoPlanificacionCompleta {
        private Map<Camion, AsignacionCamion> asignacionesPedidos;
        private Map<Camion, RutaOptimizada> rutasOptimizadas;
        private Map<String, Object> estadisticasPedidos;
        private Map<String, Object> estadisticasRutas;
        private List<String> advertencias;
        private boolean procesadoExitoso;
        private long tiempoProcesamientoMs;
        
        public ResultadoPlanificacionCompleta() {
            this.advertencias = new java.util.ArrayList<>();
        }

        public void agregarAdvertencia(String advertencia) {
            this.advertencias.add(advertencia);
        }
    }
    @Data
    class ResultadoValidacionEntrada {
        private boolean datosValidos;
        private List<String> erroresPedidos;
        private List<String> erroresCamiones;
        private List<String> erroresAlmacenes;
        private Map<String, Object> estadisticasEntrada;
        
        public ResultadoValidacionEntrada() {
            this.erroresPedidos = new java.util.ArrayList<>();
            this.erroresCamiones = new java.util.ArrayList<>();
            this.erroresAlmacenes = new java.util.ArrayList<>();
            this.estadisticasEntrada = new java.util.HashMap<>();
        }
        
        public void agregarErrorPedido(String error) { erroresPedidos.add(error); }
        public void agregarErrorCamion(String error) { erroresCamiones.add(error); }
        public void agregarErrorAlmacen(String error) { erroresAlmacenes.add(error); }
        
        public int getTotalErrores() {
            return erroresPedidos.size() + erroresCamiones.size() + erroresAlmacenes.size();
        }
    }
    @Data
    class DatosActualizacion {
        private List<Pedido> nuevosPedidos;
        private List<Camion> nuevosCamiones;
        private List<Almacen> nuevosAlmacenes;
        
        public DatosActualizacion() {
            this.nuevosPedidos = new java.util.ArrayList<>();
            this.nuevosCamiones = new java.util.ArrayList<>();
            this.nuevosAlmacenes = new java.util.ArrayList<>();
        }
        
        public boolean tieneActualizaciones() {
            return !nuevosPedidos.isEmpty() || !nuevosCamiones.isEmpty() || !nuevosAlmacenes.isEmpty();
        }
    }
    @Data
    class EventoAveria {
        private Camion camionAfectado;
        private Punto posicionActual;
        private TipoAveria tipoAveria;
        private java.time.LocalDateTime fechaHoraAveria;
        private String descripcion;
        
        public EventoAveria(Camion camion, Punto posicion, TipoAveria tipo) {
            this.camionAfectado = camion;
            this.posicionActual = posicion;
            this.tipoAveria = tipo;
            this.fechaHoraAveria = java.time.LocalDateTime.now();
        }
    }
    enum TipoAveria {
        MECANICA("Avería mecánica - camión inoperativo"),
        COMBUSTIBLE("Sin combustible - requiere reabastecimiento"),
        ACCIDENTE("Accidente - tiempo de resolución variable"),
        TRAFICO("Obstrucción de tráfico - replanificar ruta");
        
        private final String descripcion;
        
        TipoAveria(String descripcion) {
            this.descripcion = descripcion;
        }
        
        public String getDescripcion() { return descripcion; }
    }
    @Data
    class ResultadoReplanificacion {
        private Map<Camion, RutaOptimizada> rutasReplanificadas;
        private List<Camion> camionesAfectados;
        private List<Pedido> pedidosReasignados;
        private String razonReplanificacion;
        private boolean replanificacionExitosa;
    }
    @Data
    class MetricasPlanificacionCompleta {
        private int totalPedidosProcesados;
        private int totalCamionesUtilizados;
        private double eficienciaGlobalPorcentaje;
        private double distanciaTotalKm;
        private double tiempoTotalHoras;
        private double combustibleTotalGalones;
        private int rutasViables;
        private int rutasProblematicas;
        private Map<String, Object> detallesAdicionales;
        
        public MetricasPlanificacionCompleta() {
            this.detallesAdicionales = new java.util.HashMap<>();
        }
        
    }
    @Data
    class ConfiguracionIntegracion {
        private boolean habilitarValidacionEstricta = true;
        private boolean habilitarParalelizacion = true;
        private boolean habilitarCache = true;
        private int timeoutProcesamientoMs = 30000;
        private double margenSeguridadCombustible = 10.0;
        private boolean generarMetricasDetalladas = true;
        
    }
}