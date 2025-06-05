package com.plg.service.impl;

import com.plg.domain.*;
import com.plg.service.ValidadorFinalService;
import com.plg.service.ValidadorRestriccionesService;
import com.plg.service.AEstrellaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Implementación del validador final
 * Garantiza que ruta es ejecutable en mundo real
 */
@Service
public class ValidadorFinalServiceImpl implements ValidadorFinalService {
    
    private static final Logger logger = LoggerFactory.getLogger(ValidadorFinalServiceImpl.class);
    
    @Autowired
    private ValidadorRestriccionesService validadorRestricciones;
    
    @Autowired
    private AEstrellaService aEstrellaService;
    
    // Umbrales de calidad
    private static final double UMBRAL_SCORE_MINIMO = 70.0;
    private static final double UMBRAL_PRIORIDAD_URGENTE = 700.0;
    private static final double UMBRAL_TIEMPO_MAXIMO_HORAS = 12.0;
    
    @Override
    public ResultadoValidacionFinal validarRutaFinal(RutaOptimizada ruta) {
        logger.info("Iniciando validación final exhaustiva para camión {}", ruta.getCamion().getId());
        
        long tiempoInicio = System.currentTimeMillis();
        ResultadoValidacionFinal resultado = new ResultadoValidacionFinal();
        
        // 1. Reutilizar validación completa existente
        ValidadorRestriccionesService.ResultadoValidacionCompleta validacionBase = 
            validadorRestricciones.validarRutaCompleta(ruta);
        
        // 2. Validaciones específicas adicionales
        ResultadoValidacionPrioridades validacionPrioridades = validarPrioridadesSecuencia(ruta);
        ResultadoValidacionVentanasTiempo validacionTiempos = validarVentanasTiempo(ruta);
        
        // 3. Re-validación completa de combustible desde cero
        boolean combustibleRevalidado = revalidarCombustibleDesdeEcero(ruta);
        
        // 4. Verificación exhaustiva de todos los segmentos A*
        boolean todosSegmentosValidos = verificarTodosSegmentosAStar(ruta);
        
        // 5. Calcular métricas de calidad
        MetricasCalidadRuta metricas = calcularMetricasCalidad(ruta);
        
        // 6. Consolidar resultados
        boolean rutaEjecutable = validacionBase.isRutaValida() && 
                               combustibleRevalidado && 
                               todosSegmentosValidos &&
                               validacionPrioridades.isSecuenciaPrioridadesCorrecta() &&
                               validacionTiempos.isTodasEntregasDentroVentana();
        
        resultado.setRutaEjecutable(rutaEjecutable);
        resultado.setScoreCalidadGlobal(metricas.getScoreViabilidadGeneral());
        resultado.setMetricas(metricas);
        
        // 7. Detectar problemas específicos
        List<ProblemaValidacion> problemas = detectarProblemasEspecificos(ruta, validacionBase, validacionPrioridades, validacionTiempos);
        resultado.setProblemasDetectados(problemas);
        
        // 8. Generar advertencias de calidad
        List<String> advertencias = generarAdvertenciasCalidad(ruta, metricas);
        resultado.setAdvertenciasCalidad(advertencias);
        
        // 9. Determinar si requiere intervención
        resultado.setRequiereIntervencionOperador(
            !rutaEjecutable || 
            metricas.getScoreViabilidadGeneral() < UMBRAL_SCORE_MINIMO ||
            !problemas.isEmpty()
        );
        // 10. **AGREGAR: Manejo de escalación**
        if (requiereEscalacionInmediata(problemas)) {
            List<String> alertasEscalacion = generarAlertasEscalacion(problemas);
            logger.error("ESCALACIÓN REQUERIDA para camión {}: {}", 
                ruta.getCamion().getId(), String.join(" | ", alertasEscalacion));
            
            // En implementación real: enviar notificación a operadores
            resultado.setRequiereIntervencionOperador(true);
        }
        // 10. Generar resumen ejecutivo
        String resumen = generarResumenEjecutivo(resultado);
        resultado.setResumenEjecutivo(resumen);
        
        long tiempoTotal = System.currentTimeMillis() - tiempoInicio;
        logger.info("Validación final completada en {}ms: Ejecutable={}, Score={:.1f}", 
            tiempoTotal, rutaEjecutable, metricas.getScoreViabilidadGeneral());
        
        return resultado;
    }
    
