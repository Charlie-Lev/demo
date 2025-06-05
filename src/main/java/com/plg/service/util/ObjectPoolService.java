package com.plg.service.util;

import com.plg.domain.Punto;

import lombok.Data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Servicio de pooling de objetos para optimización de memoria
 * Reduce GC pressure reutilizando objetos frecuentemente creados
 */
@Component
public class ObjectPoolService {
    
    private static final Logger logger = LoggerFactory.getLogger(ObjectPoolService.class);
    
    // Pools thread-safe
    private final ConcurrentLinkedQueue<Punto> puntosPool = new ConcurrentLinkedQueue<>();
    
    // Configuración de pools
    private static final int MAX_PUNTOS_POOL = 1000;
    private static final int INITIAL_POOL_SIZE = 100;
    
    // Métricas
    private final AtomicLong puntosCreados = new AtomicLong(0);
    private final AtomicLong puntosReutilizados = new AtomicLong(0);
    private final AtomicLong puntosLiberados = new AtomicLong(0);
    
    public ObjectPoolService() {
        inicializarPools();
    }
    
    /**
     * Pre-poblar pools con objetos iniciales
     */
    private void inicializarPools() {
        // Pre-crear puntos en el pool
        for (int i = 0; i < INITIAL_POOL_SIZE; i++) {
            puntosPool.offer(new Punto(0, 0));
        }
        logger.info("ObjectPool inicializado: {} puntos pre-creados", INITIAL_POOL_SIZE);
    }
    
    /**
     * Obtener punto del pool o crear nuevo
     */
    public Punto obtenerPunto(int x, int y) {
        Punto punto = puntosPool.poll();
        
        if (punto != null) {
            // Reutilizar punto existente
            punto.setX(x);
            punto.setY(y);
            puntosReutilizados.incrementAndGet();
            return punto;
        } else {
            // Crear nuevo punto
            puntosCreados.incrementAndGet();
            return new Punto(x, y);
        }
    }
    
    /**
     * Liberar punto al pool para reutilización
     */
    public void liberarPunto(Punto punto) {
        if (punto != null && puntosPool.size() < MAX_PUNTOS_POOL) {
            // Resetear punto y devolverlo al pool
            punto.setX(0);
            punto.setY(0);
            puntosPool.offer(punto);
            puntosLiberados.incrementAndGet();
        }
    }
    
    /**
     * Obtener estadísticas del pool
     */
    public EstadisticasPool obtenerEstadisticas() {
        EstadisticasPool stats = new EstadisticasPool();
        stats.setPuntosEnPool(puntosPool.size());
        stats.setPuntosCreados(puntosCreados.get());
        stats.setPuntosReutilizados(puntosReutilizados.get());
        stats.setPuntosLiberados(puntosLiberados.get());
        
        long totalOperaciones = puntosCreados.get() + puntosReutilizados.get();
        if (totalOperaciones > 0) {
            stats.setTasaReutilizacion(
                (double) puntosReutilizados.get() / totalOperaciones * 100.0);
        }
        
        return stats;
    }
    
    /**
     * Limpiar pools (para testing o reinicio)
     */
    public void limpiarPools() {
        puntosPool.clear();
        puntosCreados.set(0);
        puntosReutilizados.set(0);
        puntosLiberados.set(0);
        logger.info("Pools limpiados");
    }
    @Data
    public static class EstadisticasPool {
        private int puntosEnPool;
        private long puntosCreados;
        private long puntosReutilizados;
        private long puntosLiberados;
        private double tasaReutilizacion;
        
        @Override
        public String toString() {
            return String.format("Pool Stats: %d en pool, %.1f%% reutilización, %d creados, %d liberados",
                puntosEnPool, tasaReutilizacion, puntosCreados, puntosLiberados);
        }
    }
}