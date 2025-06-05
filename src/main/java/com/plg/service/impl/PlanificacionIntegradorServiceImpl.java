// package com.plg.service.impl;

// import com.plg.domain.*;
// import com.plg.dto.RutaVisualizacionDTO;
// import com.plg.service.*;
// import com.plg.service.util.GestorObstaculos;
// import com.plg.service.util.GestorObstaculosTemporales;

// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
// import org.slf4j.MDC;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.stereotype.Service;

// import jakarta.annotation.PostConstruct;
// import java.time.LocalDateTime;
// import java.util.*;
// import java.util.concurrent.ConcurrentHashMap;
// import java.util.concurrent.atomic.AtomicLong;
// import java.util.stream.Collectors;

// /**
//  * Implementación del integrador principal
//  * Coordina planificador de pedidos + planificador de rutas + validaciones
//  */
// @Service
// public class PlanificacionIntegradorServiceImpl implements PlanificacionIntegradorService {
    
//     private static final Logger logger = LoggerFactory.getLogger(PlanificacionIntegradorServiceImpl.class);
    
//     @Autowired
//     private PlanificadorPedidosService planificadorPedidosService;
    
//     @Autowired
//     private PlanificadorRutasService planificadorRutasService;
    
//     @Autowired
//     private ValidadorRestriccionesService validadorRestriccionesService;
    
//     @Autowired
//     private ValidadorDatosService validadorDatosService;
    
//     @Autowired
//     private TransformadorDatosService transformadorDatosService;
    
//     @Autowired
//     private SerializadorVisualizacionService serializadorVisualizacionService;
    
//     @Autowired
//     private PedidoService pedidoService;

//     @Autowired  
//     private CamionService camionService;

//     @Autowired
//     private AlmacenService almacenService;
//     @Autowired
//         private GestorObstaculos gestorObstaculos;
//     @Autowired
//     private SimulationTimeService simulationTimeService;
    
//     // ✅ NUEVO: Para manejar obstáculos temporales
//     @Autowired
//     private ObstaculoService obstaculoService;
//     @Autowired
//     private GestorObstaculosTemporales gestorObstaculosTemporales;
    
//     private final AtomicLong correlationIdCounter = new AtomicLong(0);
    
//     // Configuración
//     private ConfiguracionIntegracion configuracion;
//     private Map<Camion, RutaOptimizada> rutasEnMemoria = new ConcurrentHashMap<>();
    
//     // ✅ NUEVO: Flag para controlar inicialización
//     private volatile boolean planificadorInicializado = false;
    
//     public PlanificacionIntegradorServiceImpl() {
//         this.configuracion = new ConfiguracionIntegracion();
//     }
    
//     /**
//      * ✅ INICIALIZACIÓN AUTOMÁTICA al arrancar Spring (CORREGIDA - sin archivo)
//      */
//     @PostConstruct
//     public void inicializarPlanificador() {
//         try {
//             logger.info("🔧 Inicializando automáticamente el planificador de rutas...");
            
//             // 1. Obtener almacenes para warm-up del cache
//             List<Almacen> almacenesActivos = almacenService.obtenerTodos();
            
//             // 2. ✅ CORREGIDO: No usar archivo físico, usar null para indicar BD
//             String rutaObstaculos = null; // null = usar obstáculos de BD
            
//             // 3. Inicializar el planificador de rutas
//             planificadorRutasService.inicializar(gestorObstaculos, almacenesActivos);
            
//             this.planificadorInicializado = true;
            
//             logger.info("✅ Planificador de rutas inicializado automáticamente exitosamente");
            
//         } catch (Exception e) {
//             logger.error("❌ Error en inicialización automática del planificador: {}", e.getMessage(), e);
//             // No lanzar excepción para no impedir el arranque de Spring
//             this.planificadorInicializado = false;
//         }
//     }
    
//     /**
//      * ✅ MÉTODO CORREGIDO: Inicialización manual si es necesaria
//      */
//     private void asegurarPlanificadorInicializado() {
//         if (!planificadorInicializado) {
//             logger.warn("⚠️ Planificador no inicializado, ejecutando inicialización manual...");
//             inicializarPlanificador();
//         }
//     }
    
//     @Override
//     public Map<Camion, RutaOptimizada> ejecutarPlanificacionCompleta() {
//         try {
//             logger.info("🚀 Ejecutando planificación completa desde BD...");
            
