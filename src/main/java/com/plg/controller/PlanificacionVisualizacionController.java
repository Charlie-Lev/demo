package com.plg.controller;

import com.plg.domain.*;
import com.plg.dto.RutaVisualizacionDTO;
import com.plg.service.PlanificacionIntegradorService;
import com.plg.service.SerializadorVisualizacionService;
import com.plg.service.ValidadorDatosService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Controller REST para integración con frontend de visualización
 * Expone endpoints optimizados para renderizado de rutas
 */
@RestController
@RequestMapping("/api/planificacion")
@CrossOrigin(origins = "*")
public class PlanificacionVisualizacionController {
    
    private static final Logger logger = LoggerFactory.getLogger(PlanificacionVisualizacionController.class);
    
    @Autowired
    private PlanificacionIntegradorService planificacionIntegradorService;
    
    @Autowired
    private SerializadorVisualizacionService serializadorVisualizacionService;
    
    @Autowired
    private ValidadorDatosService validadorDatosService;
    
    /**
     * Endpoint principal: Planificación completa desde pedidos
     * Input: Pedidos + Camiones + Almacenes
     * Output: RutaVisualizacionDTO[] listo para frontend
     */
    @PostMapping("/planificar-completo")
    public ResponseEntity<RespuestaPlanificacionCompleta> planificarCompleto(
            @RequestBody SolicitudPlanificacionCompleta solicitud) {
        
        logger.info("Recibida solicitud de planificación completa: {} pedidos, {} camiones", 
            solicitud.getPedidos().size(), solicitud.getCamiones().size());
        
        try {
            // 1. Validación de entrada
            PlanificacionIntegradorService.ResultadoValidacionEntrada validacion = 
                planificacionIntegradorService.validarDatosEntrada(
                    solicitud.getPedidos(), 
                    solicitud.getCamiones(), 
                    solicitud.getAlmacenes());
            
            if (!validacion.isDatosValidos()) {
                return ResponseEntity.badRequest()
                    .body(RespuestaPlanificacionCompleta.error(
                        "Datos de entrada inválidos: " + validacion.getTotalErrores() + " errores",
                        validacion));
            }
            
            // 2. Ejecutar planificación completa
            PlanificacionIntegradorService.ResultadoPlanificacionCompleta resultado = 
                planificacionIntegradorService.planificarCompleto(
                    solicitud.getPedidos(),
                    solicitud.getCamiones(), 
                    solicitud.getAlmacenes());
            
            if (!resultado.isProcesadoExitoso()) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(RespuestaPlanificacionCompleta.error(
                        "Error en planificación", resultado.getAdvertencias()));
            }
            
            // 3. Preparar para visualización
            List<RutaVisualizacionDTO> rutasVisualizacion = 
                serializadorVisualizacionService.convertirRutasParaVisualizacion(
                    resultado.getRutasOptimizadas());
            
            // 4. Generar payload completo
            SerializadorVisualizacionService.ConfiguracionVisualizacion configVis = 
                new SerializadorVisualizacionService.ConfiguracionVisualizacion();
            configVis.setIncluirRutasDetalladas(solicitud.isIncluirRutasDetalladas());
            configVis.setIncluirObstaculos(solicitud.isIncluirObstaculos());
            configVis.setIncluirTimeline(solicitud.isIncluirTimeline());
            
            SerializadorVisualizacionService.PayloadVisualizacionCompleto payload = 
                serializadorVisualizacionService.generarPayloadCompleto(
                    resultado.getRutasOptimizadas(),
                    combinarMetricas(resultado.getEstadisticasPedidos(), resultado.getEstadisticasRutas()),
                    configVis);
            
            // 5. Obtener métricas consolidadas
            PlanificacionIntegradorService.MetricasPlanificacionCompleta metricas = 
                planificacionIntegradorService.obtenerMetricasConsolidadas(resultado);
            
            // 6. Respuesta exitosa
            RespuestaPlanificacionCompleta respuesta = RespuestaPlanificacionCompleta.exitoso(
                rutasVisualizacion, payload, metricas, resultado.getTiempoProcesamientoMs());
            
            logger.info("Planificación completa exitosa: {} rutas generadas en {}ms", 
                rutasVisualizacion.size(), resultado.getTiempoProcesamientoMs());
            
            return ResponseEntity.ok(respuesta);
            
        } catch (Exception e) {
            logger.error("Error en planificación completa: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(RespuestaPlanificacionCompleta.error("Error interno: " + e.getMessage()));
        }
    }
    
