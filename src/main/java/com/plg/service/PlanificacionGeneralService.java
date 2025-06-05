package com.plg.service;

import com.plg.domain.*;
import com.plg.domain.enumeration.EstadoCamion;
import com.plg.dto.PlanificacionGeneralRequest;
import com.plg.dto.PlanificacionGeneralResponse;
import com.plg.repository.*;
import com.plg.service.util.GestorObstaculosTemporales;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 🚛 Servicio General de Planificación
 * 
 * Coordina la planificación completa considerando:
 * - Tiempo de simulación
 * - Filtrado de recursos según estado temporal
 * - Bloqueos temporales vigentes
 * - Integración PlanificadorPedidos + PlanificadorRutas
 */
@Service
@Transactional
public class PlanificacionGeneralService {
    
    private static final Logger logger = LoggerFactory.getLogger(PlanificacionGeneralService.class);
    
    // Servicios principales
    @Autowired
    private PlanificadorPedidosService planificadorPedidos;
    
    @Autowired
    private PlanificadorRutasService planificadorRutas;
    
    @Autowired
    private SimulationTimeService simulationTimeService;
    
    @Autowired
    private GestorObstaculosTemporales gestorObstaculosTemporales;
    
    // Repositorios
    @Autowired
    private PedidoRepository pedidoRepository;
    
    @Autowired
    private CamionRepository camionRepository;
    
    @Autowired
    private AlmacenRepository almacenRepository;
    
    @Autowired
    private ClienteRepository clienteRepository;
    