//             // ✅ ASEGURAR QUE ESTÉ INICIALIZADO
//             asegurarPlanificadorInicializado();
            
//             // 1. ✅ MODIFICADO: Obtener solo pedidos críticos (como solicitaste)
//             List<Pedido> pedidosCriticos = pedidoService.obtenerPedidosCriticos(); // ← CAMBIADO AQUÍ
            
//             // 2. ✅ CAMIONES DISPONIBLES - Dinámico en cada solicitud
//             // TODO: Para hacer dinámico camiones según disponibilidad real:
//             // List<Camion> camionesDisponibles = camionService.obtenerCamionesDisponibles(simulationTimeService.getCurrentSimulationTime());
//             List<Camion> camionesDisponibles = camionService.obtenerTodos();
            
//             // 3. ✅ ALMACENES ACTIVOS - Dinámico en cada solicitud  
//             // TODO: Para hacer dinámico almacenes según abastecimiento:
//             // List<Almacen> almacenesActivos = almacenService.obtenerAlmacenesAbastecidos(simulationTimeService.getCurrentSimulationTime());
//             List<Almacen> almacenesActivos = almacenService.obtenerTodos();
            
//             logger.info("📊 Datos obtenidos - Pedidos CRÍTICOS: {}, Camiones: {}, Almacenes: {}", 
//                     pedidosCriticos.size(), camionesDisponibles.size(), almacenesActivos.size());
            
//             if (pedidosCriticos.isEmpty()) {
//                 logger.info("✅ No hay pedidos críticos en este momento");
//                 return new HashMap<>();
//             }
            
//             // 4. ✅ ACTUALIZAR OBSTÁCULOS según tiempo de simulación actual
//             actualizarObstaculosSegunTiempoSimulacion();
            
//             // 5. Ejecutar planificación completa
//             ResultadoPlanificacionCompleta resultado = planificarCompleto(
//                 pedidosCriticos, camionesDisponibles, almacenesActivos);
            
//             if (!resultado.isProcesadoExitoso()) {
//                 throw new RuntimeException("Planificación fallida: " + resultado.getAdvertencias());
//             }
            
//             // 6. Actualizar cache interno
//             Map<Camion, RutaOptimizada> rutasCalculadas = resultado.getRutasOptimizadas();
//             rutasEnMemoria.clear();
//             rutasEnMemoria.putAll(rutasCalculadas);
            
//             logger.info("🏁 Planificación completa finalizada: {} rutas generadas para pedidos críticos", rutasCalculadas.size());
            
//             return rutasCalculadas;
            
//         } catch (Exception e) {
//             logger.error("❌ Error en planificación completa: {}", e.getMessage(), e);
//             throw new RuntimeException("Error ejecutando planificación completa", e);
//         }
//     }
    
//     /**
//      * ✅ NUEVO MÉTODO: Actualiza obstáculos según tiempo de simulación
//      */
//     private void actualizarObstaculosSegunTiempoSimulacion() {
//         try {
//             LocalDateTime tiempoSimulacion = simulationTimeService.getCurrentSimulationTime();
            
//             logger.debug("🚧 Actualizando obstáculos para tiempo simulación: {}", tiempoSimulacion);
            
//             // ✅ CORREGIDO: Usar directamente el servicio dedicado en lugar de filtrado manual
//             List<Obstaculo> obstaculosVigentes = gestorObstaculosTemporales.obtenerObstaculosVigentes();
            
//             // Para logging, obtener también el total (opcional)
//             List<Obstaculo> todosObstaculos = obstaculoService.obtenerTodos();
            
//             logger.info("🚧 Obstáculos vigentes: {} de {} totales para tiempo {}", 
//                 obstaculosVigentes.size(), todosObstaculos.size(), tiempoSimulacion);
            
//             // TODO: Si tienes un método para actualizar obstáculos en el gestor:
//             // gestorObstaculos.actualizarObstaculos(obstaculosVigentes);
            
//         } catch (Exception e) {
//             logger.error("❌ Error actualizando obstáculos según tiempo simulación: {}", e.getMessage(), e);
//             // No lanzar excepción para no interrumpir la planificación
//         }
//     }
    
