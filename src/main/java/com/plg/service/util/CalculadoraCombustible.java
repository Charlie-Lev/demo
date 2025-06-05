package com.plg.service.util;

import com.plg.domain.*;
import com.plg.domain.enumeration.TipoSegmento;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Validador de viabilidad energética para rutas de camiones
 * Maneja cálculos de consumo, peso dinámico y factibilidad
 */
@Component
public class CalculadoraCombustible {
    
    private static final Logger logger = LoggerFactory.getLogger(CalculadoraCombustible.class);
    
    // Constantes del sistema
    private static final double CAPACIDAD_TANQUE_GALONES = 25.0;
    private static final double FACTOR_CONSUMO = 180.0; // Distancia[Km] × Peso[Ton] ÷ 180
    private static final double MARGEN_SEGURIDAD_PORCENTAJE = 10.0; // 10% de margen
    private static final double FACTOR_CONVERSION_KM = 0.5; // 1 unidad grid = 0.5 km
    
    // Pesos por tipo de camión (según especificación)
    private static final double PESO_TARA_TA = 2.5; // Toneladas
    private static final double PESO_TARA_TB = 2.0;
    private static final double PESO_TARA_TC = 1.5;
    private static final double PESO_TARA_TD = 1.0;
    
    private static final double PESO_CARGA_MAXIMA_TA = 12.5; // Toneladas
    private static final double PESO_CARGA_MAXIMA_TB = 7.5;
    private static final double PESO_CARGA_MAXIMA_TC = 5.0;
    private static final double PESO_CARGA_MAXIMA_TD = 2.5;
    
    /**
     * Valida si una ruta completa es factible con el combustible disponible
     */
    public ResultadoValidacionCombustible validarRutaCompleta(RutaOptimizada ruta) {
        logger.debug("Validando viabilidad de combustible para camión {}", ruta.getCamion().getId());
        
        ResultadoValidacionCombustible resultado = new ResultadoValidacionCombustible();
        resultado.setRutaViable(true);
        resultado.setCombustibleInicial(CAPACIDAD_TANQUE_GALONES);
        
        double combustibleActual = CAPACIDAD_TANQUE_GALONES;
        double pesoActual = calcularPesoInicialCamion(ruta.getCamion(), ruta.obtenerEntregasEnOrden());
        
        // Validar cada segmento secuencialmente
        for (SegmentoRuta segmento : ruta.getSegmentos()) {
            // Actualizar peso para este segmento
            segmento.setPesoInicialTon(pesoActual);
            
            // Calcular peso final después de posible entrega
            double pesoFinal = pesoActual;
            if (segmento.getTipoSegmento() == TipoSegmento.ENTREGA && segmento.getEntrega() != null) {
                double volumenEntregado = segmento.getEntrega().getVolumenEntregadoM3();
                double pesoCargaEntregada = calcularPesoCarga(ruta.getCamion(), volumenEntregado);
                pesoFinal = pesoActual - pesoCargaEntregada;
            }
            segmento.setPesoFinalTon(pesoFinal);
            
            // Calcular consumo del segmento
            double consumoSegmento = calcularConsumoSegmento(segmento);
            
            // Verificar si hay suficiente combustible
            if (combustibleActual < consumoSegmento) {
                resultado.setRutaViable(false);
                resultado.setSegmentoProblematico(segmento);
                resultado.setCombustibleFaltante(consumoSegmento - combustibleActual);
                resultado.setMensajeError(String.format(
                    "Combustible insuficiente en segmento %d. Necesario: %.2f gal, Disponible: %.2f gal",
                    segmento.getOrdenEnRuta(), consumoSegmento, combustibleActual));
                
                logger.warn("Ruta no viable: {}", resultado.getMensajeError());
                return resultado;
            }
            
            // Actualizar estado para siguiente iteración
            combustibleActual -= consumoSegmento;
            pesoActual = pesoFinal;
            
            // Registrar consumo en el segmento
            segmento.setCombustibleConsumidoGalones(consumoSegmento);
        }
        
        // Verificar margen de seguridad final
        double margenMinimo = CAPACIDAD_TANQUE_GALONES * (MARGEN_SEGURIDAD_PORCENTAJE / 100.0);
        if (combustibleActual < margenMinimo) {
            resultado.setAdvertencia(String.format(
                "Margen de seguridad bajo. Combustible restante: %.2f gal (recomendado: %.2f gal)",
                combustibleActual, margenMinimo));
        }
        
        resultado.setCombustibleFinal(combustibleActual);
        resultado.setCombustibleTotalConsumido(CAPACIDAD_TANQUE_GALONES - combustibleActual);
        
        logger.debug("Validación completada. Combustible restante: {:.2f} gal", combustibleActual);
        return resultado;
    }







