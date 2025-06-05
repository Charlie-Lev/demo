package com.plg.service;

import com.plg.domain.*;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Servicio especializado para verificación de combustible y viabilidad de rutas
 * Valida todas las restricciones del sistema antes de ejecutar rutas
 */
public interface ValidadorRestriccionesService {
    
    ResultadoValidacionCompleta validarRutaCompleta(RutaOptimizada ruta);
    
    ResultadoValidacionCombustible validarCombustible(RutaOptimizada ruta);

    ResultadoValidacionPeso validarPesoCapacidad(Camion camion, List<Entrega> entregas);
    
    ResultadoValidacionAccesibilidad validarAccesibilidad(RutaOptimizada ruta);
    
    ResultadoValidacionTiempo validarTiempo(RutaOptimizada ruta);
    
    boolean esSecuenciaViable(Camion camion, List<Entrega> entregas, 
                             Almacen almacenOrigen, List<Almacen> almacenesRetorno);
    
    MargenSeguridadCombustible calcularMargenSeguridad(RutaOptimizada ruta);
    
    List<ProblemaDetectado> detectarProblemasPotenciales(RutaOptimizada ruta);

    List<SugerenciaCorreccion> sugerirCorrecciones(RutaOptimizada ruta, List<ProblemaDetectado> problemas);
    
    ResultadoValidacionMultiple validarMultiplesRutas(Map<Camion, RutaOptimizada> rutas);
    
    ResultadoEvaluacionCalidad evaluarCalidadRuta(RutaOptimizada ruta, CriteriosCalidad criteriosCalidad);
    
    ResultadoSimulacion simularEjecucionRuta(RutaOptimizada ruta, ConfiguracionSimulacion configuracionSimulacion);
    
    ConfiguracionRestricciones obtenerConfiguracionRestricciones();

    void actualizarConfiguracionRestricciones(ConfiguracionRestricciones configuracion);
    
    @Data
    class ResultadoValidacionCompleta {
        private boolean rutaValida;
        private ResultadoValidacionCombustible validacionCombustible;
        private ResultadoValidacionPeso validacionPeso;
        private ResultadoValidacionAccesibilidad validacionAccesibilidad;
        private ResultadoValidacionTiempo validacionTiempo;
        private List<ProblemaDetectado> problemasDetectados;
        private List<String> advertencias;
        private double scoreConfiabilidad;
        private String resumenValidacion;
        
        public ResultadoValidacionCompleta() {
            this.problemasDetectados = new java.util.ArrayList<>();
            this.advertencias = new java.util.ArrayList<>();
        }
    }
    
    @Data
    class ResultadoValidacionCombustible {
        private boolean combustibleSuficiente;
        private double combustibleNecesario;
        private double combustibleDisponible;
        private double combustibleRestante;
        private double margenSeguridad;
        private SegmentoRuta segmentoProblematico;
        private String mensajeDetalle;
    }

    @Data
    class ResultadoValidacionPeso {
        private boolean pesoValido;
        private double pesoTotal;
        private double pesoMaximo;
        private double porcentajeUtilizacion;
        private boolean excedePeso;
        private String detalleProblema;
    }
 
    @Data
    class ResultadoValidacionAccesibilidad {
        private boolean todosPuntosAccesibles;
        private List<Punto> puntosInaccesibles;
        private List<SegmentoRuta> segmentosProblematicos;
        private String mensajeError;
        
        public ResultadoValidacionAccesibilidad() {
            this.puntosInaccesibles = new java.util.ArrayList<>();
            this.segmentosProblematicos = new java.util.ArrayList<>();
        }
    }
    
    @Data
    class ResultadoValidacionTiempo {
        private boolean tiemposValidos;
        private double tiempoTotalEstimado;
        private List<Entrega> entregasTardias;
        private String advertenciaTiempo;
        
        public ResultadoValidacionTiempo() {
            this.entregasTardias = new java.util.ArrayList<>();
        }
    }

    @Data
    class MargenSeguridadCombustible {
        private double margenGalones;
        private double margenPorcentaje;
        private boolean margenSuficiente;
        private String recomendacion;
    }

    @Data
    class ProblemaDetectado {
        private SeveridadProblema severidad;
        private TipoProblema tipo;
        private String descripcion;
        private SegmentoRuta segmentoAfectado;
        private Object datosAdicionales;
    }

    @Data
    class SugerenciaCorreccion {
        private TipoProblema problemaRelacionado;
        private String descripcionSolucion;
        private double impactoEstimado;
        private int prioridadImplementacion;
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
    enum TipoProblema {
        COMBUSTIBLE_INSUFICIENTE,
        PESO_EXCEDIDO,
        PUNTO_INACCESIBLE,
        TIEMPO_INSUFICIENTE,
        CAPACIDAD_EXCEDIDA,
        RUTA_INVIABLE,
        EFICIENCIA_BAJA
    }
    @Data
    class ResultadoValidacionMultiple {
        private Map<Camion, ResultadoValidacionCompleta> resultadosPorCamion;
        private int rutasValidas;
        private int rutasProblematicas;
        private String resumenGeneral;
        public ResultadoValidacionMultiple() {
            this.resultadosPorCamion = new java.util.HashMap<>();
        }
    }
    @Data
    class CriteriosCalidad {
        private double eficienciaMinima = 70.0;
        private double margenCombustibleMinimo = 10.0;
        private boolean permitirSobrepeso = false;
    }
    @Data
    class ResultadoEvaluacionCalidad {
        private boolean cumpleEstandares;
        private double scoreCalidad;
        private String observaciones;
    }
    @Data
    class ConfiguracionSimulacion {
        private boolean simularAverias = false;
        private double probabilidadAveria = 0.1;
        private boolean simularTrafico = false;
    }
    @Data
    class ResultadoSimulacion {
        private boolean simulacionExitosa;
        private String resumenSimulacion;
    }
    @Data
    class ConfiguracionRestricciones {
        private double margenCombustibleMinimo = 10.0;
        private boolean permitirSobrecarga = false;
        private double factorSeguridadPeso = 1.1;
    }
}