//     /**
//      * ✅ NUEVO MÉTODO: Ejecuta planificación SOLO para pedidos críticos
//      */
//     public Map<Camion, RutaOptimizada> ejecutarPlanificacionPedidosCriticos() {
//         try {
//             logger.info("🚨 Ejecutando planificación SOLO para pedidos críticos...");
            
//             // ✅ ASEGURAR QUE ESTÉ INICIALIZADO
//             asegurarPlanificadorInicializado();
            
//             // 1. Obtener tiempo actual de simulación
//             LocalDateTime tiempoSimulacion = simulationTimeService.getCurrentSimulationTime();
            
//             // 2. ✅ SOLO obtener pedidos críticos (no todos)
//             List<Pedido> pedidosCriticos = pedidoService.obtenerPedidosCriticos(tiempoSimulacion, 20);
            
//             // 3. ✅ DINÁMICO: Obtener camiones y almacenes en cada solicitud
//             // TODO: Para hacer dinámico según disponibilidad real:
//             // List<Camion> camionesDisponibles = camionService.obtenerCamionesDisponiblesEnTiempo(tiempoSimulacion);
//             // List<Almacen> almacenesActivos = almacenService.obtenerAlmacenesAbastecidosEnTiempo(tiempoSimulacion);
//             List<Camion> camionesDisponibles = camionService.obtenerTodos();
//             List<Almacen> almacenesActivos = almacenService.obtenerTodos();
            
//             logger.info("🚨 Datos críticos obtenidos - Pedidos CRÍTICOS: {}, Camiones: {}, Almacenes: {}", 
//                     pedidosCriticos.size(), camionesDisponibles.size(), almacenesActivos.size());
            
//             if (pedidosCriticos.isEmpty()) {
//                 logger.info("✅ No hay pedidos críticos en este momento");
//                 return new HashMap<>();
//             }
            
//             // 4. ✅ ACTUALIZAR OBSTÁCULOS según tiempo de simulación actual
//             actualizarObstaculosSegunTiempoSimulacion();
            
//             // 5. Ejecutar planificación SOLO con pedidos críticos
//             ResultadoPlanificacionCompleta resultado = planificarCompleto(
//                 pedidosCriticos, camionesDisponibles, almacenesActivos);
            
//             if (!resultado.isProcesadoExitoso()) {
//                 throw new RuntimeException("Planificación de críticos fallida: " + resultado.getAdvertencias());
//             }
            
//             // 6. Actualizar cache interno SOLO con rutas críticas
//             Map<Camion, RutaOptimizada> rutasCriticas = resultado.getRutasOptimizadas();
            
//             logger.info("🏁 Planificación de pedidos críticos finalizada: {} rutas generadas para {} pedidos críticos", 
//                 rutasCriticas.size(), pedidosCriticos.size());
            
//             return rutasCriticas;
            
//         } catch (Exception e) {
//             logger.error("❌ Error en planificación de pedidos críticos: {}", e.getMessage(), e);
//             throw new RuntimeException("Error ejecutando planificación de pedidos críticos", e);
//         }
//     }
    
//     /**
//      * ✅ NUEVO MÉTODO: Obtiene solo las rutas que contienen pedidos críticos
//      */
//     public Map<Camion, RutaOptimizada> obtenerRutasConPedidosCriticos() {
//         try {
//             logger.info("🔍 Filtrando rutas que contienen pedidos críticos...");
            
//             // 1. Obtener pedidos críticos actuales
//             LocalDateTime tiempoSimulacion = simulationTimeService.getCurrentSimulationTime();
//             List<Pedido> pedidosCriticos = pedidoService.obtenerPedidosCriticos(tiempoSimulacion, 50);
            
//             if (pedidosCriticos.isEmpty()) {
//                 logger.info("✅ No hay pedidos críticos para filtrar");
//                 return new HashMap<>();
//             }
            
//             // 2. Obtener todas las rutas actuales (o generar si no existen)
//             Map<Camion, RutaOptimizada> todasLasRutas = rutasEnMemoria.isEmpty() ? 
//                 ejecutarPlanificacionCompleta() : rutasEnMemoria;
            
//             // 3. Filtrar solo rutas con pedidos críticos
//             Set<Integer> idsPedidosCriticos = pedidosCriticos.stream()
//                 .map(Pedido::getId)
//                 .collect(Collectors.toSet());
            
//             Map<Camion, RutaOptimizada> rutasFiltradas = new HashMap<>();
            
