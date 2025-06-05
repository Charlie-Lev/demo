package com.plg.service;

import com.plg.domain.Obstaculo;
import com.plg.repository.ObstaculoRepository;
import com.plg.service.util.GestorObstaculos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ObstaculoService {
    
    private static final Logger logger = LoggerFactory.getLogger(ObstaculoService.class);
    
    @Autowired
    private ObstaculoRepository obstaculoRepository;
    
    @Autowired
    private GestorObstaculos gestorObstaculos;
    
    // Patrón mejorado para parsear el formato
    private static final Pattern PATRON_BLOQUEO = Pattern.compile(
        "(\\d{2})d(\\d{2})h(\\d{2})m-(\\d{2})d(\\d{2})h(\\d{2})m:(.+)"
    );
    
    /**
     * Procesa archivo de bloqueos y los guarda en BD
     */
    @Transactional
    public void procesarArchivoBloqueos(MultipartFile file, YearMonth periodo) throws IOException {
        logger.info("🚧 Procesando archivo de bloqueos: {} para período {}", 
                   file.getOriginalFilename(), periodo);
        
        List<Obstaculo> bloqueos = new ArrayList<>();
        int lineaNumero = 0;
        int lineasProcesadas = 0;
        int lineasIgnoradas = 0;
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String linea;
            
            while ((linea = reader.readLine()) != null) {
                lineaNumero++;
                
                // Ignorar líneas vacías y comentarios
                if (linea.trim().isEmpty() || linea.trim().startsWith("#")) {
                    continue;
                }
                
                try {
                    Obstaculo bloqueo = parsearLineaBloqueo(linea.trim(), periodo);
                    if (bloqueo != null) {
                        bloqueos.add(bloqueo);
                        lineasProcesadas++;
                    } else {
                        lineasIgnoradas++;
                        logger.warn("Línea {} ignorada (formato inválido): {}", lineaNumero, linea);
                    }
                } catch (Exception e) {
                    lineasIgnoradas++;
                    logger.error("Error procesando línea {}: {} - Error: {}", 
                               lineaNumero, linea, e.getMessage());
                }
            }
        }
        
        if (!bloqueos.isEmpty()) {
            // Limpiar bloqueos anteriores del mismo período
            limpiarBloqueosDelPeriodo(periodo);
            
            // Guardar nuevos bloqueos
            obstaculoRepository.saveAll(bloqueos);
            logger.info("✅ Guardados {} bloqueos en BD", bloqueos.size());
            
            // Actualizar gestor con bloqueos activos
            actualizarGestorConBloqueosActivos();
        } else {
            logger.warn("⚠️ No se encontraron bloqueos válidos en el archivo");
        }
        
        logger.info("📊 Resumen procesamiento: {} líneas procesadas, {} ignoradas, {} bloqueos creados", 
                   lineasProcesadas, lineasIgnoradas, bloqueos.size());
    }
    
    /**
     * Parsea una línea de bloqueo CORREGIDO
     */
    private Obstaculo parsearLineaBloqueo(String linea, YearMonth periodo) {
        Matcher matcher = PATRON_BLOQUEO.matcher(linea);
        
        if (!matcher.matches()) {
            logger.debug("Línea no coincide con patrón esperado: {}", linea);
            return null;
        }
        
        try {
            // Extraer componentes de tiempo
            int diaInicio = Integer.parseInt(matcher.group(1));
            int horaInicio = Integer.parseInt(matcher.group(2));
            int minutoInicio = Integer.parseInt(matcher.group(3));
            
            int diaFin = Integer.parseInt(matcher.group(4));
            int horaFin = Integer.parseInt(matcher.group(5));
            int minutoFin = Integer.parseInt(matcher.group(6));
            
            String coordenadas = matcher.group(7);
            
            // Validar días del mes
            int diasEnMes = periodo.lengthOfMonth();
            if (diaInicio < 1 || diaInicio > diasEnMes || diaFin < 1 || diaFin > diasEnMes) {
                logger.warn("Días inválidos para período {}: inicio={}, fin={}", periodo, diaInicio, diaFin);
                return null;
            }
            
            // Crear fechas
            LocalDateTime fechaHoraInicio = periodo.atDay(diaInicio).atTime(horaInicio, minutoInicio);
            LocalDateTime fechaHoraFin = periodo.atDay(diaFin).atTime(horaFin, minutoFin);
            
            // Convertir a timestamps
            long timestampInicio = fechaHoraInicio.atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
            long timestampFin = fechaHoraFin.atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
            
            // Parsear coordenadas del polígono
            List<int[]> puntos = parsearCoordenadas(coordenadas);
            if (puntos.isEmpty()) {
                logger.warn("No se pudieron parsear coordenadas: {}", coordenadas);
                return null;
            }
            
            // Crear obstáculo
            Obstaculo obstaculo = new Obstaculo("BLOQUEO_TEMPORAL", coordenadas, timestampInicio, timestampFin);
            obstaculo.setDescripcion(String.format("Bloqueo temporal %s - %s", 
                fechaHoraInicio.format(DateTimeFormatter.ofPattern("dd/MM HH:mm")),
                fechaHoraFin.format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))));
            
            logger.debug("Bloqueo creado: {} puntos, {}s a {}s", 
                       puntos.size(), timestampInicio, timestampFin);
            
            return obstaculo;
            
        } catch (Exception e) {
            logger.error("Error parseando línea de bloqueo: {} - {}", linea, e.getMessage());
            return null;
        }
    }
    
    /**
     * Parsea coordenadas CORREGIDO
     */
    private List<int[]> parsearCoordenadas(String coordenadas) {
        List<int[]> puntos = new ArrayList<>();
        
        if (coordenadas == null || coordenadas.trim().isEmpty()) {
            return puntos;
        }
        
        try {
            String[] coords = coordenadas.split(",");
            
            // Debe ser número par de coordenadas (x,y pares)
            if (coords.length % 2 != 0) {
                logger.warn("Número impar de coordenadas: {}", coordenadas);
                return puntos;
            }
            
            for (int i = 0; i < coords.length; i += 2) {
                int x = Integer.parseInt(coords[i].trim());
                int y = Integer.parseInt(coords[i + 1].trim());
                
                // Validar rango de coordenadas (ajustar según tu grid)
                if (x < 0 || x > 1000 || y < 0 || y > 1000) {
                    logger.warn("Coordenadas fuera de rango: ({}, {})", x, y);
                    continue;
                }
                
                puntos.add(new int[]{x, y});
            }
            
        } catch (NumberFormatException e) {
            logger.error("Error parseando coordenadas: {} - {}", coordenadas, e.getMessage());
        }
        
        return puntos;
    }

    @Transactional
    public void limpiarBloqueosDelPeriodo(YearMonth periodo) {
        logger.info("🧹 Limpiando bloqueos existentes del período: {}", periodo);
        
        try {
            List<Obstaculo> bloqueosTemporales = obstaculoRepository.findByTipo("BLOQUEO_TEMPORAL");
            if (!bloqueosTemporales.isEmpty()) {
                obstaculoRepository.deleteAll(bloqueosTemporales);
                logger.info("🗑️ Eliminados {} bloqueos temporales existentes", bloqueosTemporales.size());
            }
        } catch (Exception e) {
            logger.error("Error limpiando bloqueos del período {}: {}", periodo, e.getMessage(), e);
            throw new RuntimeException("Error limpiando bloqueos existentes", e);
        }
    }

    public void actualizarGestorConBloqueosActivos() {
        logger.info("🔄 Actualizando gestor con bloqueos activos...");
        
        try {
            long ahora = System.currentTimeMillis() / 1000;
            List<Obstaculo> todosLosBloqueos = obstaculoRepository.findByTipo("BLOQUEO_TEMPORAL");
            
            // Limpiar obstáculos temporales del gestor
            gestorObstaculos.limpiarObstaculos();
            
            int bloqueosActivos = 0;
            for (Obstaculo bloqueo : todosLosBloqueos) {
                if (bloqueo.estaActivoEn(ahora)) {
                    agregarBloqueoAlGestor(bloqueo);
                    bloqueosActivos++;
                }
            }
            
            logger.info("✅ Gestor actualizado con {} bloqueos activos de {} totales", 
                       bloqueosActivos, todosLosBloqueos.size());
                       
        } catch (Exception e) {
            logger.error("Error actualizando gestor con bloqueos activos: {}", e.getMessage(), e);
            throw new RuntimeException("Error actualizando gestor de obstáculos", e);
        }
    }

    private void agregarBloqueoAlGestor(Obstaculo bloqueo) {
        try {
            if (bloqueo.getPuntosPoligono() != null && !bloqueo.getPuntosPoligono().isEmpty()) {
                List<int[]> puntos = parsearCoordenadas(bloqueo.getPuntosPoligono());
                
                // Agregar cada punto como obstáculo
                for (int[] punto : puntos) {
                    gestorObstaculos.agregarObstaculoPuntual(new com.plg.domain.Punto(punto[0], punto[1]));
                }
                
                logger.debug("Agregado bloqueo con {} puntos al gestor", puntos.size());
            }
        } catch (Exception e) {
            logger.error("Error agregando bloqueo {} al gestor: {}", bloqueo.getId(), e.getMessage());
        }
    }

    public List<Obstaculo> obtenerBloqueosActivosEn(LocalDateTime momento) {
        long timestamp = momento.atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
        
        return obstaculoRepository.findByTipo("BLOQUEO_TEMPORAL").stream()
                .filter(bloqueo -> bloqueo.estaActivoEn(timestamp))
                .toList();
    }
    
    public List<Obstaculo> obtenerTodosLosBloqueos() {
        return obstaculoRepository.findAll();
    }

    public BloqueoEstadisticas obtenerEstadisticasBloqueos() {
        BloqueoEstadisticas stats = new BloqueoEstadisticas();
        
        try {
            long ahora = System.currentTimeMillis() / 1000;
            List<Obstaculo> todosBloqueos = obstaculoRepository.findByTipo("BLOQUEO_TEMPORAL");
            
            stats.total = todosBloqueos.size();
            stats.activos = (int) todosBloqueos.stream()
                    .filter(b -> b.estaActivoEn(ahora))
                    .count();
            stats.inactivos = stats.total - stats.activos;
            
        } catch (Exception e) {
            logger.error("Error calculando estadísticas de bloqueos: {}", e.getMessage());
            stats.total = 0;
            stats.activos = 0;
            stats.inactivos = 0;
        }
        
        return stats;
    }
    
    public List<Obstaculo> obtenerTodos() {
        return obtenerTodosLosBloqueos();
    }
    
    public List<Obstaculo> obtenerActivos() {
        return obtenerBloqueosActivosEn(LocalDateTime.now());
    }
    
    public long contarTotal() {
        return obstaculoRepository.count();
    }
    
    @Transactional
    public void limpiarObstaculosExistentes() {
        obstaculoRepository.deleteAll();
        gestorObstaculos.limpiarObstaculos();
        logger.info("🗑️ Todos los obstáculos eliminados");
    }
    public static class BloqueoEstadisticas {
        public int total;
        public int activos;
        public int inactivos;
        
        @Override
        public String toString() {
            return String.format("Bloqueos: %d total (%d activos, %d inactivos)", 
                               total, activos, inactivos);
        }
    }
    @Transactional
    public void eliminarTodos() {
        try {
            logger.info("🗑️ Eliminando todos los obstáculos...");
            obstaculoRepository.deleteAll();
            gestorObstaculos.limpiarObstaculos();
            logger.info("✅ Todos los obstáculos eliminados correctamente");
        } catch (Exception e) {
            logger.error("❌ Error eliminando obstáculos: {}", e.getMessage(), e);
            throw new RuntimeException("Error eliminando obstáculos", e);
        }
    }
    @Transactional
    public Obstaculo guardar(Obstaculo obstaculo) {
        try {
            logger.debug("💾 Guardando obstáculo: {}", obstaculo.getTipo());
            Obstaculo obstaculoGuardado = obstaculoRepository.save(obstaculo);
            
            // Actualizar gestor si es un obstáculo activo
            if (obstaculo.estaActivoEn(System.currentTimeMillis() / 1000)) {
                agregarBloqueoAlGestor(obstaculoGuardado);
            }
            
            logger.debug("✅ Obstáculo guardado con ID: {}", obstaculoGuardado.getId());
            return obstaculoGuardado;
            
        } catch (Exception e) {
            logger.error("❌ Error guardando obstáculo: {}", e.getMessage(), e);
            throw new RuntimeException("Error guardando obstáculo", e);
        }
    }
}