package com.plg.service.impl;

import com.plg.domain.*;
import com.plg.domain.enumeration.EstadoSegmento;
import com.plg.domain.enumeration.TipoSegmento;
import com.plg.service.AEstrellaService;
import com.plg.service.PlanificadorRutasService;
import com.plg.service.util.GestorObstaculos;

import jakarta.annotation.PreDestroy;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Implementación del planificador de rutas usando ACO (Ant Colony Optimization) + A*
 * 
 * Algoritmo principal:
 * 1. Para cada camión, optimizar orden de entregas con ACO (TSP)
 * 2. Calcular pathfinding A* entre cada par de puntos
 * 3. Construir RutaOptimizada con segmentos detallados
 * 4. Validar factibilidad (combustible, capacidad)
 * 5. Paralelizar cálculos de múltiples camiones
 */
@Service
public class PlanificadorRutasServiceImpl implements PlanificadorRutasService {
    
    private static final Logger logger = LoggerFactory.getLogger(PlanificadorRutasServiceImpl.class);
    
    @Autowired
    private AEstrellaService aEstrellaService;
    
    @Autowired
    private GestorObstaculos gestorObstaculos;
    
    // Configuración
    private ConfiguracionPlanificadorRutas configuracion;
    
    // Thread pool para paralelización
    private ThreadPoolExecutor executorService;
    private final AtomicInteger threadCounter = new AtomicInteger(0);
    
    // Cache de distancias calculadas
    private final Map<String, Double> cacheDistancias = new ConcurrentHashMap<>();
    
    // Estadísticas thread-safe
    private final AtomicLong totalCalculos = new AtomicLong(0);
    private final AtomicLong calculosExitosos = new AtomicLong(0);
    private final AtomicLong calculosFallidos = new AtomicLong(0);
    private final AtomicLong tiempoTotalMs = new AtomicLong(0);
    private final AtomicLong totalOptimizacionesACO = new AtomicLong(0);
    private final AtomicLong tiempoTotalACOMs = new AtomicLong(0);
    private final AtomicLong totalCalculosAEstrella = new AtomicLong(0);
    private final AtomicLong tiempoTotalAEstrellaMs = new AtomicLong(0);
    private final AtomicLong calculosParalelos = new AtomicLong(0);

    public PlanificadorRutasServiceImpl() {
        this.configuracion = ConfiguracionPlanificadorRutas.balanceada();
        inicializarThreadPool();
    }

    private void inicializarThreadPool() {
        ThreadFactory threadFactory = r -> {
            Thread thread = new Thread(r, "PlanificadorRutas-" + threadCounter.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        };
        
        this.executorService = new ThreadPoolExecutor(
            configuracion.getNumeroThreads(),
            configuracion.getNumeroThreads(),
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            threadFactory,
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        executorService.prestartAllCoreThreads();
        logger.info("ThreadPool PlanificadorRutas inicializado: {} threads", 
            configuracion.getNumeroThreads());
    }

    @Override
    public Map<Camion, RutaOptimizada> planificarRutas(
            Map<Camion, AsignacionCamion> asignaciones, 
            List<Almacen> almacenes) {
        return planificarRutas(asignaciones, almacenes, configuracion);
    }

    @Override
    public Map<Camion, RutaOptimizada> planificarRutas(
            Map<Camion, AsignacionCamion> asignaciones, 
            List<Almacen> almacenes,
            ConfiguracionPlanificadorRutas configuracion) {
        
        long tiempoInicio = System.currentTimeMillis();
        totalCalculos.addAndGet(asignaciones.size());
        
        logger.info("🚛 INICIANDO PLANIFICACIÓN DE RUTAS para {} camiones", asignaciones.size());
        
        // Validaciones iniciales
        if (asignaciones.isEmpty() || almacenes.isEmpty()) {
            logger.warn("Asignaciones o almacenes vacíos");
            return new HashMap<>();
        }

        // Si no hay paralelización o hay pocos camiones, procesar secuencialmente
        if (!configuracion.isHabilitarParalelizacion() || asignaciones.size() <= 1) {
            return planificarRutasSecuencial(asignaciones, almacenes);
        }

        // Procesar en paralelo
        calculosParalelos.addAndGet(asignaciones.size());
        return planificarRutasParalelo(asignaciones, almacenes);
    }

    private Map<Camion, RutaOptimizada> planificarRutasSecuencial(
            Map<Camion, AsignacionCamion> asignaciones, 
            List<Almacen> almacenes) {
        
        Map<Camion, RutaOptimizada> resultados = new HashMap<>();
        
        for (Map.Entry<Camion, AsignacionCamion> entry : asignaciones.entrySet()) {
            Camion camion = entry.getKey();
            AsignacionCamion asignacion = entry.getValue();
            
            try {
                RutaOptimizada ruta = planificarRutaCamion(camion, asignacion, almacenes);
                if (ruta != null && ruta.isRutaViable()) {
                    resultados.put(camion, ruta);
                    calculosExitosos.incrementAndGet();
                } else {
                    logger.warn("No se pudo calcular ruta viable para camión {}", camion.getCodigo());
                    calculosFallidos.incrementAndGet();
                }
            } catch (Exception e) {
                logger.error("Error calculando ruta para camión {}: {}", 
                    camion.getCodigo(), e.getMessage());
                calculosFallidos.incrementAndGet();
            }
        }
        
        logger.info("Planificación secuencial completada: {}/{} rutas exitosas", 
            resultados.size(), asignaciones.size());
        
        return resultados;
    }

    private Map<Camion, RutaOptimizada> planificarRutasParalelo(
            Map<Camion, AsignacionCamion> asignaciones, 
            List<Almacen> almacenes) {
        
        logger.info("Planificando {} rutas en paralelo", asignaciones.size());
        
        // Crear futures para cada camión
        List<CompletableFuture<Map.Entry<Camion, RutaOptimizada>>> futures = 
            asignaciones.entrySet().stream()
                .map(entry -> CompletableFuture.supplyAsync(() -> {
                    try {
                        Camion camion = entry.getKey();
                        AsignacionCamion asignacion = entry.getValue();
                        RutaOptimizada ruta = planificarRutaCamion(camion, asignacion, almacenes);
                        
                        if (ruta != null && ruta.isRutaViable()) {
                            calculosExitosos.incrementAndGet();
                            return Map.entry(camion, ruta);
                        } else {
                            calculosFallidos.incrementAndGet();
                            return null;
                        }
                    } catch (Exception e) {
                        logger.error("Error en planificación paralela: {}", e.getMessage());
                        calculosFallidos.incrementAndGet();
                        return null;
                    }
                }, executorService))
                .collect(Collectors.toList());

        // Esperar resultados con timeout
        Map<Camion, RutaOptimizada> resultados = new HashMap<>();
        try {
            long timeoutTotal = configuracion.getTimeoutCalculoMs() * asignaciones.size() / 2;
            timeoutTotal = Math.max(timeoutTotal, 30000); // Mínimo 30 segundos
            
            CompletableFuture<Void> todosFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));
            
            todosFutures.get(timeoutTotal, TimeUnit.MILLISECONDS);
            
            // Recolectar resultados exitosos
            futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .forEach(entry -> resultados.put(entry.getKey(), entry.getValue()));
                
        } catch (TimeoutException e) {
            logger.warn("Timeout en planificación paralela, recolectando resultados parciales");
            resultados.putAll(recolectarResultadosParciales(futures));
        } catch (Exception e) {
            logger.error("Error en planificación paralela: {}", e.getMessage());
        }

        logger.info("Planificación paralela completada: {}/{} rutas exitosas", 
            resultados.size(), asignaciones.size());
        
        return resultados;
    }