//             for (Map.Entry<Camion, RutaOptimizada> entry : todasLasRutas.entrySet()) {
//                 RutaOptimizada ruta = entry.getValue();
                
//                 // Verificar si la ruta contiene algún pedido crítico
//                 boolean contienePedidoCritico = ruta.obtenerEntregasEnOrden().stream()
//                     .anyMatch(entrega -> idsPedidosCriticos.contains(entrega.getPedido().getId()));
                
//                 if (contienePedidoCritico) {
//                     rutasFiltradas.put(entry.getKey(), ruta);
//                 }
//             }
            
//             logger.info("🔍 Filtrado completado: {} rutas contienen pedidos críticos de {} rutas totales", 
//                 rutasFiltradas.size(), todasLasRutas.size());
            
//             return rutasFiltradas;
            
//         } catch (Exception e) {
//             logger.error("❌ Error filtrando rutas con pedidos críticos: {}", e.getMessage(), e);
//             return new HashMap<>();
//         }
//     }
    
//     @Override
//     public ResultadoPlanificacionCompleta planificarCompleto(List<Pedido> pedidosPendientes, 
//                                                            List<Camion> camionesDisponibles, 
//                                                            List<Almacen> almacenesOperativos) {
//         String correlationId = "PLAN-" + System.currentTimeMillis() + "-" + correlationIdCounter.incrementAndGet();
//         MDC.put("correlationId", correlationId);
//         logger.info("Iniciando planificación completa: {} pedidos, {} camiones, {} almacenes", 
//             pedidosPendientes.size(), camionesDisponibles.size(), almacenesOperativos.size());
        
//         long tiempoInicio = System.currentTimeMillis();
//         ResultadoPlanificacionCompleta resultado = new ResultadoPlanificacionCompleta();
        
//         try {
//             // ✅ ASEGURAR QUE ESTÉ INICIALIZADO
//             asegurarPlanificadorInicializado();
            
//             // 1. Validación de datos de entrada
//             if (configuracion.isHabilitarValidacionEstricta()) {
//                 ResultadoValidacionEntrada validacion = validarDatosEntrada(
//                     pedidosPendientes, camionesDisponibles, almacenesOperativos);
                
//                 if (!validacion.isDatosValidos()) {
//                     resultado.setProcesadoExitoso(false);
//                     resultado.agregarAdvertencia("Validación de entrada falló: " + validacion.getTotalErrores() + " errores");
//                     return resultado;
//                 }
//             }
            
//             // 2. Crear copias de trabajo para no modificar originales
//             List<Pedido> pedidosCopia = crearCopiasPedidos(pedidosPendientes);
//             List<Camion> camionesCopia = new ArrayList<>(camionesDisponibles);
//             List<Almacen> almacenesCopia = new ArrayList<>(almacenesOperativos);
            
//             // 3. Planificación de pedidos → asignaciones
//             logger.info("[{}] FASE 1: Ejecutando planificación de pedidos", correlationId);
//             Map<Camion, AsignacionCamion> asignaciones = planificadorPedidosService.planificarPedidos(
//                 pedidosCopia, camionesCopia);
            
//             if (asignaciones.isEmpty()) {
//                 logger.error("[{}] FASE 1: FALLO - No se generaron asignaciones de pedidos", correlationId);
//                 resultado.setProcesadoExitoso(false);
//                 resultado.agregarAdvertencia("No se generaron asignaciones de pedidos");
//                 return resultado;
//             }
//             logger.info("[{}] FASE 1: ÉXITO - {} camiones con asignaciones generadas", correlationId, asignaciones.size());
//             resultado.setAsignacionesPedidos(new HashMap<>(asignaciones));
            
//             // 4. Obtener estadísticas de asignación
//             List<AsignacionCamion> listaAsignaciones = new ArrayList<>(asignaciones.values());
//             Map<String, Object> estadisticasPedidos = planificadorPedidosService.obtenerEstadisticasPlanificacion(listaAsignaciones);
//             resultado.setEstadisticasPedidos(estadisticasPedidos);
            
//             // 5. Planificación de rutas → rutas optimizadas
//             logger.info("[{}] FASE 2: Ejecutando planificación de rutas", correlationId);
//             Map<Camion, RutaOptimizada> rutas = planificadorRutasService.planificarRutas(
//                 asignaciones, almacenesCopia);
//             logger.info("[{}] FASE 2: ÉXITO - {} rutas optimizadas generadas", correlationId, rutas.size());
//             resultado.setRutasOptimizadas(rutas);
            
