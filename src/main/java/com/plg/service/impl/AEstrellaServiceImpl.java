package com.plg.service.impl;

import com.plg.domain.Punto;
import com.plg.domain.ResultadoAEstrella;
import com.plg.domain.enumeration.TipoError;
import com.plg.service.AEstrellaService;
import com.plg.service.util.GestorObstaculos;
import com.plg.service.util.ObjectPoolService;

import jakarta.annotation.PreDestroy;
import lombok.Data;

import com.plg.service.util.CacheRutas;
import com.plg.service.util.CacheRutasWarmup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import java.util.concurrent.ExecutionException;
/**
 * Implementación optimizada del algoritmo A* para pathfinding en grid cartesiano
 * Thread-safe, con cache inteligente y métricas de rendimiento
 */
@Service
public class AEstrellaServiceImpl implements AEstrellaService {
    
    private static final Logger logger = LoggerFactory.getLogger(AEstrellaServiceImpl.class);
    
    @Autowired
    private GestorObstaculos gestorObstaculos;
    
    @Autowired
    private CacheRutas cacheRutas;
    @Autowired
    private CacheRutasWarmup cacheWarmup;
    // Configuración
    private ConfiguracionAEstrella configuracion;
    
    // Pool de threads para cálculos asíncronos
    private ThreadPoolExecutor executorService;  // Cambiar a ThreadPoolExecutor para más control
    private final AtomicInteger threadCounter = new AtomicInteger(0);  // ✅ NUEVO - Contador para nombres de threads
    
    // Estadísticas thread-safe
    private final AtomicLong totalCalculos = new AtomicLong(0);
    private final AtomicLong calculosExitosos = new AtomicLong(0);
    private final AtomicLong calculosFallidos = new AtomicLong(0);
    private final AtomicLong calculosDesdeCache = new AtomicLong(0);
    private final AtomicLong tiempoTotalMs = new AtomicLong(0);
    private final AtomicLong nodesTotalExplorados = new AtomicLong(0);
    private final AtomicLong calculosParalelos = new AtomicLong(0);
    // Pool de objetos para reducir GC pressure
    private final Queue<NodoAEstrella> nodoPool = new ArrayDeque<>();
    // Pool para objetos Punto 
    @Autowired
    private ObjectPoolService objectPoolService;
    // Monitor de memoria

