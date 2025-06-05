package com.plg.service.util;

import com.plg.domain.Punto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cache inteligente para almacenamiento de rutas pre-calculadas
 * Optimiza rendimiento evitando recálculos de A* para rutas frecuentes
 */
@Component
public class CacheRutas {
    
    private static final Logger logger = LoggerFactory.getLogger(CacheRutas.class);
    
    // Configuración del cache
    private static final int CAPACIDAD_MAXIMA_DEFAULT = 5000;
    private static final int LIMITE_HITS_WARM_UP = 10;
    private static final long TIEMPO_VIDA_MS = 3600000; // 1 hora
    
    // Almacenamiento thread-safe
    private final Map<ClaveRuta, EntradaCache> cache;
    private final Map<ClaveRuta, Integer> contadorAccesos;
    private final Queue<ClaveRuta> colaLRU;
    
    // Configuración
    private int capacidadMaxima;
    private boolean cacheHabilitado;
    
    // Métricas
    private final AtomicLong totalConsultas = new AtomicLong(0);
    private final AtomicLong totalHits = new AtomicLong(0);
    private final AtomicLong totalMisses = new AtomicLong(0);
    private final AtomicLong totalInserciones = new AtomicLong(0);
    private final AtomicLong totalEvictions = new AtomicLong(0);
    
    public CacheRutas() {
        this.capacidadMaxima = CAPACIDAD_MAXIMA_DEFAULT;
        this.cacheHabilitado = true;
        this.cache = new ConcurrentHashMap<>();
        this.contadorAccesos = new ConcurrentHashMap<>();
        this.colaLRU = new LinkedList<>();
    }
    
    /**
     * Busca una ruta en el cache
     */
    public Optional<List<Punto>> obtenerRuta(Punto origen, Punto destino) {
        if (!cacheHabilitado) {
            return Optional.empty();
        }
        
        ClaveRuta clave = new ClaveRuta(origen, destino);
        totalConsultas.incrementAndGet();
        
        EntradaCache entrada = cache.get(clave);
        
        if (entrada != null && !entrada.estaExpirada()) {
            // Cache hit
            totalHits.incrementAndGet();
            entrada.actualizarUltimoAcceso();
            contadorAccesos.merge(clave, 1, Integer::sum);
            actualizarLRU(clave);
            
            logger.debug("Cache HIT: {} -> {} (distancia: {})", 
                origen, destino, entrada.getRuta().size());
            
            return Optional.of(new ArrayList<>(entrada.getRuta()));
        } else {
            // Cache miss
            totalMisses.incrementAndGet();
            
            if (entrada != null) {
                // Entrada expirada, remover
                cache.remove(clave);
                contadorAccesos.remove(clave);
                colaLRU.remove(clave);
            }
            
            logger.debug("Cache MISS: {} -> {}", origen, destino);
            return Optional.empty();
        }
    }
    
    /**
     * Almacena una ruta en el cache
     */
    public void almacenarRuta(Punto origen, Punto destino, List<Punto> ruta) {
        if (!cacheHabilitado || ruta == null || ruta.isEmpty()) {
            return;
        }
        
        ClaveRuta clave = new ClaveRuta(origen, destino);
        
        // Verificar espacio disponible
        if (cache.size() >= capacidadMaxima) {
            evictarRutaMenosUsada();
        }
        
        // Crear entrada de cache
        EntradaCache entrada = new EntradaCache(new ArrayList<>(ruta));
        
        // Almacenar
        cache.put(clave, entrada);
        contadorAccesos.put(clave, 1);
        colaLRU.offer(clave);
        
        totalInserciones.incrementAndGet();
        
        logger.debug("Cache STORE: {} -> {} (distancia: {})", 
            origen, destino, ruta.size());
    }
    
    /**
     * Pre-carga rutas frecuentes para warm-up del cache
     */
    public void precargarRutasFrecuentes(List<Punto> puntosImportantes) {
        logger.info("Iniciando pre-carga de rutas frecuentes para {} puntos", puntosImportantes.size());
        
        int rutasGeneradas = 0;
        
        // Generar combinaciones de puntos importantes (almacenes, puntos frecuentes)
        for (int i = 0; i < puntosImportantes.size(); i++) {
            for (int j = i + 1; j < puntosImportantes.size(); j++) {
                Punto origen = puntosImportantes.get(i);
                Punto destino = puntosImportantes.get(j);
                
                // Solo pre-cargar si no existe en cache
                if (obtenerRuta(origen, destino).isEmpty()) {
                    // Marcar para pre-cálculo (sin calcular aquí)
                    marcarParaPrecalculo(origen, destino);
                    rutasGeneradas++;
                }
            }
        }
        
        logger.info("Pre-carga completada: {} rutas marcadas para cálculo", rutasGeneradas);
    }
    
