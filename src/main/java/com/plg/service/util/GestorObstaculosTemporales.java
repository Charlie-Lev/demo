package com.plg.service.util;

import com.plg.domain.Obstaculo;
import com.plg.service.ObstaculoService;
import com.plg.service.SimulationTimeService;

import lombok.Data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ✅ NUEVO: Gestor de obstáculos temporales según tiempo de simulación
 * Maneja obstáculos que tienen fecha de inicio y fin (bloqueos temporales)
 */
@Component
public class GestorObstaculosTemporales {
    
    private static final Logger logger = LoggerFactory.getLogger(GestorObstaculosTemporales.class);
    
    @Autowired
    private ObstaculoService obstaculoService;
    
    @Autowired
    private SimulationTimeService simulationTimeService;
    
    /**
     * Obtiene obstáculos activos según el tiempo de simulación actual
     */
    public List<Obstaculo> obtenerObstaculosVigentes() {
        LocalDateTime tiempoSimulacion = simulationTimeService.getCurrentSimulationTime();
        return obtenerObstaculosVigentesEn(tiempoSimulacion);
    }
    
    /**
     * Obtiene obstáculos activos en un momento específico de simulación
     */
    public List<Obstaculo> obtenerObstaculosVigentesEn(LocalDateTime tiempoSimulacion) {
        try {
            long timestampSimulacion = tiempoSimulacion
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
            
            logger.debug("🚧 Obteniendo obstáculos vigentes para tiempo: {} (timestamp: {})", 
                tiempoSimulacion, timestampSimulacion);
            
            // Obtener todos los obstáculos
            List<Obstaculo> todosObstaculos = obstaculoService.obtenerTodos();
            
            // Filtrar solo los activos en el tiempo específico
            List<Obstaculo> obstaculosVigentes = todosObstaculos.stream()
                .filter(obs -> {
                    boolean vigente = obs.estaActivoEn(timestampSimulacion);
                    if (vigente) {
                        logger.trace("✅ Obstáculo {} vigente: {} - {}", 
                            obs.getId(), obs.getTipo(), obs.getDescripcion());
                    } else {
                        logger.trace("❌ Obstáculo {} NO vigente: {} - {}", 
                            obs.getId(), obs.getTipo(), obs.getDescripcion());
                    }
                    return vigente;
                })
                .collect(Collectors.toList());
            
            logger.info("🚧 Obstáculos vigentes: {} de {} totales para tiempo {}", 
                obstaculosVigentes.size(), todosObstaculos.size(), tiempoSimulacion);
            
            return obstaculosVigentes;
            
        } catch (Exception e) {
            logger.error("❌ Error obteniendo obstáculos vigentes: {}", e.getMessage(), e);
            // En caso de error, devolver lista vacía para no bloquear la planificación
            return List.of();
        }
    }
    
    /**
     * Verifica si hay obstáculos temporales que afecten una ruta específica
     */
    public boolean hayObstaculosEnRuta(int x1, int y1, int x2, int y2) {
        List<Obstaculo> obstaculosVigentes = obtenerObstaculosVigentes();
        
        return obstaculosVigentes.stream()
            .anyMatch(obs -> obstaculoAfectaRuta(obs, x1, y1, x2, y2));
    }
    
    /**
     * Verifica si hay obstáculos temporales que afecten una ruta específica en un momento dado
     */
    public boolean hayObstaculosEnRutaEn(int x1, int y1, int x2, int y2, LocalDateTime momento) {
        List<Obstaculo> obstaculosVigentes = obtenerObstaculosVigentesEn(momento);
        
        return obstaculosVigentes.stream()
            .anyMatch(obs -> obstaculoAfectaRuta(obs, x1, y1, x2, y2));
    }
    
    /**
     * Obtiene estadísticas de obstáculos temporales
     */
    public EstadisticasObstaculos obtenerEstadisticas() {
        try {
            List<Obstaculo> todosObstaculos = obstaculoService.obtenerTodos();
            List<Obstaculo> obstaculosVigentes = obtenerObstaculosVigentes();
            
            EstadisticasObstaculos stats = new EstadisticasObstaculos();
            stats.setTotalObstaculos(todosObstaculos.size());
            stats.setObstaculosVigentes(obstaculosVigentes.size());
            stats.setObstaculosNoVigentes(todosObstaculos.size() - obstaculosVigentes.size());
            
            // Contar por tipo
            long permanentes = todosObstaculos.stream()
                .filter(obs -> obs.getTimestampInicio() == null || obs.getTimestampFin() == null)
                .count();
            
            long temporales = todosObstaculos.stream()
                .filter(obs -> obs.getTimestampInicio() != null && obs.getTimestampFin() != null)
                .count();
            
            stats.setObstaculosPermanentes((int) permanentes);
            stats.setObstaculosTemporales((int) temporales);
            stats.setTiempoAnalisis(simulationTimeService.getCurrentSimulationTime());
            
            return stats;
            
        } catch (Exception e) {
            logger.error("❌ Error obteniendo estadísticas de obstáculos: {}", e.getMessage(), e);
            return new EstadisticasObstaculos();
        }
    }
    
