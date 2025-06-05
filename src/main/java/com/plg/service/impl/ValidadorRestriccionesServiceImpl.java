package com.plg.service.impl;

import com.plg.domain.*;
import com.plg.service.ValidadorRestriccionesService;
import com.plg.service.AEstrellaService;
import com.plg.service.util.CalculadoraCombustible;
import com.plg.service.util.GestorObstaculos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Implementación completa para validación de restricciones de rutas
 * Verifica combustible, peso, accesibilidad, tiempo y calidad
 */
@Service
public class ValidadorRestriccionesServiceImpl implements ValidadorRestriccionesService {
    
    private static final Logger logger = LoggerFactory.getLogger(ValidadorRestriccionesServiceImpl.class);
    
    @Autowired
    private CalculadoraCombustible calculadoraCombustible;
    
    @Autowired
    private GestorObstaculos gestorObstaculos;
    
    @Autowired
    private AEstrellaService aEstrellaService;
    
    // Configuración del validador
    private ConfiguracionRestricciones configuracion;
    
    // Constantes del sistema
    // private static final double CAPACIDAD_TANQUE_GALONES = 25.0;
    // private static final double VELOCIDAD_PROMEDIO_KMH = 50.0;
    // private static final double TIEMPO_DESCARGA_HORAS = 0.25; // 15 minutos
    
    private static final double UMBRAL_ALERTA_TEMPRANA = 15.0;     // 15% mínimo recomendado
    private static final double UMBRAL_CRITICO = 10.0;            // 10% nivel crítico
    private static final double UMBRAL_EMERGENCIA = 5.0;          // 5% emergencia

    public ValidadorRestriccionesServiceImpl() {
        this.configuracion = new ConfiguracionRestricciones();
    }
    
    @Override
    public ResultadoValidacionCompleta validarRutaCompleta(RutaOptimizada ruta) {
        logger.debug("Validando ruta completa para camión {}", ruta.getCamion().getId());
        
        ResultadoValidacionCompleta resultado = new ResultadoValidacionCompleta();
        
        // 1. Validación de combustible con alertas tempranas
        ResultadoValidacionCombustible validacionCombustible = validarCombustibleConAlertas(ruta);
        resultado.setValidacionCombustible(validacionCombustible);
        
        // 2. Validación de peso y capacidad
        List<Entrega> entregas = ruta.obtenerEntregasEnOrden();
        ResultadoValidacionPeso validacionPeso = validarPesoCapacidad(ruta.getCamion(), entregas);
        resultado.setValidacionPeso(validacionPeso);
        
        // 3. Validación de accesibilidad
        ResultadoValidacionAccesibilidad validacionAccesibilidad = validarAccesibilidad(ruta);
        resultado.setValidacionAccesibilidad(validacionAccesibilidad);
        
        // 4. Validación de tiempo
        ResultadoValidacionTiempo validacionTiempo = validarTiempo(ruta);
        resultado.setValidacionTiempo(validacionTiempo);
        
        // 5. Consolidar resultados
        boolean rutaValida = validacionCombustible.isCombustibleSuficiente() &&
                           validacionPeso.isPesoValido() &&
                           validacionAccesibilidad.isTodosPuntosAccesibles() &&
                           validacionTiempo.isTiemposValidos();
        
        resultado.setRutaValida(rutaValida);
        
        // 6. Calcular score de confiabilidad
        double scoreConfiabilidad = calcularScoreConfiabilidad(resultado);
        resultado.setScoreConfiabilidad(scoreConfiabilidad);
        
        // 7. Generar resumen
        resultado.setResumenValidacion(generarResumenValidacion(resultado));
        
        logger.debug("Validación completa: {} (score: {:.1f})", 
            rutaValida ? "VÁLIDA" : "INVÁLIDA", scoreConfiabilidad);
        
        return resultado;
    }
    /**
     * Validación de combustible extendida con alertas tempranas avanzadas
     */
    private ResultadoValidacionCombustible validarCombustibleConAlertas(RutaOptimizada ruta) {
        // Usar validación base de CalculadoraCombustible
        CalculadoraCombustible.ResultadoValidacionCombustible validacionBase = 
            calculadoraCombustible.validarRutaCompleta(ruta);
        
        // Convertir a resultado extendido
        ResultadoValidacionCombustible resultado = new ResultadoValidacionCombustible();
        resultado.setCombustibleSuficiente(validacionBase.isRutaViable());
        resultado.setCombustibleDisponible(25.0); // Capacidad inicial del tanque
        resultado.setCombustibleRestante(validacionBase.getCombustibleFinal());
        resultado.setCombustibleNecesario(validacionBase.getCombustibleTotalConsumido());
        
        // Calcular margen de seguridad
        double porcentajeRestante = (resultado.getCombustibleRestante() / 25.0) * 100.0;
        resultado.setMargenSeguridad(porcentajeRestante);
        
        // Generar alertas tempranas avanzadas
        String mensajeDetalle = generarMensajeAlertas(porcentajeRestante, validacionBase);
        resultado.setMensajeDetalle(mensajeDetalle);
        
        // Identificar segmento problemático si existe
        if (validacionBase.getSegmentoProblematico() != null) {
            // Buscar el segmento correspondiente en la ruta
            Optional<SegmentoRuta> segmentoProblematico = ruta.getSegmentos().stream()
                .filter(s -> s.getCombustibleConsumidoGalones() > 0)
                .max(Comparator.comparingDouble(SegmentoRuta::getCombustibleConsumidoGalones));
            
            resultado.setSegmentoProblematico(segmentoProblematico.orElse(null));
        }
        
        return resultado;
    }