    public AEstrellaServiceImpl() {
        this.configuracion = new ConfiguracionAEstrella();
        inicializarThreadPool();
    }
    private void inicializarThreadPool() {
        // ThreadFactory personalizado para naming
        ThreadFactory threadFactory = new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, 
                    configuracion.getPrefijosNombreThread() + "-" + threadCounter.incrementAndGet());
                thread.setDaemon(true);  // Daemon threads para shutdown limpio
                thread.setPriority(Thread.NORM_PRIORITY);
                return thread;
            }
        };
        
        // Crear ThreadPoolExecutor con configuración específica
        this.executorService = new ThreadPoolExecutor(
            configuracion.getNumeroThreads(),              // corePoolSize
            configuracion.getNumeroThreads(),              // maximumPoolSize
            60L,                                           // keepAliveTime
            TimeUnit.SECONDS,                              // keepAliveTime unit
            new LinkedBlockingQueue<>(),                   // Unbounded queue como requerido
            threadFactory,                                 // ThreadFactory personalizado
            new ThreadPoolExecutor.CallerRunsPolicy()     // RejectionHandler - ejecutar en caller thread
        );
        
        // Configurar para pre-start de threads
        executorService.prestartAllCoreThreads();
        logger.info("Ejecutando warm-up tasks para thread pool");
        ejecutarWarmupTasksDummy();
        logger.info("ThreadPool A* inicializado: {} threads, prefijo: '{}'", 
            configuracion.getNumeroThreads(), configuracion.getPrefijosNombreThread());
    }
    public void reconfigurarThreadPool() {
        if (executorService != null && !executorService.isShutdown()) {
            shutdownThreadPoolGraceful();
        }
        inicializarThreadPool();
        logger.info("ThreadPool A* reconfigurado");
    }
    @Override
    public ResultadoAEstrella calcularRuta(Punto origen, Punto destino) {
        return calcularRuta(origen, destino, configuracion);
    }
    
    @Override
    public ResultadoAEstrella calcularRuta(Punto origen, Punto destino, ConfiguracionAEstrella config) {
        totalCalculos.incrementAndGet();
        long tiempoInicio = System.currentTimeMillis();
        cacheWarmup.registrarRutaSolicitada(origen, destino);
        logger.debug("Calculando ruta A*: {} -> {}", origen, destino);
        
        // Validaciones previas
        ResultadoAEstrella validacion = validarEntrada(origen, destino);
        if (validacion != null) {
            calculosFallidos.incrementAndGet();
            return validacion;
        }
        
        // Verificar cache primero
        if (config.isUsarCache()) {
            Optional<List<Punto>> rutaCache = cacheRutas.obtenerRuta(origen, destino);
            if (rutaCache.isPresent()) {
                calculosDesdeCache.incrementAndGet();
                calculosExitosos.incrementAndGet();
                return ResultadoAEstrella.desdeCache(origen, destino, rutaCache.get());
            }
        }
        
        // Ejecutar algoritmo A*
        ResultadoAEstrella resultado = ejecutarAEstrella(origen, destino, config, tiempoInicio);
        
        // Actualizar estadísticas
        if (resultado.isCalculoExitoso()) {
            calculosExitosos.incrementAndGet();
            tiempoTotalMs.addAndGet(resultado.getTiempoCalculoMs());
            nodesTotalExplorados.addAndGet(resultado.getNodosExplorados());
            
            // Almacenar en cache si es exitoso
            if (config.isUsarCache() && resultado.isRutaOptima()) {
                cacheRutas.almacenarRuta(origen, destino, resultado.getRutaEncontrada());
            }
        } else {
            calculosFallidos.incrementAndGet();
        }
        
        logger.debug("Ruta A* completada: {} ({}ms)", 
            resultado.isCalculoExitoso() ? "ÉXITO" : "FALLO", resultado.getTiempoCalculoMs());
        
        return resultado;
    }
    
    @Override
    public List<ResultadoAEstrella> calcularRutasParalelo(List<ParOrigenDestino> pares) {
        logger.info("Calculando {} rutas en paralelo", pares.size());
        
        // Verificar si paralelización está habilitada - FALLBACK
        if (!configuracion.isHabilitarParalelizacion() || pares.size() == 1) {
            logger.debug("Paralelización deshabilitada o pocos elementos, ejecutando secuencial");
            return pares.stream()
                .map(par -> calcularRuta(par.getOrigen(), par.getDestino()))
                .toList();
        }
        
        calculosParalelos.addAndGet(pares.size());
        
        // C) SINCRONIZACIÓN Y AGREGACIÓN - CompletableFuture.allOf()
        List<CompletableFuture<ResultadoAEstrella>> futures = pares.stream()
            .map(par -> CompletableFuture.supplyAsync(() -> {
                try {
                    return calcularRuta(par.getOrigen(), par.getDestino());
                } catch (Exception e) {
                    logger.error("Error en cálculo A* paralelo: {} -> {} - {}", 
                        par.getOrigen(), par.getDestino(), e.getMessage());
                    return ResultadoAEstrella.fallido(
                        par.getOrigen(), par.getDestino(), 
                        TipoError.ERROR_INTERNO, 
                        "Error en task paralelo: " + e.getMessage());
                }
            }, executorService))
            .collect(Collectors.toList());
        
        // CompletableFuture.allOf() con timeout y error aggregation
        try {
            // Esperar todos los cálculos con timeout global
            CompletableFuture<Void> todosFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));
            
            // Timeout global configurable (por defecto 30 segundos)
            long timeoutGlobal = configuracion.getTimeoutMs() * pares.size() / 4; // 25% del tiempo total
            timeoutGlobal = Math.max(timeoutGlobal, 10000); // Mínimo 10 segundos
            
            todosFutures.get(timeoutGlobal, TimeUnit.MILLISECONDS);
            
            // Result collection - Todos completados exitosamente
            List<ResultadoAEstrella> resultados = futures.stream()
                .map(CompletableFuture::join) // Safe join ya que sabemos que están completos
                .collect(Collectors.toList());
            
            logger.info("Cálculo paralelo completado: {}/{} exitosos", 
                resultados.stream().mapToInt(r -> r.isCalculoExitoso() ? 1 : 0).sum(),
                resultados.size());
            
            return resultados;
            
        } catch (TimeoutException e) {
            // Error aggregation - Timeout global
            logger.warn("Timeout global en cálculo paralelo ({}ms), recolectando resultados parciales", 
                configuracion.getTimeoutMs() * pares.size() / 4);
            
            return recolectarResultadosParciales(futures, pares);
            
        } catch (ExecutionException | InterruptedException e) {
            // Error aggregation - Error en algún cálculo
            logger.error("Error en cálculo paralelo: {}", e.getMessage());
            
            // Fallback - Si paralelización falla completamente, ejecutar secuencialmente
            logger.info("Fallback: ejecutando {} cálculos secuencialmente", pares.size());
            return pares.stream()
                .map(par -> calcularRuta(par.getOrigen(), par.getDestino()))
                .collect(Collectors.toList());
        }
    }
    /**
     * Result collection parcial cuando hay timeout
     */
    private List<ResultadoAEstrella> recolectarResultadosParciales(
            List<CompletableFuture<ResultadoAEstrella>> futures, 
            List<ParOrigenDestino> pares) {
        
        List<ResultadoAEstrella> resultados = new ArrayList<>();
        
        for (int i = 0; i < futures.size(); i++) {
            CompletableFuture<ResultadoAEstrella> future = futures.get(i);
            ParOrigenDestino par = pares.get(i);
            
            try {
                // Timeout individual de 100ms para recolección rápida
                ResultadoAEstrella resultado = future.get(100, TimeUnit.MILLISECONDS);
                resultados.add(resultado);
                
            } catch (TimeoutException | ExecutionException | InterruptedException e) {
                // Cancelar future pendiente
                future.cancel(true);
                
                // Crear resultado de timeout
                ResultadoAEstrella resultadoTimeout = ResultadoAEstrella.fallido(
                    par.getOrigen(), par.getDestino(), 
                    TipoError.TIMEOUT, "Timeout en recolección parcial");
                resultados.add(resultadoTimeout);
            }
        }
        
        logger.info("Recolección parcial completada: {}/{} resultados obtenidos", 
            resultados.stream().mapToInt(r -> r.isCalculoExitoso() ? 1 : 0).sum(),
            resultados.size());
        
        return resultados;
    }        
    @Override
    public CompletableFuture<ResultadoAEstrella> calcularRutaAsincrona(Punto origen, Punto destino) {
        return CompletableFuture.supplyAsync(() -> calcularRuta(origen, destino), executorService);
    }
    
    @Override
    public boolean existeRuta(Punto origen, Punto destino) {
        // Verificación rápida sin calcular la ruta completa
        
        // 1. Verificar cache primero
        if (cacheRutas.obtenerRuta(origen, destino).isPresent()) {
            return true;
        }
        
        // 2. Verificación simple de alcanzabilidad
        if (!gestorObstaculos.esPuntoValido(origen) || !gestorObstaculos.esPuntoValido(destino)) {
            return false;
        }
        
        // 3. A* limitado (máximo 1000 nodos)
        ConfiguracionAEstrella configRapida = new ConfiguracionAEstrella();
        configRapida.setMaxNodosExplorados(1000);
        configRapida.setTimeoutMs(500);
        configRapida.setUsarCache(false);
        
        ResultadoAEstrella resultado = calcularRuta(origen, destino, configRapida);
        return resultado.isRutaExiste();
    }
    
    @Override
    public int calcularDistanciaEstimada(Punto origen, Punto destino) {
        return origen.distanciaManhattanHasta(destino);
    }
    
    @Override
    public void precalcularRutasFrecuentes(List<Punto> puntosImportantes) {
        logger.info("Pre-calculando rutas frecuentes entre {} puntos", puntosImportantes.size());
        
        List<ParOrigenDestino> paresPrecalculo = new ArrayList<>();
        
        // Generar todos los pares de puntos importantes
        for (int i = 0; i < puntosImportantes.size(); i++) {
            for (int j = i + 1; j < puntosImportantes.size(); j++) {
                Punto p1 = puntosImportantes.get(i);
                Punto p2 = puntosImportantes.get(j);
                
                paresPrecalculo.add(new ParOrigenDestino(p1, p2, "Precálculo " + i + "-" + j));
                paresPrecalculo.add(new ParOrigenDestino(p2, p1, "Precálculo " + j + "-" + i));
            }
        }
        
        // Calcular en paralelo
        calcularRutasParalelo(paresPrecalculo);
        
        logger.info("Pre-cálculo completado: {} rutas procesadas", paresPrecalculo.size());
    }
    
    @Override
    public void optimizarConfiguracion() {
        // Optimización basada en estadísticas de uso
        EstadisticasAEstrella stats = obtenerEstadisticas();
        
        if (stats.getTiempoPromedioMs() > 1000) {
            // Si los cálculos son lentos, usar configuración más agresiva
            configuracion.setMaxNodosExplorados(5000);
            configuracion.setFactorHeuristica(1.2);
            logger.info("Configuración optimizada para velocidad");
        } else if (stats.getTasaExito() < 90.0) {
            // Si la tasa de éxito es baja, usar configuración más conservadora
            configuracion.setMaxNodosExplorados(20000);
            configuracion.setTimeoutMs(10000);
            logger.info("Configuración optimizada para precisión");
        }
    }
    
    @Override
    public EstadisticasAEstrella obtenerEstadisticas() {
        EstadisticasAEstrella stats = new EstadisticasAEstrella();
        
        // Estadísticas existentes...
        long total = totalCalculos.get();
        stats.setTotalCalculos(total);
        stats.setCalculosExitosos(calculosExitosos.get());
        stats.setCalculosFallidos(calculosFallidos.get());
        stats.setCalculosDesdeCache(calculosDesdeCache.get());
        
        if (total > 0) {
            stats.setTiempoPromedioMs((double) tiempoTotalMs.get() / (total - calculosDesdeCache.get()));
            stats.setNodosPromedioExplorados((double) nodesTotalExplorados.get() / (total - calculosDesdeCache.get()));
        }
        
        if (executorService != null && !executorService.isShutdown()) {
            stats.setThreadPoolTareasCompletadas(executorService.getCompletedTaskCount());
            stats.setThreadPoolTareasEnCola(executorService.getQueue().size());
            stats.setThreadPoolTamanoActual(executorService.getPoolSize());
            
            // Calcular utilización del pool
            long totalTareas = executorService.getTaskCount();
            if (totalTareas > 0) {
                stats.setUtilizacionPromPoolThreads(
                    (double) executorService.getActiveCount() / executorService.getPoolSize() * 100.0);
            }
            
            // Calcular throughput (cálculos por segundo)
            if (tiempoTotalMs.get() > 0) {
                double segundosTotal = tiempoTotalMs.get() / 1000.0;
                stats.setThroughputCalculosPorSegundo(total / segundosTotal);
            }
        }
        
        // Estadísticas del cache (existentes)
        CacheRutas.EstadisticasCache statsCache = cacheRutas.obtenerEstadisticas();
        stats.setTasaHitCache(statsCache.getTasaHit());
        stats.setRutasEnCache(statsCache.getTamanoActual());
        
        return stats;
    }
    
    @Override
    public void configurar(ConfiguracionAEstrella configuracion) {
        ConfiguracionAEstrella configAnterior = this.configuracion;
        this.configuracion = configuracion;
        
        // Si cambió la configuración del pool, reconfigurarlo
        if (configAnterior.getNumeroThreads() != configuracion.getNumeroThreads() ||
            !configAnterior.getPrefijosNombreThread().equals(configuracion.getPrefijosNombreThread()) ||
            configAnterior.isHabilitarParalelizacion() != configuracion.isHabilitarParalelizacion()) {
            
            logger.info("Reconfigurando ThreadPool A* por cambios en configuración");
            reconfigurarThreadPool();
        }
        
        logger.info("Configuración A* actualizada: timeout={}ms, maxNodos={}, threads={}", 
            configuracion.getTimeoutMs(), configuracion.getMaxNodosExplorados(), 
            configuracion.getNumeroThreads());
    }
    
    @Override
    public void reiniciar() {
        totalCalculos.set(0);
        calculosExitosos.set(0);
        calculosFallidos.set(0);
        calculosDesdeCache.set(0);
        tiempoTotalMs.set(0);
        nodesTotalExplorados.set(0);
        
        cacheRutas.invalidarCache();
        
        logger.info("Servicio A* reiniciado");
    }
    
    // Métodos privados - Implementación del algoritmo A*
    
    private ResultadoAEstrella validarEntrada(Punto origen, Punto destino) {
        if (origen == null || destino == null) {
            return ResultadoAEstrella.fallido(origen, destino, TipoError.CONFIGURACION_INVALIDA, 
                "Origen o destino nulo");
        }
        
        if (origen.equals(destino)) {
            // Caso trivial: mismo punto
            List<Punto> rutaTrivial = Arrays.asList(origen.clonar());
            return ResultadoAEstrella.exitoso(origen, destino, rutaTrivial);
        }
        
        if (!gestorObstaculos.isMapaInicializado()) {
            return ResultadoAEstrella.fallido(origen, destino, TipoError.MAPA_NO_DISPONIBLE, 
                "Mapa de obstáculos no inicializado");
        }
        
        if (!gestorObstaculos.esPuntoValido(origen)) {
            return ResultadoAEstrella.fallido(origen, destino, TipoError.ORIGEN_INVALIDO, 
                "Punto de origen obstruido o fuera de límites");
        }
        
        if (!gestorObstaculos.esPuntoValido(destino)) {
            return ResultadoAEstrella.fallido(origen, destino, TipoError.DESTINO_INVALIDO, 
                "Punto de destino obstruido o fuera de límites");
        }
        
        return null; // Validación pasada
    }
    
    private ResultadoAEstrella ejecutarAEstrella(Punto origen, Punto destino, 
                                               ConfiguracionAEstrella config, long tiempoInicio) {
        
        // � Log del inicio de cálculo
        logger.info("🗺️ INICIANDO A* desde ({},{}) hacia ({},{})", 
                origen.getX(), origen.getY(), destino.getX(), destino.getY());
        
        // Si está habilitada la búsqueda bidireccional, usar ese algoritmo
        if (config.isUsarBusquedaBidireccional()) {
            return ejecutarAEstrellaBidireccional(origen, destino, config, tiempoInicio);
        }
        
        ResultadoAEstrella resultado = new ResultadoAEstrella(origen, destino);
        
        // Estructuras del algoritmo A*
        int distanciaEstimada = origen.distanciaManhattanHasta(destino);
        int capacidadEstimada = Math.min(distanciaEstimada * 3, config.getMaxNodosExplorados());
    
        PriorityQueue<NodoAEstrella> frontera = new PriorityQueue<>(
            Math.max(16, capacidadEstimada / 10), 
            Comparator.comparingDouble(NodoAEstrella::getF)
        );
        Set<Punto> visitados = new HashSet<>(capacidadEstimada);
        Map<Punto, Integer> costosG = new HashMap<>(capacidadEstimada);
        Map<Punto, Punto> padres = new HashMap<>(capacidadEstimada);
    
        // Inicialización
        NodoAEstrella nodoInicial = obtenerNodo(origen, 0, calcularH(origen, destino, config));
        frontera.add(nodoInicial);
        costosG.put(origen, 0);
        
        int nodosExplorados = 0;
        boolean timeoutAlcanzado = false;
        boolean limiteProfundidadAlcanzado = false;
        
        // 🔥 Log de inicialización
        logger.info("⚡ INICIALIZACIÓN A*: Distancia estimada: {} unidades, Capacidad frontera: {}", 
                   distanciaEstimada, capacidadEstimada);
        
        // Bucle principal del algoritmo A*
        while (!frontera.isEmpty() && !timeoutAlcanzado && !limiteProfundidadAlcanzado) {
            
            // Verificar timeout
            if (System.currentTimeMillis() - tiempoInicio > config.getTimeoutMs()) {
                timeoutAlcanzado = true;
                break;
            }
            
            // Verificar límite de nodos
            if (nodosExplorados >= config.getMaxNodosExplorados()) {
                limiteProfundidadAlcanzado = true;
                break;
            }
            
            // Obtener el nodo con menor f
            NodoAEstrella actual = frontera.poll();
            Punto puntoActual = actual.getPunto();
            liberarNodo(actual);
            
            // Si ya fue visitado, continuar
            if (visitados.contains(puntoActual)) {
                continue;
            }
            
            // Marcar como visitado
            visitados.add(puntoActual);
            nodosExplorados++;
            
            // 🔥 Log de nodo siendo explorado
            logger.debug("🔍 Explorando nodo: ({},{}) - G:{} H:{} F:{:.1f} [Explorados: {}/{}]", 
                        puntoActual.getX(), puntoActual.getY(), 
                        actual.getG(), actual.getH(), actual.getF(), 
                        nodosExplorados, config.getMaxNodosExplorados());
            
            if (config.isModoDebug()) {
                resultado.agregarNodoExplorado(puntoActual);
                resultado.agregarTrazaDebug(String.format("Explorando: %s (g=%d, h=%d, f=%.2f)", 
                    puntoActual, actual.getG(), actual.getH(), actual.getF()));
            }
            
            // ¿Llegamos al destino?
            if (puntoActual.equals(destino)) {
                logger.info("🎯 DESTINO ENCONTRADO en ({},{}) después de explorar {} nodos en {}ms", 
                           destino.getX(), destino.getY(), nodosExplorados, 
                           System.currentTimeMillis() - tiempoInicio);
                
                List<Punto> rutaCompleta = reconstruirRuta(padres, destino);
                resultado.establecerRutaEncontrada(rutaCompleta);
                resultado.completarCalculo(tiempoInicio, nodosExplorados, frontera.size());
                
                // 🔥 Log de la ruta completa nodo por nodo
                logRutaCompleta(rutaCompleta, origen, destino);
                
                return resultado;
            }
            
            // Explorar vecinos con Jump Point Search para líneas rectas
            List<Punto> vecinosOptimizados = obtenerVecinosSimples(puntoActual);
            
            // 🔥 Log de vecinos encontrados
            logger.trace("  📋 Vecinos encontrados para ({},{}): {}", 
                        puntoActual.getX(), puntoActual.getY(), vecinosOptimizados.size());
    
            for (Punto vecino : vecinosOptimizados) {
                if (visitados.contains(vecino)) {
                    continue;
                }
                
                int nuevoG = costosG.get(puntoActual) + calcularCostoMovimiento(puntoActual, vecino);
                
                // 🔥 Log de vecino siendo evaluado
                logger.trace("  📍 Evaluando vecino ({},{}) desde ({},{}) - G:{}", 
                            vecino.getX(), vecino.getY(), 
                            puntoActual.getX(), puntoActual.getY(), nuevoG);
                
                // 🔥 Verificar si hay obstáculos
                if (gestorObstaculos != null && !gestorObstaculos.esPuntoValido(vecino)) {
                    logger.trace("    🚧 OBSTÁCULO DETECTADO en ({},{}) - Saltando vecino", 
                                vecino.getX(), vecino.getY());
                    continue;
                }
                
                if (!costosG.containsKey(vecino) || nuevoG < costosG.get(vecino)) {
                    costosG.put(vecino, nuevoG);
                    padres.put(vecino, puntoActual);
                    
                    int h = calcularH(vecino, destino, config);
                    NodoAEstrella nodoVecino = obtenerNodo(vecino, nuevoG, h);
                    frontera.add(nodoVecino);
                    
                    // 🔥 Log cuando agrega nodo a la cola
                    logger.trace("    ✅ Agregado a cola: ({},{}) - G:{} H:{} F:{}", 
                               vecino.getX(), vecino.getY(), nuevoG, h, nuevoG + h);
                } else {
                    logger.trace("    ❌ Camino más costoso descartado: ({},{}) - G:{} vs {}", 
                               vecino.getX(), vecino.getY(), nuevoG, costosG.get(vecino));
                }
            }
            
            // 🔥 Log periódico de progreso
            if (nodosExplorados % 1000 == 0) {
                logger.debug("🔄 Progreso A*: {} nodos explorados, {} en frontera, tiempo: {}ms", 
                            nodosExplorados, frontera.size(), 
                            System.currentTimeMillis() - tiempoInicio);
            }
        }
        
        // No se encontró ruta
        resultado.setTimeoutAlcanzado(timeoutAlcanzado);
        resultado.setLimiteProfundidadAlcanzado(limiteProfundidadAlcanzado);
        resultado.completarCalculo(tiempoInicio, nodosExplorados, frontera.size());
        
        TipoError tipoError;
        String mensaje;
        
        if (timeoutAlcanzado) {
            tipoError = TipoError.TIMEOUT;
            mensaje = String.format("Timeout alcanzado (%dms)", config.getTimeoutMs());
            logger.warn("⏰ TIMEOUT A*: {} nodos explorados en {}ms", 
                       nodosExplorados, config.getTimeoutMs());
        } else if (limiteProfundidadAlcanzado) {
            tipoError = TipoError.MEMORIA_INSUFICIENTE;
            mensaje = String.format("Límite de nodos alcanzado (%d)", config.getMaxNodosExplorados());
            logger.warn("🔒 LÍMITE NODOS A*: {} nodos explorados", config.getMaxNodosExplorados());
        } else {
            tipoError = TipoError.RUTA_NO_EXISTE;
            mensaje = "No existe ruta entre los puntos";
            logger.warn("❌ NO HAY RUTA desde ({},{}) hacia ({},{}) - {} nodos explorados", 
                       origen.getX(), origen.getY(), destino.getX(), destino.getY(), nodosExplorados);
        }
        
        while (!frontera.isEmpty()) {
            liberarNodo(frontera.poll());
        }
        
        return ResultadoAEstrella.fallido(origen, destino, tipoError, mensaje);
    }
    private void logRutaCompleta(List<Punto> ruta, Punto origen, Punto destino) {
        logger.info("🛣️ RUTA COMPLETA desde ({},{}) hacia ({},{}):", 
                origen.getX(), origen.getY(), destino.getX(), destino.getY());
        
        // 🔥 MOSTRAR TODOS LOS PASOS DE LA RUTA
        for (int i = 0; i < ruta.size(); i++) {
            Punto punto = ruta.get(i);
            
            if (i == 0) {
                logger.info("  🏁 INICIO: ({},{})", punto.getX(), punto.getY());
            } else if (i == ruta.size() - 1) {
                logger.info("  🎯 FIN: ({},{})", punto.getX(), punto.getY());
            } else {
                // 🔥 CALCULAR DIRECCIÓN DEL MOVIMIENTO ANTERIOR Y SIGUIENTE
                String direccionAnterior = "";
                String direccionSiguiente = "";
                
                if (i > 0) {
                    Punto anterior = ruta.get(i - 1);
                    direccionAnterior = determinarDireccionDetallada(anterior, punto);
                }
                
                if (i < ruta.size() - 1) {
                    Punto siguiente = ruta.get(i + 1);
                    direccionSiguiente = determinarDireccionDetallada(punto, siguiente);
                    
                    // 🔥 DETECTAR CAMBIO DE DIRECCIÓN
                    if (!direccionAnterior.isEmpty() && !direccionSiguiente.isEmpty() && 
                        !direccionAnterior.equals(direccionSiguiente)) {
                        logger.info("  🔄 Paso {}: ({},{}) - CAMBIO: {} → {} ⚠️", 
                            i, punto.getX(), punto.getY(), direccionAnterior, direccionSiguiente);
                    } else {
                        logger.info("  📍 Paso {}: ({},{}) - {}", 
                            i, punto.getX(), punto.getY(), direccionSiguiente);
                    }
                } else {
                    logger.info("  📍 Paso {}: ({},{})", i, punto.getX(), punto.getY());
                }
            }
        }
        
        logger.info("📊 Total de pasos en ruta: {}", ruta.size());
    }

    private String determinarDireccionDetallada(Punto desde, Punto hacia) {
        int deltaX = hacia.getX() - desde.getX();
        int deltaY = hacia.getY() - desde.getY();
        
        if (deltaX > 0 && deltaY == 0) return "ESTE";
        if (deltaX < 0 && deltaY == 0) return "OESTE"; 
        if (deltaX == 0 && deltaY > 0) return "NORTE";
        if (deltaX == 0 && deltaY < 0) return "SUR";
        
        // Diagonales
        if (deltaX > 0 && deltaY > 0) return "NORESTE";
        if (deltaX > 0 && deltaY < 0) return "SURESTE";
        if (deltaX < 0 && deltaY > 0) return "NOROESTE";
        if (deltaX < 0 && deltaY < 0) return "SUROESTE";
        
        return "ESTÁTICO";
    }

    private ResultadoAEstrella ejecutarAEstrellaBidireccional(Punto origen, Punto destino, 
                                                            ConfiguracionAEstrella config, long tiempoInicio) {
        
        // Inicializar búsquedas desde ambos extremos
        Map<Punto, Integer> costosDesdeOrigen = new HashMap<>();
        Map<Punto, Integer> costosDesdeDestino = new HashMap<>();
        Map<Punto, Punto> padresOrigen = new HashMap<>();
        Map<Punto, Punto> padresDestino = new HashMap<>();
        
        PriorityQueue<NodoAEstrella> fronteraOrigen = new PriorityQueue<>(Comparator.comparingDouble(NodoAEstrella::getF));
        PriorityQueue<NodoAEstrella> fronteraDestino = new PriorityQueue<>(Comparator.comparingDouble(NodoAEstrella::getF));
        
        Set<Punto> visitadosOrigen = new HashSet<>();
        Set<Punto> visitadosDestino = new HashSet<>();
        
        // Inicializar nodos de partida
        fronteraOrigen.add(new NodoAEstrella(origen, 0, calcularH(origen, destino, config)));
        fronteraDestino.add(new NodoAEstrella(destino, 0, calcularH(destino, origen, config)));
        
        costosDesdeOrigen.put(origen, 0);
        costosDesdeDestino.put(destino, 0);
        
        Punto puntoEncontrado = null;
        
        while (!fronteraOrigen.isEmpty() && !fronteraDestino.isEmpty() && puntoEncontrado == null) {
            // Alternar entre búsquedas
            if (fronteraOrigen.size() <= fronteraDestino.size()) {
                puntoEncontrado = expandirFrontera(fronteraOrigen, visitadosOrigen, costosDesdeOrigen, 
                                                padresOrigen, visitadosDestino, destino, config, true);
            } else {
                puntoEncontrado = expandirFrontera(fronteraDestino, visitadosDestino, costosDesdeDestino, 
                                                padresDestino, visitadosOrigen, origen, config, false);
            }
        }
        
        if (puntoEncontrado != null) {
            // Reconstruir ruta combinando ambas búsquedas
            List<Punto> rutaCompleta = reconstruirRutaBidireccional(padresOrigen, padresDestino, puntoEncontrado);
            return ResultadoAEstrella.exitoso(origen, destino, rutaCompleta);
        }
        
        return ResultadoAEstrella.fallido(origen, destino, TipoError.RUTA_NO_EXISTE, "Sin ruta bidireccional");
    }

    private int calcularH(Punto actual, Punto destino, ConfiguracionAEstrella config) {
        // Heurística Manhattan distance con factor de peso
        int distanciaManhattan = actual.distanciaManhattanHasta(destino);
        return (int) (distanciaManhattan * config.getFactorHeuristica());
    }
    
    private List<Punto> reconstruirRuta(Map<Punto, Punto> padres, Punto destino) {
        List<Punto> ruta = new ArrayList<>();
        Punto actual = destino;
        
        // Reconstruir hacia atrás
        while (actual != null) {
            ruta.add(actual.clonar());
            actual = padres.get(actual);
        }
        
        // Invertir para obtener ruta desde origen a destino
        Collections.reverse(ruta);
        return ruta;
    }
    private NodoAEstrella obtenerNodo(Punto punto, int g, int h) {
        NodoAEstrella nodo = nodoPool.poll();
        if (nodo == null) {
            nodo = new NodoAEstrella(punto, g, h);
        } else {
            nodo.reutilizar(punto, g, h);
        }
        return nodo;
    }

    private void liberarNodo(NodoAEstrella nodo) {
        if (nodoPool.size() < 100) { // Limitar tamaño del pool
            nodoPool.offer(nodo);
        }
    }
    private int calcularCostoMovimiento(Punto desde, Punto hasta) {
        // En JPS, el costo es la distancia Manhattan entre los puntos
        return desde.distanciaManhattanHasta(hasta);
    }

    private Punto expandirFrontera(PriorityQueue<NodoAEstrella> frontera, Set<Punto> visitados,
                                  Map<Punto, Integer> costos, Map<Punto, Punto> padres,
                                  Set<Punto> visitadosOpuestos, Punto objetivo,
                                  ConfiguracionAEstrella config, boolean esDesdeOrigen) {
        
        if (frontera.isEmpty()) {
            return null;
        }
        
        NodoAEstrella actual = frontera.poll();
        Punto puntoActual = actual.getPunto();
        
        // Si ya fue visitado, ignorar
        if (visitados.contains(puntoActual)) {
            return null;
        }
        
        // Añadir a visitados
        visitados.add(puntoActual);
        
        // Si este punto ya fue visitado desde la dirección opuesta, tenemos un camino
        if (visitadosOpuestos.contains(puntoActual)) {
            return puntoActual; // Punto de encuentro
        }
        
        // Expandir vecinos
        for (Punto vecino : gestorObstaculos.obtenerPuntosAdyacentesValidos(puntoActual)) {
            if (visitados.contains(vecino)) {
                objectPoolService.liberarPunto(vecino); // Liberar punto no usado
                continue;
            }
            
            int nuevoG = costos.get(puntoActual) + 1;
            
            if (!costos.containsKey(vecino) || nuevoG < costos.get(vecino)) {
                costos.put(vecino, nuevoG);
                padres.put(vecino, puntoActual);
                
                int h = calcularH(vecino, objetivo, config);
                frontera.add(new NodoAEstrella(vecino, nuevoG, h));
            }
        }
        
        return null;
    }
    
    private List<Punto> reconstruirRutaBidireccional(Map<Punto, Punto> padresOrigen, 
                                                   Map<Punto, Punto> padresDestino, 
                                                   Punto puntoEncuentro) {
        // Reconstruir camino desde origen hasta punto de encuentro
        List<Punto> rutaDesdeOrigen = new ArrayList<>();
        Punto actual = puntoEncuentro;
        
        while (actual != null) {
            rutaDesdeOrigen.add(actual.clonar());
            actual = padresOrigen.get(actual);
        }
        Collections.reverse(rutaDesdeOrigen); // Ordenar desde origen hacia punto de encuentro
        
        // Reconstruir camino desde punto de encuentro hasta destino
        List<Punto> rutaHaciaDestino = new ArrayList<>();
        actual = padresDestino.get(puntoEncuentro); // Comenzar desde el siguiente al punto de encuentro
        
        while (actual != null) {
            rutaHaciaDestino.add(actual.clonar());
            actual = padresDestino.get(actual);
        }
        
        // Combinar ambas rutas
        rutaDesdeOrigen.addAll(rutaHaciaDestino);
        return rutaDesdeOrigen;
    }

    @Data
    // Clase auxiliar para nodos del algoritmo A*
    private static class NodoAEstrella {
        private Punto punto;
        private int g;  // Costo real desde el origen
        private int h;  // Heurística al destino
        
        public NodoAEstrella(Punto punto, int g, int h) {
            this.punto = punto.clonar();
            this.g = g;
            this.h = h;
        }

        public void reutilizar(Punto punto, int g, int h) {
            this.punto = punto.clonar();
            this.g = g;
            this.h = h;
        }

        
        public double getF() {
            return g + h;  // Costo total estimado
        }
        
        public Punto getPunto() { return punto; }
        public int getG() { return g; }
        public int getH() { return h; }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            NodoAEstrella that = (NodoAEstrella) obj;
            return punto.equals(that.punto);
        }
        
        @Override
        public int hashCode() {
            return punto.hashCode();
        }
    }

    private void shutdownThreadPoolGraceful() {
        if (executorService == null || executorService.isShutdown()) {
            return;
        }
        
        logger.info("Iniciando shutdown graceful del ThreadPool A*");
        
        try {
            // 1. Iniciar shutdown ordenado
            executorService.shutdown();
            
            // 2. Esperar terminación con timeout
            boolean terminated = executorService.awaitTermination(
                configuracion.getTimeoutShutdownMs(), TimeUnit.MILLISECONDS);
            
            if (!terminated) {
                // 3. Forzar shutdown si no termina en tiempo
                logger.warn("ThreadPool A* no terminó en {}ms, forzando shutdown", 
                    configuracion.getTimeoutShutdownMs());
                
                List<Runnable> pendingTasks = executorService.shutdownNow();
                logger.warn("ThreadPool A* forzado - {} tareas pendientes canceladas", 
                    pendingTasks.size());
                
                // 4. Esperar un poco más después del shutdown forzado
                if (!executorService.awaitTermination(2000, TimeUnit.MILLISECONDS)) {
                    logger.error("ThreadPool A* no respondió a shutdown forzado");
                }
            } else {
                logger.info("ThreadPool A* terminado exitosamente");
            }
            
        } catch (InterruptedException e) {
            // 5. Manejar interrupción
            logger.warn("Shutdown graceful interrumpido, forzando cierre");
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    @PreDestroy
    public void shutdown() {
        logger.info("Cerrando servicio A*");
        shutdownThreadPoolGraceful();
    }
    private List<Punto> obtenerVecinosSimples(Punto actual) {
        List<Punto> vecinos = new ArrayList<>();
        
        // Movimientos Manhattan simples: arriba, abajo, izquierda, derecha
        int[][] direcciones = {
            {0, 1},   // Arriba
            {0, -1},  // Abajo
            {-1, 0},  // Izquierda
            {1, 0}    // Derecha
        };
        
        for (int[] dir : direcciones) {
            int nuevoX = actual.getX() + dir[0];
            int nuevoY = actual.getY() + dir[1];
            Punto vecino = new Punto(nuevoX, nuevoY);
            
            // Verificar si el punto está dentro de límites y no está obstruido
            if (gestorObstaculos.esPuntoValido(vecino)) {
                vecinos.add(vecino);
            }
        }
        
        return vecinos;
    }
    private void ejecutarWarmupTasksDummy() {
        List<CompletableFuture<Void>> warmupTasks = new ArrayList<>();
        
        for (int i = 0; i < configuracion.getNumeroThreads() * 2; i++) {
            final int taskId = i;
            CompletableFuture<Void> task = CompletableFuture.runAsync(() -> {
                // Task dummy para warm-up
                Punto dummy1 = new Punto(taskId % 10, taskId % 10);
                Punto dummy2 = new Punto((taskId + 5) % 10, (taskId + 5) % 10);
                
                try {
                    Thread.sleep(10); // Simular trabajo mínimo
                    dummy1.distanciaManhattanHasta(dummy2); // Trigger hotspot
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, executorService);
            
            warmupTasks.add(task);
        }
        
        try {
            CompletableFuture.allOf(warmupTasks.toArray(new CompletableFuture[0]))
                .get(2000, TimeUnit.MILLISECONDS);
            logger.info("Thread pool warm-up completado: {} tasks ejecutadas", warmupTasks.size());
        } catch (Exception e) {
            logger.warn("Thread pool warm-up parcial: {}", e.getMessage());
        }
    }
}