    /**
     * Invalida todo el cache (útil cuando cambian obstáculos)
     */
    public void invalidarCache() {
        cache.clear();
        contadorAccesos.clear();
        colaLRU.clear();
        
        logger.info("Cache invalidado completamente");
    }
    
    /**
     * Invalida rutas que pasan por un punto específico
     */
    public void invalidarRutasPorPunto(Punto punto) {
        Set<ClaveRuta> clavesAEliminar = new HashSet<>();
        
        for (Map.Entry<ClaveRuta, EntradaCache> entry : cache.entrySet()) {
            if (entry.getValue().getRuta().contains(punto)) {
                clavesAEliminar.add(entry.getKey());
            }
        }
        
        for (ClaveRuta clave : clavesAEliminar) {
            cache.remove(clave);
            contadorAccesos.remove(clave);
            colaLRU.remove(clave);
        }
        
        logger.info("Invalidadas {} rutas que pasan por punto {}", clavesAEliminar.size(), punto);
    }
    
    /**
     * Obtiene estadísticas del cache
     */
    public EstadisticasCache obtenerEstadisticas() {
        EstadisticasCache stats = new EstadisticasCache();
        
        long consultas = totalConsultas.get();
        long hits = totalHits.get();
        long misses = totalMisses.get();
        
        stats.setTotalConsultas(consultas);
        stats.setTotalHits(hits);
        stats.setTotalMisses(misses);
        stats.setTotalInserciones(totalInserciones.get());
        stats.setTotalEvictions(totalEvictions.get());
        
        stats.setTamanoActual(cache.size());
        stats.setCapacidadMaxima(capacidadMaxima);
        stats.setPorcentajeUso((double) cache.size() / capacidadMaxima * 100.0);
        
        if (consultas > 0) {
            stats.setTasaHit((double) hits / consultas * 100.0);
            stats.setTasaMiss((double) misses / consultas * 100.0);
        }
        
        // Calcular entrada más popular
        String rutaMasPopular = contadorAccesos.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(entry -> entry.getKey().toString() + " (" + entry.getValue() + " accesos)")
            .orElse("N/A");
        stats.setRutaMasPopular(rutaMasPopular);
        
        return stats;
    }
    
    /**
     * Optimiza el cache eliminando entradas poco usadas
     */
    public void optimizarCache() {
        logger.info("Iniciando optimización de cache");
        
        int tamanoInicial = cache.size();
        
        // Eliminar entradas expiradas
        eliminarEntradasExpiradas();
        
        // Si aún está por encima del 80% de capacidad, eliminar las menos usadas
        if (cache.size() > capacidadMaxima * 0.8) {
            eliminarEntradasMenosUsadas();
        }
        
        int tamanoFinal = cache.size();
        logger.info("Optimización completada: {} -> {} entradas", tamanoInicial, tamanoFinal);
    }
    
    /**
     * Configura el tamaño máximo del cache
     */
    public void configurarCapacidad(int nuevaCapacidad) {
        this.capacidadMaxima = Math.max(100, nuevaCapacidad); // Mínimo 100
        
        // Reducir cache si excede nueva capacidad
        while (cache.size() > capacidadMaxima) {
            evictarRutaMenosUsada();
        }
        
        logger.info("Capacidad del cache configurada a: {}", capacidadMaxima);
    }
    
    /**
     * Habilita o deshabilita el cache
     */
    public void habilitarCache(boolean habilitado) {
        this.cacheHabilitado = habilitado;
        
        if (!habilitado) {
            invalidarCache();
        }
        
        logger.info("Cache {}", habilitado ? "habilitado" : "deshabilitado");
    }
    
    // Métodos privados
    