    @Override
    public ResultadoValidacionCombustible validarCombustible(RutaOptimizada ruta) {
        return validarCombustibleConAlertas(ruta);
    }
    
    @Override
    public ResultadoValidacionPeso validarPesoCapacidad(Camion camion, List<Entrega> entregas) {
        ResultadoValidacionPeso resultado = new ResultadoValidacionPeso();
        
        // Calcular peso total de la carga
        double volumenTotal = entregas.stream()
            .mapToDouble(Entrega::getVolumenEntregadoM3)
            .sum();
        
        double pesoTotal = calculadoraCombustible.calcularPesoInicialCamion(camion, entregas);
        
        // Obtener límites del camión
        double pesoMaximo = obtenerPesoMaximo(camion);
        double capacidadMaxima = camion.getMaxCargaM3();
        
        resultado.setPesoTotal(pesoTotal);
        resultado.setPesoMaximo(pesoMaximo);
        resultado.setPorcentajeUtilizacion((volumenTotal / capacidadMaxima) * 100.0);
        
        // Validar peso con factor de seguridad
        boolean excedePeso = pesoTotal > (pesoMaximo * configuracion.getFactorSeguridadPeso());
        resultado.setExcedePeso(excedePeso);
        resultado.setPesoValido(!excedePeso);
        
        if (excedePeso) {
            resultado.setDetalleProblema(String.format(
                "Peso total %.2f ton excede límite seguro %.2f ton (factor seguridad: %.1f)",
                pesoTotal, pesoMaximo * configuracion.getFactorSeguridadPeso(), 
                configuracion.getFactorSeguridadPeso()));
        }
        
        return resultado;
    }
    
