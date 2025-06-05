package com.plg.service.util;

import com.plg.domain.Punto;
import com.plg.service.AEstrellaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Sistema de invalidación inteligente para cache de rutas
 * Maneja detección automática de rutas afectadas y refresh en background
 */
@Component
public class CacheInvalidacionInteligente {
    
    private static final Logger logger = LoggerFactory.getLogger(CacheInvalidacionInteligente.class);
    
    @Autowired
    private CacheRutas cacheRutas;
    
    @Autowired
    private AEstrellaService aEstrellaService;
    
    @Autowired
    private GestorObstaculos gestorObstaculos;
    
    // Background executor para refresh automático
    private final ScheduledExecutorService refreshExecutor = 
        Executors.newScheduledThreadPool(3, r -> {
            Thread t = new Thread(r, "CacheRefresh-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
        });
    
    // Cola de rutas para re-calcular automáticamente
    private final BlockingQueue<RutaParaRefrescar> colaRefresh = 
        new LinkedBlockingQueue<>(1000);
    
    // Mapa de rutas por región para invalidación por área
    private final Map<RegionGrid, Set<ClaveRuta>> rutasPorRegion = 
        new ConcurrentHashMap<>();
    
    // Configuración
    private ConfiguracionInvalidacion configuracion = new ConfiguracionInvalidacion();
    
    // Workers en background para refresh
    private volatile boolean refreshActivo = true;
    
    /**
     * Inicializa el sistema de invalidación inteligente
     */
    public void inicializar() {
        logger.info("Iniciando sistema de invalidación inteligente de cache");
        
        // Iniciar workers de refresh en background
        for (int i = 0; i < 2; i++) {
            refreshExecutor.submit(this::workerRefreshBackground);
        }
        
        // Programar análisis periódico de regiones
        refreshExecutor.scheduleAtFixedRate(
            this::analizarYOptimizarRegiones, 
            5, 30, TimeUnit.MINUTES
        );
        
        logger.info("Sistema de invalidación inteligente iniciado");
    }
    
    /**
     * Detecta automáticamente qué rutas se ven afectadas por cambios de obstáculos
     */
    public Set<ClaveRuta> detectarRutasAfectadas(List<Punto> obstaculosNuevos, 
                                                List<Punto> obstaculosEliminados) {
        logger.debug("Detectando rutas afectadas por {} obstáculos nuevos y {} eliminados", 
            obstaculosNuevos.size(), obstaculosEliminados.size());
        
        Set<ClaveRuta> rutasAfectadas = new HashSet<>();
        
        // 1. Detectar por punto específico (método actual)
        for (Punto obstaculo : obstaculosNuevos) {
            rutasAfectadas.addAll(detectarRutasPorPunto(obstaculo));
        }
        
        for (Punto obstaculo : obstaculosEliminados) {
            rutasAfectadas.addAll(detectarRutasPorPunto(obstaculo));
        }
        
        // 2. Detectar por área/región afectada
        Set<RegionGrid> regionesAfectadas = calcularRegionesAfectadas(
            obstaculosNuevos, obstaculosEliminados);
        
        for (RegionGrid region : regionesAfectadas) {
            Set<ClaveRuta> rutasEnRegion = rutasPorRegion.get(region);
            if (rutasEnRegion != null) {
                rutasAfectadas.addAll(rutasEnRegion);
            }
        }
        
        // 3. Detectar rutas con paths alternativos potenciales
        if (!obstaculosEliminados.isEmpty()) {
            rutasAfectadas.addAll(detectarRutasConAlternativasPotenciales(obstaculosEliminados));
        }
        
        logger.info("Detectadas {} rutas afectadas por cambios de obstáculos", rutasAfectadas.size());
        return rutasAfectadas;
    }
    
    /**
     * Invalidación inteligente por área afectada
     */
    public void invalidarPorArea(Punto centro, int radio) {
        logger.info("Invalidando por área: centro={}, radio={}", centro, radio);
        
        Set<ClaveRuta> rutasAfectadas = new HashSet<>();
        
        // Calcular área de invalidación
        int minX = centro.getX() - radio;
        int maxX = centro.getX() + radio;
        int minY = centro.getY() - radio;
        int maxY = centro.getY() + radio;
        
        // Encontrar todas las rutas que pasan por el área
        for (Map.Entry<RegionGrid, Set<ClaveRuta>> entry : rutasPorRegion.entrySet()) {
            RegionGrid region = entry.getKey();
            
            // Si la región intersecta con el área de invalidación
            if (region.intersectaCon(minX, minY, maxX, maxY)) {
                rutasAfectadas.addAll(entry.getValue());
            }
        }
        
        // Invalidar rutas encontradas
        invalidarYProgramarRefresh(rutasAfectadas, "Invalidación por área");
    }
    
    /**
     * Invalidación inteligente cuando se agregan nuevos obstáculos
     */
    public void invalidarPorNuevosObstaculos(List<Punto> nuevosObstaculos) {
        Set<ClaveRuta> rutasAfectadas = detectarRutasAfectadas(nuevosObstaculos, List.of());
        invalidarYProgramarRefresh(rutasAfectadas, "Nuevos obstáculos");
    }
    
    /**
     * Invalidación inteligente cuando se eliminan obstáculos
     */
    public void invalidarPorObstaculosEliminados(List<Punto> obstaculosEliminados) {
        Set<ClaveRuta> rutasAfectadas = detectarRutasAfectadas(List.of(), obstaculosEliminados);
        invalidarYProgramarRefresh(rutasAfectadas, "Obstáculos eliminados");
    }
    
    /**
     * Registra una ruta en el sistema de tracking por región
     */
    public void registrarRutaEnRegiones(ClaveRuta claveRuta, List<Punto> ruta) {
        if (ruta == null || ruta.isEmpty()) return;
        
        Set<RegionGrid> regiones = calcularRegionesDeRuta(ruta);
        
        for (RegionGrid region : regiones) {
            rutasPorRegion.computeIfAbsent(region, k -> ConcurrentHashMap.newKeySet())
                          .add(claveRuta);
        }
        
        logger.debug("Ruta {} registrada en {} regiones", claveRuta, regiones.size());
    }
    
    /**
     * Refresh automático de rutas invalidadas en background
     */
    public void programarRefreshRuta(ClaveRuta claveRuta, String razon, int prioridad) {
        RutaParaRefrescar rutaRefresh = new RutaParaRefrescar(claveRuta, razon, prioridad);
        
        boolean agregada = colaRefresh.offer(rutaRefresh);
        if (!agregada) {
            logger.warn("Cola de refresh llena, descartando ruta: {}", claveRuta);
        } else {
            logger.debug("Programado refresh de ruta: {} (razón: {})", claveRuta, razon);
        }
    }
    
    /**
     * Obtiene estadísticas del sistema de invalidación
     */
    public EstadisticasInvalidacion obtenerEstadisticas() {
        EstadisticasInvalidacion stats = new EstadisticasInvalidacion();
        
        stats.setRegionesTotales(rutasPorRegion.size());
        stats.setRutasTrackeadas(rutasPorRegion.values().stream()
            .mapToInt(Set::size)
            .sum());
        stats.setColaRefreshPendientes(colaRefresh.size());
        stats.setRefreshActivo(refreshActivo);
        
        return stats;
    }
    
    /**
     * Configura parámetros del sistema de invalidación
     */
    public void configurar(ConfiguracionInvalidacion nuevaConfiguracion) {
        this.configuracion = nuevaConfiguracion;
        logger.info("Configuración de invalidación actualizada");
    }
    
    /**
     * Shutdown graceful del sistema
     */
    public void shutdown() {
        logger.info("Iniciando shutdown del sistema de invalidación");
        
        refreshActivo = false;
        refreshExecutor.shutdown();
        
        try {
            if (!refreshExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                refreshExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            refreshExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("Sistema de invalidación cerrado");
    }
    
    // Métodos privados auxiliares
    
    private Set<ClaveRuta> detectarRutasPorPunto(Punto punto) {
        Set<ClaveRuta> rutasAfectadas = new HashSet<>();
        
        // Buscar en regiones cercanas al punto
        RegionGrid regionCentral = new RegionGrid(punto.getX(), punto.getY(), configuracion.getTamanoRegion());
        
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                RegionGrid regionVecina = new RegionGrid(
                    regionCentral.getX() + dx, 
                    regionCentral.getY() + dy, 
                    configuracion.getTamanoRegion()
                );
                
                Set<ClaveRuta> rutasEnRegion = rutasPorRegion.get(regionVecina);
                if (rutasEnRegion != null) {
                    rutasAfectadas.addAll(rutasEnRegion);
                }
            }
        }
        
        return rutasAfectadas;
    }
    
    private Set<RegionGrid> calcularRegionesAfectadas(List<Punto> obstaculosNuevos, 
                                                     List<Punto> obstaculosEliminados) {
        Set<RegionGrid> regiones = new HashSet<>();
        
        for (Punto punto : obstaculosNuevos) {
            regiones.add(new RegionGrid(punto.getX(), punto.getY(), configuracion.getTamanoRegion()));
        }
        
        for (Punto punto : obstaculosEliminados) {
            regiones.add(new RegionGrid(punto.getX(), punto.getY(), configuracion.getTamanoRegion()));
        }
        
        return regiones;
    }
    
    private Set<ClaveRuta> detectarRutasConAlternativasPotenciales(List<Punto> obstaculosEliminados) {
        Set<ClaveRuta> rutasCandidatas = new HashSet<>();
        
        // Buscar rutas que podrían beneficiarse de los obstáculos eliminados
        for (Punto obstaculo : obstaculosEliminados) {
            Set<ClaveRuta> rutasCercanas = detectarRutasPorPunto(obstaculo);
            rutasCandidatas.addAll(rutasCercanas);
        }
        
        return rutasCandidatas;
    }
    
    private Set<RegionGrid> calcularRegionesDeRuta(List<Punto> ruta) {
        Set<RegionGrid> regiones = new HashSet<>();
        
        for (Punto punto : ruta) {
            RegionGrid region = new RegionGrid(punto.getX(), punto.getY(), configuracion.getTamanoRegion());
            regiones.add(region);
        }
        
        return regiones;
    }
    
    private void invalidarYProgramarRefresh(Set<ClaveRuta> rutasAfectadas, String razon) {
        if (rutasAfectadas.isEmpty()) return;
        
        logger.info("Invalidando {} rutas por: {}", rutasAfectadas.size(), razon);
        
        for (ClaveRuta claveRuta : rutasAfectadas) {
            // Invalidar del cache
            cacheRutas.invalidarRutasPorPunto(claveRuta.getOrigen());
            
            // Programar refresh en background
            if (configuracion.isRefreshAutomaticoHabilitado()) {
                programarRefreshRuta(claveRuta, razon, 1);
            }
        }
    }
    
    /**
     * Worker que ejecuta refresh de rutas en background
     */
    private void workerRefreshBackground() {
        logger.debug("Iniciando worker de refresh background");
        
        while (refreshActivo) {
            try {
                RutaParaRefrescar rutaRefresh = colaRefresh.poll(5, TimeUnit.SECONDS);
                
                if (rutaRefresh != null) {
                    ejecutarRefreshRuta(rutaRefresh);
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error en worker de refresh: {}", e.getMessage(), e);
            }
        }
        
        logger.debug("Worker de refresh background terminado");
    }
    
    private void ejecutarRefreshRuta(RutaParaRefrescar rutaRefresh) {
        try {
            ClaveRuta clave = rutaRefresh.getClaveRuta();
            logger.debug("Refrescando ruta: {} (razón: {})", clave, rutaRefresh.getRazon());
            
            // Re-calcular la ruta
            var resultado = aEstrellaService.calcularRuta(clave.getOrigen(), clave.getDestino());
            
            if (resultado.isCalculoExitoso()) {
                // Almacenar en cache la nueva ruta
                cacheRutas.almacenarRuta(clave.getOrigen(), clave.getDestino(), 
                                        resultado.getRutaEncontrada());
                
                // Re-registrar en regiones
                registrarRutaEnRegiones(clave, resultado.getRutaEncontrada());
                
                logger.debug("Ruta refrescada exitosamente: {}", clave);
            } else {
                logger.warn("Fallo al refrescar ruta {}: {}", clave, resultado.getMensajeError());
            }
            
        } catch (Exception e) {
            logger.error("Error refrescando ruta {}: {}", rutaRefresh.getClaveRuta(), e.getMessage(), e);
        }
    }
    
    private void analizarYOptimizarRegiones() {
        logger.debug("Iniciando análisis y optimización de regiones");
        
        // Limpiar regiones vacías
        rutasPorRegion.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        
        // Estadísticas
        int regionesLimpiadas = 0;
        int rutasTotales = rutasPorRegion.values().stream().mapToInt(Set::size).sum();
        
        logger.debug("Optimización de regiones completada: {} regiones, {} rutas trackeadas", 
            rutasPorRegion.size(), rutasTotales);
    }
    
    // Clases auxiliares
    
    /**
     * Clave inmutable para identificar rutas en el cache
     */
    public static class ClaveRuta {
        private final Punto origen;
        private final Punto destino;
        private final int hashCode;
        
        public ClaveRuta(Punto origen, Punto destino) {
            this.origen = origen.clonar();
            this.destino = destino.clonar();
            this.hashCode = Objects.hash(origen.getX(), origen.getY(), destino.getX(), destino.getY());
        }
        
        public Punto getOrigen() { return origen; }
        public Punto getDestino() { return destino; }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            ClaveRuta that = (ClaveRuta) obj;
            return origen.equals(that.origen) && destino.equals(that.destino);
        }
        
        @Override
        public int hashCode() { return hashCode; }
        
        @Override
        public String toString() {
            return String.format("%s -> %s", origen, destino);
        }
    }
    
    /**
     * Representa una región del grid para invalidación por área
     */
    private static class RegionGrid {
        private final int x;
        private final int y;
        private final int tamano;
        
        public RegionGrid(int puntoX, int puntoY, int tamanoRegion) {
            this.x = puntoX / tamanoRegion;
            this.y = puntoY / tamanoRegion;
            this.tamano = tamanoRegion;
        }
        
        public int getX() { return x; }
        public int getY() { return y; }
        
        public boolean intersectaCon(int minX, int minY, int maxX, int maxY) {
            int regionMinX = x * tamano;
            int regionMaxX = (x + 1) * tamano - 1;
            int regionMinY = y * tamano;
            int regionMaxY = (y + 1) * tamano - 1;
            
            return !(regionMaxX < minX || regionMinX > maxX || 
                    regionMaxY < minY || regionMinY > maxY);
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            RegionGrid that = (RegionGrid) obj;
            return x == that.x && y == that.y && tamano == that.tamano;
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(x, y, tamano);
        }
        
        @Override
        public String toString() {
            return String.format("Region[%d,%d]", x, y);
        }
    }
    
    /**
     * Ruta pendiente de refresh en background
     */
    private static class RutaParaRefrescar implements Comparable<RutaParaRefrescar> {
        private final ClaveRuta claveRuta;
        private final String razon;
        private final int prioridad;
        private final long timestamp;
        
        public RutaParaRefrescar(ClaveRuta claveRuta, String razon, int prioridad) {
            this.claveRuta = claveRuta;
            this.razon = razon;
            this.prioridad = prioridad;
            this.timestamp = System.currentTimeMillis();
        }
        
        public ClaveRuta getClaveRuta() { return claveRuta; }
        public String getRazon() { return razon; }
        public int getPrioridad() { return prioridad; }
        public long getTimestamp() { return timestamp; }
        
        @Override
        public int compareTo(RutaParaRefrescar other) {
            // Orden por prioridad (mayor prioridad primero), luego por timestamp
            int comparePrioridad = Integer.compare(other.prioridad, this.prioridad);
            if (comparePrioridad != 0) return comparePrioridad;
            
            return Long.compare(this.timestamp, other.timestamp);
        }
    }
    
    /**
     * Configuración del sistema de invalidación
     */
    public static class ConfiguracionInvalidacion {
        private int tamanoRegion = 50; // Tamaño de región en unidades de grid
        private boolean refreshAutomaticoHabilitado = true;
        private int maxRutasRefreshConcurrentes = 10;
        private long timeoutRefreshMs = 5000;
        
        // Getters y setters
        public int getTamanoRegion() { return tamanoRegion; }
        public void setTamanoRegion(int tamanoRegion) { this.tamanoRegion = tamanoRegion; }
        
        public boolean isRefreshAutomaticoHabilitado() { return refreshAutomaticoHabilitado; }
        public void setRefreshAutomaticoHabilitado(boolean refreshAutomaticoHabilitado) { 
            this.refreshAutomaticoHabilitado = refreshAutomaticoHabilitado; 
        }
        
        public int getMaxRutasRefreshConcurrentes() { return maxRutasRefreshConcurrentes; }
        public void setMaxRutasRefreshConcurrentes(int maxRutasRefreshConcurrentes) { 
            this.maxRutasRefreshConcurrentes = maxRutasRefreshConcurrentes; 
        }
        
        public long getTimeoutRefreshMs() { return timeoutRefreshMs; }
        public void setTimeoutRefreshMs(long timeoutRefreshMs) { 
            this.timeoutRefreshMs = timeoutRefreshMs; 
        }
    }
    
    /**
     * Estadísticas del sistema de invalidación
     */
    public static class EstadisticasInvalidacion {
        private int regionesTotales;
        private int rutasTrackeadas;
        private int colaRefreshPendientes;
        private boolean refreshActivo;
        
        // Getters y setters
        public int getRegionesTotales() { return regionesTotales; }
        public void setRegionesTotales(int regionesTotales) { this.regionesTotales = regionesTotales; }
        
        public int getRutasTrackeadas() { return rutasTrackeadas; }
        public void setRutasTrackeadas(int rutasTrackeadas) { this.rutasTrackeadas = rutasTrackeadas; }
        
        public int getColaRefreshPendientes() { return colaRefreshPendientes; }
        public void setColaRefreshPendientes(int colaRefreshPendientes) { 
            this.colaRefreshPendientes = colaRefreshPendientes; 
        }
        
        public boolean isRefreshActivo() { return refreshActivo; }
        public void setRefreshActivo(boolean refreshActivo) { this.refreshActivo = refreshActivo; }
        
        @Override
        public String toString() {
            return String.format("Invalidación: %d regiones, %d rutas, %d pendientes refresh", 
                regionesTotales, rutasTrackeadas, colaRefreshPendientes);
        }
    }
}