    private Map<Camion, RutaOptimizada> recolectarResultadosParciales(
            List<CompletableFuture<Map.Entry<Camion, RutaOptimizada>>> futures) {
        
        Map<Camion, RutaOptimizada> resultados = new HashMap<>();
        
        for (CompletableFuture<Map.Entry<Camion, RutaOptimizada>> future : futures) {
            try {
                Map.Entry<Camion, RutaOptimizada> resultado = future.get(100, TimeUnit.MILLISECONDS);
                if (resultado != null) {
                    resultados.put(resultado.getKey(), resultado.getValue());
                }
            } catch (TimeoutException | ExecutionException | InterruptedException e) {
                future.cancel(true);
            }
        }
        
        return resultados;
    }

    @Override
    public RutaOptimizada planificarRutaCamion(
            Camion camion, 
            AsignacionCamion asignacion, 
            List<Almacen> almacenes) {
        
        long tiempoInicio = System.currentTimeMillis();
        
        logger.debug("🚛 Planificando ruta para camión {} con {} entregas", 
            camion.getCodigo(), asignacion.getEntregas().size());

        try {
            // 1. Validaciones previas
            if (asignacion.getEntregas().isEmpty()) {
                logger.warn("Camión {} sin entregas asignadas", camion.getCodigo());
                return null;
            }

            // 2. Determinar punto de inicio y almacén de destino
            Punto puntoInicio = obtenerPuntoInicioCamion(camion, almacenes);
            Almacen almacenDestino = encontrarAlmacenMasCercano(puntoInicio, almacenes);
            // 🔥 LOG INICIAL DEL CAMIÓN
            logger.info("🚛 PLANIFICACIÓN CAMIÓN {}: Inicio en ({},{}) con {} entregas", 
                camion.getCodigo(), puntoInicio.getX(), puntoInicio.getY(), 
                asignacion.getEntregas().size());
            // 3. Optimizar orden de entregas usando ACO
            List<Entrega> entregasOptimizadas = optimizarOrdenEntregas(
                asignacion.getEntregas(), puntoInicio);
            // 🔥 LOG SECUENCIA DE ENTREGAS PLANIFICADA
            logSecuenciaEntregasPlanificada(camion, puntoInicio, entregasOptimizadas, almacenDestino);
            // 4. Calcular ruta completa
            RutaOptimizada ruta = calcularRutaCompleta(
                camion, puntoInicio, entregasOptimizadas, almacenDestino);
            // 🔥 LOG RUTA COMPLETA CALCULADA
            logRutaCompletaCalculada(camion, ruta);
            // 5. Validar factibilidad
            if (!esRutaFactible(ruta, camion)) {
                logger.warn("Ruta no factible para camión {}", camion.getCodigo());
                return null;
            }

            // 6. Optimización local (opcional)
            if (configuracion.isUsarOptimizacionLocal()) {
                ruta = optimizarRutaLocal(ruta);
            }
            // 🔥 LOG ANÁLISIS DE CAMBIOS DE DIRECCIÓN
            logAnalisisCambiosDireccion(camion, ruta);
            long tiempoTotal = System.currentTimeMillis() - tiempoInicio;
            tiempoTotalMs.addAndGet(tiempoTotal);

            logger.debug("✅ Ruta calculada para camión {} en {}ms: {:.1f}km, {} entregas", 
                camion.getCodigo(), tiempoTotal, ruta.getDistanciaTotalKm(), 
                ruta.getNumeroEntregas());

            return ruta;

        } catch (Exception e) {
            logger.error("Error planificando ruta para camión {}: {}", 
                camion.getCodigo(), e.getMessage());
            return null;
        }
    }

    @Override
    public CompletableFuture<RutaOptimizada> planificarRutaCamionAsincrona(
            Camion camion, 
            AsignacionCamion asignacion, 
            List<Almacen> almacenes) {
        
        return CompletableFuture.supplyAsync(
            () -> planificarRutaCamion(camion, asignacion, almacenes), 
            executorService);
    }

    @Override
    public List<Entrega> optimizarOrdenEntregas(List<Entrega> entregas, Punto puntoInicio) {
        return optimizarOrdenEntregas(entregas, puntoInicio, configuracion.getConfiguracionACO());
    }

    @Override
    public List<Entrega> optimizarOrdenEntregas(
            List<Entrega> entregas, 
            Punto puntoInicio,
            ConfiguracionACO configuracionACO) {
        
        long tiempoInicio = System.currentTimeMillis();
        totalOptimizacionesACO.incrementAndGet();
        
        logger.debug("🐜 Optimizando orden de {} entregas con ACO", entregas.size());

        // Si hay pocas entregas, usar orden por prioridad
        if (entregas.size() <= 2) {
            List<Entrega> resultado = entregas.stream()
                .sorted((e1, e2) -> Integer.compare(
                    e2.getPedido().getPrioridad(), 
                    e1.getPedido().getPrioridad()))
                .collect(Collectors.toList());
            
            long tiempoACO = System.currentTimeMillis() - tiempoInicio;
            tiempoTotalACOMs.addAndGet(tiempoACO);
            
            return resultado;
        }

        try {
            // Ejecutar algoritmo ACO
            List<Entrega> entregasOptimizadas = ejecutarACO(entregas, puntoInicio, configuracionACO);
            
            long tiempoACO = System.currentTimeMillis() - tiempoInicio;
            tiempoTotalACOMs.addAndGet(tiempoACO);
            
            logger.debug("✅ ACO completado en {}ms para {} entregas", 
                tiempoACO, entregas.size());
            
            return entregasOptimizadas;
            
        } catch (Exception e) {
            logger.error("Error en optimización ACO: {}", e.getMessage());
            // Fallback: orden por prioridad
            return entregas.stream()
                .sorted((e1, e2) -> Integer.compare(
                    e2.getPedido().getPrioridad(), 
                    e1.getPedido().getPrioridad()))
                .collect(Collectors.toList());
        }
    }

