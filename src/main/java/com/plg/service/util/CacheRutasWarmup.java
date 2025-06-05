package com.plg.service.util;

import com.plg.domain.Punto;
import com.plg.service.AEstrellaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import java.nio.file.*;

/**
 * Servicio especializado para estrategias de warm-up del cache
 * Implementa background loading, persistence y análisis de patrones
 */
@Component
public class CacheRutasWarmup {
    
    private static final Logger logger = LoggerFactory.getLogger(CacheRutasWarmup.class);
    
    @Autowired
    private AEstrellaService aEstrellaService;
    
    @Autowired
    private CacheRutas cacheRutas;
    
    // Background executor para pre-cálculos
    private final ScheduledExecutorService backgroundExecutor = 
        Executors.newScheduledThreadPool(2);
    
    // Historial de rutas solicitadas
    private final Map<String, Integer> historialRutas = new ConcurrentHashMap<>();
    
    // Configuración
    private static final String CACHE_PERSISTENCE_FILE = "cache_rutas.dat";
    private static final int TOP_RUTAS_FRECUENTES = 100;
    
    /**
     * Inicia warm-up completo del cache
     */
    public void iniciarWarmupCompleto(List<Punto> almacenes) {
        logger.info("Iniciando warm-up completo del cache");
        
        // 1. Cargar cache persistido
        cargarCacheDesdeDiscoDisk();
        
        // 2. Pre-calcular rutas entre almacenes
        precalcularRutasAlmacenes(almacenes);
        
        // 3. Iniciar background loading
        iniciarBackgroundLoading();
        
        // 4. Programar persistencia automática
        programarPersistenciaAutomatica();
    }
    
    /**
     * Pre-cálculo de todas las rutas entre almacenes
     */
    private void precalcularRutasAlmacenes(List<Punto> almacenes) {
        logger.info("Pre-calculando rutas entre {} almacenes", almacenes.size());
        
        CompletableFuture.runAsync(() -> {
            try {
                aEstrellaService.precalcularRutasFrecuentes(almacenes);
                logger.info("Pre-cálculo de almacenes completado");
            } catch (Exception e) {
                logger.error("Error en pre-cálculo de almacenes: {}", e.getMessage(), e);
            }
        }, backgroundExecutor);
    }
    
    /**
     * Background loading de rutas frecuentes
     */
    private void iniciarBackgroundLoading() {
        // Cada 10 minutos, calcular rutas más frecuentes
        backgroundExecutor.scheduleAtFixedRate(() -> {
            try {
                List<ParRutaFrecuente> rutasFrecuentes = obtenerRutasMasFrecuentes();
                precalcularRutasFrecuentesBackground(rutasFrecuentes);
            } catch (Exception e) {
                logger.error("Error en background loading: {}", e.getMessage(), e);
            }
        }, 1, 10, TimeUnit.MINUTES);
    }
    
    /**
     * Registra una ruta solicitada para análisis de frecuencia
     */
    public void registrarRutaSolicitada(Punto origen, Punto destino) {
        String clave = generarClaveRuta(origen, destino);
        historialRutas.merge(clave, 1, Integer::sum);
    }
    
    /**
     * Obtiene las rutas más frecuentemente solicitadas
     */
    private List<ParRutaFrecuente> obtenerRutasMasFrecuentes() {
        return historialRutas.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(TOP_RUTAS_FRECUENTES)
            .map(entry -> parsearClaveRuta(entry.getKey(), entry.getValue()))
            .filter(Objects::nonNull)
            .toList();
    }
    
    /**
     * Pre-calcula rutas frecuentes en background
     */
    private void precalcularRutasFrecuentesBackground(List<ParRutaFrecuente> rutasFrecuentes) {
        logger.debug("Iniciando pre-cálculo background de {} rutas frecuentes", rutasFrecuentes.size());
        
        for (ParRutaFrecuente par : rutasFrecuentes) {
            // Solo calcular si no está en cache
            if (cacheRutas.obtenerRuta(par.getOrigen(), par.getDestino()).isEmpty()) {
                try {
                    aEstrellaService.calcularRuta(par.getOrigen(), par.getDestino());
                    Thread.sleep(50); // Pequeña pausa para no saturar
                } catch (Exception e) {
                    logger.warn("Error pre-calculando ruta {}: {}", par, e.getMessage());
                }
            }
        }
    }
    