    @Override
    public ResultadoValidacionAccesibilidad validarAccesibilidad(RutaOptimizada ruta) {
        ResultadoValidacionAccesibilidad resultado = new ResultadoValidacionAccesibilidad();
        
        List<Punto> puntosInaccesibles = new ArrayList<>();
        List<SegmentoRuta> segmentosProblematicos = new ArrayList<>();
        
        // Validar cada segmento de la ruta
        for (SegmentoRuta segmento : ruta.getSegmentos()) {
            // Verificar origen
            if (!gestorObstaculos.esPuntoValido(segmento.getOrigen())) {
                puntosInaccesibles.add(segmento.getOrigen());
                segmentosProblematicos.add(segmento);
            }
            
            // Verificar destino
            if (!gestorObstaculos.esPuntoValido(segmento.getDestino())) {
                puntosInaccesibles.add(segmento.getDestino());
                segmentosProblematicos.add(segmento);
            }
            
            // Verificar que existe ruta entre origen y destino
            boolean existeRuta = aEstrellaService.existeRuta(segmento.getOrigen(), segmento.getDestino());
            if (!existeRuta) {
                segmentosProblematicos.add(segmento);
            }
        }
        
        resultado.setTodosPuntosAccesibles(puntosInaccesibles.isEmpty() && segmentosProblematicos.isEmpty());
        resultado.setPuntosInaccesibles(puntosInaccesibles);
        resultado.setSegmentosProblematicos(segmentosProblematicos);
        
        if (!resultado.isTodosPuntosAccesibles()) {
            resultado.setMensajeError(String.format(
                "Detectados %d puntos inaccesibles y %d segmentos problemáticos",
                puntosInaccesibles.size(), segmentosProblematicos.size()));
        }
        
        return resultado;
    }
    
    @Override
    public ResultadoValidacionTiempo validarTiempo(RutaOptimizada ruta) {
        ResultadoValidacionTiempo resultado = new ResultadoValidacionTiempo();
        
        double tiempoTotal = ruta.getTiempoEstimadoHoras();
        resultado.setTiempoTotalEstimado(tiempoTotal);
        
        // Verificar límites razonables de tiempo
        boolean tiemposValidos = tiempoTotal > 0 && tiempoTotal <= 24; // Máximo 24 horas
        resultado.setTiemposValidos(tiemposValidos);
        
        // Verificar entregas tardías (simplificado)
        List<Entrega> entregasTardias = new ArrayList<>();
        // En implementación completa, se verificarían horarios límite de pedidos
        resultado.setEntregasTardias(entregasTardias);
        
        if (tiempoTotal > 12) {
            resultado.setAdvertenciaTiempo(String.format(
                "Ruta muy larga: %.1f horas. Considerar dividir en múltiples jornadas.", tiempoTotal));
        }
        
        return resultado;
    }
    
    @Override
    public boolean esSecuenciaViable(Camion camion, List<Entrega> entregas, 
                                   Almacen almacenOrigen, List<Almacen> almacenesRetorno) {
        
        // Crear ruta temporal para validación
        RutaOptimizada rutaTemporal = new RutaOptimizada(camion, almacenOrigen);
        
        // Simular construcción de la ruta
        Punto posicionActual = new Punto(almacenOrigen.getX(), almacenOrigen.getY());
        
        for (int i = 0; i < entregas.size(); i++) {
            Entrega entrega = entregas.get(i);
            SegmentoRuta segmento = SegmentoRuta.crearSegmentoEntrega(posicionActual, entrega, i + 1);
            
            // Simular distancia (Manhattan por simplicidad)
            Punto destino = new Punto(entrega.getPedido().getUbicacionX(), entrega.getPedido().getUbicacionY());
            int distanciaGrid = posicionActual.distanciaManhattanHasta(destino);
            segmento.setDistanciaKm(distanciaGrid * 0.5); // Factor de conversión
            
            rutaTemporal.agregarSegmento(segmento);
            posicionActual = destino;
        }
        
        // Validar viabilidad rápida
        ResultadoValidacionCompleta validacion = validarRutaCompleta(rutaTemporal);
        return validacion.isRutaValida();
    }
    
    @Override
    public MargenSeguridadCombustible calcularMargenSeguridad(RutaOptimizada ruta) {
        MargenSeguridadCombustible margen = new MargenSeguridadCombustible();
        
        CalculadoraCombustible.ResultadoValidacionCombustible validacion = 
            calculadoraCombustible.validarRutaCompleta(ruta);
        
        double combustibleRestante = validacion.getCombustibleFinal();
        double porcentajeMargen = (combustibleRestante / 25.0) * 100.0;
        
        margen.setMargenGalones(combustibleRestante);
        margen.setMargenPorcentaje(porcentajeMargen);
        margen.setMargenSuficiente(porcentajeMargen >= configuracion.getMargenCombustibleMinimo());
        
        // Generar recomendación basada en margen
        String recomendacion = generarRecomendacionMargen(porcentajeMargen);
        margen.setRecomendacion(recomendacion);
        
        return margen;
    }
    