    /**
     * Calcula el consumo de combustible para un segmento específico
     * Fórmula: Distancia[Km] × Peso[Ton] ÷ 180
     */
    public double calcularConsumoSegmento(SegmentoRuta segmento) {
        if (segmento.getDistanciaKm() <= 0 || segmento.getPesoInicialTon() <= 0) {
            return 0.0;
        }
        
        // Usar peso promedio para mayor precisión
        double pesoPromedio = (segmento.getPesoInicialTon() + segmento.getPesoFinalTon()) / 2.0;
        double consumo = (segmento.getDistanciaKm() * pesoPromedio) / FACTOR_CONSUMO;
        
        // Aplicar margen de seguridad
        double margen = consumo * (MARGEN_SEGURIDAD_PORCENTAJE / 100.0);
        
        return consumo + margen;
    }
    
    /**
     * Calcula el peso inicial del camión con su carga completa
     */
    public double calcularPesoInicialCamion(Camion camion, List<Entrega> entregas) {
        double pesoTara = obtenerPesoTara(camion);
        double volumenTotalCarga = entregas.stream()
            .mapToDouble(Entrega::getVolumenEntregadoM3)
            .sum();
        
        double pesoCarga = calcularPesoCarga(camion, volumenTotalCarga);
        
        return pesoTara + pesoCarga;
    }
    
    /**
     * Calcula el peso de carga basado en volumen y tipo de camión
     */
    public double calcularPesoCarga(Camion camion, double volumenM3) {
        double pesoMaximoCarga = obtenerPesoMaximoCarga(camion);
        double capacidadMaxima = camion.getMaxCargaM3();
        
        if (capacidadMaxima <= 0) {
            return 0.0;
        }
        
        // Proporción: peso = (volumen / capacidad_máxima) * peso_máximo
        return (volumenM3 / capacidadMaxima) * pesoMaximoCarga;
    }
    
    /**
     * Calcula la distancia máxima que puede recorrer con la carga actual
     */
    public double calcularDistanciaMaxima(Camion camion, double volumenCargaM3, double combustibleDisponible) {
        double pesoTotal = calcularPesoInicialCamion(camion, 
            List.of(crearEntregaFicticia(volumenCargaM3)));
        
        if (pesoTotal <= 0) {
            return 0.0;
        }
        
        // Despejar fórmula: Distancia = Combustible × FACTOR_CONSUMO ÷ Peso
        return (combustibleDisponible * FACTOR_CONSUMO) / pesoTotal;
    }
    
    /**
     * Verifica si es posible llegar a un destino específico
     */
    public boolean puedeAlcanzarDestino(Punto origen, Punto destino, 
                                       Camion camion, double volumenCargaM3, 
                                       double combustibleDisponible) {
        
        int distanciaGrid = origen.distanciaManhattanHasta(destino);
        double distanciaKm = distanciaGrid * FACTOR_CONVERSION_KM;
        
        double pesoTotal = calcularPesoInicialCamion(camion,
            List.of(crearEntregaFicticia(volumenCargaM3)));
        
        double consumoNecesario = (distanciaKm * pesoTotal) / FACTOR_CONSUMO;
        double consumoConMargen = consumoNecesario * (1 + MARGEN_SEGURIDAD_PORCENTAJE / 100.0);
        
        return combustibleDisponible >= consumoConMargen;
    }
    