    // ✅ Métodos auxiliares privados
    
    /**
     * Verifica si un obstáculo afecta una ruta específica
     */
    private boolean obstaculoAfectaRuta(Obstaculo obstaculo, int x1, int y1, int x2, int y2) {
        if (obstaculo.getTipo() == null) {
            return false;
        }
        
        switch (obstaculo.getTipo().toUpperCase()) {
            case "PUNTO":
                return puntoEnRuta(obstaculo.getCoordenadaX(), obstaculo.getCoordenadaY(), x1, y1, x2, y2);
                
            case "LINEA_H":
            case "LINEA_V":
                return lineaIntersectaRuta(obstaculo, x1, y1, x2, y2);
                
            case "POLIGONO":
            case "BLOQUEO_TEMPORAL":
                return poligonoIntersectaRuta(obstaculo, x1, y1, x2, y2);
                
            default:
                logger.warn("⚠️ Tipo de obstáculo desconocido: {}", obstaculo.getTipo());
                return false;
        }
    }
    
    /**
     * Verifica si un punto está en la ruta (línea recta)
     */
    private boolean puntoEnRuta(int px, int py, int x1, int y1, int x2, int y2) {
        // Verificar si el punto está en la línea entre (x1,y1) y (x2,y2)
        if (x1 == x2) { // Línea vertical
            return px == x1 && py >= Math.min(y1, y2) && py <= Math.max(y1, y2);
        }
        if (y1 == y2) { // Línea horizontal
            return py == y1 && px >= Math.min(x1, x2) && px <= Math.max(x1, x2);
        }
        
        // Línea diagonal (simplificado)
        double pendiente = (double)(y2 - y1) / (x2 - x1);
        double esperadoY = y1 + pendiente * (px - x1);
        
        return Math.abs(py - esperadoY) < 0.5 && 
               px >= Math.min(x1, x2) && px <= Math.max(x1, x2);
    }
    
    /**
     * Verifica si una línea obstáculo intersecta con la ruta
     */
    private boolean lineaIntersectaRuta(Obstaculo obstaculo, int x1, int y1, int x2, int y2) {
        Integer ox1 = obstaculo.getCoordenadaX();
        Integer oy1 = obstaculo.getCoordenadaY();
        Integer ox2 = obstaculo.getCoordenadaX2();
        Integer oy2 = obstaculo.getCoordenadaY2();
        
        if (ox1 == null || oy1 == null || ox2 == null || oy2 == null) {
            return false;
        }
        
        // Verificar intersección de líneas (algoritmo simplificado)
        return lineasSeIntersectan(x1, y1, x2, y2, ox1, oy1, ox2, oy2);
    }
    
    /**
     * Verifica si un polígono intersecta con la ruta
     */
    private boolean poligonoIntersectaRuta(Obstaculo obstaculo, int x1, int y1, int x2, int y2) {
        String puntosPoligono = obstaculo.getPuntosPoligono();
        if (puntosPoligono == null || puntosPoligono.trim().isEmpty()) {
            return false;
        }
        
        try {
            String[] coords = puntosPoligono.split(",");
            if (coords.length < 6) { // Mínimo 3 puntos (6 coordenadas)
                return false;
            }
            
            // Verificar si algún segmento del polígono intersecta la ruta
            for (int i = 0; i < coords.length - 2; i += 2) {
                int px1 = Integer.parseInt(coords[i].trim());
                int py1 = Integer.parseInt(coords[i + 1].trim());
                int px2 = Integer.parseInt(coords[(i + 2) % coords.length].trim());
                int py2 = Integer.parseInt(coords[(i + 3) % coords.length].trim());
                
                if (lineasSeIntersectan(x1, y1, x2, y2, px1, py1, px2, py2)) {
                    return true;
                }
            }
            
            return false;
            
        } catch (NumberFormatException e) {
            logger.error("❌ Error parseando puntos de polígono: {}", puntosPoligono, e);
            return false;
        }
    }
    
    /**
     * Algoritmo simplificado para verificar intersección de líneas
     */
    private boolean lineasSeIntersectan(int x1, int y1, int x2, int y2, int x3, int y3, int x4, int y4) {
        // Implementación simplificada - en producción usar algoritmo más robusto
        double denom = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4);
        if (Math.abs(denom) < 0.001) {
            return false; // Líneas paralelas
        }
        
        double t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / denom;
        double u = -((x1 - x2) * (y1 - y3) - (y1 - y2) * (x1 - x3)) / denom;
        
        return t >= 0 && t <= 1 && u >= 0 && u <= 1;
    }
    
    // ✅ Clase auxiliar para estadísticas
    @Data
    public static class EstadisticasObstaculos {
        private int totalObstaculos;
        private int obstaculosVigentes;
        private int obstaculosNoVigentes;
        private int obstaculosPermanentes;
        private int obstaculosTemporales;
        private LocalDateTime tiempoAnalisis;
    }
}