    @Override
    public List<ProblemaDetectado> detectarProblemasPotenciales(RutaOptimizada ruta) {
        List<ProblemaDetectado> problemas = new ArrayList<>();
        
        // Validación completa
        ResultadoValidacionCompleta validacion = validarRutaCompleta(ruta);
        
        // Problema de combustible
        if (!validacion.getValidacionCombustible().isCombustibleSuficiente()) {
            ProblemaDetectado problema = new ProblemaDetectado();
            problema.setSeveridad(SeveridadProblema.CRITICO);
            problema.setTipo(TipoProblema.COMBUSTIBLE_INSUFICIENTE);
            problema.setDescripcion("Combustible insuficiente para completar la ruta");
            problema.setSegmentoAfectado(validacion.getValidacionCombustible().getSegmentoProblematico());
            problemas.add(problema);
        }
        
        // Alerta temprana de combustible
        double margenPorcentaje = validacion.getValidacionCombustible().getMargenSeguridad();
        if (margenPorcentaje < UMBRAL_ALERTA_TEMPRANA && margenPorcentaje >= UMBRAL_CRITICO) {
            ProblemaDetectado problema = new ProblemaDetectado();
            problema.setSeveridad(SeveridadProblema.MEDIO);
            problema.setTipo(TipoProblema.COMBUSTIBLE_INSUFICIENTE);
            problema.setDescripcion(String.format("Margen de combustible bajo: %.1f%%", margenPorcentaje));
            problemas.add(problema);
        }
        
        // Problema de peso
        if (!validacion.getValidacionPeso().isPesoValido()) {
            ProblemaDetectado problema = new ProblemaDetectado();
            problema.setSeveridad(SeveridadProblema.CRITICO);
            problema.setTipo(TipoProblema.PESO_EXCEDIDO);
            problema.setDescripcion(validacion.getValidacionPeso().getDetalleProblema());
            problemas.add(problema);
        }
        
        // Problemas de accesibilidad
        if (!validacion.getValidacionAccesibilidad().isTodosPuntosAccesibles()) {
            ProblemaDetectado problema = new ProblemaDetectado();
            problema.setSeveridad(SeveridadProblema.CRITICO);
            problema.setTipo(TipoProblema.PUNTO_INACCESIBLE);
            problema.setDescripcion(validacion.getValidacionAccesibilidad().getMensajeError());
            problemas.add(problema);
        }
        
        // Problema de eficiencia baja
        double utilizacion = ruta.calcularPorcentajeUtilizacion();
        if (utilizacion < 50.0) {
            ProblemaDetectado problema = new ProblemaDetectado();
            problema.setSeveridad(SeveridadProblema.BAJO);
            problema.setTipo(TipoProblema.EFICIENCIA_BAJA);
            problema.setDescripcion(String.format("Baja utilización del camión: %.1f%%", utilizacion));
            problemas.add(problema);
        }
        
        return problemas;
    }
    