    /**
     * Encuentra el almacén más conveniente para retorno con criterios avanzados
     */
    public Almacen encontrarMejorAlmacenRetorno(Punto posicionActual, List<Almacen> almacenesDisponibles,
                                            Camion camion, double combustibleDisponible) {
        
        if (almacenesDisponibles == null || almacenesDisponibles.isEmpty()) {
            logger.warn("No hay almacenes disponibles para retorno");
            return null;
        }
        
        logger.debug("Evaluando {} almacenes para retorno desde posición {}", 
            almacenesDisponibles.size(), posicionActual);
        
        Almacen mejorAlmacen = null;
        double mejorScore = Double.MIN_VALUE;
        
        for (Almacen almacen : almacenesDisponibles) {
            double score = evaluarAlmacenRetorno(posicionActual, almacen, camion, combustibleDisponible);
            
            if (score > mejorScore) {
                mejorScore = score;
                mejorAlmacen = almacen;
            }
            
            logger.debug("Almacén {}: score = {:.3f}", almacen.getId(), score);
        }
        
        if (mejorAlmacen != null) {
            logger.debug("Seleccionado almacén {} para retorno con score {:.3f}", 
                mejorAlmacen.getId(), mejorScore);
        } else {
            logger.warn("Ningún almacén es viable para retorno");
        }
        
        return mejorAlmacen;
    }

    /**
     * Evalúa un almacén específico para retorno considerando múltiples criterios
     */
    private double evaluarAlmacenRetorno(Punto posicionActual, Almacen almacen, 
                                    Camion camion, double combustibleDisponible) {
        
        Punto posicionAlmacen = new Punto(almacen.getX(), almacen.getY());
        
        // 1. CRITERIO DE VIABILIDAD (ELIMINA OPCIONES NO ALCANZABLES)
        if (!puedeAlcanzarDestino(posicionActual, posicionAlmacen, camion, 0.0, combustibleDisponible)) {
            return Double.MIN_VALUE; // No alcanzable = score mínimo
        }
        
        // 2. CRITERIO DE DISTANCIA (30% del score)
        double distancia = posicionActual.distanciaManhattanHasta(posicionAlmacen);
        double scoreDistancia = 1.0 / (distancia + 1); // Inverso de la distancia
        
        // 3. CRITERIO DE CAPACIDAD DEL ALMACÉN (25% del score)
        double scoreCapacidad = Math.min(almacen.getCapacidad() / 1000.0, 1.0); // Normalizado a [0,1]
        
        // 4. CRITERIO DE COMBUSTIBLE RESTANTE DESPUÉS DEL RETORNO (20% del score)
        double consumoEstimado = calcularConsumoEstimadoRetorno(posicionActual, posicionAlmacen, camion);
        double combustibleRestante = combustibleDisponible - consumoEstimado;
        double scoreCombustible = Math.max(0, combustibleRestante / CAPACIDAD_TANQUE_GALONES);
        
        // 5. CRITERIO DE PREFERENCIA POR ALMACÉN PRINCIPAL (15% del score)
        double scorePreferencia = almacen.getEsPrincipal() ? 1.0 : 0.6;
        
        // 6. CRITERIO DE ESTADO OPERACIONAL (10% del score)
        double scoreOperacional = evaluarEstadoOperacional(almacen);
        
        // SCORE COMPUESTO CON PESOS
        double scoreTotal = (scoreDistancia * 0.30) +
                        (scoreCapacidad * 0.25) +
                        (scoreCombustible * 0.20) +
                        (scorePreferencia * 0.15) +
                        (scoreOperacional * 0.10);
        
        return scoreTotal;
    }