    private List<Entrega> ejecutarACO(
            List<Entrega> entregas, 
            Punto puntoInicio, 
            ConfiguracionACO config) {
        
        int n = entregas.size();
        
        // Crear matriz de distancias
        double[][] distancias = calcularMatrizDistancias(entregas, puntoInicio);
        
        // Inicializar matriz de feromonas
        double[][] feromonas = inicializarMatrizFeromonas(n + 1, config.getFeromonaInicial());
        
        List<Entrega> mejorSolucion = null;
        double mejorDistancia = Double.MAX_VALUE;
        int iteracionesSinMejora = 0;
        
        // Iteraciones ACO
        for (int iteracion = 0; iteracion < config.getNumeroIteraciones(); iteracion++) {
            
            // Construir soluciones con hormigas
            List<SolucionHormiga> soluciones = new ArrayList<>();
            
            for (int hormiga = 0; hormiga < config.getNumeroHormigas(); hormiga++) {
                SolucionHormiga solucion = construirSolucion(
                    entregas, distancias, feromonas, config);
                soluciones.add(solucion);
                
                // Verificar si es la mejor solución hasta ahora
                if (solucion.distanciaTotal < mejorDistancia) {
                    mejorDistancia = solucion.distanciaTotal;
                    mejorSolucion = solucion.entregas;
                    iteracionesSinMejora = 0;
                } else {
                    iteracionesSinMejora++;
                }
            }
            
            // Actualizar feromonas
            actualizarFeromonas(feromonas, soluciones, config);
            
            // Criterio de parada por convergencia
            if (iteracionesSinMejora >= config.getIteracionesSinMejora()) {
                logger.debug("ACO convergió en iteración {} (sin mejora por {} iteraciones)", 
                    iteracion, iteracionesSinMejora);
                break;
            }
        }
        
        logger.debug("ACO: mejor distancia {:.2f} después de {} iteraciones", 
            mejorDistancia, config.getNumeroIteraciones());
        
        return mejorSolucion != null ? mejorSolucion : entregas;
    }

    private double[][] calcularMatrizDistancias(List<Entrega> entregas, Punto puntoInicio) {
        int n = entregas.size();
        double[][] distancias = new double[n + 1][n + 1];
        
        // Posición 0 = punto de inicio
        List<Punto> puntos = new ArrayList<>();
        puntos.add(puntoInicio);
        
        // Posiciones 1..n = entregas
        for (Entrega entrega : entregas) {
            puntos.add(new Punto(
                entrega.getPedido().getUbicacionX(),
                entrega.getPedido().getUbicacionY()
            ));
        }
        
        // Calcular distancias entre todos los pares de puntos
        for (int i = 0; i <= n; i++) {
            for (int j = 0; j <= n; j++) {
                if (i == j) {
                    distancias[i][j] = 0.0;
                } else {
                    distancias[i][j] = calcularDistanciaEntrePuntos(puntos.get(i), puntos.get(j));
                }
            }
        }
        
        return distancias;
    }

    private double calcularDistanciaEntrePuntos(Punto p1, Punto p2) {
        String clave = String.format("%d,%d-%d,%d", p1.getX(), p1.getY(), p2.getX(), p2.getY());
        
        return cacheDistancias.computeIfAbsent(clave, k -> {
            totalCalculosAEstrella.incrementAndGet();
            long inicio = System.currentTimeMillis();
            
            // Usar A* para calcular distancia real considerando obstáculos
            ResultadoAEstrella resultado = aEstrellaService.calcularRuta(p1, p2);
            
            long tiempo = System.currentTimeMillis() - inicio;
            tiempoTotalAEstrellaMs.addAndGet(tiempo);
            
            if (resultado.isRutaExiste()) {
                return (double) resultado.getDistanciaGrid();
            } else {
                // Fallback: distancia Manhattan
                return (double) p1.distanciaManhattanHasta(p2);
            }
        });
    }

    private double[][] inicializarMatrizFeromonas(int tamaño, double valorInicial) {
        double[][] feromonas = new double[tamaño][tamaño];
        for (int i = 0; i < tamaño; i++) {
            Arrays.fill(feromonas[i], valorInicial);
        }
        return feromonas;
    }

    private SolucionHormiga construirSolucion(
            List<Entrega> entregas,
            double[][] distancias,
            double[][] feromonas,
            ConfiguracionACO config) {
        
        int n = entregas.size();
        List<Entrega> solucion = new ArrayList<>();
        Set<Integer> visitados = new HashSet<>();
        int actual = 0; // Comenzar desde punto inicial
        double distanciaTotal = 0.0;
        
        // Construir ruta visitando todas las entregas
        while (visitados.size() < n) {
            int siguiente = seleccionarSiguienteEntrega(
                actual, visitados, distancias, feromonas, config);
            
            if (siguiente != -1) {
                visitados.add(siguiente);
                solucion.add(entregas.get(siguiente - 1)); // -1 porque entregas van de 1..n
                distanciaTotal += distancias[actual][siguiente];
                actual = siguiente;
            } else {
                break; // No debería pasar, pero por seguridad
            }
        }
        
        return new SolucionHormiga(solucion, distanciaTotal);
    }

    private int seleccionarSiguienteEntrega(
            int actual,
            Set<Integer> visitados,
            double[][] distancias,
            double[][] feromonas,
            ConfiguracionACO config) {
        
        int n = distancias.length - 1; // Número de entregas
        List<Integer> candidatos = new ArrayList<>();
        
        // Encontrar candidatos no visitados
        for (int i = 1; i <= n; i++) {
            if (!visitados.contains(i)) {
                candidatos.add(i);
            }
        }
        
        if (candidatos.isEmpty()) {
            return -1;
        }
        
        // Estrategia de selección: explotación vs exploración
        Random random = new Random();
        
        if (random.nextDouble() < config.getQ0()) {
            // Explotación: elegir el mejor candidato
            return candidatos.stream()
                .max((i, j) -> Double.compare(
                    calcularAtraccion(actual, i, distancias, feromonas, config),
                    calcularAtraccion(actual, j, distancias, feromonas, config)))
                .orElse(candidatos.get(0));
        } else {
            // Exploración: selección probabilística
            return seleccionarProbabilistico(actual, candidatos, distancias, feromonas, config);
        }
    }