//             // 6. Obtener estadísticas de rutas
//             Map<String, Object> estadisticasRutas = planificadorRutasService.obtenerEstadisticasPlanificacion(rutas);
//             resultado.setEstadisticasRutas(estadisticasRutas);
            
//             // 7. Validación final de rutas
//             if (configuracion.isHabilitarValidacionEstricta()) {
//                 PlanificadorRutasService.ResultadoValidacionRutas validacionRutas = 
//                     planificadorRutasService.validarRutas(rutas);
                
//                 if (!validacionRutas.isTodasRutasValidas()) {
//                     resultado.agregarAdvertencia(
//                         String.format("Validación de rutas: %d problemáticas de %d totales", 
//                             validacionRutas.getRutasProblematicas(), rutas.size()));
//                 }
//             }
            
//             // 8. Marcar como exitoso
//             resultado.setProcesadoExitoso(true);
            
//             logger.info("Planificación completa exitosa: {} camiones con rutas asignadas", rutas.size());
            
//         } catch (Exception e) {
//             logger.error("Error en planificación completa: {}", e.getMessage(), e);
//             resultado.setProcesadoExitoso(false);
//             resultado.agregarAdvertencia("Error interno: " + e.getMessage());
//         } finally {
//             resultado.setTiempoProcesamientoMs(System.currentTimeMillis() - tiempoInicio);
//             logger.info("[{}] Planificación finalizada en {}ms", correlationId, resultado.getTiempoProcesamientoMs());
//             MDC.remove("correlationId");
//         }
        
//         logger.info("📊 RESUMEN FINAL DE PLANIFICACIÓN:");
//         logger.info("═════════════════════════════════");
//         logger.info("📦 Pedidos procesados: {}", pedidosPendientes.size());
//         logger.info("🚛 Camiones disponibles: {}", camionesDisponibles.size());
//         logger.info("🚛 Camiones con asignaciones: {}", resultado.getAsignacionesPedidos().size());
//         logger.info("🛣️ Rutas generadas: {}", resultado.getRutasOptimizadas().size());

//         // Log detallado de asignaciones finales
//         if (resultado.getAsignacionesPedidos() != null) {
//             logger.info("📦 ASIGNACIONES FINALES:");
//             for (Map.Entry<Camion, AsignacionCamion> entry : resultado.getAsignacionesPedidos().entrySet()) {
//                 Camion camion = entry.getKey();
//                 AsignacionCamion asignacion = entry.getValue();
                
//                 logger.info("   🚛 {} ({}): {} entregas - {:.1f}/{} m³ ({:.1f}%)",
//                     camion.getCodigo(), camion.getTipo(),
//                     asignacion.obtenerNumeroEntregas(),
//                     asignacion.getCapacidadUtilizada(),
//                     camion.getMaxCargaM3(),
//                     asignacion.obtenerPorcentajeUtilizacion());
//             }
//         }

//         // Log detallado de rutas finales
//         if (resultado.getRutasOptimizadas() != null) {
//             logger.info("🛣️ RUTAS FINALES:");
//             for (Map.Entry<Camion, RutaOptimizada> entry : resultado.getRutasOptimizadas().entrySet()) {
//                 Camion camion = entry.getKey();
//                 RutaOptimizada ruta = entry.getValue();
                
//                 logger.info("   🚛 {} ({}): {} entregas - {:.1f} km - {:.1f} h - {}",
//                     camion.getCodigo(), camion.getTipo(),
//                     ruta.getNumeroEntregas(),
//                     ruta.getDistanciaTotalKm(),
//                     ruta.getTiempoEstimadoHoras(),
//                     ruta.isRutaViable() ? "VIABLE" : "NO VIABLE");
                
//                 // Log entregas en la ruta
//                 List<Entrega> entregas = ruta.obtenerEntregasEnOrden();
//                 if (!entregas.isEmpty()) {
//                     logger.info("     📦 Entregas en ruta:");
//                     for (int i = 0; i < entregas.size(); i++) {
//                         Entrega entrega = entregas.get(i);
//                         logger.info("       [{}] Pedido {} - ({},{}) - {:.1f} m³",
//                             i+1, entrega.getPedido().getId(),
//                             entrega.getPedido().getUbicacionX(), entrega.getPedido().getUbicacionY(),
//                             entrega.getVolumenEntregadoM3());
//                     }
//                 } else {
//                     logger.warn("     ⚠️ RUTA SIN ENTREGAS (posible problema)");
//                 }
//             }
//         }
//         return resultado;
//     }
    