    /**
     * Endpoint de integración: Desde asignaciones existentes
     * Input: Map<Camion, AsignacionCamion> del planificador de pedidos
     * Output: RutaVisualizacionDTO[] optimizado
     */
    @PostMapping("/integrar-desde-asignaciones")
    public ResponseEntity<RespuestaIntegracionAsignaciones> integrarDesdeAsignaciones(
            @RequestBody SolicitudIntegracionAsignaciones solicitud) {
        
        logger.info("Recibida solicitud de integración desde asignaciones: {} camiones", 
            solicitud.getAsignaciones().size());
        
        try {
            // 1. Convertir formato de entrada
            Map<Camion, AsignacionCamion> asignaciones = convertirAsignaciones(solicitud.getAsignaciones());
            
            // 2. Generar rutas optimizadas
            Map<Camion, RutaOptimizada> rutas = planificacionIntegradorService.integrarDesdeAsignaciones(
                asignaciones, solicitud.getAlmacenes());
            
            if (rutas.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(RespuestaIntegracionAsignaciones.error("No se generaron rutas válidas"));
            }
            
            // 3. Preparar para visualización
            List<RutaVisualizacionDTO> rutasVisualizacion = 
                serializadorVisualizacionService.convertirRutasParaVisualizacion(rutas);
            
            // 4. Respuesta exitosa
            RespuestaIntegracionAsignaciones respuesta = RespuestaIntegracionAsignaciones.exitoso(
                rutasVisualizacion, rutas);
            
            logger.info("Integración exitosa: {} rutas generadas", rutasVisualizacion.size());
            
            return ResponseEntity.ok(respuesta);
            
        } catch (Exception e) {
            logger.error("Error en integración desde asignaciones: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(RespuestaIntegracionAsignaciones.error("Error interno: " + e.getMessage()));
        }
    }
    
    /**
     * Endpoint para obtener solo datos de visualización optimizados
     * Input: Map<Camion, RutaOptimizada> 
     * Output: JSON optimizado para frontend
     */
    @PostMapping("/visualizacion/optimizar")
    public ResponseEntity<SerializadorVisualizacionService.PayloadVisualizacionCompleto> 
            optimizarParaVisualizacion(@RequestBody SolicitudOptimizacionVisualizacion solicitud) {
        
        try {
            // 1. Convertir rutas de entrada
            Map<Camion, RutaOptimizada> rutas = convertirRutasOptimizadas(solicitud.getRutas());
            
            // 2. Generar payload completo
            SerializadorVisualizacionService.PayloadVisualizacionCompleto payload = 
                serializadorVisualizacionService.generarPayloadCompleto(
                    rutas, 
                    solicitud.getMetricas(), 
                    solicitud.getConfiguracion());
            
            // 3. Optimizar según nivel solicitado
            SerializadorVisualizacionService.PayloadVisualizacionCompleto payloadOptimizado = 
                serializadorVisualizacionService.optimizarPayload(
                    payload, solicitud.getNivelOptimizacion());
            
            return ResponseEntity.ok(payloadOptimizado);
            
        } catch (Exception e) {
            logger.error("Error optimizando visualización: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Endpoint para validación previa de datos
     */
    @PostMapping("/validar-entrada")
    public ResponseEntity<PlanificacionIntegradorService.ResultadoValidacionEntrada> 
            validarEntrada(@RequestBody SolicitudValidacion solicitud) {
        
        try {
            PlanificacionIntegradorService.ResultadoValidacionEntrada resultado = 
                planificacionIntegradorService.validarDatosEntrada(
                    solicitud.getPedidos(),
                    solicitud.getCamiones(),
                    solicitud.getAlmacenes());
            
            return ResponseEntity.ok(resultado);
            
        } catch (Exception e) {
            logger.error("Error en validación: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    // Métodos auxiliares privados
    
    private Map<Camion, AsignacionCamion> convertirAsignaciones(List<AsignacionDTO> asignacionesDTO) {
        Map<Camion, AsignacionCamion> asignaciones = new HashMap<>();
        
        for (AsignacionDTO dto : asignacionesDTO) {
            Camion camion = dto.getCamion();
            AsignacionCamion asignacion = new AsignacionCamion(camion);
            
            for (Entrega entrega : dto.getEntregas()) {
                asignacion.agregarEntrega(entrega);
            }
            
            asignaciones.put(camion, asignacion);
        }
        
        return asignaciones;
    }
    
    private Map<Camion, RutaOptimizada> convertirRutasOptimizadas(List<RutaOptimizadaDTO> rutasDTO) {
        Map<Camion, RutaOptimizada> rutas = new HashMap<>();
        
        for (RutaOptimizadaDTO dto : rutasDTO) {
            rutas.put(dto.getCamion(), dto.getRuta());
        }
        
        return rutas;
    }
    
    private Map<String, Object> combinarMetricas(Map<String, Object> metricasPedidos, 
                                                Map<String, Object> metricasRutas) {
        Map<String, Object> combinadas = new HashMap<>();
        
        if (metricasPedidos != null) {
            combinadas.putAll(metricasPedidos);
        }
        if (metricasRutas != null) {
            combinadas.putAll(metricasRutas);
        }
        
        return combinadas;
    }
    
    // Clases DTO para requests/responses
    
    public static class SolicitudPlanificacionCompleta {
        private List<Pedido> pedidos;
        private List<Camion> camiones;
        private List<Almacen> almacenes;
        private boolean incluirRutasDetalladas = true;
        private boolean incluirObstaculos = true;
        private boolean incluirTimeline = true;
        
        // Getters y setters
        public List<Pedido> getPedidos() { return pedidos; }
        public void setPedidos(List<Pedido> pedidos) { this.pedidos = pedidos; }
        
        public List<Camion> getCamiones() { return camiones; }
        public void setCamiones(List<Camion> camiones) { this.camiones = camiones; }
        
        public List<Almacen> getAlmacenes() { return almacenes; }
        public void setAlmacenes(List<Almacen> almacenes) { this.almacenes = almacenes; }
        
        public boolean isIncluirRutasDetalladas() { return incluirRutasDetalladas; }
        public void setIncluirRutasDetalladas(boolean incluirRutasDetalladas) { this.incluirRutasDetalladas = incluirRutasDetalladas; }
        
        public boolean isIncluirObstaculos() { return incluirObstaculos; }
        public void setIncluirObstaculos(boolean incluirObstaculos) { this.incluirObstaculos = incluirObstaculos; }
        
        public boolean isIncluirTimeline() { return incluirTimeline; }
        public void setIncluirTimeline(boolean incluirTimeline) { this.incluirTimeline = incluirTimeline; }
    }
    
    public static class RespuestaPlanificacionCompleta {
        private boolean exitoso;
        private String mensaje;
        private List<RutaVisualizacionDTO> rutas;
        private SerializadorVisualizacionService.PayloadVisualizacionCompleto payload;
        private PlanificacionIntegradorService.MetricasPlanificacionCompleta metricas;
        private long tiempoProcesamientoMs;
        private Object errores;
        
        public static RespuestaPlanificacionCompleta exitoso(
                List<RutaVisualizacionDTO> rutas,
                SerializadorVisualizacionService.PayloadVisualizacionCompleto payload,
                PlanificacionIntegradorService.MetricasPlanificacionCompleta metricas,
                long tiempoMs) {
            
            RespuestaPlanificacionCompleta respuesta = new RespuestaPlanificacionCompleta();
            respuesta.setExitoso(true);
            respuesta.setMensaje("Planificación completada exitosamente");
            respuesta.setRutas(rutas);
            respuesta.setPayload(payload);
            respuesta.setMetricas(metricas);
            respuesta.setTiempoProcesamientoMs(tiempoMs);
            return respuesta;
        }
        
        public static RespuestaPlanificacionCompleta error(String mensaje, Object errores) {
            RespuestaPlanificacionCompleta respuesta = new RespuestaPlanificacionCompleta();
            respuesta.setExitoso(false);
            respuesta.setMensaje(mensaje);
            respuesta.setErrores(errores);
            return respuesta;
        }
        
        public static RespuestaPlanificacionCompleta error(String mensaje) {
            return error(mensaje, null);
        }
        
        // Getters y setters
        public boolean isExitoso() { return exitoso; }
        public void setExitoso(boolean exitoso) { this.exitoso = exitoso; }
        
        public String getMensaje() { return mensaje; }
        public void setMensaje(String mensaje) { this.mensaje = mensaje; }
        
        public List<RutaVisualizacionDTO> getRutas() { return rutas; }
        public void setRutas(List<RutaVisualizacionDTO> rutas) { this.rutas = rutas; }
        
        public SerializadorVisualizacionService.PayloadVisualizacionCompleto getPayload() { return payload; }
        public void setPayload(SerializadorVisualizacionService.PayloadVisualizacionCompleto payload) { this.payload = payload; }
        
        public PlanificacionIntegradorService.MetricasPlanificacionCompleta getMetricas() { return metricas; }
        public void setMetricas(PlanificacionIntegradorService.MetricasPlanificacionCompleta metricas) { this.metricas = metricas; }
        
        public long getTiempoProcesamientoMs() { return tiempoProcesamientoMs; }
        public void setTiempoProcesamientoMs(long tiempoProcesamientoMs) { this.tiempoProcesamientoMs = tiempoProcesamientoMs; }
        
        public Object getErrores() { return errores; }
        public void setErrores(Object errores) { this.errores = errores; }
    }
    
    public static class SolicitudIntegracionAsignaciones {
        private List<AsignacionDTO> asignaciones;
        private List<Almacen> almacenes;
        
        // Getters y setters
        public List<AsignacionDTO> getAsignaciones() { return asignaciones; }
        public void setAsignaciones(List<AsignacionDTO> asignaciones) { this.asignaciones = asignaciones; }
        
        public List<Almacen> getAlmacenes() { return almacenes; }
        public void setAlmacenes(List<Almacen> almacenes) { this.almacenes = almacenes; }
    }
    
    public static class AsignacionDTO {
        private Camion camion;
        private List<Entrega> entregas;
        
        // Getters y setters
        public Camion getCamion() { return camion; }
        public void setCamion(Camion camion) { this.camion = camion; }
        
        public List<Entrega> getEntregas() { return entregas; }
        public void setEntregas(List<Entrega> entregas) { this.entregas = entregas; }
    }
    
    public static class RespuestaIntegracionAsignaciones {
        private boolean exitoso;
        private String mensaje;
        private List<RutaVisualizacionDTO> rutas;
        private Map<Camion, RutaOptimizada> rutasDetalladas;
        
        public static RespuestaIntegracionAsignaciones exitoso(
                List<RutaVisualizacionDTO> rutas, 
                Map<Camion, RutaOptimizada> rutasDetalladas) {
            
            RespuestaIntegracionAsignaciones respuesta = new RespuestaIntegracionAsignaciones();
            respuesta.setExitoso(true);
            respuesta.setMensaje("Integración exitosa");
            respuesta.setRutas(rutas);
            respuesta.setRutasDetalladas(rutasDetalladas);
            return respuesta;
        }
        
        public static RespuestaIntegracionAsignaciones error(String mensaje) {
            RespuestaIntegracionAsignaciones respuesta = new RespuestaIntegracionAsignaciones();
            respuesta.setExitoso(false);
            respuesta.setMensaje(mensaje);
            return respuesta;
        }
        
        // Getters y setters
        public boolean isExitoso() { return exitoso; }
        public void setExitoso(boolean exitoso) { this.exitoso = exitoso; }
        
        public String getMensaje() { return mensaje; }
        public void setMensaje(String mensaje) { this.mensaje = mensaje; }
        
        public List<RutaVisualizacionDTO> getRutas() { return rutas; }
        public void setRutas(List<RutaVisualizacionDTO> rutas) { this.rutas = rutas; }
        
        public Map<Camion, RutaOptimizada> getRutasDetalladas() { return rutasDetalladas; }
        public void setRutasDetalladas(Map<Camion, RutaOptimizada> rutasDetalladas) { this.rutasDetalladas = rutasDetalladas; }
    }
    
    public static class SolicitudOptimizacionVisualizacion {
        private List<RutaOptimizadaDTO> rutas;
        private Map<String, Object> metricas;
        private SerializadorVisualizacionService.ConfiguracionVisualizacion configuracion;
        private int nivelOptimizacion = 1;
        
        // Getters y setters
        public List<RutaOptimizadaDTO> getRutas() { return rutas; }
        public void setRutas(List<RutaOptimizadaDTO> rutas) { this.rutas = rutas; }
        
        public Map<String, Object> getMetricas() { return metricas; }
        public void setMetricas(Map<String, Object> metricas) { this.metricas = metricas; }
        
        public SerializadorVisualizacionService.ConfiguracionVisualizacion getConfiguracion() { return configuracion; }
        public void setConfiguracion(SerializadorVisualizacionService.ConfiguracionVisualizacion configuracion) { this.configuracion = configuracion; }
        
        public int getNivelOptimizacion() { return nivelOptimizacion; }
        public void setNivelOptimizacion(int nivelOptimizacion) { this.nivelOptimizacion = nivelOptimizacion; }
    }
    
    public static class RutaOptimizadaDTO {
        private Camion camion;
        private RutaOptimizada ruta;
        
        // Getters y setters
        public Camion getCamion() { return camion; }
        public void setCamion(Camion camion) { this.camion = camion; }
        
        public RutaOptimizada getRuta() { return ruta; }
        public void setRuta(RutaOptimizada ruta) { this.ruta = ruta; }
    }
    
    public static class SolicitudValidacion {
        private List<Pedido> pedidos;
        private List<Camion> camiones;
        private List<Almacen> almacenes;
        
        // Getters y setters
        public List<Pedido> getPedidos() { return pedidos; }
        public void setPedidos(List<Pedido> pedidos) { this.pedidos = pedidos; }
        
        public List<Camion> getCamiones() { return camiones; }
        public void setCamiones(List<Camion> camiones) { this.camiones = camiones; }
        
        public List<Almacen> getAlmacenes() { return almacenes; }
        public void setAlmacenes(List<Almacen> almacenes) { this.almacenes = almacenes; }
    }
}