    @Override
    public List<SugerenciaCorreccion> sugerirCorrecciones(RutaOptimizada ruta, List<ProblemaDetectado> problemas) {
        List<SugerenciaCorreccion> sugerencias = new ArrayList<>();
        
        for (ProblemaDetectado problema : problemas) {
            switch (problema.getTipo()) {
                case COMBUSTIBLE_INSUFICIENTE:
                    if (problema.getSeveridad() == SeveridadProblema.CRITICO) {
                        SugerenciaCorreccion sugerencia = new SugerenciaCorreccion();
                        sugerencia.setProblemaRelacionado(TipoProblema.COMBUSTIBLE_INSUFICIENTE);
                        sugerencia.setDescripcionSolucion("Reducir carga del camión o dividir entregas entre múltiples viajes");
                        sugerencia.setImpactoEstimado(0.8);
                        sugerencia.setPrioridadImplementacion(1);
                        sugerencias.add(sugerencia);
                    } else {
                        SugerenciaCorreccion sugerencia = new SugerenciaCorreccion();
                        sugerencia.setProblemaRelacionado(TipoProblema.COMBUSTIBLE_INSUFICIENTE);
                        sugerencia.setDescripcionSolucion("Considerar reabastecimiento intermedio o ruta más eficiente");
                        sugerencia.setImpactoEstimado(0.6);
                        sugerencia.setPrioridadImplementacion(2);
                        sugerencias.add(sugerencia);
                    }
                    break;
                    
                case PESO_EXCEDIDO:
                    SugerenciaCorreccion sugerencia = new SugerenciaCorreccion();
                    sugerencia.setProblemaRelacionado(TipoProblema.PESO_EXCEDIDO);
                    sugerencia.setDescripcionSolucion("Reducir volumen total de carga o usar camión de mayor capacidad");
                    sugerencia.setImpactoEstimado(0.9);
                    sugerencia.setPrioridadImplementacion(1);
                    sugerencias.add(sugerencia);
                    break;
                    
                case EFICIENCIA_BAJA:
                    SugerenciaCorreccion sugerenciaEf = new SugerenciaCorreccion();
                    sugerenciaEf.setProblemaRelacionado(TipoProblema.EFICIENCIA_BAJA);
                    sugerenciaEf.setDescripcionSolucion("Agregar más entregas al camión o consolidar con otra ruta");
                    sugerenciaEf.setImpactoEstimado(0.4);
                    sugerenciaEf.setPrioridadImplementacion(3);
                    sugerencias.add(sugerenciaEf);
                    break;
                default:
                    break;
            }
        }
        
        return sugerencias;
    }
    
    @Override
    public ResultadoValidacionMultiple validarMultiplesRutas(Map<Camion, RutaOptimizada> rutas) {
        ResultadoValidacionMultiple resultado = new ResultadoValidacionMultiple();
        
        Map<Camion, ResultadoValidacionCompleta> resultadosPorCamion = new ConcurrentHashMap<>();
        
        // Validar en paralelo
        List<CompletableFuture<Void>> futures = rutas.entrySet().stream()
            .map(entry -> CompletableFuture.runAsync(() -> {
                ResultadoValidacionCompleta validacion = validarRutaCompleta(entry.getValue());
                resultadosPorCamion.put(entry.getKey(), validacion);
            }))
            .collect(Collectors.toList());
        
        // Esperar todas las validaciones
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        // Consolidar resultados
        int rutasValidas = (int) resultadosPorCamion.values().stream()
            .filter(ResultadoValidacionCompleta::isRutaValida)
            .count();
        
        resultado.setRutasValidas(rutasValidas);
        resultado.setRutasProblematicas(rutas.size() - rutasValidas);
        resultado.getResultadosPorCamion().putAll(resultadosPorCamion);
        
        String resumenGeneral = String.format("Validación múltiple: %d/%d rutas válidas (%.1f%%)",
            rutasValidas, rutas.size(), (double) rutasValidas / rutas.size() * 100.0);
        resultado.setResumenGeneral(resumenGeneral);
        
        return resultado;
    }
    