//     // ✅ RESTO DE MÉTODOS PERMANECEN IGUAL...
    
//     @Override
//     public Map<Camion, RutaOptimizada> integrarDesdeAsignaciones(
//             Map<Camion, AsignacionCamion> asignacionesExistentes,
//             List<Almacen> almacenesDisponibles) {
        
//         logger.info("Integrando desde asignaciones existentes: {} camiones", asignacionesExistentes.size());
        
//         // ✅ ASEGURAR QUE ESTÉ INICIALIZADO
//         asegurarPlanificadorInicializado();
        
//         // Validar entrada
//         if (asignacionesExistentes == null || asignacionesExistentes.isEmpty()) {
//             logger.warn("Asignaciones vacías o nulas");
//             return new HashMap<>();
//         }
        
//         // Crear copias para no modificar originales
//         Map<Camion, AsignacionCamion> asignacionesCopia = crearCopiasAsignaciones(asignacionesExistentes);
//         List<Almacen> almacenesCopia = new ArrayList<>(almacenesDisponibles);
        
//         try {
//             // Delegar al planificador de rutas
//             Map<Camion, RutaOptimizada> rutas = planificadorRutasService.planificarRutas(
//                 asignacionesCopia, almacenesCopia);
            
//             logger.info("Integración exitosa: {} rutas generadas", rutas.size());
//             return rutas;
            
//         } catch (Exception e) {
//             logger.error("Error en integración desde asignaciones: {}", e.getMessage(), e);
//             return new HashMap<>();
//         }
//     }
    
//     @Override
//     public ResultadoValidacionEntrada validarDatosEntrada(List<Pedido> pedidos, 
//                                                          List<Camion> camiones, 
//                                                          List<Almacen> almacenes) {
        
//         logger.debug("Validando datos de entrada");
        
//         ResultadoValidacionEntrada resultado = new ResultadoValidacionEntrada();
        
//         // Validar pedidos
//         if (pedidos == null || pedidos.isEmpty()) {
//             resultado.agregarErrorPedido("Lista de pedidos vacía o nula");
//         } else {
//             for (int i = 0; i < pedidos.size(); i++) {
//                 Pedido pedido = pedidos.get(i);
//                 if (pedido == null) {
//                     resultado.agregarErrorPedido("Pedido " + i + " es nulo");
//                     continue;
//                 }
                
//                 if (pedido.getUbicacionX() == null || pedido.getUbicacionY() == null) {
//                     resultado.agregarErrorPedido("Pedido " + pedido.getId() + " sin coordenadas válidas");
//                 }
                
//                 if (pedido.getVolumenM3() <= 0) {
//                     resultado.agregarErrorPedido("Pedido " + pedido.getId() + " con volumen inválido: " + pedido.getVolumenM3());
//                 }
//             }
//         }
        
//         // Validar camiones
//         if (camiones == null || camiones.isEmpty()) {
//             resultado.agregarErrorCamion("Lista de camiones vacía o nula");
//         } else {
//             for (int i = 0; i < camiones.size(); i++) {
//                 Camion camion = camiones.get(i);
//                 if (camion == null) {
//                     resultado.agregarErrorCamion("Camión " + i + " es nulo");
//                     continue;
//                 }
                
//                 if (camion.getMaxCargaM3() == null || camion.getMaxCargaM3() <= 0) {
//                     resultado.agregarErrorCamion("Camión " + camion.getId() + " con capacidad inválida");
//                 }
//             }
//         }
        
//         // Validar almacenes
//         if (almacenes == null || almacenes.isEmpty()) {
//             resultado.agregarErrorAlmacen("Lista de almacenes vacía o nula");
//         } else {
//             for (int i = 0; i < almacenes.size(); i++) {
//                 Almacen almacen = almacenes.get(i);
//                 if (almacen == null) {
//                     resultado.agregarErrorAlmacen("Almacén " + i + " es nulo");
//                     continue;
//                 }
                
