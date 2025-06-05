package com.plg.controller;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.plg.domain.Camion;
import com.plg.domain.Pedido;
import com.plg.service.PedidoService;
import com.plg.service.SimulationTimeService;
@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {
    private static final Logger logger = LoggerFactory.getLogger(PedidoController.class);  // ← AGREGAR

    @Autowired
    private PedidoService pedidoService;
    @Autowired
    private SimulationTimeService simulationTimeService;
    @PostMapping("/cargar/{anio}/{mes}")
    public ResponseEntity<String> cargarPedidos(
            @RequestParam("file") MultipartFile file,
            @PathVariable int anio,
            @PathVariable int mes
    ) {
        try {
            YearMonth periodo = YearMonth.of(anio, mes);
            pedidoService.procesarArchivoPedidos(file, periodo);
            return ResponseEntity.ok("Pedidos cargados exitosamente.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al procesar archivo de pedidos: " + e.getMessage());
        }
    }
    @GetMapping
    public List<Pedido> obtenerPedidos() {
        return pedidoService.obtenerTodos();
    }


    /**
     * Endpoint principal optimizado con paginación y filtros
     */
    // @GetMapping
    // public ResponseEntity<Page<Pedido>> obtenerPedidosPaginados(
    //         @RequestParam(defaultValue = "0") int page,
    //         @RequestParam(defaultValue = "20") int size,
    //         @RequestParam(defaultValue = "id") String sortBy,
    //         @RequestParam(defaultValue = "desc") String sortDir,
    //         @RequestParam(required = false) String filtroCliente,
    //         @RequestParam(required = false) Double volumenMin,
    //         @RequestParam(required = false) Double volumenMax,
    //         @RequestParam(required = false) Integer prioridadMin,
    //         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaDesde,
    //         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaHasta
    // ) {
    //     try {
    //         Page<Pedido> pedidos = pedidoService.obtenerPedidosPaginados(
    //             page, size, sortBy, sortDir, 
    //             filtroCliente, volumenMin, volumenMax, prioridadMin,
    //             fechaDesde, fechaHasta
    //         );
    //         return ResponseEntity.ok(pedidos);
    //     } catch (Exception e) {
    //         return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    //     }
    // }

    /**
     * Búsqueda rápida con texto libre
     */
    @GetMapping("/buscar")
    public ResponseEntity<Page<Pedido>> buscarPedidos(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        try {
            org.springframework.data.domain.Pageable pageable = 
                org.springframework.data.domain.PageRequest.of(page, size);
            Page<Pedido> resultados = pedidoService.buscarPedidos(q, pageable);
            return ResponseEntity.ok(resultados);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Endpoint para estadísticas rápidas
     */
    @GetMapping("/resumen")
    public ResponseEntity<PedidoService.ResumenPedidos> obtenerResumen() {
        try {
            PedidoService.ResumenPedidos resumen = pedidoService.obtenerResumen();
            return ResponseEntity.ok(resumen);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Endpoint legacy para compatibilidad (pero limitado)
     */
    @GetMapping("/todos")
    public ResponseEntity<List<Pedido>> obtenerTodos(
            @RequestParam(defaultValue = "100") int limite
    ) {
        try {
            if (limite > 500) {
                return ResponseEntity.badRequest().build(); // Evitar sobrecarga
            }
            
            org.springframework.data.domain.Pageable pageable = 
                org.springframework.data.domain.PageRequest.of(0, limite);
            Page<Pedido> page = pedidoService.obtenerPedidosPaginados(
                0, limite, "id", "desc", null, null, null, null, null, null
            );
            return ResponseEntity.ok(page.getContent());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Endpoint para pedidos urgentes (carga rápida)
     */
    @GetMapping("/urgentes")
    public ResponseEntity<Page<Pedido>> obtenerPedidosUrgentes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        try {
            Page<Pedido> pedidos = pedidoService.obtenerPedidosPaginados(
                page, size, "prioridad", "desc", 
                null, null, null, 700, null, null
            );
            return ResponseEntity.ok(pedidos);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    @GetMapping("/memoria")
    public ResponseEntity<Map<String, Object>> obtenerPedidosEnMemoria() {
        try {
            // Obtener estadísticas y datos en memoria
            PedidoService.ResumenPedidos resumen = pedidoService.obtenerResumen();
            
            // Obtener algunos pedidos recientes
            org.springframework.data.domain.Pageable pageable = 
                org.springframework.data.domain.PageRequest.of(0, 50);
            Page<Pedido> pedidosRecientes = pedidoService.obtenerPedidosPaginados(
                0, 50, "fechaHoraRegistro", "desc", 
                null, null, null, null, null, null
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("timestamp", System.currentTimeMillis());
            response.put("resumen", resumen);
            response.put("pedidosRecientes", pedidosRecientes.getContent());
            response.put("totalPaginas", pedidosRecientes.getTotalPages());
            response.put("totalElementos", pedidosRecientes.getTotalElements());
            response.put("mensaje", "Datos de pedidos en memoria obtenidos exitosamente");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("❌ Error obteniendo pedidos en memoria: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "Error obteniendo pedidos en memoria: " + e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Endpoint para obtener pedidos críticos no asignados
     */
    @GetMapping("/criticos")
    public ResponseEntity<Map<String, Object>> obtenerPedidosCriticos(
            @RequestParam(defaultValue = "10") int limite,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime horaSimulacion
    ) {
        try {
            logger.info("🚨 Obteniendo pedidos críticos - límite: {}", limite);
            
            List<Pedido> pedidosCriticos;
            
            // Usar hora de simulación si se proporciona, sino usar servicio de simulación
            if (horaSimulacion != null) {
                pedidosCriticos = pedidoService.obtenerPedidosCriticos(horaSimulacion, limite);
            } else {
                pedidosCriticos = pedidoService.obtenerPedidosCriticos(limite);
            }
            
            // Obtener estadísticas adicionales
            PedidoService.EstadisticasPedidosCriticos estadisticas;
            if (horaSimulacion != null) {
                estadisticas = pedidoService.obtenerEstadisticasCriticos(horaSimulacion);
            } else {
                // Usar tiempo actual de simulación
                LocalDateTime horaActual = simulationTimeService.getCurrentSimulationTime();
                estadisticas = pedidoService.obtenerEstadisticasCriticos(horaActual);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("pedidosCriticos", pedidosCriticos);
            response.put("estadisticas", estadisticas);
            response.put("timestamp", System.currentTimeMillis());
            response.put("horaAnalisis", horaSimulacion != null ? horaSimulacion : 
                        simulationTimeService.getCurrentSimulationTime());
            
            logger.info("✅ Retornando {} pedidos críticos", pedidosCriticos.size());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("❌ Error obteniendo pedidos críticos: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "Error al obtener pedidos críticos: " + e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Solo estadísticas de pedidos críticos (endpoint ligero)
     */
    @GetMapping("/criticos/estadisticas")
    public ResponseEntity<PedidoService.EstadisticasPedidosCriticos> obtenerEstadisticasCriticos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime horaSimulacion
    ) {
        try {
            LocalDateTime hora = horaSimulacion != null ? horaSimulacion : 
                                simulationTimeService.getCurrentSimulationTime();
            
            PedidoService.EstadisticasPedidosCriticos estadisticas = 
                pedidoService.obtenerEstadisticasCriticos(hora);
            
            return ResponseEntity.ok(estadisticas);
            
        } catch (Exception e) {
            logger.error("Error obteniendo estadísticas críticos: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