    @Override
    public ResultadoValidacionPrioridades validarPrioridadesSecuencia(RutaOptimizada ruta) {
        ResultadoValidacionPrioridades resultado = new ResultadoValidacionPrioridades();
        
        List<Entrega> entregas = ruta.obtenerEntregasEnOrden();
        if (entregas.isEmpty()) {
            resultado.setSecuenciaPrioridadesCorrecta(true);
            return resultado;
        }
        
        List<ProblemaOrdenPrioridad> problemas = new ArrayList<>();
        int entregasUrgentesAlFinal = 0;
        double tiempoAcumulado = 0.0;
        double tiempoTotalPrioridadAlta = 0.0;
        int contadorPrioridadAlta = 0;
        
        // Verificar que entregas urgentes estén temprano en secuencia
        for (int i = 0; i < entregas.size(); i++) {
            Entrega entrega = entregas.get(i);
            int prioridad = entrega.getPedido().getPrioridad();
            
            // Calcular tiempo acumulado hasta esta entrega
            if (i > 0) {
                SegmentoRuta segmento = ruta.getSegmentos().get(i);
                tiempoAcumulado += segmento.getTiempoEstimadoHoras();
            }
            
            // Verificar entregas urgentes
            if (prioridad >= UMBRAL_PRIORIDAD_URGENTE) {
                contadorPrioridadAlta++;
                tiempoTotalPrioridadAlta += tiempoAcumulado;
                
                // Problema: entrega urgente muy tarde en secuencia
                if (i > entregas.size() * 0.6) { // Más del 60% de la ruta
                    ProblemaOrdenPrioridad problema = new ProblemaOrdenPrioridad();
                    problema.setEntregaProblematica(entrega);
                    problema.setPosicionActual(i + 1);
                    problema.setPosicionRecomendada(Math.max(1, entregas.size() / 3));
                    problema.setRazonProblema("Entrega urgente programada muy tarde en secuencia");
                    problemas.add(problema);
                    
                    entregasUrgentesAlFinal++;
                }
            }
            
            // Verificar inversiones de prioridad (entrega baja prioridad antes que alta)
            if (i < entregas.size() - 1) {
                Entrega siguienteEntrega = entregas.get(i + 1);
                if (siguienteEntrega.getPedido().getPrioridad() > prioridad + 200) { // Diferencia significativa
                    ProblemaOrdenPrioridad problema = new ProblemaOrdenPrioridad();
                    problema.setEntregaProblematica(siguienteEntrega);
                    problema.setPosicionActual(i + 2);
                    problema.setPosicionRecomendada(i + 1);
                    problema.setRazonProblema("Inversión de prioridad detectada");
                    problemas.add(problema);
                }
            }
        }
        
        // Calcular score de distribución de prioridades
        double scoreDistribucion = calcularScoreDistribucionPrioridades(entregas);
        
        resultado.setSecuenciaPrioridadesCorrecta(problemas.isEmpty());
        resultado.setProblemasOrden(problemas);
        resultado.setEntregasUrgentesAlFinal(entregasUrgentesAlFinal);
        resultado.setScoreDistribucionPrioridades(scoreDistribucion);
        resultado.setTiempoPromedioPrioridadAlta(
            contadorPrioridadAlta > 0 ? tiempoTotalPrioridadAlta / contadorPrioridadAlta : 0.0
        );
        
        return resultado;
    }
    