    /**
     * Guarda el cache en disco para persistencia
     */
    public void guardarCacheEnDisco() {
        try {
            CacheRutas.EstadisticasCache stats = cacheRutas.obtenerEstadisticas();
            
            Map<String, Object> datosCache = new HashMap<>();
            datosCache.put("timestamp", System.currentTimeMillis());
            datosCache.put("estadisticas", stats);
            datosCache.put("historialRutas", new HashMap<>(historialRutas));
            
            // Serializar a disco
            try (ObjectOutputStream oos = new ObjectOutputStream(
                    Files.newOutputStream(Paths.get(CACHE_PERSISTENCE_FILE)))) {
                oos.writeObject(datosCache);
            }
            
            logger.info("Cache guardado en disco: {} entradas", stats.getTamanoActual());
            
        } catch (Exception e) {
            logger.error("Error guardando cache en disco: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Carga el cache desde disco
     */
    @SuppressWarnings("unchecked")
    private void cargarCacheDesdeDiscoDisk() {
        try {
            Path cacheFile = Paths.get(CACHE_PERSISTENCE_FILE);
            if (!Files.exists(cacheFile)) {
                logger.info("No existe archivo de cache persistido");
                return;
            }
            
            try (ObjectInputStream ois = new ObjectInputStream(
                    Files.newInputStream(cacheFile))) {
                
                Map<String, Object> datosCache = (Map<String, Object>) ois.readObject();
                
                // Restaurar historial de rutas
                Map<String, Integer> historialCargado = 
                    (Map<String, Integer>) datosCache.get("historialRutas");
                if (historialCargado != null) {
                    historialRutas.putAll(historialCargado);
                }
                
                long timestamp = (Long) datosCache.get("timestamp");
                long tiempoTranscurrido = System.currentTimeMillis() - timestamp;
                
                // Solo usar cache si es reciente (menos de 24 horas)
                if (tiempoTranscurrido < TimeUnit.HOURS.toMillis(24)) {
                    logger.info("Cache cargado desde disco: {} rutas en historial", 
                        historialRutas.size());
                } else {
                    logger.info("Cache en disco obsoleto, iniciando limpio");
                    historialRutas.clear();
                }
            }
            
        } catch (Exception e) {
            logger.error("Error cargando cache desde disco: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Programa persistencia automática cada hora
     */
    private void programarPersistenciaAutomatica() {
        backgroundExecutor.scheduleAtFixedRate(() -> {
            try {
                guardarCacheEnDisco();
            } catch (Exception e) {
                logger.error("Error en persistencia automática: {}", e.getMessage(), e);
            }
        }, 1, 1, TimeUnit.HOURS);
    }
    
    /**
     * Shutdown graceful
     */
    public void shutdown() {
        logger.info("Iniciando shutdown del warm-up cache");
        
        // Guardar cache antes de cerrar
        guardarCacheEnDisco();
        
        // Cerrar executor
        backgroundExecutor.shutdown();
        try {
            if (!backgroundExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                backgroundExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            backgroundExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    // Métodos auxiliares
    
    private String generarClaveRuta(Punto origen, Punto destino) {
        return String.format("%d,%d->%d,%d", 
            origen.getX(), origen.getY(), destino.getX(), destino.getY());
    }
    
    private ParRutaFrecuente parsearClaveRuta(String clave, int frecuencia) {
        try {
            String[] partes = clave.split("->");
            String[] origenStr = partes[0].split(",");
            String[] destinoStr = partes[1].split(",");
            
            Punto origen = new Punto(Integer.parseInt(origenStr[0]), Integer.parseInt(origenStr[1]));
            Punto destino = new Punto(Integer.parseInt(destinoStr[0]), Integer.parseInt(destinoStr[1]));
            
            return new ParRutaFrecuente(origen, destino, frecuencia);
        } catch (Exception e) {
            logger.warn("Error parseando clave de ruta: {}", clave);
            return null;
        }
    }
    
    /**
     * Clase auxiliar para rutas frecuentes
     */
    private static class ParRutaFrecuente {
        private final Punto origen;
        private final Punto destino;
        private final int frecuencia;
        
        public ParRutaFrecuente(Punto origen, Punto destino, int frecuencia) {
            this.origen = origen;
            this.destino = destino;
            this.frecuencia = frecuencia;
        }
        
        public Punto getOrigen() { return origen; }
        public Punto getDestino() { return destino; }
        public int getFrecuencia() { return frecuencia; }
        
        @Override
        public String toString() {
            return String.format("%s->%s (freq: %d)", origen, destino, frecuencia);
        }
    }
}