    /**
     * Calcula consumo estimado para llegar al almacén de retorno
     */
    private double calcularConsumoEstimadoRetorno(Punto origen, Punto destino, Camion camion) {
        // Peso solo con tara del camión (sin carga)
        double pesoTara = obtenerPesoTara(camion);
        
        int distanciaGrid = origen.distanciaManhattanHasta(destino);
        double distanciaKm = distanciaGrid * FACTOR_CONVERSION_KM;
        
        // Fórmula: Distancia[Km] × Peso[Ton] ÷ 180
        double consumoBase = (distanciaKm * pesoTara) / FACTOR_CONSUMO;
        
        // Aplicar margen de seguridad
        return consumoBase * (1 + MARGEN_SEGURIDAD_PORCENTAJE / 100.0);
    }

    /**
     * Evalúa el estado operacional del almacén
     */
    private double evaluarEstadoOperacional(Almacen almacen) {
        // En implementación real, esto consultaría el estado actual del almacén
        // Por ahora, todos los almacenes se consideran operacionales
        
        // Factores que podrían influir:
        // - Horario de operación
        // - Estado de mantenimiento
        // - Capacidad disponible real
        // - Congestión actual
        
        // Simulación: almacenes principales tienen mejor score operacional
        if (almacen.getEsPrincipal()) {
            return 1.0; // 100% operacional
        } else {
            return 0.8; // 80% operacional para secundarios
        }
    }

    /**
     * Encuentra múltiples opciones de almacén para balanceo de carga
     */
    public List<AlmacenOpcionRetorno> obtenerOpcionesRetornoBalanceadas(Punto posicionActual, 
                                                                    List<Almacen> almacenesDisponibles,
                                                                    Camion camion, 
                                                                    double combustibleDisponible) {
        
        List<AlmacenOpcionRetorno> opciones = new ArrayList<>();
        
        for (Almacen almacen : almacenesDisponibles) {
            double score = evaluarAlmacenRetorno(posicionActual, almacen, camion, combustibleDisponible);
            
            if (score > 0) { // Solo almacenes viables
                AlmacenOpcionRetorno opcion = new AlmacenOpcionRetorno();
                opcion.setAlmacen(almacen);
                opcion.setScore(score);
                opcion.setDistanciaKm(posicionActual.distanciaManhattanHasta(new Punto(almacen.getX(), almacen.getY())) * FACTOR_CONVERSION_KM);
                opcion.setCombustibleRestanteEstimado(combustibleDisponible - calcularConsumoEstimadoRetorno(posicionActual, new Punto(almacen.getX(), almacen.getY()), camion));
                opciones.add(opcion);
            }
        }
        
        // Ordenar por score descendente
        opciones.sort((o1, o2) -> Double.compare(o2.getScore(), o1.getScore()));
        
        return opciones;
    }

    /**
     * Clase auxiliar para opciones de retorno
     */
    public static class AlmacenOpcionRetorno {
        private Almacen almacen;
        private double score;
        private double distanciaKm;
        private double combustibleRestanteEstimado;
        
        // Getters y setters
        public Almacen getAlmacen() { return almacen; }
        public void setAlmacen(Almacen almacen) { this.almacen = almacen; }
        
        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }
        
        public double getDistanciaKm() { return distanciaKm; }
        public void setDistanciaKm(double distanciaKm) { this.distanciaKm = distanciaKm; }
        
        public double getCombustibleRestanteEstimado() { return combustibleRestanteEstimado; }
        public void setCombustibleRestanteEstimado(double combustibleRestanteEstimado) { 
            this.combustibleRestanteEstimado = combustibleRestanteEstimado; 
        }
        