    @Override
    public ResultadoValidacionVentanasTiempo validarVentanasTiempo(RutaOptimizada ruta) {
        ResultadoValidacionVentanasTiempo resultado = new ResultadoValidacionVentanasTiempo();
        
        List<EntregaTardia> entregasTardias = new ArrayList<>();
        double tiempoAcumulado = 0.0;
        int entregasFueraVentana = 0;
        
        List<Entrega> entregas = ruta.obtenerEntregasEnOrden();
        
        for (int i = 0; i < entregas.size(); i++) {
            Entrega entrega = entregas.get(i);
            
            if (i < ruta.getSegmentos().size()) {
                SegmentoRuta segmento = ruta.getSegmentos().get(i);
                tiempoAcumulado += segmento.getTiempoEstimadoHoras();
            }
            
            Integer horasLimite = entrega.getPedido().getHorasLimite();
            if (horasLimite != null && horasLimite > 0) {
                
                if (tiempoAcumulado > horasLimite) {
                    EntregaTardia entregaTardia = new EntregaTardia();
                    entregaTardia.setEntrega(entrega);
                    entregaTardia.setTiempoEstimadoEntrega(tiempoAcumulado);
                    entregaTardia.setTiempoLimite(horasLimite);
                    entregaTardia.setMinutosRetraso((tiempoAcumulado - horasLimite) * 60);
                    
                    entregasTardias.add(entregaTardia);
                    entregasFueraVentana++;
                }
            }
        }

        double tiempoTotalRuta = ruta.getTiempoEstimadoHoras();
        
        resultado.setTodasEntregasDentroVentana(entregasTardias.isEmpty());
        resultado.setEntregasFueraVentana(entregasFueraVentana);
        resultado.setEntregasTardias(entregasTardias);
        resultado.setTiempoTotalRuta(tiempoTotalRuta);
        
        // Identificar ventana más crítica
        if (!entregasTardias.isEmpty()) {
            EntregaTardia masCritica = entregasTardias.stream()
                .max(Comparator.comparingDouble(EntregaTardia::getMinutosRetraso))
                .orElse(null);
            
            if (masCritica != null) {
                resultado.setVentanaMasCritica(String.format("Pedido %d: %.0f min retraso", 
                    masCritica.getEntrega().getPedido().getId(), masCritica.getMinutosRetraso()));
            }
        }
        
        return resultado;
    }
    
    @Override
    public MetricasCalidadRuta calcularMetricasCalidad(RutaOptimizada ruta) {
        MetricasCalidadRuta metricas = new MetricasCalidadRuta();
        Map<String, Double> detalladas = new HashMap<>();

        double distanciaTotal = ruta.getDistanciaTotalKm();
        metricas.setDistanciaTotal(distanciaTotal);
        detalladas.put("distanciaTotalKm", distanciaTotal);
        
        double tiempoEstimado = ruta.getTiempoEstimadoHoras();
        metricas.setTiempoEstimado(tiempoEstimado);
        detalladas.put("tiempoEstimadoHoras", tiempoEstimado);
        
        double consumoTotal = ruta.getCombustibleNecesarioGalones();
        metricas.setConsumoTotal(consumoTotal);
        detalladas.put("combustibleTotalGalones", consumoTotal);
        
        double combustibleRestante = ruta.getCombustibleRestanteGalones();
        double scoreCombustible = Math.min(100.0, (combustibleRestante / 25.0) * 100.0);
        metricas.setScoreCombustible(scoreCombustible);
        detalladas.put("combustibleRestantePorcentaje", scoreCombustible);
        
        double eficienciaUtilizacion = ruta.calcularPorcentajeUtilizacion();
        double scoreEficiencia = Math.min(100.0, eficienciaUtilizacion);
        metricas.setScoreEficiencia(scoreEficiencia);
        metricas.setEficienciaUtilizacion(eficienciaUtilizacion); // NUEVO
        detalladas.put("utilizacionCamion", eficienciaUtilizacion);
        
        ResultadoValidacionPrioridades validacionPrioridades = validarPrioridadesSecuencia(ruta);
        double scorePrioridades = validacionPrioridades.getScoreDistribucionPrioridades();
        metricas.setScorePrioridades(scorePrioridades);
        metricas.setScorePrioridad(scorePrioridades); // NUEVO - alias para compatibilidad
        detalladas.put("entregasUrgentesAlFinal", (double)validacionPrioridades.getEntregasUrgentesAlFinal());
        
        ResultadoValidacionVentanasTiempo validacionTiempos = validarVentanasTiempo(ruta);
        double scoreTiempos = validacionTiempos.isTodasEntregasDentroVentana() ? 100.0 : 
            Math.max(0.0, 100.0 - (validacionTiempos.getEntregasFueraVentana() * 20.0));
        metricas.setScoreTiempos(scoreTiempos);
        detalladas.put("entregasFueraVentana", (double)validacionTiempos.getEntregasFueraVentana());
        
        double scoreAccesibilidad = ruta.esRutaFactible() ? 100.0 : 0.0;
        metricas.setScoreAccesibilidad(scoreAccesibilidad);
        detalladas.put("rutaFactible", scoreAccesibilidad);
        
        double scoreGeneral = (scoreCombustible * 0.3) + 
                            (scoreEficiencia * 0.2) + 
                            (scorePrioridades * 0.2) + 
                            (scoreTiempos * 0.2) + 
                            (scoreAccesibilidad * 0.1);
        
        metricas.setScoreViabilidadGeneral(scoreGeneral);
        metricas.setMetricasDetalladas(detalladas);
        
        return metricas;
    }
        