    private double calcularAtraccion(
            int desde,
            int hasta,
            double[][] distancias,
            double[][] feromonas,
            ConfiguracionACO config) {
        
        double feromona = Math.pow(feromonas[desde][hasta], config.getAlfa());
        double visibilidad = Math.pow(1.0 / distancias[desde][hasta], config.getBeta());
        
        return feromona * visibilidad;
    }

    private int seleccionarProbabilistico(
            int actual,
            List<Integer> candidatos,
            double[][] distancias,
            double[][] feromonas,
            ConfiguracionACO config) {
        
        // Calcular probabilidades
        double[] probabilidades = new double[candidatos.size()];
        double suma = 0.0;
        
        for (int i = 0; i < candidatos.size(); i++) {
            int candidato = candidatos.get(i);
            probabilidades[i] = calcularAtraccion(actual, candidato, distancias, feromonas, config);
            suma += probabilidades[i];
        }
        
        // Normalizar probabilidades
        if (suma > 0) {
            for (int i = 0; i < probabilidades.length; i++) {
                probabilidades[i] /= suma;
            }
        }
        
        // Selección por ruleta
        Random random = new Random();
        double r = random.nextDouble();
        double acumulado = 0.0;
        
        for (int i = 0; i < candidatos.size(); i++) {
            acumulado += probabilidades[i];
            if (r <= acumulado) {
                return candidatos.get(i);
            }
        }
        
        // Fallback
        return candidatos.get(candidatos.size() - 1);
    }

