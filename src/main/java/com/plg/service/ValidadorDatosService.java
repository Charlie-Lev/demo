package com.plg.service;

import com.plg.domain.*;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Servicio especializado en validación de datos de entrada
 * Garantiza integridad antes del procesamiento
 */
public interface ValidadorDatosService {

    ResultadoValidacionPedido validarPedido(Pedido pedido);
    
    Map<Pedido, ResultadoValidacionPedido> validarPedidos(List<Pedido> pedidos);
    
    ResultadoValidacionCamion validarCamion(Camion camion);
 
    Map<Camion, ResultadoValidacionCamion> validarCamiones(List<Camion> camiones);
    
    ResultadoValidacionAlmacen validarAlmacen(Almacen almacen);
    
    boolean sonCoordenadasValidas(int x, int y);
    
    boolean esPuntoAccesible(Punto punto);
    
    ResultadoValidacionCoherencia validarCoherenciaGeneral(
        List<Pedido> pedidos, 
        List<Camion> camiones, 
        List<Almacen> almacenes
    );
    boolean esAsignacionFactible(AsignacionCamion asignacion);

    void configurarValidacion(ConfiguracionValidacion configuracion);
    @Data
    class ResultadoValidacionPedido {
        private boolean pedidoValido;
        private List<String> errores;
        private List<String> advertencias;
        private Map<String, Object> metadatos;
        
        public ResultadoValidacionPedido() {
            this.errores = new java.util.ArrayList<>();
            this.advertencias = new java.util.ArrayList<>();
            this.metadatos = new java.util.HashMap<>();
        }
        public void agregarError(String error) { errores.add(error); }
        public void agregarAdvertencia(String advertencia) { advertencias.add(advertencia); }
    }
    @Data
    class ResultadoValidacionCamion {
        private boolean camionValido;
        private List<String> errores;
        private List<String> advertencias;
        private double factorEficiencia;
        
        public ResultadoValidacionCamion() {
            this.errores = new java.util.ArrayList<>();
            this.advertencias = new java.util.ArrayList<>();
        }
        
        public void agregarError(String error) { errores.add(error); }
        public void agregarAdvertencia(String advertencia) { advertencias.add(advertencia); }
    }
    
    @Data
    class ResultadoValidacionAlmacen {
        private boolean almacenValido;
        private List<String> errores;
        private List<String> advertencias;
        private boolean esAccesible;
        
        public ResultadoValidacionAlmacen() {
            this.errores = new java.util.ArrayList<>();
            this.advertencias = new java.util.ArrayList<>();
        }
        
        public void agregarError(String error) { errores.add(error); }
        public void agregarAdvertencia(String advertencia) { advertencias.add(advertencia); }
    }
    
    @Data
    class ResultadoValidacionCoherencia {
        private boolean coherenciaValida;
        private List<String> problemasDetectados;
        private Map<String, Integer> estadisticasValidacion;
        
        public ResultadoValidacionCoherencia() {
            this.problemasDetectados = new java.util.ArrayList<>();
            this.estadisticasValidacion = new java.util.HashMap<>();
        }
        
        public void agregarProblema(String problema) { problemasDetectados.add(problema); }
    }
    
    @Data
    class ConfiguracionValidacion {
        private int gridMinX = 0;
        private int gridMinY = 0;
        private int gridMaxX = 1000;
        private int gridMaxY = 1000;
        private double volumenMinimoM3 = 0.1;
        private double volumenMaximoM3 = 50.0;
        private boolean validarAccesibilidadPuntos = true;
        private boolean validarCoherenciaEstricta = true;
    }
}