//                 // Validar coordenadas de almacén usando validador de datos
//                 if (!validadorDatosService.sonCoordenadasValidas(almacen.getX(), almacen.getY())) {
//                     resultado.agregarErrorAlmacen("Almacén " + almacen.getId() + " con coordenadas inválidas");
//                 }
//             }
//         }
        
//         // Generar estadísticas
//         resultado.getEstadisticasEntrada().put("totalPedidos", pedidos != null ? pedidos.size() : 0);
//         resultado.getEstadisticasEntrada().put("totalCamiones", camiones != null ? camiones.size() : 0);
//         resultado.getEstadisticasEntrada().put("totalAlmacenes", almacenes != null ? almacenes.size() : 0);
//         resultado.getEstadisticasEntrada().put("totalErrores", resultado.getTotalErrores());
        
//         // Determinar validez general
//         resultado.setDatosValidos(resultado.getTotalErrores() == 0);
        
//         logger.debug("Validación completada: {} errores encontrados", resultado.getTotalErrores());
        
//         return resultado;
//     }
    
//     @Override
//     public List<RutaVisualizacionDTO> prepararParaVisualizacion(Map<Camion, RutaOptimizada> rutas) {
//         logger.debug("Preparando {} rutas para visualización", rutas.size());
        
//         return serializadorVisualizacionService.convertirRutasParaVisualizacion(rutas);
//     }
    
//     @Override
//     public ResultadoReplanificacion replanificarPorEventos(Map<Camion, RutaOptimizada> rutasActuales,
//                                                           List<EventoAveria> eventosAverias,
//                                                           DatosActualizacion nuevosDatos) {
        
//         logger.info("Re-planificando por {} eventos de avería", eventosAverias.size());
        
//         // ✅ ASEGURAR QUE ESTÉ INICIALIZADO
//         asegurarPlanificadorInicializado();
        
//         ResultadoReplanificacion resultado = new ResultadoReplanificacion();
        
//         try {
//             // Identificar camiones afectados
//             List<Camion> camionesAfectados = eventosAverias.stream()
//                 .map(EventoAveria::getCamionAfectado)
//                 .collect(Collectors.toList());
            
//             resultado.setCamionesAfectados(camionesAfectados);
            
//             // Delegar al planificador de rutas
//             Map<Camion, RutaOptimizada> rutasReplanificadas = planificadorRutasService.replanificarPorAverias(
//                 rutasActuales, camionesAfectados, nuevosDatos.getNuevosAlmacenes());
            
//             resultado.setRutasReplanificadas(rutasReplanificadas);
//             resultado.setReplanificacionExitosa(true);
//             resultado.setRazonReplanificacion("Eventos de avería: " + eventosAverias.size() + " camiones afectados");
            
//             // Identificar pedidos que necesitan reasignación
//             List<Pedido> pedidosReasignados = identificarPedidosReasignados(rutasActuales, rutasReplanificadas);
//             resultado.setPedidosReasignados(pedidosReasignados);
            
//             logger.info("Re-planificación exitosa: {} rutas actualizadas", rutasReplanificadas.size());
            
//         } catch (Exception e) {
//             logger.error("Error en re-planificación: {}", e.getMessage(), e);
//             resultado.setReplanificacionExitosa(false);
//             resultado.setRazonReplanificacion("Error: " + e.getMessage());
//         }
        
//         return resultado;
//     }
    
//     @Override
//     public MetricasPlanificacionCompleta obtenerMetricasConsolidadas(ResultadoPlanificacionCompleta resultado) {
//         logger.debug("Generando métricas consolidadas");
        
//         MetricasPlanificacionCompleta metricas = new MetricasPlanificacionCompleta();
        
//         if (resultado.getAsignacionesPedidos() != null) {
//             // Métricas de pedidos
//             int totalPedidos = resultado.getAsignacionesPedidos().values().stream()
//                 .mapToInt(AsignacionCamion::obtenerNumeroEntregas)
//                 .sum();
//             metricas.setTotalPedidosProcesados(totalPedidos);
            
//             // Eficiencia global de asignaciones
//             Map<String, Object> statsPedidos = resultado.getEstadisticasPedidos();
//             if (statsPedidos != null && statsPedidos.containsKey("eficienciaAsignacion")) {
//                 Object eficiencia = statsPedidos.get("eficienciaAsignacion");
//                 if (eficiencia instanceof Number) {
//                     metricas.setEficienciaGlobalPorcentaje(((Number) eficiencia).doubleValue());
//                 }
//             }
//         }
        