    private void actualizarFeromonas(
            double[][] feromonas,
            List<SolucionHormiga> soluciones,
            ConfiguracionACO config) {
        
        int n = feromonas.length;
        
        // Evaporación
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                feromonas[i][j] *= (1.0 - config.getRho());
            }
        }
        
        // Depósito de feromonas por las mejores hormigas
        for (SolucionHormiga solucion : soluciones) {
            double deposito = 1.0 / solucion.distanciaTotal;
            
            // Depositar feromonas en los arcos de la solución
            int anterior = 0; // Punto de inicio
            for (int i = 0; i < solucion.entregas.size(); i++) {
                int actual = i + 1; // Entregas van de 1..n
                feromonas[anterior][actual] += deposito;
                feromonas[actual][anterior] += deposito; // Grafo no dirigido
                anterior = actual;
            }
        }
    }

    @Override
    public List<SegmentoRuta> calcularSegmentosRuta(
            List<Punto> secuenciaPuntos, 
            List<Entrega> entregas) {
        
        List<SegmentoRuta> segmentos = new ArrayList<>();
        logger.debug("🗺️ Calculando {} segmentos de ruta con A*", secuenciaPuntos.size() - 1);        
        for (int i = 0; i < secuenciaPuntos.size() - 1; i++) {
            Punto origen = secuenciaPuntos.get(i);
            Punto destino = secuenciaPuntos.get(i + 1);
            
            // Determinar tipo de segmento
            TipoSegmento tipo;
            Entrega entrega = null;
            
            if (i < entregas.size()) {
                tipo = TipoSegmento.ENTREGA;
                entrega = entregas.get(i);
            } else {
                tipo = TipoSegmento.RETORNO_ALMACEN;
            }
            logger.debug("  📍 Segmento {}: ({},{}) -> ({},{}) [{}]", 
            i + 1, origen.getX(), origen.getY(), destino.getX(), destino.getY(), tipo);
            // Crear segmento
            SegmentoRuta segmento = new SegmentoRuta(origen, destino, tipo);
            segmento.setEntrega(entrega);
            segmento.setOrdenEnRuta(i);
            
            // Calcular pathfinding A*
            ResultadoAEstrella resultado = aEstrellaService.calcularRuta(origen, destino);
            
            if (resultado.isRutaExiste()) {
                segmento.establecerRutaDetallada(resultado.getRutaEncontrada());
                logger.debug("    ✅ A* exitoso: {} pasos, {:.1f}km", 
                resultado.getRutaEncontrada().size(), resultado.getDistanciaKm());
            } else {
                logger.warn("No se pudo calcular pathfinding para segmento {}: {} -> {}", 
                    i, origen, destino);
                // Usar ruta directa como fallback
                segmento.establecerRutaDetallada(Arrays.asList(origen, destino));
            }
            
            segmentos.add(segmento);
        }
        
        return segmentos;
    }

    @Override
    public RutaOptimizada calcularRutaCompleta(
            Camion camion,
            Punto puntoActual,
            List<Entrega> entregas,
            Almacen almacenDestino) {
        
        // Crear ruta base
        RutaOptimizada ruta = new RutaOptimizada(camion, 
            encontrarAlmacenMasCercano(puntoActual, Arrays.asList(almacenDestino)));
        ruta.setAlmacenDestino(almacenDestino);
        
        // Construir secuencia de puntos
        List<Punto> secuenciaPuntos = new ArrayList<>();
        secuenciaPuntos.add(puntoActual);
        
        // Agregar puntos de entregas
        for (Entrega entrega : entregas) {
            secuenciaPuntos.add(new Punto(
                entrega.getPedido().getUbicacionX(),
                entrega.getPedido().getUbicacionY()
            ));
        }
        
        // Agregar punto de retorno al almacén
        if (configuracion.isForzarRetornoAlmacen()) {
            secuenciaPuntos.add(new Punto(almacenDestino.getX(), almacenDestino.getY()));
        }
        // 🔥 LOG SECUENCIA DE PUNTOS PLANIFICADA
        logSecuenciaPuntosCompleta(camion, secuenciaPuntos, entregas);
        // Calcular segmentos con A*
        List<SegmentoRuta> segmentos = calcularSegmentosRuta(secuenciaPuntos, entregas);
        
        // Agregar segmentos a la ruta
        for (SegmentoRuta segmento : segmentos) {
            ruta.agregarSegmento(segmento);
        }
        
        return ruta;
    }

    @Override
    public boolean esRutaFactible(RutaOptimizada ruta, Camion camion) {
        if (ruta == null || camion == null) {
            return false;
        }
        
        // Verificar combustible
        double combustibleNecesario = ruta.getCombustibleNecesarioGalones();
        double combustibleDisponible = camion.getConsumoGalones(); // Asumiendo tanque lleno
        double margen = combustibleNecesario * configuracion.getMargenCombustibleSeguridad();
        
        if (combustibleNecesario + margen > combustibleDisponible) {
            logger.debug("Ruta no factible por combustible: necesario {:.1f}, disponible {:.1f}", 
                combustibleNecesario + margen, combustibleDisponible);
            return false;
        }
        
        // Verificar capacidad (ya debería estar verificado por PlanificadorPedidos)
        double volumenTotal = ruta.obtenerEntregasEnOrden().stream()
            .mapToDouble(Entrega::getVolumenEntregadoM3)
            .sum();
        
        if (volumenTotal > camion.getMaxCargaM3()) {
            logger.debug("Ruta no factible por capacidad: volumen {}, capacidad {}", 
                volumenTotal, camion.getMaxCargaM3());
            return false;
        }
        
        // Verificar que tiene al menos una entrega
        if (ruta.getNumeroEntregas() == 0) {
            return false;
        }
        
        return true;
    }

    @Override
    public MetricasCalidadRuta calcularMetricasCalidad(RutaOptimizada ruta) {
        MetricasCalidadRuta metricas = new MetricasCalidadRuta();
        
        if (ruta == null) {
            return metricas;
        }
        
        // Métricas básicas
        metricas.setDistanciaTotalKm(ruta.getDistanciaTotalKm());
        metricas.setTiempoTotalHoras(ruta.getTiempoEstimadoHoras());
        metricas.setCombustibleTotalGalones(ruta.getCombustibleNecesarioGalones());
        metricas.setNumeroEntregas(ruta.getNumeroEntregas());
        metricas.setNumeroSegmentos(ruta.getSegmentos().size());
        
        // Utilización del camión
        if (ruta.getCamion() != null) {
            metricas.setUtilizacionCamion(ruta.calcularPorcentajeUtilizacion());
        }
        
        // Eficiencia
        if (metricas.getCombustibleTotalGalones() > 0) {
            metricas.setEficienciaDistancia(
                metricas.getDistanciaTotalKm() / metricas.getCombustibleTotalGalones());
        }
        
        // CORREGIR: Índice de optimización con mejor validación
        double distanciaDirecta = calcularDistanciaDirectaEntregas(ruta.obtenerEntregasEnOrden());
        if (distanciaDirecta > 0 && metricas.getDistanciaTotalKm() > 0) {
            double factorOptimizacion = distanciaDirecta / metricas.getDistanciaTotalKm();
            
            // Asegurar que esté entre 0 y 1
            factorOptimizacion = Math.min(1.0, Math.max(0.0, factorOptimizacion));
            
            // Convertir a índice 0-100
            metricas.setIndiceOptimizacion(factorOptimizacion * 100.0);
            metricas.setDistanciaVsOptima(metricas.getDistanciaTotalKm() / distanciaDirecta);
        } else {
            // Si no se puede calcular, asumir optimización media
            metricas.setIndiceOptimizacion(50.0);
            metricas.setDistanciaVsOptima(1.0);
        }
        
        return metricas;
    }

    private double calcularDistanciaDirectaEntregas(List<Entrega> entregas) {
        if (entregas == null || entregas.size() <= 1) {
            return 0.0;
        }
        
        double distanciaTotal = 0.0;
        for (int i = 0; i < entregas.size() - 1; i++) {
            Entrega entregaActual = entregas.get(i);
            Entrega entregaSiguiente = entregas.get(i + 1);
            
            // Validar que los pedidos no sean nulos
            if (entregaActual.getPedido() == null || entregaSiguiente.getPedido() == null) {
                continue;
            }
            
            Punto p1 = new Punto(
                entregaActual.getPedido().getUbicacionX(),
                entregaActual.getPedido().getUbicacionY()
            );
            Punto p2 = new Punto(
                entregaSiguiente.getPedido().getUbicacionX(),
                entregaSiguiente.getPedido().getUbicacionY()
            );
            
            // Usar distancia Manhattan (más apropiada para el dominio del problema)
            distanciaTotal += p1.distanciaManhattanHasta(p2);
        }
        
        // Si solo hay una entrega, calcular distancia desde un punto de referencia
        if (entregas.size() == 1 && distanciaTotal == 0.0) {
            Entrega entrega = entregas.get(0);
            if (entrega.getPedido() != null) {
                // Usar distancia desde origen (0,0) como referencia mínima
                Punto puntoEntrega = new Punto(
                    entrega.getPedido().getUbicacionX(),
                    entrega.getPedido().getUbicacionY()
                );
                return puntoEntrega.distanciaManhattanHasta(new Punto(0, 0));
            }
        }
        
        return distanciaTotal;
    }

    @Override
    public RutaOptimizada optimizarRutaLocal(RutaOptimizada rutaBase) {
        // Implementación simplificada de optimización local
        // Podrían implementarse algoritmos como 2-opt, 3-opt, etc.
        return rutaBase; // Por ahora, retornar la ruta original
    }

    @Override
    public Almacen encontrarAlmacenMasCercano(Punto punto, List<Almacen> almacenes) {
        return almacenes.stream()
            .min((a1, a2) -> {
                int dist1 = punto.distanciaManhattanHasta(new Punto(a1.getX(), a1.getY()));
                int dist2 = punto.distanciaManhattanHasta(new Punto(a2.getX(), a2.getY()));
                return Integer.compare(dist1, dist2);
            })
            .orElse(almacenes.get(0));
    }

    private Punto obtenerPuntoInicioCamion(Camion camion, List<Almacen> almacenes) {
        // Si el camión tiene posición específica, usarla
        if (camion.getUbicacionX() != 0 || camion.getUbicacionY() != 0) {
            return new Punto(camion.getUbicacionX(), camion.getUbicacionY());
        }
        
        // Si no, usar almacén principal
        Almacen almacenPrincipal = almacenes.stream()
            .filter(Almacen::getEsPrincipal)
            .findFirst()
            .orElse(almacenes.get(0));
        
        return new Punto(almacenPrincipal.getX(), almacenPrincipal.getY());
    }

    @Override
    public void precalcularRutasAlmacenes(List<Almacen> almacenes) {
        logger.info("Precalculando rutas entre {} almacenes", almacenes.size());
        
        List<Punto> puntosAlmacenes = almacenes.stream()
            .map(a -> new Punto(a.getX(), a.getY()))
            .collect(Collectors.toList());
        
        aEstrellaService.precalcularRutasFrecuentes(puntosAlmacenes);
    }

    @Override
    public EstadisticasPlanificacionRutas obtenerEstadisticas() {
        EstadisticasPlanificacionRutas stats = new EstadisticasPlanificacionRutas();
        
        long total = totalCalculos.get();
        stats.setTotalCalculos(total);
        stats.setCalculosExitosos(calculosExitosos.get());
        stats.setCalculosFallidos(calculosFallidos.get());
        stats.setCalculosParalelos(calculosParalelos.get());
        
        if (total > 0) {
            stats.setTiempoPromedioMs((double) tiempoTotalMs.get() / total);
        }
        
        // Estadísticas ACO
        long totalACO = totalOptimizacionesACO.get();
        stats.setTotalOptimizacionesACO(totalACO);
        if (totalACO > 0) {
            stats.setTiempoPromedioACOMs((double) tiempoTotalACOMs.get() / totalACO);
        }
        
        // Estadísticas A*
        long totalAEstrella = totalCalculosAEstrella.get();
        stats.setTotalCalculosAEstrella(totalAEstrella);
        if (totalAEstrella > 0) {
            stats.setTiempoPromedioAEstrellaMs((double) tiempoTotalAEstrellaMs.get() / totalAEstrella);
        }
        
        // Estado del sistema
        stats.setRutasEnCache(cacheDistancias.size());
        if (executorService != null) {
            stats.setThreadPoolSize(executorService.getPoolSize());
        }
        
        return stats;
    }

    @Override
    public void configurar(ConfiguracionPlanificadorRutas configuracion) {
        ConfiguracionPlanificadorRutas configAnterior = this.configuracion;
        this.configuracion = configuracion;
        
        // Reconfigurar thread pool si es necesario
        if (configAnterior.getNumeroThreads() != configuracion.getNumeroThreads()) {
            logger.info("Reconfigurando ThreadPool PlanificadorRutas");
            shutdownThreadPool();
            inicializarThreadPool();
        }
        
        // Configurar servicios dependientes
        if (aEstrellaService != null) {
            aEstrellaService.configurar(configuracion.getConfiguracionAEstrella());
        }
        
        logger.info("Configuración PlanificadorRutas actualizada");
    }

    @Override
    public void reiniciar() {
        totalCalculos.set(0);
        calculosExitosos.set(0);
        calculosFallidos.set(0);
        tiempoTotalMs.set(0);
        totalOptimizacionesACO.set(0);
        tiempoTotalACOMs.set(0);
        totalCalculosAEstrella.set(0);
        tiempoTotalAEstrellaMs.set(0);
        calculosParalelos.set(0);
        
        cacheDistancias.clear();
        
        logger.info("PlanificadorRutas reiniciado");
    }

    @PreDestroy
    public void shutdown() {
        logger.info("Cerrando PlanificadorRutasService");
        shutdownThreadPool();
    }

    private void shutdownThreadPool() {
        if (executorService != null && !executorService.isShutdown()) {
            try {
                executorService.shutdown();
                if (!executorService.awaitTermination(5000, TimeUnit.MILLISECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    @Data
    private static class SolucionHormiga {
        private final List<Entrega> entregas;
        private final double distanciaTotal;
        
        public SolucionHormiga(List<Entrega> entregas, double distanciaTotal) {
            this.entregas = new ArrayList<>(entregas);
            this.distanciaTotal = distanciaTotal;
        }
    }
    /**
     * Log de la secuencia de entregas planificada (antes del A*)
     */
    private void logSecuenciaEntregasPlanificada(Camion camion, Punto puntoInicio, 
                                            List<Entrega> entregas, Almacen almacenDestino) {
        
        logger.info("📋 SECUENCIA PLANIFICADA para camión {}:", camion.getCodigo());
        logger.info("  🏁 INICIO: ({},{}) [Almacén/Posición inicial]", 
            puntoInicio.getX(), puntoInicio.getY());
        
        for (int i = 0; i < entregas.size(); i++) {
            Entrega entrega = entregas.get(i);
            Pedido pedido = entrega.getPedido();
            
            logger.info("  📦 Entrega {}: ({},{}) - Pedido {} - Vol: {:.1f}m³ - Prioridad: {}", 
                i + 1, 
                pedido.getUbicacionX(), pedido.getUbicacionY(),
                pedido.getId(),
                entrega.getVolumenEntregadoM3(),
                pedido.getPrioridad());
        }
        
        if (configuracion.isForzarRetornoAlmacen()) {
            logger.info("  🏠 RETORNO: ({},{}) [Almacén destino]", 
                almacenDestino.getX(), almacenDestino.getY());
        }
        
        logger.info("📊 Total de paradas planificadas: {} entregas + retorno", entregas.size());
    }

    /**
     * Log de la secuencia completa de puntos (plan de alto nivel)
     */
    private void logSecuenciaPuntosCompleta(Camion camion, List<Punto> secuenciaPuntos, 
                                        List<Entrega> entregas) {
        
        logger.info("🗺️ SECUENCIA DE MOVIMIENTOS para camión {}:", camion.getCodigo());
        
        StringBuilder secuencia = new StringBuilder();
        secuencia.append("  🛣️ RUTA: ");
        
        for (int i = 0; i < secuenciaPuntos.size(); i++) {
            Punto punto = secuenciaPuntos.get(i);
            
            if (i == 0) {
                secuencia.append(String.format("INICIO(%d,%d)", punto.getX(), punto.getY()));
            } else if (i == secuenciaPuntos.size() - 1) {
                secuencia.append(String.format(" → ALMACÉN(%d,%d)", punto.getX(), punto.getY()));
            } else {
                secuencia.append(String.format(" → (%d,%d)", punto.getX(), punto.getY()));
            }
        }
        
        logger.info(secuencia.toString());
        
        // Log de distancias directas entre puntos consecutivos
        double distanciaDirectaTotal = 0.0;
        for (int i = 0; i < secuenciaPuntos.size() - 1; i++) {
            Punto actual = secuenciaPuntos.get(i);
            Punto siguiente = secuenciaPuntos.get(i + 1);
            int distanciaDirecta = actual.distanciaManhattanHasta(siguiente);
            distanciaDirectaTotal += distanciaDirecta;
            
            logger.debug("    📏 Tramo {}: ({},{}) → ({},{}) = {} unidades", 
                i + 1, actual.getX(), actual.getY(), 
                siguiente.getX(), siguiente.getY(), distanciaDirecta);
        }
        
        logger.info("📏 Distancia directa total planificada: {:.0f} unidades grid", distanciaDirectaTotal);
    }

    /**
     * Log del análisis de cambios de dirección en la ruta calculada
     */
    private void logAnalisisCambiosDireccion(Camion camion, RutaOptimizada ruta) {
        if (ruta == null || ruta.getSegmentos().isEmpty()) {
            return;
        }
        
        logger.info("🔄 ANÁLISIS DE CAMBIOS DE DIRECCIÓN para camión {}:", camion.getCodigo());
        
        List<Punto> rutaCompleta = ruta.obtenerSecuenciaPuntos();
        if (rutaCompleta.size() < 3) {
            logger.info("  ℹ️ Ruta muy corta para análisis de direcciones");
            return;
        }
        
        int cambiosDireccion = 0;
        String direccionAnterior = null;
        List<String> secuenciaMovimientos = new ArrayList<>();
        
        for (int i = 0; i < rutaCompleta.size() - 1; i++) {
            Punto actual = rutaCompleta.get(i);
            Punto siguiente = rutaCompleta.get(i + 1);
            
            String direccionActual = determinarDireccion(actual, siguiente);
            
            if (direccionAnterior != null && !direccionActual.equals(direccionAnterior)) {
                cambiosDireccion++;
                
                logger.info("  🔄 CAMBIO {}: en ({},{}) - {} → {}", 
                    cambiosDireccion, actual.getX(), actual.getY(), 
                    direccionAnterior, direccionActual);
                
                secuenciaMovimientos.add(String.format("CAMBIO en (%d,%d): %s→%s", 
                    actual.getX(), actual.getY(), direccionAnterior, direccionActual));
            }
            
            direccionAnterior = direccionActual;
        }
        
        logger.info("📊 Total de cambios de dirección: {}", cambiosDireccion);
        
        // Log de segmentos largos en la misma dirección
        logSegmentosLinears(camion, rutaCompleta);
    }

    /**
     * Log de segmentos lineales largos (donde va en línea recta)
     */
    private void logSegmentosLinears(Camion camion, List<Punto> rutaCompleta) {
        
        List<SegmentoLineal> segmentosLineales = new ArrayList<>();
        String direccionActual = null;
        Punto inicioSegmento = null;
        int longitudSegmento = 0;
        
        for (int i = 0; i < rutaCompleta.size() - 1; i++) {
            Punto actual = rutaCompleta.get(i);
            Punto siguiente = rutaCompleta.get(i + 1);
            
            String nuevaDireccion = determinarDireccion(actual, siguiente);
            
            if (direccionActual == null || !nuevaDireccion.equals(direccionActual)) {
                // Fin del segmento anterior
                if (direccionActual != null && longitudSegmento >= 3) {
                    segmentosLineales.add(new SegmentoLineal(
                        inicioSegmento, actual, direccionActual, longitudSegmento));
                }
                
                // Inicio de nuevo segmento
                direccionActual = nuevaDireccion;
                inicioSegmento = actual;
                longitudSegmento = 1;
            } else {
                longitudSegmento++;
            }
        }
        
        // Último segmento
        if (direccionActual != null && longitudSegmento >= 3) {
            segmentosLineales.add(new SegmentoLineal(
                inicioSegmento, rutaCompleta.get(rutaCompleta.size() - 1), 
                direccionActual, longitudSegmento));
        }
        
        if (!segmentosLineales.isEmpty()) {
            logger.info("➡️ SEGMENTOS LINEALES LARGOS (3+ pasos):");
            for (int i = 0; i < segmentosLineales.size(); i++) {
                SegmentoLineal seg = segmentosLineales.get(i);
                logger.info("  📏 Segmento {}: ({},{}) → ({},{}) - {} {} por {} pasos", 
                    i + 1, 
                    seg.inicio.getX(), seg.inicio.getY(),
                    seg.fin.getX(), seg.fin.getY(),
                    seg.direccion, 
                    seg.direccion.toLowerCase(),
                    seg.longitud);
            }
        } else {
            logger.info("  ⚡ Sin segmentos lineales largos - ruta con muchas curvas");
        }
    }

    /**
     * Log de la ruta completa calculada (después del A*)
     */
    private void logRutaCompletaCalculada(Camion camion, RutaOptimizada ruta) {
        if (ruta == null) {
            logger.warn("❌ No se pudo calcular ruta para camión {}", camion.getCodigo());
            return;
        }
        
        logger.info("✅ RUTA CALCULADA para camión {}:", camion.getCodigo());
        logger.info("  📊 Métricas generales:");
        logger.info("    📏 Distancia total: {:.1f} km", ruta.getDistanciaTotalKm());
        logger.info("    ⏱️ Tiempo estimado: {:.1f} horas", ruta.getTiempoEstimadoHoras());
        logger.info("    🚚 Entregas: {}", ruta.getNumeroEntregas());
        logger.info("    🗺️ Segmentos A*: {}", ruta.getSegmentos().size());
        
        // Log de cada segmento calculado
        logger.info("  🗺️ Detalle de segmentos:");
        for (int i = 0; i < ruta.getSegmentos().size(); i++) {
            SegmentoRuta segmento = ruta.getSegmentos().get(i);
            
            String tipoDesc = switch (segmento.getTipoSegmento()) {
                case ENTREGA -> "ENTREGA";
                case RETORNO_ALMACEN -> "RETORNO";
                case MOVIMIENTO -> "MOVIMIENTO";
            };
            
            logger.info("    📍 Seg {}: ({},{}) → ({},{}) - {} - {:.1f}km", 
                i + 1,
                segmento.getOrigen().getX(), segmento.getOrigen().getY(),
                segmento.getDestino().getX(), segmento.getDestino().getY(),
                tipoDesc,
                segmento.getDistanciaKm());
            
            // Si tiene ruta detallada A*, mostrar algunos puntos clave
            if (segmento.getRutaDetallada() != null && segmento.getRutaDetallada().size() > 2) {
                List<Punto> rutaDetallada = segmento.getRutaDetallada();
                logger.debug("      🛣️ A* ({} pasos): {} ... {}", 
                    rutaDetallada.size(),
                    rutaDetallada.get(0),
                    rutaDetallada.get(rutaDetallada.size() - 1));
            }
        }
        
        // 🔥 AGREGAR LA SECUENCIA COMPLETA PASO A PASO
        logSecuenciaCompletaPorSegmentos(camion, ruta);
    }
    private void logSecuenciaCompletaPorSegmentos(Camion camion, RutaOptimizada ruta) {
        if (ruta == null || ruta.getSegmentos().isEmpty()) {
            return;
        }
        
        logger.info("🗺️ SECUENCIA COMPLETA PASO A PASO para camión {}:", camion.getCodigo());
        logger.info("═══════════════════════════════════════════════════════════════");
        
        int pasoGlobal = 1;
        
        for (int segIdx = 0; segIdx < ruta.getSegmentos().size(); segIdx++) {
            SegmentoRuta segmento = ruta.getSegmentos().get(segIdx);
            
            String tipoSegmento = switch (segmento.getTipoSegmento()) {
                case ENTREGA -> "🚚 ENTREGA";
                case RETORNO_ALMACEN -> "🏠 RETORNO";
                case MOVIMIENTO -> "🔄 MOVIMIENTO";
            };
            
            logger.info("--- SEGMENTO {} ({}) ---", segIdx + 1, tipoSegmento);
            logger.info("    Desde: ({},{}) → Hacia: ({},{})", 
                segmento.getOrigen().getX(), segmento.getOrigen().getY(),
                segmento.getDestino().getX(), segmento.getDestino().getY());
            
            if (segmento.getEntrega() != null) {
                logger.info("    📦 Pedido: {} - Vol: {:.1f}m³", 
                    segmento.getEntrega().getPedido().getId(),
                    segmento.getEntrega().getVolumenEntregadoM3());
            }
            
            // 🔥 MOSTRAR CADA PASO DEL SEGMENTO
            List<Punto> rutaDetallada = segmento.getRutaDetallada();
            if (rutaDetallada != null && rutaDetallada.size() > 1) {
                
                logger.info("    🛣️ Pathfinding A* ({} pasos):", rutaDetallada.size());
                
                for (int i = 0; i < rutaDetallada.size(); i++) {
                    Punto punto = rutaDetallada.get(i);
                    
                    if (i == 0) {
                        logger.info("      [{}] 🏁 ({},{}) - INICIO SEGMENTO", 
                            pasoGlobal, punto.getX(), punto.getY());
                    } else if (i == rutaDetallada.size() - 1) {
                        logger.info("      [{}] 🎯 ({},{}) - FIN SEGMENTO", 
                            pasoGlobal, punto.getX(), punto.getY());
                    } else {
                        // Calcular dirección y detectar cambios
                        String direccion = "";
                        boolean esCambio = false;
                        
                        if (i > 0 && i < rutaDetallada.size() - 1) {
                            Punto anterior = rutaDetallada.get(i - 1);
                            Punto siguiente = rutaDetallada.get(i + 1);
                            
                            String dirAnterior = determinarDireccion(anterior, punto);
                            String dirSiguiente = determinarDireccion(punto, siguiente);
                            
                            direccion = dirSiguiente;
                            esCambio = !dirAnterior.equals(dirSiguiente);
                        }
                        
                        if (esCambio) {
                            logger.info("      [{}] 🔄 ({},{}) - {} ⚠️ CAMBIO DE DIRECCIÓN", 
                                pasoGlobal, punto.getX(), punto.getY(), direccion);
                        } else {
                            logger.info("      [{}] 📍 ({},{}) - {}", 
                                pasoGlobal, punto.getX(), punto.getY(), direccion);
                        }
                    }
                    pasoGlobal++;
                }
            } else {
                logger.info("    ⚠️ Sin ruta detallada - movimiento directo");
                pasoGlobal += 2; // Origen y destino
            }
            
            logger.info("    📏 Distancia segmento: {:.1f}km", segmento.getDistanciaKm());
            logger.info("");
        }
        
        logger.info("═══════════════════════════════════════════════════════════════");
        logger.info("📊 RESUMEN CAMIÓN {}:", camion.getCodigo());
        logger.info("    🔢 Total pasos: {}", pasoGlobal - 1);
        logger.info("    📏 Distancia total: {:.1f}km", ruta.getDistanciaTotalKm());
        logger.info("    ⏱️ Tiempo estimado: {:.1f}h", ruta.getTiempoEstimadoHoras());
        logger.info("    🚚 Entregas: {}", ruta.getNumeroEntregas());
        logger.info("═══════════════════════════════════════════════════════════════");
    }
    /**
     * Determina la dirección de movimiento entre dos puntos
     */
    private String determinarDireccion(Punto desde, Punto hacia) {
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

    /**
     * Clase auxiliar para segmentos lineales
     */
    private static class SegmentoLineal {
        final Punto inicio;
        final Punto fin;
        final String direccion;
        final int longitud;
        
        SegmentoLineal(Punto inicio, Punto fin, String direccion, int longitud) {
            this.inicio = inicio;
            this.fin = fin;
            this.direccion = direccion;
            this.longitud = longitud;
        }
    }
    /**
     * Log resumen final de toda la planificación
     */
    private void logResumenFinalPlanificacion(Map<Camion, RutaOptimizada> rutasCalculadas) {
        
        logger.info("🏁 RESUMEN FINAL DE PLANIFICACIÓN DE RUTAS:");
        logger.info("================================================");
        
        if (rutasCalculadas.isEmpty()) {
            logger.warn("❌ No se calcularon rutas exitosas");
            return;
        }
        
        double distanciaTotal = 0.0;
        double tiempoTotal = 0.0;
        int entregasTotal = 0;
        
        for (Map.Entry<Camion, RutaOptimizada> entry : rutasCalculadas.entrySet()) {
            Camion camion = entry.getKey();
            RutaOptimizada ruta = entry.getValue();
            
            distanciaTotal += ruta.getDistanciaTotalKm();
            tiempoTotal += ruta.getTiempoEstimadoHoras();
            entregasTotal += ruta.getNumeroEntregas();
            
            // Log secuencia simplificada de cada camión
            List<Punto> secuencia = ruta.obtenerSecuenciaPuntos();
            StringBuilder rutaResumen = new StringBuilder();
            
            for (int i = 0; i < secuencia.size(); i++) {
                Punto p = secuencia.get(i);
                if (i > 0) rutaResumen.append(" → ");
                rutaResumen.append(String.format("(%d,%d)", p.getX(), p.getY()));
                
                // Solo mostrar primeros y últimos puntos si es muy larga
                if (secuencia.size() > 6 && i == 2) {
                    rutaResumen.append(" → ... → ");
                    i = secuencia.size() - 2; // Saltar al penúltimo
                }
            }
            
            logger.info("🚚 Camión {}: {} entregas - {:.1f}km - {:.1f}h", 
                camion.getCodigo(), ruta.getNumeroEntregas(), 
                ruta.getDistanciaTotalKm(), ruta.getTiempoEstimadoHoras());
            logger.info("   🛣️ Ruta: {}", rutaResumen.toString());
            
            // Log análisis rápido de eficiencia
            double utilizacion = ruta.calcularPorcentajeUtilizacion();
            String eficiencia = utilizacion > 80 ? "ALTA" : utilizacion > 60 ? "MEDIA" : "BAJA";
            logger.info("   📊 Utilización: {:.1f}% [{}]", utilizacion, eficiencia);
        }
        
        logger.info("================================================");
        logger.info("📊 TOTALES:");
        logger.info("  🚚 Camiones con rutas: {}", rutasCalculadas.size());
        logger.info("  📦 Total entregas: {}", entregasTotal);
        logger.info("  📏 Distancia total: {:.1f} km", distanciaTotal);
        logger.info("  ⏱️ Tiempo total estimado: {:.1f} horas", tiempoTotal);
        logger.info("  📈 Promedio por camión: {:.1f} km, {:.1f} h", 
            distanciaTotal / rutasCalculadas.size(), 
            tiempoTotal / rutasCalculadas.size());
        logger.info("================================================");
    }
}