        @Override
        public String toString() {
            return String.format("Almacén %d: score=%.3f, dist=%.1fkm, combustible=%.1fgal",
                almacen.getId(), score, distanciaKm, combustibleRestanteEstimado);
        }
    }    


    // Métodos auxiliares privados
    
    private double obtenerPesoTara(Camion camion) {
        String tipo = camion.getTipo();
        if (tipo == null) return PESO_TARA_TD; // Valor por defecto más conservador
        
        return switch (tipo.toUpperCase()) {
            case "TA" -> PESO_TARA_TA;
            case "TB" -> PESO_TARA_TB;
            case "TC" -> PESO_TARA_TC;
            case "TD" -> PESO_TARA_TD;
            default -> PESO_TARA_TD;
        };
    }
    
    private double obtenerPesoMaximoCarga(Camion camion) {
        String tipo = camion.getTipo();
        if (tipo == null) return PESO_CARGA_MAXIMA_TD;
        
        return switch (tipo.toUpperCase()) {
            case "TA" -> PESO_CARGA_MAXIMA_TA;
            case "TB" -> PESO_CARGA_MAXIMA_TB;
            case "TC" -> PESO_CARGA_MAXIMA_TC;
            case "TD" -> PESO_CARGA_MAXIMA_TD;
            default -> PESO_CARGA_MAXIMA_TD;
        };
    }
    
    private Entrega crearEntregaFicticia(double volumen) {
        Entrega entrega = new Entrega();
        entrega.setVolumenEntregadoM3(volumen);
        return entrega;
    }
    
    /**
     * Clase para resultado de validación de combustible
     */
    public static class ResultadoValidacionCombustible {
        private boolean rutaViable;
        private double combustibleInicial;
        private double combustibleFinal;
        private double combustibleTotalConsumido;
        private double combustibleFaltante;
        private SegmentoRuta segmentoProblematico;
        private String mensajeError;
        private String advertencia;
        
        // Getters y setters
        public boolean isRutaViable() { return rutaViable; }
        public void setRutaViable(boolean rutaViable) { this.rutaViable = rutaViable; }
        
        public double getCombustibleInicial() { return combustibleInicial; }
        public void setCombustibleInicial(double combustibleInicial) { this.combustibleInicial = combustibleInicial; }
        
        public double getCombustibleFinal() { return combustibleFinal; }
        public void setCombustibleFinal(double combustibleFinal) { this.combustibleFinal = combustibleFinal; }
        
        public double getCombustibleTotalConsumido() { return combustibleTotalConsumido; }
        public void setCombustibleTotalConsumido(double combustibleTotalConsumido) { this.combustibleTotalConsumido = combustibleTotalConsumido; }
        
        public double getCombustibleFaltante() { return combustibleFaltante; }
        public void setCombustibleFaltante(double combustibleFaltante) { this.combustibleFaltante = combustibleFaltante; }
        
        public SegmentoRuta getSegmentoProblematico() { return segmentoProblematico; }
        public void setSegmentoProblematico(SegmentoRuta segmentoProblematico) { this.segmentoProblematico = segmentoProblematico; }
        
        public String getMensajeError() { return mensajeError; }
        public void setMensajeError(String mensajeError) { this.mensajeError = mensajeError; }
        
        public String getAdvertencia() { return advertencia; }
        public void setAdvertencia(String advertencia) { this.advertencia = advertencia; }
    }
    /**
     * Resultado completo de selección de almacén con fallback strategies
     */
    public static class ResultadoSeleccionAlmacen {
        private Almacen almacenSeleccionado;
        private TipoResultadoSeleccion tipoResultado;
        private String mensajeAlerta;
        private List<Entrega> entregasReducidas;
        private double combustibleRestanteEstimado;
        private List<AlmacenOpcionRetorno> opcionesAlternativas;
        private boolean requiereIntervencionOperador;
        
        // Getters y setters completos...
        public Almacen getAlmacenSeleccionado() { return almacenSeleccionado; }
        public void setAlmacenSeleccionado(Almacen almacenSeleccionado) { this.almacenSeleccionado = almacenSeleccionado; }
        
        public TipoResultadoSeleccion getTipoResultado() { return tipoResultado; }
        public void setTipoResultado(TipoResultadoSeleccion tipoResultado) { this.tipoResultado = tipoResultado; }
        
        public String getMensajeAlerta() { return mensajeAlerta; }
        public void setMensajeAlerta(String mensajeAlerta) { this.mensajeAlerta = mensajeAlerta; }
        
        public List<Entrega> getEntregasReducidas() { return entregasReducidas; }
        public void setEntregasReducidas(List<Entrega> entregasReducidas) { this.entregasReducidas = entregasReducidas; }
        
        public double getCombustibleRestanteEstimado() { return combustibleRestanteEstimado; }
        public void setCombustibleRestanteEstimado(double combustibleRestanteEstimado) { this.combustibleRestanteEstimado = combustibleRestanteEstimado; }
        
        public List<AlmacenOpcionRetorno> getOpcionesAlternativas() { return opcionesAlternativas; }
        public void setOpcionesAlternativas(List<AlmacenOpcionRetorno> opcionesAlternativas) { this.opcionesAlternativas = opcionesAlternativas; }
        
        public boolean isRequiereIntervencionOperador() { return requiereIntervencionOperador; }
        public void setRequiereIntervencionOperador(boolean requiereIntervencionOperador) { this.requiereIntervencionOperador = requiereIntervencionOperador; }
    }

    /**
     * Tipos de resultado para selección de almacén
     */
    public enum TipoResultadoSeleccion {
        EXITO_NORMAL("Almacén seleccionado normalmente"),
        EXITO_CON_ALERTAS("Almacén seleccionado con advertencias"),
        EMERGENCY_ENTREGAS_REDUCIDAS("Entregas reducidas para viabilidad"),
        EMERGENCY_SIN_OPCIONES("Sin almacenes alcanzables"),
        ERROR_CRITICO("Error crítico en selección");
        
        private final String descripcion;
        
        TipoResultadoSeleccion(String descripcion) {
            this.descripcion = descripcion;
        }
        
        public String getDescripcion() { return descripcion; }
    }

    /**
     * Selector de almacén con fallback strategies completas
     * Implementa toda la lógica de emergencia y backup plans
     */
    public ResultadoSeleccionAlmacen seleccionarAlmacenConFallbacks(
            Punto posicionActual, 
            List<Almacen> almacenesDisponibles,
            Camion camion, 
            double combustibleDisponible,
            List<Entrega> entregasPendientes) {
        
        logger.info("Seleccionando almacén de retorno con fallback strategies");
        
        ResultadoSeleccionAlmacen resultado = new ResultadoSeleccionAlmacen();
        
        // PASO 1: Intentar selección normal
        Almacen almacenOptimal = encontrarMejorAlmacenRetorno(posicionActual, almacenesDisponibles, camion, combustibleDisponible);
        
        if (almacenOptimal != null) {
            // ✅ ÉXITO NORMAL
            resultado.setAlmacenSeleccionado(almacenOptimal);
            resultado.setTipoResultado(TipoResultadoSeleccion.EXITO_NORMAL);
            resultado.setCombustibleRestanteEstimado(
                combustibleDisponible - calcularConsumoEstimadoRetorno(posicionActual, 
                    new Punto(almacenOptimal.getX(), almacenOptimal.getY()), camion)
            );
            
            // Verificar si hay alertas tempranas
            if (resultado.getCombustibleRestanteEstimado() < CAPACIDAD_TANQUE_GALONES * 0.15) {
                resultado.setTipoResultado(TipoResultadoSeleccion.EXITO_CON_ALERTAS);
                resultado.setMensajeAlerta("Margen de combustible bajo al retornar: " + 
                    String.format("%.1f gal", resultado.getCombustibleRestanteEstimado()));
            }
            
            return resultado;
        }
        
        // PASO 2: BACKUP PLAN - Buscar almacén más cercano aunque no sea optimal
        logger.warn("No se encontró almacén optimal, iniciando backup plan");
        
        Almacen almacenMasCercano = encontrarAlmacenMasCercanoViable(posicionActual, almacenesDisponibles, camion, combustibleDisponible);
        
        if (almacenMasCercano != null) {
            resultado.setAlmacenSeleccionado(almacenMasCercano);
            resultado.setTipoResultado(TipoResultadoSeleccion.EXITO_CON_ALERTAS);
            resultado.setMensajeAlerta("Utilizando almacén subóptimo pero viable: " + almacenMasCercano.getId());
            resultado.setRequiereIntervencionOperador(true);
            return resultado;
        }
        
        // PASO 3: EMERGENCY MODE - Reducir entregas hasta hacer viable el retorno
        logger.error("Ningún almacén alcanzable, iniciando modo emergencia");
        
        ResultadoEmergencia emergencia = aplicarModoEmergencia(posicionActual, almacenesDisponibles, camion, combustibleDisponible, entregasPendientes);
        
        if (emergencia.isExitoso()) {
            resultado.setAlmacenSeleccionado(emergencia.getAlmacenViable());
            resultado.setTipoResultado(TipoResultadoSeleccion.EMERGENCY_ENTREGAS_REDUCIDAS);
            resultado.setEntregasReducidas(emergencia.getEntregasReducidas());
            resultado.setMensajeAlerta("EMERGENCIA: Reducidas " + 
                (entregasPendientes.size() - emergencia.getEntregasReducidas().size()) + 
                " entregas para viabilidad de retorno");
            resultado.setRequiereIntervencionOperador(true);
            return resultado;
        }
        
        // PASO 4: ERROR CRÍTICO - Sin opciones viables
        logger.error("ERROR CRÍTICO: Sin almacenes alcanzables ni opciones de emergencia");
        
        resultado.setTipoResultado(TipoResultadoSeleccion.ERROR_CRITICO);
        resultado.setMensajeAlerta("CRÍTICO: Sin opciones de retorno viables. Combustible insuficiente.");
        resultado.setRequiereIntervencionOperador(true);
        
        // Generar reporte de opciones evaluadas para debugging
        List<AlmacenOpcionRetorno> todasOpciones = obtenerOpcionesRetornoBalanceadas(
            posicionActual, almacenesDisponibles, camion, combustibleDisponible);
        resultado.setOpcionesAlternativas(todasOpciones);
        
        return resultado;
    }

    /**
     * Encuentra el almacén más cercano que sea mínimamente viable
     */
    private Almacen encontrarAlmacenMasCercanoViable(Punto posicionActual, 
                                                    List<Almacen> almacenesDisponibles,
                                                    Camion camion, 
                                                    double combustibleDisponible) {
        
        Almacen masCercano = null;
        double menorDistancia = Double.MAX_VALUE;
        
        for (Almacen almacen : almacenesDisponibles) {
            Punto posicionAlmacen = new Punto(almacen.getX(), almacen.getY());
            
            // Solo considerar si es alcanzable
            if (puedeAlcanzarDestino(posicionActual, posicionAlmacen, camion, 0.0, combustibleDisponible)) {
                double distancia = posicionActual.distanciaManhattanHasta(posicionAlmacen);
                
                if (distancia < menorDistancia) {
                    menorDistancia = distancia;
                    masCercano = almacen;
                }
            }
        }
        
        return masCercano;
    }

    /**
     * Aplica modo emergencia reduciendo entregas hasta hacer viable el retorno
     */
    private ResultadoEmergencia aplicarModoEmergencia(Punto posicionActual,
                                                    List<Almacen> almacenesDisponibles,
                                                    Camion camion,
                                                    double combustibleDisponible,
                                                    List<Entrega> entregasPendientes) {
        
        logger.warn("Aplicando modo emergencia - reduciendo entregas");
        
        ResultadoEmergencia resultado = new ResultadoEmergencia();
        
        // Ordenar entregas por prioridad descendente (mantener las más importantes)
        List<Entrega> entregasOrdenadas = new ArrayList<>(entregasPendientes);
        entregasOrdenadas.sort((e1, e2) -> Integer.compare(e2.getPedido().getPrioridad(), e1.getPedido().getPrioridad()));
        
        // Intentar con cada subconjunto de entregas, empezando por todas y reduciendo
        for (int numEntregas = entregasOrdenadas.size(); numEntregas >= 0; numEntregas--) {
            List<Entrega> entregasReducidas = entregasOrdenadas.subList(0, numEntregas);
            
            // Calcular combustible que se liberaría al no hacer estas entregas
            double volumenLiberado = entregasPendientes.stream()
                .skip(numEntregas)
                .mapToDouble(Entrega::getVolumenEntregadoM3)
                .sum();
            
            // Combustible ahorrado por menor peso
            double pesoLiberado = calcularPesoCarga(camion, volumenLiberado);
            double combustibleAhorrado = estimarCombustibleAhorradoPorPeso(pesoLiberado, posicionActual);
            
            double combustibleAjustado = combustibleDisponible + combustibleAhorrado;
            
            // Probar si ahora algún almacén es alcanzable
            Almacen almacenViable = encontrarMejorAlmacenRetorno(posicionActual, almacenesDisponibles, camion, combustibleAjustado);
            
            if (almacenViable != null) {
                resultado.setExitoso(true);
                resultado.setAlmacenViable(almacenViable);
                resultado.setEntregasReducidas(entregasReducidas);
                resultado.setCombustibleAhorrado(combustibleAhorrado);
                
                logger.warn("Modo emergencia exitoso: {} entregas mantenidas de {}", 
                    numEntregas, entregasPendientes.size());
                
                return resultado;
            }
        }
        
        // Si llegamos aquí, ni siquiera sin entregas es viable
        resultado.setExitoso(false);
        logger.error("Modo emergencia fallido: Ni sin entregas es posible alcanzar almacén");
        
        return resultado;
    }

    /**
     * Estima combustible ahorrado por reducción de peso
     */
    private double estimarCombustibleAhorradoPorPeso(double pesoReducido, Punto posicionActual) {
        // Estimación conservadora: asumir distancia promedio restante de 50 unidades grid
        double distanciaEstimada = 50 * FACTOR_CONVERSION_KM;
        
        // Combustible ahorrado = distancia × peso_reducido ÷ factor_consumo
        return (distanciaEstimada * pesoReducido) / FACTOR_CONSUMO;
    }

    /**
     * Clase auxiliar para resultados de modo emergencia
     */
    private static class ResultadoEmergencia {
        private boolean exitoso;
        private Almacen almacenViable;
        private List<Entrega> entregasReducidas;
        private double combustibleAhorrado;
        
        public ResultadoEmergencia() {
            this.entregasReducidas = new ArrayList<>();
        }
        
        // Getters y setters
        public boolean isExitoso() { return exitoso; }
        public void setExitoso(boolean exitoso) { this.exitoso = exitoso; }
        
        public Almacen getAlmacenViable() { return almacenViable; }
        public void setAlmacenViable(Almacen almacenViable) { this.almacenViable = almacenViable; }
        
        public List<Entrega> getEntregasReducidas() { return entregasReducidas; }
        public void setEntregasReducidas(List<Entrega> entregasReducidas) { this.entregasReducidas = entregasReducidas; }
        
        public double getCombustibleAhorrado() { return combustibleAhorrado; }
        public void setCombustibleAhorrado(double combustibleAhorrado) { this.combustibleAhorrado = combustibleAhorrado; }
    }
}