//         if (resultado.getRutasOptimizadas() != null) {
//             // Métricas de rutas
//             metricas.setTotalCamionesUtilizados(resultado.getRutasOptimizadas().size());
            
//             double distanciaTotal = resultado.getRutasOptimizadas().values().stream()
//                 .mapToDouble(RutaOptimizada::getDistanciaTotalKm)
//                 .sum();
//             metricas.setDistanciaTotalKm(distanciaTotal);
            
//             double tiempoTotal = resultado.getRutasOptimizadas().values().stream()
//                 .mapToDouble(RutaOptimizada::getTiempoEstimadoHoras)
//                 .sum();
//             metricas.setTiempoTotalHoras(tiempoTotal);
            
//             double combustibleTotal = resultado.getRutasOptimizadas().values().stream()
//                 .mapToDouble(RutaOptimizada::getCombustibleNecesarioGalones)
//                 .sum();
//             metricas.setCombustibleTotalGalones(combustibleTotal);
            
//             // Contar rutas viables vs problemáticas
//             long rutasViables = resultado.getRutasOptimizadas().values().stream()
//                 .filter(RutaOptimizada::esRutaFactible)
//                 .count();
//             metricas.setRutasViables((int) rutasViables);
//             metricas.setRutasProblematicas(resultado.getRutasOptimizadas().size() - (int) rutasViables);
//         }
        
//         // Detalles adicionales
//         metricas.getDetallesAdicionales().put("tiempoProcesamientoMs", resultado.getTiempoProcesamientoMs());
//         metricas.getDetallesAdicionales().put("procesadoExitoso", resultado.isProcesadoExitoso());
//         metricas.getDetallesAdicionales().put("numeroAdvertencias", resultado.getAdvertencias().size());
        
//         return metricas;
//     }
    
//     @Override
//     public void configurarIntegracion(ConfiguracionIntegracion configuracion) {
//         this.configuracion = configuracion;
//         logger.info("Configuración de integración actualizada");
//     }
    
//     // Métodos auxiliares privados
    
//     private List<Pedido> crearCopiasPedidos(List<Pedido> originales) {
//         return originales.stream()
//             .map(Pedido::clone)
//             .collect(Collectors.toList());
//     }
    
//     private Map<Camion, AsignacionCamion> crearCopiasAsignaciones(Map<Camion, AsignacionCamion> originales) {
//         Map<Camion, AsignacionCamion> copias = new HashMap<>();
        
//         for (Map.Entry<Camion, AsignacionCamion> entry : originales.entrySet()) {
//             // Crear nueva asignación con el mismo camión pero entregas copiadas
//             AsignacionCamion nuevaAsignacion = new AsignacionCamion(entry.getKey());
            
//             for (Entrega entrega : entry.getValue().getEntregas()) {
//                 nuevaAsignacion.agregarEntrega(entrega); // Entrega ya es mutable
//             }
            
//             copias.put(entry.getKey(), nuevaAsignacion);
//         }
        
//         return copias;
//     }
    
//     private List<Pedido> identificarPedidosReasignados(Map<Camion, RutaOptimizada> rutasOriginales,
//                                                       Map<Camion, RutaOptimizada> rutasReplanificadas) {
        
//         Set<Integer> pedidosOriginales = rutasOriginales.values().stream()
//             .flatMap(ruta -> ruta.obtenerEntregasEnOrden().stream())
//             .map(entrega -> entrega.getPedido().getId())
//             .collect(Collectors.toSet());
        
//         Set<Integer> pedidosReplanificados = rutasReplanificadas.values().stream()
//             .flatMap(ruta -> ruta.obtenerEntregasEnOrden().stream())
//             .map(entrega -> entrega.getPedido().getId())
//             .collect(Collectors.toSet());
        
//         // Encontrar diferencias (pedidos que cambiaron de asignación)
//         pedidosOriginales.removeAll(pedidosReplanificados);
        
//         // Convertir IDs de vuelta a objetos Pedido (simplificado)
//         return rutasReplanificadas.values().stream()
//             .flatMap(ruta -> ruta.obtenerEntregasEnOrden().stream())
//             .map(Entrega::getPedido)
//             .filter(pedido -> pedidosOriginales.contains(pedido.getId()))
//             .collect(Collectors.toList());
//     }
// }