    private boolean revalidarCombustibleDesdeEcero(RutaOptimizada ruta) {
        try {
            ValidadorRestriccionesService.ResultadoValidacionCombustible validacion = 
                validadorRestricciones.validarCombustible(ruta);
            
            return validacion.isCombustibleSuficiente();
            
        } catch (Exception e) {
            logger.error("Error re-validando combustible: {}", e.getMessage());
            return false;
        }
    }
    
    private boolean verificarTodosSegmentosAStar(RutaOptimizada ruta) {
        for (SegmentoRuta segmento : ruta.getSegmentos()) {
            boolean existeRuta = aEstrellaService.existeRuta(segmento.getOrigen(), segmento.getDestino());
            if (!existeRuta) {
                logger.warn("Segmento sin ruta A* válida: {} -> {}", 
                    segmento.getOrigen(), segmento.getDestino());
                return false;
            }
        }
        return true;
    }
    
    private double calcularScoreDistribucionPrioridades(List<Entrega> entregas) {
        if (entregas.isEmpty()) return 100.0;
        
        double score = 100.0;
        
        for (int i = 0; i < entregas.size() - 1; i++) {
            int prioridadActual = entregas.get(i).getPedido().getPrioridad();
            int prioridadSiguiente = entregas.get(i + 1).getPedido().getPrioridad();
            
            if (prioridadSiguiente > prioridadActual + 100) { // Inversión significativa
                score -= 10.0; // Penalización de 10 puntos
            }
        }
        
        return Math.max(0.0, score);
    }
    
    private List<ProblemaValidacion> detectarProblemasEspecificos(
            RutaOptimizada ruta,
            ValidadorRestriccionesService.ResultadoValidacionCompleta validacionBase,
            ResultadoValidacionPrioridades validacionPrioridades,
            ResultadoValidacionVentanasTiempo validacionTiempos) {
        
        List<ProblemaValidacion> problemas = new ArrayList<>();
        
        if (!validacionBase.getValidacionCombustible().isCombustibleSuficiente()) {
            ProblemaValidacion problema = new ProblemaValidacion();
            problema.setTipo(TipoProblemaValidacion.COMBUSTIBLE_CRITICO);
            problema.setSeveridad(SeveridadProblema.CRITICO);
            problema.setDescripcion("Combustible insuficiente para completar ruta");
            problema.setSolucionSugerida("Reducir número de entregas o cambiar secuencia");
            problemas.add(problema);
        }
        
        if (!validacionPrioridades.isSecuenciaPrioridadesCorrecta()) {
            ProblemaValidacion problema = new ProblemaValidacion();
            problema.setTipo(TipoProblemaValidacion.SECUENCIA_PRIORIDADES_INCORRECTA);
            problema.setSeveridad(SeveridadProblema.ALTO);
            problema.setDescripcion("Secuencia de entregas no respeta prioridades");
            problema.setSolucionSugerida("Re-ordenar entregas por prioridad");
            problemas.add(problema);
        }
        
        if (!validacionTiempos.isTodasEntregasDentroVentana()) {
            ProblemaValidacion problema = new ProblemaValidacion();
            problema.setTipo(TipoProblemaValidacion.VENTANA_TIEMPO_VIOLADA);
            problema.setSeveridad(SeveridadProblema.ALTO);
            problema.setDescripcion("Entregas fuera de ventana de tiempo");
            problema.setSolucionSugerida("Optimizar secuencia o dividir en múltiples rutas");
            problemas.add(problema);
        }
        
        if (!validacionBase.getValidacionPeso().isPesoValido()) {
            ProblemaValidacion problema = new ProblemaValidacion();
            problema.setTipo(TipoProblemaValidacion.PESO_EXCEDIDO);
            problema.setSeveridad(SeveridadProblema.CRITICO);
            problema.setDescripcion("Peso total excede límites del camión");
            problema.setSolucionSugerida("Reducir volumen de carga o usar camión de mayor capacidad");
            problemas.add(problema);
        }
        
        // **AGREGAR: Problemas de accesibilidad**
        if (!validacionBase.getValidacionAccesibilidad().isTodosPuntosAccesibles()) {
            ProblemaValidacion problema = new ProblemaValidacion();
            problema.setTipo(TipoProblemaValidacion.PUNTO_INACCESIBLE);
            problema.setSeveridad(SeveridadProblema.CRITICO);
            problema.setDescripcion("Puntos de entrega inaccesibles detectados");
            problema.setSolucionSugerida("Verificar obstáculos o usar rutas alternativas");
            problemas.add(problema);
        }
        
        // **AGREGAR: Problemas de eficiencia**
        double eficiencia = ruta.calcularPorcentajeUtilizacion();
        if (eficiencia < 30.0) { // Menos del 30% de utilización
            ProblemaValidacion problema = new ProblemaValidacion();
            problema.setTipo(TipoProblemaValidacion.EFICIENCIA_SUBOPTIMA);
            problema.setSeveridad(SeveridadProblema.MEDIO);
            problema.setDescripcion(String.format("Eficiencia muy baja: %.1f%% de utilización", eficiencia));
            problema.setSolucionSugerida("Agregar más entregas al camión o consolidar con otra ruta");
            problemas.add(problema);
        }
        
        return problemas;
    }
    