    @Override
    public ResultadoEvaluacionCalidad evaluarCalidadRuta(RutaOptimizada ruta, CriteriosCalidad criteriosCalidad) {
        ResultadoEvaluacionCalidad resultado = new ResultadoEvaluacionCalidad();
        
        double eficiencia = ruta.calcularPorcentajeUtilizacion();
        MargenSeguridadCombustible margen = calcularMargenSeguridad(ruta);
        
        boolean cumpleEficiencia = eficiencia >= criteriosCalidad.getEficienciaMinima();
        boolean cumpleMargen = margen.getMargenPorcentaje() >= criteriosCalidad.getMargenCombustibleMinimo();
        
        boolean cumpleEstandares = cumpleEficiencia && cumpleMargen;
        resultado.setCumpleEstandares(cumpleEstandares);
        
        // Calcular score de calidad (0-100)
        double scoreEficiencia = Math.min(eficiencia / criteriosCalidad.getEficienciaMinima(), 1.0);
        double scoreMargen = Math.min(margen.getMargenPorcentaje() / criteriosCalidad.getMargenCombustibleMinimo(), 1.0);
        double scoreCalidad = (scoreEficiencia + scoreMargen) / 2.0 * 100.0;
        
        resultado.setScoreCalidad(scoreCalidad);
        
        StringBuilder observaciones = new StringBuilder();
        if (!cumpleEficiencia) {
            observaciones.append(String.format("Eficiencia baja: %.1f%% (mín: %.1f%%). ", 
                eficiencia, criteriosCalidad.getEficienciaMinima()));
        }
        if (!cumpleMargen) {
            observaciones.append(String.format("Margen combustible bajo: %.1f%% (mín: %.1f%%). ", 
                margen.getMargenPorcentaje(), criteriosCalidad.getMargenCombustibleMinimo()));
        }
        if (cumpleEstandares) {
            observaciones.append("Ruta cumple todos los estándares de calidad.");
        }
        
        resultado.setObservaciones(observaciones.toString());
        
        return resultado;
    }
    
    @Override
    public ResultadoSimulacion simularEjecucionRuta(RutaOptimizada ruta, ConfiguracionSimulacion configuracionSimulacion) {
        ResultadoSimulacion resultado = new ResultadoSimulacion();
        
        // Simulación simplificada - en implementación completa sería más elaborada
        boolean simulacionExitosa = ruta.esRutaFactible();
        
        StringBuilder resumen = new StringBuilder();
        resumen.append("Simulación de ruta: ");
        
        if (simulacionExitosa) {
            resumen.append("EXITOSA. ");
            resumen.append(String.format("Tiempo estimado: %.1f h, ", ruta.getTiempoEstimadoHoras()));
            resumen.append(String.format("Combustible restante: %.1f gal.", ruta.getCombustibleRestanteGalones()));
        } else {
            resumen.append("FALLIDA. ");
            resumen.append("Ruta no viable con configuración actual.");
        }
        
        resultado.setSimulacionExitosa(simulacionExitosa);
        resultado.setResumenSimulacion(resumen.toString());
        
        return resultado;
    }
    
    @Override
    public ConfiguracionRestricciones obtenerConfiguracionRestricciones() {
        return this.configuracion;
    }
    
    @Override
    public void actualizarConfiguracionRestricciones(ConfiguracionRestricciones configuracion) {
        this.configuracion = configuracion;
        logger.info("Configuración de restricciones actualizada");
    }
    
    // Métodos auxiliares privados
    private String generarMensajeAlertas(double porcentajeRestante, 
                                       CalculadoraCombustible.ResultadoValidacionCombustible validacionBase) {
        StringBuilder mensaje = new StringBuilder();
        
        if (porcentajeRestante < UMBRAL_EMERGENCIA) {
            mensaje.append("🚨 EMERGENCIA: Combustible crítico (").append(String.format("%.1f%%", porcentajeRestante)).append("). ");
        } else if (porcentajeRestante < UMBRAL_CRITICO) {
            mensaje.append("⚠️ CRÍTICO: Combustible muy bajo (").append(String.format("%.1f%%", porcentajeRestante)).append("). ");
        } else if (porcentajeRestante < UMBRAL_ALERTA_TEMPRANA) {
            mensaje.append("⚡ ALERTA: Margen de combustible reducido (").append(String.format("%.1f%%", porcentajeRestante)).append("). ");
        } else {
            mensaje.append("✅ NORMAL: Margen de combustible adecuado (").append(String.format("%.1f%%", porcentajeRestante)).append("). ");
        }
        
        if (validacionBase.getAdvertencia() != null) {
            mensaje.append(validacionBase.getAdvertencia());
        }
        
        return mensaje.toString();
    }