    /**
     * 🎯 Método principal: Ejecuta planificación completa
     */
    public PlanificacionGeneralResponse ejecutarPlanificacionCompleta(
            LocalDateTime tiempoSimulacion, 
            PlanificacionGeneralRequest request) {
        
        long tiempoInicio = System.currentTimeMillis();
        
        logger.info("🚀 INICIANDO PLANIFICACIÓN GENERAL");
        logger.info("⏰ Tiempo simulación: {}", 
            tiempoSimulacion.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
        
        PlanificacionGeneralResponse response = new PlanificacionGeneralResponse();
        response.setTiempoSimulacion(tiempoSimulacion);
        response.setTimestamp(LocalDateTime.now());
        
        try {
            // ===== 1. FILTRAR Y VALIDAR RECURSOS =====
            logger.info("1️⃣ Filtrando recursos según tiempo de simulación");
            RecursosDisponibles recursos = filtrarRecursosSegunTiempo(tiempoSimulacion);
            
            validarRecursosMinimos(recursos);
            
            // ===== 2. CONFIGURAR BLOQUEOS TEMPORALES =====
            logger.info("2️⃣ Configurando bloqueos temporales vigentes");
            configurarBloqueosVigentes(tiempoSimulacion);
            
            // ===== 3. EJECUTAR PLANIFICACIÓN DE PEDIDOS =====
            logger.info("3️⃣ Ejecutando planificación de pedidos");
            Map<Camion, AsignacionCamion> asignaciones = ejecutarPlanificacionPedidos(recursos);
            
            // ===== 4. EJECUTAR PLANIFICACIÓN DE RUTAS =====
            logger.info("4️⃣ Ejecutando planificación de rutas");
            Map<Camion, RutaOptimizada> rutas = ejecutarPlanificacionRutas(
                asignaciones, recursos.getAlmacenes());
            
            // ===== 5. COMPILAR RESULTADOS =====
            logger.info("5️⃣ Compilando resultados finales");
            compilarResultados(response, recursos, asignaciones, rutas);
            
            // ===== 6. MÉTRICAS Y ESTADÍSTICAS =====
            response.setExitoso(true);
            response.setTiempoEjecucionMs(System.currentTimeMillis() - tiempoInicio);
            
            logger.info("✅ PLANIFICACIÓN COMPLETADA EN {}ms", 
                response.getTiempoEjecucionMs());
            logger.info("📊 Resumen: {} camiones, {} rutas, {} pedidos atendidos", 
                response.getCamionesAsignados(), response.getRutasCalculadas(), 
                response.getPedidosAtendidos());
            
            return response;
            
        } catch (Exception e) {
            logger.error("❌ Error en planificación general: {}", e.getMessage(), e);
            
            response.setExitoso(false);
            response.setMensajeError(e.getMessage());
            response.setTiempoEjecucionMs(System.currentTimeMillis() - tiempoInicio);
            
            return response;
        }
    }
    
    /**
     * 🔍 Filtra recursos disponibles según el tiempo de simulación
     */
    private RecursosDisponibles filtrarRecursosSegunTiempo(LocalDateTime tiempoSimulacion) {
        logger.info("   🔍 FILTRADO DE RECURSOS:");
        
        // 📦 Pedidos pendientes (no asignados completamente)
        List<Pedido> pedidosPendientes = pedidoRepository.findPedidosNoAsignados();
        
        // Filtrar por tiempo (solo pedidos que ya fueron registrados)
        pedidosPendientes = pedidosPendientes.stream()
            .filter(pedido -> pedido.getFechaHoraRegistro().isBefore(tiempoSimulacion))
            .collect(Collectors.toList());
        
        logger.info("      📦 Pedidos pendientes: {}", pedidosPendientes.size());
        
        // 🚛 Camiones disponibles
        List<Camion> camionesDisponibles = camionRepository.findAll().stream()
            .filter(camion -> determinarEstadoCamion(camion, tiempoSimulacion) == EstadoCamion.DISPONIBLE)
            .collect(Collectors.toList());
        
        logger.info("      🚛 Camiones disponibles: {}", camionesDisponibles.size());
        
        // 🏢 Almacenes abastecidos
        List<Almacen> almacenesAbastecidos = almacenRepository.findAll().stream()
            .filter(almacen -> estaAlmacenAbastecido(almacen, tiempoSimulacion))
            .collect(Collectors.toList());
        
        logger.info("      🏢 Almacenes abastecidos: {}", almacenesAbastecidos.size());
        
        // 🚧 Bloqueos vigentes
        List<Obstaculo> bloqueosVigentes = gestorObstaculosTemporales
            .obtenerObstaculosVigentesEn(tiempoSimulacion);
        
        logger.info("      🚧 Bloqueos vigentes: {}", bloqueosVigentes.size());
        
        return new RecursosDisponibles(
            pedidosPendientes, 
            camionesDisponibles, 
            almacenesAbastecidos, 
            bloqueosVigentes
        );
    }
    
    /**
     * 🚛 Determina el estado de un camión en un momento específico
     */
    private EstadoCamion determinarEstadoCamion(Camion camion, LocalDateTime momento) {
        // TODO: Implementar lógica real basada en:
        // - Rutas activas
        // - Mantenimientos programados
        // - Averías temporales
        // - Ubicación actual
        
        // Por ahora, todos disponibles (implementación básica)
        return EstadoCamion.DISPONIBLE;
    }
    
    /**
     * 🏢 Verifica si un almacén está abastecido en un momento específico
     */
    private boolean estaAlmacenAbastecido(Almacen almacen, LocalDateTime momento) {
        // TODO: Implementar lógica real basada en:
        // - Horarios de abastecimiento
        // - Inventario actual
        // - Capacidad vs demanda
        
        // Por ahora, todos abastecidos excepto en horarios nocturnos
        int hora = momento.getHour();
        return hora >= 6 && hora <= 22; // Disponible de 6 AM a 10 PM
    }
    
    /**
     * ✅ Valida que hay recursos mínimos para planificar
     */
    private void validarRecursosMinimos(RecursosDisponibles recursos) {
        if (recursos.getPedidos().isEmpty()) {
            throw new IllegalStateException("No hay pedidos pendientes para planificar");
        }
        
        if (recursos.getCamiones().isEmpty()) {
            throw new IllegalStateException("No hay camiones disponibles para planificar");
        }
        
        if (recursos.getAlmacenes().isEmpty()) {
            throw new IllegalStateException("No hay almacenes abastecidos disponibles");
        }
        
        logger.info("   ✅ Recursos mínimos validados");
    }
    
    /**
     * 🚧 Configura bloqueos temporales vigentes en el gestor
     */
    private void configurarBloqueosVigentes(LocalDateTime tiempoSimulacion) {
        List<Obstaculo> bloqueosVigentes = gestorObstaculosTemporales
            .obtenerObstaculosVigentesEn(tiempoSimulacion);
        
        logger.info("   🚧 Configurando {} bloqueos vigentes", bloqueosVigentes.size());
        
        // Aquí el GestorObstaculosTemporales ya maneja la lógica
        // No necesitamos hacer más configuración manual
    }
    
    /**
     * 📦 Ejecuta planificación de pedidos
     */
    private Map<Camion, AsignacionCamion> ejecutarPlanificacionPedidos(RecursosDisponibles recursos) {
        logger.info("   📦 Ejecutando PlanificadorPedidos");
        
        // Calcular prioridades de pedidos si no están establecidas
        for (Pedido pedido : recursos.getPedidos()) {
            if (pedido.getPrioridad() == 0) {
                int prioridad = planificadorPedidos.calcularPrioridad(pedido);
                pedido.setPrioridad(prioridad);
            }
        }
        
        Map<Camion, AsignacionCamion> asignaciones = planificadorPedidos
            .planificarPedidos(recursos.getPedidos(), recursos.getCamiones());
        
        logger.info("   ✅ PlanificadorPedidos completado: {} asignaciones", 
            asignaciones.size());
        
        return asignaciones;
    }
    
    /**
     * 🛣️ Ejecuta planificación de rutas
     */
    private Map<Camion, RutaOptimizada> ejecutarPlanificacionRutas(
            Map<Camion, AsignacionCamion> asignaciones, 
            List<Almacen> almacenes) {
        
        logger.info("   🛣️ Ejecutando PlanificadorRutas");
        
        Map<Camion, RutaOptimizada> rutas = planificadorRutas
            .planificarRutas(asignaciones, almacenes);
        
        logger.info("   ✅ PlanificadorRutas completado: {} rutas", rutas.size());
        
        return rutas;
    }
    
    /**
     * 📊 Compila resultados finales
     */
    private void compilarResultados(
            PlanificacionGeneralResponse response,
            RecursosDisponibles recursos,
            Map<Camion, AsignacionCamion> asignaciones,
            Map<Camion, RutaOptimizada> rutas) {
        
        // Métricas básicas
        response.setCamionesAsignados(asignaciones.size());
        response.setRutasCalculadas(rutas.size());
        
        // Contar pedidos atendidos
        int pedidosAtendidos = asignaciones.values().stream()
            .mapToInt(AsignacionCamion::obtenerNumeroEntregas)
            .sum();
        response.setPedidosAtendidos(pedidosAtendidos);
        
        // Métricas de distancia y tiempo
        double distanciaTotal = rutas.values().stream()
            .mapToDouble(RutaOptimizada::getDistanciaTotalKm)
            .sum();
        response.setDistanciaTotalKm(distanciaTotal);
        
        double tiempoTotal = rutas.values().stream()
            .mapToDouble(RutaOptimizada::getTiempoEstimadoHoras)
            .sum();
        response.setTiempoTotalHoras(tiempoTotal);
        
        // Utilización promedio de camiones
        double utilizacionPromedio = asignaciones.values().stream()
            .mapToDouble(AsignacionCamion::obtenerPorcentajeUtilizacion)
            .average()
            .orElse(0.0);
        
        // Estadísticas adicionales
        Map<String, Object> estadisticas = new HashMap<>();
        estadisticas.put("pedidosDisponibles", recursos.getPedidos().size());
        estadisticas.put("camionesDisponibles", recursos.getCamiones().size());
        estadisticas.put("almacenesAbastecidos", recursos.getAlmacenes().size());
        estadisticas.put("bloqueosVigentes", recursos.getBloqueosVigentes().size());
        estadisticas.put("utilizacionPromedioCamiones", Math.round(utilizacionPromedio * 100.0) / 100.0);
        estadisticas.put("eficienciaAsignacion", calcularEficienciaAsignacion(asignaciones));
        estadisticas.put("distanciaPromedioPorCamion", 
            rutas.size() > 0 ? distanciaTotal / rutas.size() : 0.0);
        
        response.setEstadisticas(estadisticas);
        response.setAsignaciones(asignaciones);
        response.setRutas(rutas);
    }
    
    /**
     * 📊 Obtiene estado actual de recursos
     */
    public Map<String, Object> obtenerEstadoRecursos(LocalDateTime tiempoSimulacion) {
        logger.info("📊 Obteniendo estado de recursos para: {}", tiempoSimulacion);
        
        RecursosDisponibles recursos = filtrarRecursosSegunTiempo(tiempoSimulacion);
        
        Map<String, Object> estado = new HashMap<>();
        estado.put("pedidosPendientes", recursos.getPedidos().size());
        estado.put("camionesDisponibles", recursos.getCamiones().size());
        estado.put("almacenesAbastecidos", recursos.getAlmacenes().size());
        estado.put("bloqueosVigentes", recursos.getBloqueosVigentes().size());
        
        // Detalles de pedidos
        Map<String, Object> detallesPedidos = new HashMap<>();
        detallesPedidos.put("total", recursos.getPedidos().size());
        detallesPedidos.put("volumenTotal", recursos.getPedidos().stream()
            .mapToDouble(Pedido::getVolumenM3).sum());
        detallesPedidos.put("prioridadPromedio", recursos.getPedidos().stream()
            .mapToInt(Pedido::getPrioridad).average().orElse(0.0));
        
        estado.put("detallesPedidos", detallesPedidos);
        
        // Detalles de camiones
        Map<String, Object> detallesCamiones = new HashMap<>();
        detallesCamiones.put("total", recursos.getCamiones().size());
        detallesCamiones.put("capacidadTotal", recursos.getCamiones().stream()
            .mapToInt(Camion::getMaxCargaM3).sum());
        detallesCamiones.put("tipos", recursos.getCamiones().stream()
            .collect(Collectors.groupingBy(Camion::getTipo, Collectors.counting())));
        
        estado.put("detallesCamiones", detallesCamiones);
        
        return estado;
    }
    
    /**
     * 📈 Obtiene métricas de planificación
     */
    public Map<String, Object> obtenerMetricas() {
        Map<String, Object> metricas = new HashMap<>();
        
        // Métricas de servicios principales
        metricas.put("pedidos", planificadorPedidos.obtenerEstadisticasPlanificacion(Collections.emptyList()));
        metricas.put("rutas", planificadorRutas.obtenerEstadisticas());
        metricas.put("bloqueos", gestorObstaculosTemporales.obtenerEstadisticas());
        
        return metricas;
    }
    
    /**
     * 🧪 Ejecuta test de escenario completo (como el JUnit)
     */
    public Map<String, Object> ejecutarTestEscenarioCompleto() {
        logger.info("🧪 EJECUTANDO TEST DE ESCENARIO COMPLETO");
        
        Map<String, Object> resultado = new HashMap<>();
        
        try {
            // Usar tiempo específico para test
            LocalDateTime tiempoTest = LocalDateTime.of(2025, 1, 15, 14, 30, 0);
            
            PlanificacionGeneralRequest request = new PlanificacionGeneralRequest();
            request.setTiempoSimulacion(tiempoTest);
            
            PlanificacionGeneralResponse response = ejecutarPlanificacionCompleta(tiempoTest, request);
            
            resultado.put("exitoso", response.isExitoso());
            resultado.put("tiempoEjecucion", response.getTiempoEjecucionMs());
            resultado.put("camionesAsignados", response.getCamionesAsignados());
            resultado.put("rutasCalculadas", response.getRutasCalculadas());
            resultado.put("pedidosAtendidos", response.getPedidosAtendidos());
            resultado.put("estadisticas", response.getEstadisticas());
            resultado.put("mensaje", "Test de escenario ejecutado exitosamente");
            
        } catch (Exception e) {
            logger.error("❌ Error en test de escenario: {}", e.getMessage(), e);
            resultado.put("exitoso", false);
            resultado.put("error", e.getMessage());
        }
        
        return resultado;
    }
    
    /**
     * 🔄 Reinicia servicios
     */
    public void reiniciarServicios() {
        logger.info("🔄 Reiniciando servicios de planificación");
        
        planificadorPedidos.reiniciar();
        planificadorRutas.reiniciar();
        
        logger.info("✅ Servicios reiniciados");
    }
    
    // Métodos auxiliares
    
    private double calcularEficienciaAsignacion(Map<Camion, AsignacionCamion> asignaciones) {
        if (asignaciones.isEmpty()) return 0.0;
        
        double sumaUtilizacion = asignaciones.values().stream()
            .mapToDouble(AsignacionCamion::obtenerPorcentajeUtilizacion)
            .sum();
        
        return sumaUtilizacion / asignaciones.size();
    }
    
    /**
     * 📂 Clase auxiliar para recursos filtrados
     */
    public static class RecursosDisponibles {
        private final List<Pedido> pedidos;
        private final List<Camion> camiones;
        private final List<Almacen> almacenes;
        private final List<Obstaculo> bloqueosVigentes;
        
        public RecursosDisponibles(List<Pedido> pedidos, List<Camion> camiones, 
                                 List<Almacen> almacenes, List<Obstaculo> bloqueosVigentes) {
            this.pedidos = pedidos;
            this.camiones = camiones;
            this.almacenes = almacenes;
            this.bloqueosVigentes = bloqueosVigentes;
        }
        
        public List<Pedido> getPedidos() { return pedidos; }
        public List<Camion> getCamiones() { return camiones; }
        public List<Almacen> getAlmacenes() { return almacenes; }
        public List<Obstaculo> getBloqueosVigentes() { return bloqueosVigentes; }
    }
}