    private List<String> generarAdvertenciasCalidad(RutaOptimizada ruta, MetricasCalidadRuta metricas) {
        List<String> advertencias = new ArrayList<>();
        
        if (metricas.getScoreCombustible() < 15.0) {
            advertencias.add("Margen de combustible muy bajo - riesgo operacional");
        }
        
        if (metricas.getScoreEficiencia() < 50.0) {
            advertencias.add("Baja utilización del camión - considerar agregar más entregas");
        }
        
        if (metricas.getScorePrioridades() < 80.0) {
            advertencias.add("Secuencia de prioridades subóptima");
        }
        
        if (ruta.getTiempoEstimadoHoras() > UMBRAL_TIEMPO_MAXIMO_HORAS) {
            advertencias.add("Ruta muy larga - considerar dividir en múltiples jornadas");
        }
        
        return advertencias;
    }
    
    private String generarResumenEjecutivo(ResultadoValidacionFinal resultado) {
        StringBuilder resumen = new StringBuilder();
        if (resultado.isRutaEjecutable()) {
            resumen.append("✅ RUTA EJECUTABLE - ");
            resumen.append(String.format("Calidad: %.1f/100", resultado.getScoreCalidadGlobal()));
            
            if (resultado.getScoreCalidadGlobal() >= 90) {
                resumen.append(" (EXCELENTE)");
            } else if (resultado.getScoreCalidadGlobal() >= 75) {
                resumen.append(" (BUENA)");
            } else {
                resumen.append(" (ACEPTABLE)");
            }
        } else {
            resumen.append("❌ RUTA NO EJECUTABLE - ");
            resumen.append(String.format("%d problemas críticos detectados", 
                resultado.getProblemasDetectados().size()));
        }
        
        if (resultado.isRequiereIntervencionOperador()) {
            resumen.append(" - REQUIERE INTERVENCIÓN OPERADOR");
        }
        
        return resumen.toString();
    }

    @Override
    public ResultadoValidacionMultipleFinal validarMultiplesRutasFinal(Map<Camion, RutaOptimizada> rutas) {
        ResultadoValidacionMultipleFinal resultado = new ResultadoValidacionMultipleFinal();
        Map<Camion, ResultadoValidacionFinal> resultados = new HashMap<>();
        
        for (Map.Entry<Camion, RutaOptimizada> entry : rutas.entrySet()) {
            ResultadoValidacionFinal validacion = validarRutaFinal(entry.getValue());
            resultados.put(entry.getKey(), validacion);
        }
        
        resultado.setResultadosPorCamion(resultados);
        return resultado;
    }

    @Override
    public ReporteValidacionAuditoria generarReporteAuditoria(RutaOptimizada ruta, ResultadoValidacionFinal resultado) {
        ReporteValidacionAuditoria reporte = new ReporteValidacionAuditoria();
        reporte.setFechaValidacion(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        reporte.setIdentificadorRuta("RUTA_" + ruta.getCamion().getId());
        return reporte;
    }

    private boolean requiereEscalacionInmediata(List<ProblemaValidacion> problemas) {
        return problemas.stream()
            .anyMatch(p -> p.getSeveridad() == SeveridadProblema.CRITICO);
    }

    private List<String> generarAlertasEscalacion(List<ProblemaValidacion> problemas) {
        List<String> alertas = new ArrayList<>();
        
        for (ProblemaValidacion problema : problemas) {
            if (problema.getSeveridad() == SeveridadProblema.CRITICO) {
                alertas.add(String.format("🚨 CRÍTICO: %s - %s", 
                    problema.getDescripcion(), problema.getSolucionSugerida()));
            }
        }
        
        return alertas;
    }
}