    private String generarRecomendacionMargen(double porcentajeMargen) {
        if (porcentajeMargen < UMBRAL_EMERGENCIA) {
            return "ACCIÓN INMEDIATA: Reducir carga o abortar ruta. Riesgo de quedar varado.";
        } else if (porcentajeMargen < UMBRAL_CRITICO) {
            return "PRECAUCIÓN: Considerar reabastecimiento intermedio o ruta alternativa.";
        } else if (porcentajeMargen < UMBRAL_ALERTA_TEMPRANA) {
            return "SUPERVISIÓN: Monitorear consumo durante ejecución.";
        } else {
            return "ÓPTIMO: Margen de seguridad adecuado para operación normal.";
        }
    }    


    private double calcularScoreConfiabilidad(ResultadoValidacionCompleta resultado) {
        double score = 100.0;
        
        // Penalizar por problemas críticos
        if (!resultado.getValidacionCombustible().isCombustibleSuficiente()) {
            score -= 40.0;
        }
        if (!resultado.getValidacionPeso().isPesoValido()) {
            score -= 30.0;
        }
        if (!resultado.getValidacionAccesibilidad().isTodosPuntosAccesibles()) {
            score -= 30.0;
        }
        
        // Penalizar por alertas tempranas
        double margenCombustible = resultado.getValidacionCombustible().getMargenSeguridad();
        if (margenCombustible < UMBRAL_ALERTA_TEMPRANA) {
            score -= (UMBRAL_ALERTA_TEMPRANA - margenCombustible) * 2; // 2 puntos por cada % bajo el umbral
        }
        
        return Math.max(0.0, score);
    }

    // private double obtenerPesoMaximoCombinado(Camion camion) {
    //     // Peso máximo combinado según tipo de camión
    //     String tipo = camion.getTipo();
    //     if (tipo == null) return 3.5; // TD por defecto
        
    //     return switch (tipo.toUpperCase()) {
    //         case "TA" -> 15.0; // 2.5 tara + 12.5 carga
    //         case "TB" -> 9.5;  // 2.0 tara + 7.5 carga
    //         case "TC" -> 6.5;  // 1.5 tara + 5.0 carga
    //         case "TD" -> 3.5;  // 1.0 tara + 2.5 carga
    //         default -> 3.5;
    //     };
    // }
    
    private String generarResumenValidacion(ResultadoValidacionCompleta resultado) {
        StringBuilder resumen = new StringBuilder();
        
        if (resultado.isRutaValida()) {
            resumen.append("✅ RUTA VÁLIDA");
            
            double scoreConfiabilidad = resultado.getScoreConfiabilidad();
            if (scoreConfiabilidad >= 90) {
                resumen.append(" - EXCELENTE");
            } else if (scoreConfiabilidad >= 75) {
                resumen.append(" - BUENA");
            } else {
                resumen.append(" - ACEPTABLE");
            }
        } else {
            resumen.append("❌ RUTA INVÁLIDA");
        }
        
        // Agregar detalles principales
        if (!resultado.getValidacionCombustible().isCombustibleSuficiente()) {
            resumen.append(" | Combustible insuficiente");
        } else {
            double margen = resultado.getValidacionCombustible().getMargenSeguridad();
            if (margen < UMBRAL_ALERTA_TEMPRANA) {
                resumen.append(String.format(" | Margen bajo: %.1f%%", margen));
            }
        }
        
        return resumen.toString();
    }
    
    private double obtenerPesoMaximo(Camion camion) {
        // Usar peso bruto del camión como límite máximo
        if (camion.getPesoBruto() > 0) {
            return camion.getPesoBruto();
        }
        
        // Fallback basado en tipo de camión
        String tipo = camion.getTipo();
        return switch (tipo != null ? tipo.toUpperCase() : "TD") {
            case "TA" -> 15.0; // 2.5 tara + 12.5 carga
            case "TB" -> 9.5;  // 2.0 tara + 7.5 carga
            case "TC" -> 6.5;  // 1.5 tara + 5.0 carga
            case "TD" -> 3.5;  // 1.0 tara + 2.5 carga
            default -> 3.5;
        };
    }
}