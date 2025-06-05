package com.plg.controller;

import com.plg.domain.Obstaculo;
import com.plg.dto.ObstaculoDTO;
import com.plg.service.ObstaculoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.plg.service.SimulationTimeService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
@RestController
@RequestMapping("/api/bloqueos")
@CrossOrigin(origins = "*")
public class ObstaculoController {
    
    private static final Logger logger = LoggerFactory.getLogger(ObstaculoController.class);
    
    @Autowired
    private ObstaculoService obstaculoService;
    @Autowired
    private SimulationTimeService simulationTimeService;
    /**
     * Cargar bloqueos desde archivo para un período específico
     */
    @PostMapping("/cargar/{anio}/{mes}")
    public ResponseEntity<Map<String, Object>> cargarBloqueosDesdeArchivo(
            @RequestParam("file") MultipartFile file,
            @PathVariable int anio,
            @PathVariable int mes) {
        try {
            logger.info("📁 Recibida solicitud de carga de bloqueos: {} para {}/{}", 
                       file.getOriginalFilename(), anio, mes);
            
            // Validar archivo
            if (file.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", "El archivo está vacío");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Validar período
            if (anio < 2020 || anio > 2030 || mes < 1 || mes > 12) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", "Período inválido: " + anio + "/" + mes);
                return ResponseEntity.badRequest().body(response);
            }
            
            YearMonth periodo = YearMonth.of(anio, mes);
            
            // Procesar archivo
            obstaculoService.procesarArchivoBloqueos(file, periodo);
            
            // Respuesta exitosa
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Bloqueos cargados exitosamente");
            response.put("periodo", periodo.toString());
            response.put("archivo", file.getOriginalFilename());
            response.put("tamanoArchivo", file.getSize());
            response.put("estadisticas", obstaculoService.obtenerEstadisticasBloqueos());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("❌ Error procesando archivo de bloqueos: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "Error al procesar archivo de bloqueos: " + e.getMessage());
            response.put("details", e.getClass().getSimpleName());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Obtener todos los bloqueos del sistema
     */
    @GetMapping
    public ResponseEntity<List<ObstaculoDTO>> obtenerTodosLosBloqueos() {
        try {
            List<Obstaculo> bloqueos = obstaculoService.obtenerTodosLosBloqueos();
            List<ObstaculoDTO> bloqueosDTO = bloqueos.stream()
                .map(ObstaculoDTO::fromEntity)
                .collect(Collectors.toList());
            return ResponseEntity.ok(bloqueosDTO);
            
        } catch (Exception e) {
            logger.error("❌ Error obteniendo bloqueos: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Obtener solo bloqueos activos en este momento
     */
    @GetMapping("/activos")
    public ResponseEntity<List<Obstaculo>> obtenerBloqueosActivos() {
        try {
            List<Obstaculo> bloqueosActivos = obstaculoService.obtenerBloqueosActivosEn(
                java.time.LocalDateTime.now()
            );
            return ResponseEntity.ok(bloqueosActivos);
            
        } catch (Exception e) {
            logger.error("❌ Error obteniendo bloqueos activos: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Estadísticas completas de bloqueos - MÉTODO COMPLETADO
     */
    @GetMapping("/estadisticas")
    public ResponseEntity<Map<String, Object>> obtenerEstadisticas() {
        try {
            ObstaculoService.BloqueoEstadisticas stats = obstaculoService.obtenerEstadisticasBloqueos();
            
            Map<String, Object> response = new HashMap<>();
            response.put("total", stats.total);
            response.put("activos", stats.activos);
            response.put("inactivos", stats.inactivos);
            response.put("timestamp", System.currentTimeMillis());
            response.put("porcentajeActivos", stats.total > 0 ? 
                        (stats.activos * 100.0 / stats.total) : 0);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("❌ Error obteniendo estadísticas: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Recargar bloqueos activos en el gestor
     */
    @PostMapping("/recargar")
    public ResponseEntity<Map<String, Object>> recargarBloqueosActivos() {
        try {
            logger.info("🔄 Recargando bloqueos activos en el gestor...");
            
            obstaculoService.actualizarGestorConBloqueosActivos();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Bloqueos activos recargados exitosamente");
            response.put("estadisticas", obstaculoService.obtenerEstadisticasBloqueos());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("❌ Error recargando bloqueos: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "Error al recargar bloqueos: " + e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Limpiar todos los bloqueos
     */
    @DeleteMapping("/limpiar")
    public ResponseEntity<Map<String, Object>> limpiarTodosLosBloqueos() {
        try {
            logger.info("🗑️ Limpiando todos los bloqueos...");
            
            obstaculoService.limpiarObstaculosExistentes();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Todos los bloqueos han sido eliminados");
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("❌ Error limpiando bloqueos: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "Error al limpiar bloqueos: " + e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    /**
     * Obtener bloqueos activos según el tiempo de SIMULACIÓN actual
     */
    @GetMapping("/activos-simulacion")
    public ResponseEntity<Map<String, Object>> obtenerBloqueosActivosSimulacion() {
        try {
            // ✅ Usar tiempo de simulación, NO tiempo real
            LocalDateTime tiempoSimulacion = simulationTimeService.getCurrentSimulationTime();
            
            logger.info("🕐 Evaluando bloqueos activos para tiempo simulación: {}", tiempoSimulacion);
            
            List<Obstaculo> bloqueosActivos = obstaculoService.obtenerBloqueosActivosEn(tiempoSimulacion);
            
            // Convertir a DTO con información adicional
            List<Map<String, Object>> bloqueosDTO = bloqueosActivos.stream()
                .map(bloqueo -> {
                    Map<String, Object> dto = new HashMap<>();
                    dto.put("id", bloqueo.getId());
                    dto.put("tipo", bloqueo.getTipo());
                    dto.put("coordenadaX", bloqueo.getCoordenadaX());
                    dto.put("coordenadaY", bloqueo.getCoordenadaY());
                    dto.put("puntosPoligono", bloqueo.getPuntosPoligono());
                    dto.put("descripcion", bloqueo.getDescripcion());
                    
                    // ✅ Agregar información de tiempo
                    if (bloqueo.getTimestampInicio() != null && bloqueo.getTimestampFin() != null) {
                        LocalDateTime inicio = LocalDateTime.ofEpochSecond(bloqueo.getTimestampInicio(), 0, 
                                            java.time.ZoneOffset.systemDefault().getRules().getOffset(java.time.Instant.now()));
                        LocalDateTime fin = LocalDateTime.ofEpochSecond(bloqueo.getTimestampFin(), 0, 
                                        java.time.ZoneOffset.systemDefault().getRules().getOffset(java.time.Instant.now()));
                        
                        dto.put("fechaInicio", inicio.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
                        dto.put("fechaFin", fin.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
                        dto.put("timestampInicio", bloqueo.getTimestampInicio());
                        dto.put("timestampFin", bloqueo.getTimestampFin());
                    }
                    
                    dto.put("estaActivoEnSimulacion", true); // Solo incluimos los activos
                    
                    // ✅ Parsear puntos del polígono
                    dto.put("puntosFormateados", parsearPuntosPoligono(bloqueo.getPuntosPoligono()));
                    
                    return dto;
                })
                .collect(Collectors.toList());
            
            // Respuesta completa
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("tiempoSimulacion", tiempoSimulacion);
            response.put("tiempoSimulacionFormatted", tiempoSimulacion.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
            response.put("bloqueosActivos", bloqueosDTO);
            response.put("totalActivos", bloqueosDTO.size());
            response.put("timestamp", System.currentTimeMillis());
            
            logger.info("✅ Encontrados {} bloqueos activos en tiempo simulación {}", 
                    bloqueosDTO.size(), tiempoSimulacion);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("❌ Error obteniendo bloqueos activos para simulación: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "Error al obtener bloqueos activos: " + e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Obtener estado detallado de TODOS los bloqueos con respecto al tiempo de simulación
     */
    @GetMapping("/estado-simulacion")
    public ResponseEntity<Map<String, Object>> obtenerEstadoTodosBloqueos() {
        try {
            LocalDateTime tiempoSimulacion = simulationTimeService.getCurrentSimulationTime();
            long timestampSimulacion = tiempoSimulacion.atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
            
            List<Obstaculo> todosBloqueos = obstaculoService.obtenerTodosLosBloqueos();
            
            List<Map<String, Object>> bloqueoEstados = todosBloqueos.stream()
                .map(bloqueo -> {
                    Map<String, Object> estado = new HashMap<>();
                    estado.put("id", bloqueo.getId());
                    estado.put("tipo", bloqueo.getTipo());
                    estado.put("descripcion", bloqueo.getDescripcion());
                    estado.put("puntosPoligono", bloqueo.getPuntosPoligono());
                    
                    // ✅ Evaluar si está activo en tiempo de simulación
                    boolean activoEnSimulacion = bloqueo.estaActivoEn(timestampSimulacion);
                    estado.put("activoEnSimulacion", activoEnSimulacion);
                    
                    if (bloqueo.getTimestampInicio() != null && bloqueo.getTimestampFin() != null) {
                        LocalDateTime inicio = LocalDateTime.ofEpochSecond(bloqueo.getTimestampInicio(), 0, 
                                            java.time.ZoneOffset.systemDefault().getRules().getOffset(java.time.Instant.now()));
                        LocalDateTime fin = LocalDateTime.ofEpochSecond(bloqueo.getTimestampFin(), 0, 
                                        java.time.ZoneOffset.systemDefault().getRules().getOffset(java.time.Instant.now()));
                        
                        estado.put("fechaInicio", inicio.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
                        estado.put("fechaFin", fin.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
                        
                        // ✅ Información de estado temporal
                        if (timestampSimulacion < bloqueo.getTimestampInicio()) {
                            estado.put("estadoTemporal", "FUTURO");
                            long minutosHastaInicio = java.time.Duration.between(tiempoSimulacion, inicio).toMinutes();
                            estado.put("minutosHastaInicio", minutosHastaInicio);
                        } else if (timestampSimulacion > bloqueo.getTimestampFin()) {
                            estado.put("estadoTemporal", "VENCIDO");
                            long minutosDesdeVencimiento = java.time.Duration.between(fin, tiempoSimulacion).toMinutes();
                            estado.put("minutosDesdeVencimiento", minutosDesdeVencimiento);
                        } else {
                            estado.put("estadoTemporal", "ACTIVO");
                            long minutosRestantes = java.time.Duration.between(tiempoSimulacion, fin).toMinutes();
                            estado.put("minutosRestantes", minutosRestantes);
                        }
                    }
                    
                    // ✅ Parsear puntos del polígono
                    estado.put("puntosFormateados", parsearPuntosPoligono(bloqueo.getPuntosPoligono()));
                    
                    return estado;
                })
                .collect(Collectors.toList());
            
            // Estadísticas
            long activos = bloqueoEstados.stream().filter(b -> (Boolean) b.get("activoEnSimulacion")).count();
            long futuros = bloqueoEstados.stream().filter(b -> "FUTURO".equals(b.get("estadoTemporal"))).count();
            long vencidos = bloqueoEstados.stream().filter(b -> "VENCIDO".equals(b.get("estadoTemporal"))).count();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("tiempoSimulacion", tiempoSimulacion);
            response.put("tiempoSimulacionFormatted", tiempoSimulacion.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
            response.put("bloqueos", bloqueoEstados);
            response.put("estadisticas", Map.of(
                "total", todosBloqueos.size(),
                "activos", activos,
                "futuros", futuros,
                "vencidos", vencidos
            ));
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("❌ Error obteniendo estado de bloqueos: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "Error al obtener estado de bloqueos: " + e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Método auxiliar para parsear puntos del polígono en formato legible
     */
    private List<Map<String, Integer>> parsearPuntosPoligono(String puntosPoligono) {
        List<Map<String, Integer>> puntos = new ArrayList<>();
        
        if (puntosPoligono == null || puntosPoligono.trim().isEmpty()) {
            return puntos;
        }
        
        try {
            String[] coords = puntosPoligono.split(",");
            
            // Debe ser número par de coordenadas (x,y pares)
            for (int i = 0; i < coords.length; i += 2) {
                if (i + 1 < coords.length) {
                    Map<String, Integer> punto = new HashMap<>();
                    punto.put("x", Integer.parseInt(coords[i].trim()));
                    punto.put("y", Integer.parseInt(coords[i + 1].trim()));
                    puntos.add(punto);
                }
            }
            
        } catch (NumberFormatException e) {
            logger.warn("Error parseando coordenadas: {} - {}", puntosPoligono, e.getMessage());
        }
        
        return puntos;
    }

    /**
     * Evaluar bloqueos en un tiempo específico de simulación
     */
    @GetMapping("/evaluar-tiempo")
    public ResponseEntity<Map<String, Object>> evaluarBloqueosEnTiempo(
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) 
            LocalDateTime tiempoEvaluacion) {
        try {
            logger.info("🕐 Evaluando bloqueos para tiempo específico: {}", tiempoEvaluacion);
            
            List<Obstaculo> bloqueosActivos = obstaculoService.obtenerBloqueosActivosEn(tiempoEvaluacion);
            
            List<Map<String, Object>> bloqueosDTO = bloqueosActivos.stream()
                .map(bloqueo -> {
                    Map<String, Object> dto = new HashMap<>();
                    dto.put("id", bloqueo.getId());
                    dto.put("descripcion", bloqueo.getDescripcion());
                    dto.put("puntosPoligono", bloqueo.getPuntosPoligono());
                    dto.put("puntosFormateados", parsearPuntosPoligono(bloqueo.getPuntosPoligono()));
                    return dto;
                })
                .collect(Collectors.toList());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("tiempoEvaluacion", tiempoEvaluacion);
            response.put("tiempoEvaluacionFormatted", tiempoEvaluacion.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
            response.put("bloqueosActivos", bloqueosDTO);
            response.put("totalActivos", bloqueosDTO.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("❌ Error evaluando bloqueos en tiempo específico: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "Error al evaluar bloqueos: " + e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}