    private void evictarRutaMenosUsada() {
        if (cache.isEmpty()) return;
        
        // Encontrar entrada menos usada usando LRU + contador de accesos
        ClaveRuta claveAEliminar = contadorAccesos.entrySet().stream()
            .min(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(colaLRU.poll());
        
        if (claveAEliminar != null) {
            cache.remove(claveAEliminar);
            contadorAccesos.remove(claveAEliminar);
            colaLRU.remove(claveAEliminar);
            totalEvictions.incrementAndGet();
            
            logger.debug("Evicted ruta: {}", claveAEliminar);
        }
    }
    
    private void eliminarEntradasExpiradas() {
        Set<ClaveRuta> clavesExpiradas = new HashSet<>();
        
        for (Map.Entry<ClaveRuta, EntradaCache> entry : cache.entrySet()) {
            if (entry.getValue().estaExpirada()) {
                clavesExpiradas.add(entry.getKey());
            }
        }
        
        for (ClaveRuta clave : clavesExpiradas) {
            cache.remove(clave);
            contadorAccesos.remove(clave);
            colaLRU.remove(clave);
        }
        
        if (!clavesExpiradas.isEmpty()) {
            logger.debug("Eliminadas {} entradas expiradas", clavesExpiradas.size());
        }
    }
    
    private void eliminarEntradasMenosUsadas() {
        // Eliminar 20% de las entradas menos usadas
        int cantidadAEliminar = (int) (cache.size() * 0.2);
        
        List<ClaveRuta> menosUsadas = contadorAccesos.entrySet().stream()
            .sorted(Map.Entry.comparingByValue())
            .limit(cantidadAEliminar)
            .map(Map.Entry::getKey)
            .toList();
        
        for (ClaveRuta clave : menosUsadas) {
            cache.remove(clave);
            contadorAccesos.remove(clave);
            colaLRU.remove(clave);
        }
        
        logger.debug("Eliminadas {} entradas menos usadas", menosUsadas.size());
    }
    
    private void actualizarLRU(ClaveRuta clave) {
        colaLRU.remove(clave);
        colaLRU.offer(clave);
    }
    
    private void marcarParaPrecalculo(Punto origen, Punto destino) {
        // En implementación real, esto podría agregar a una cola de pre-cálculo
        logger.debug("Marcado para pre-cálculo: {} -> {}", origen, destino);
    }
    
    // Clases auxiliares
    
    private static class ClaveRuta {
        private final Punto origen;
        private final Punto destino;
        private final int hashCode;
        
        public ClaveRuta(Punto origen, Punto destino) {
            this.origen = origen.clonar();
            this.destino = destino.clonar();
            this.hashCode = Objects.hash(origen.getX(), origen.getY(), destino.getX(), destino.getY());
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            
            ClaveRuta that = (ClaveRuta) obj;
            return origen.equals(that.origen) && destino.equals(that.destino);
        }
        
        @Override
        public int hashCode() {
            return hashCode;
        }
        
        @Override
        public String toString() {
            return String.format("%s -> %s", origen, destino);
        }
    }
    
    private static class EntradaCache {
        private final List<Punto> ruta;
        private final long tiempoCreacion;
        private long ultimoAcceso;
        
        public EntradaCache(List<Punto> ruta) {
            this.ruta = ruta;
            this.tiempoCreacion = System.currentTimeMillis();
            this.ultimoAcceso = tiempoCreacion;
        }
        
        public List<Punto> getRuta() {
            return ruta;
        }
        
        public boolean estaExpirada() {
            return (System.currentTimeMillis() - ultimoAcceso) > TIEMPO_VIDA_MS;
        }
        
        public void actualizarUltimoAcceso() {
            this.ultimoAcceso = System.currentTimeMillis();
        }
    }
    
    public static class EstadisticasCache {
        private long totalConsultas;
        private long totalHits;
        private long totalMisses;
        private long totalInserciones;
        private long totalEvictions;
        private int tamanoActual;
        private int capacidadMaxima;
        private double porcentajeUso;
        private double tasaHit;
        private double tasaMiss;
        private String rutaMasPopular;
        
        // Getters y setters
        public long getTotalConsultas() { return totalConsultas; }
        public void setTotalConsultas(long totalConsultas) { this.totalConsultas = totalConsultas; }
        
        public long getTotalHits() { return totalHits; }
        public void setTotalHits(long totalHits) { this.totalHits = totalHits; }
        
        public long getTotalMisses() { return totalMisses; }
        public void setTotalMisses(long totalMisses) { this.totalMisses = totalMisses; }
        
        public long getTotalInserciones() { return totalInserciones; }
        public void setTotalInserciones(long totalInserciones) { this.totalInserciones = totalInserciones; }
        
        public long getTotalEvictions() { return totalEvictions; }
        public void setTotalEvictions(long totalEvictions) { this.totalEvictions = totalEvictions; }
        
        public int getTamanoActual() { return tamanoActual; }
        public void setTamanoActual(int tamanoActual) { this.tamanoActual = tamanoActual; }
        
        public int getCapacidadMaxima() { return capacidadMaxima; }
        public void setCapacidadMaxima(int capacidadMaxima) { this.capacidadMaxima = capacidadMaxima; }
        
        public double getPorcentajeUso() { return porcentajeUso; }
        public void setPorcentajeUso(double porcentajeUso) { this.porcentajeUso = porcentajeUso; }
        
        public double getTasaHit() { return tasaHit; }
        public void setTasaHit(double tasaHit) { this.tasaHit = tasaHit; }
        
        public double getTasaMiss() { return tasaMiss; }
        public void setTasaMiss(double tasaMiss) { this.tasaMiss = tasaMiss; }
        
        public String getRutaMasPopular() { return rutaMasPopular; }
        public void setRutaMasPopular(String rutaMasPopular) { this.rutaMasPopular = rutaMasPopular; }
        
        @Override
        public String toString() {
            return String.format("Cache: %d/%d (%.1f%%), Hit: %.1f%%, Miss: %.1f%%, Popular: %s",
                tamanoActual, capacidadMaxima, porcentajeUso, tasaHit, tasaMiss, rutaMasPopular);
        }
    }
}