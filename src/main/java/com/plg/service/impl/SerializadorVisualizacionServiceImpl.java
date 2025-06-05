package com.plg.service.impl;

import com.plg.domain.*;
import com.plg.dto.RutaVisualizacionDTO;
import com.plg.service.SerializadorVisualizacionService;
import com.plg.service.util.GestorObstaculos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementación del serializador para visualización
 * Optimiza datos para transferencia y renderizado frontend
 */
@Service
public class SerializadorVisualizacionServiceImpl implements SerializadorVisualizacionService {
    
    private static final Logger logger = LoggerFactory.getLogger(SerializadorVisualizacionServiceImpl.class);
    
    @Autowired
    private GestorObstaculos gestorObstaculos;
    
    private ConfiguracionSerializacion configuracion;
    private ConfiguracionVisualizacion configuracionVisualizacion;
    
    // Colores para diferentes elementos
    private static final Map<String, String> COLORES_TIPOS_CAMION = Map.of(
        "TA", "#FF6B6B", "TB", "#4ECDC4", "TC", "#45B7D1", "TD", "#96CEB4"
    );
    
    private static final Map<String, String> COLORES_PRIORIDAD = Map.of(
        "ALTA", "#FF4757", "MEDIA", "#FFA726", "BAJA", "#66BB6A"
    );
    
    public SerializadorVisualizacionServiceImpl() {
        this.configuracion = new ConfiguracionSerializacion();
        this.configuracionVisualizacion = new ConfiguracionVisualizacion();
    }
    
    @Override
    public List<RutaVisualizacionDTO> convertirRutasParaVisualizacion(Map<Camion, RutaOptimizada> rutas) {
        logger.debug("Convirtiendo {} rutas para visualización", rutas.size());
        
        List<RutaVisualizacionDTO> rutasDTO = new ArrayList<>();
        
        for (Map.Entry<Camion, RutaOptimizada> entry : rutas.entrySet()) {
            Camion camion = entry.getKey();
            RutaOptimizada ruta = entry.getValue();
            
            RutaVisualizacionDTO rutaDTO = convertirRutaIndividual(camion, ruta);
            rutasDTO.add(rutaDTO);
        }
        
        logger.debug("Conversión completada: {} rutas DTO generadas", rutasDTO.size());
        return rutasDTO;
    }
    
    @Override
    public RutaVisualizacionDTO convertirRutaIndividual(Camion camion, RutaOptimizada ruta) {
        RutaVisualizacionDTO dto = new RutaVisualizacionDTO();
        
        // Información básica del camión
        dto.setCamionId(camion.getId());
        dto.setCodigoCamion(camion.getCodigo());
        dto.setTipoCamion(camion.getTipo());
        dto.setCapacidadMaxima(camion.getMaxCargaM3());
        dto.setColor(COLORES_TIPOS_CAMION.getOrDefault(camion.getTipo(), "#999999"));
        
        // Información de la ruta
        dto.setDistanciaTotalKm(redondear(ruta.getDistanciaTotalKm()));
        dto.setTiempoEstimadoHoras(redondear(ruta.getTiempoEstimadoHoras()));
        dto.setCombustibleNecesario(redondear(ruta.getCombustibleNecesarioGalones()));
        dto.setCombustibleRestante(redondear(ruta.getCombustibleRestanteGalones()));
        dto.setNumeroEntregas(ruta.getNumeroEntregas());
        dto.setPorcentajeUtilizacion(redondear(ruta.calcularPorcentajeUtilizacion()));
        dto.setEsViable(ruta.esRutaFactible());
        
        // Estado de la ruta
        dto.setEstado(ruta.getEstado() != null ? ruta.getEstado().toString() : "PLANIFICADA");
        dto.setObservaciones(ruta.getObservaciones());
        
        // Conversión de segmentos
        List<RutaVisualizacionDTO.SegmentoVisualizacionDTO> segmentosDTO = new ArrayList<>();
        
        for (SegmentoRuta segmento : ruta.getSegmentos()) {
            RutaVisualizacionDTO.SegmentoVisualizacionDTO segmentoDTO = 
                new RutaVisualizacionDTO.SegmentoVisualizacionDTO();
            
            // Puntos del segmento
            segmentoDTO.setOrigen(new int[]{segmento.getOrigen().getX(), segmento.getOrigen().getY()});
            segmentoDTO.setDestino(new int[]{segmento.getDestino().getX(), segmento.getDestino().getY()});
            segmentoDTO.setTipoSegmento(segmento.getTipoSegmento().toString());
            segmentoDTO.setOrden(segmento.getOrdenEnRuta());
            
            // Métricas del segmento
            segmentoDTO.setDistanciaKm(redondear(segmento.getDistanciaKm()));
            segmentoDTO.setTiempoEstimadoHoras(redondear(segmento.getTiempoEstimadoHoras()));
            
            // Ruta detallada (optimizada para visualización)
            if (configuracionVisualizacion.isIncluirRutasDetalladas() && segmento.getRutaDetallada() != null) {
                List<int[]> rutaDetallada = optimizarRutaDetallada(segmento.getRutaDetallada());
                segmentoDTO.setRutaDetallada(rutaDetallada);
            }
            
            // Información específica según tipo
            if (segmento.getEntrega() != null) {
                Entrega entrega = segmento.getEntrega();
                segmentoDTO.setPedidoId(entrega.getPedido().getId());
                segmentoDTO.setVolumenEntrega(redondear(entrega.getVolumenEntregadoM3()));
                segmentoDTO.setPrioridad(entrega.getPedido().getPrioridad());
                segmentoDTO.setTipoEntrega(entrega.getTipoEntrega());
                
                // Color basado en prioridad
                String categoriaPrioridad = categorizarPrioridad(entrega.getPedido().getPrioridad());
                segmentoDTO.setColor(COLORES_PRIORIDAD.getOrDefault(categoriaPrioridad, "#757575"));
            } else if (segmento.getAlmacen() != null) {
                segmentoDTO.setAlmacenId(segmento.getAlmacen().getId());
                segmentoDTO.setColor("#9E9E9E"); // Gris para retornos
            }
            
            segmentosDTO.add(segmentoDTO);
        }
        
        dto.setSegmentos(segmentosDTO);
        
        // Metadatos adicionales
        Map<String, Object> metadatos = new HashMap<>();
        metadatos.put("fechaCreacion", formatearFecha(LocalDateTime.now()));
        metadatos.put("algoritmoUtilizado", "A* + Greedy + LocalSearch");
        metadatos.put("scoreOptimizacion", calcularScoreOptimizacion(ruta));
        dto.setMetadatos(metadatos);
        
        return dto;
    }
    
    @Override
    public PayloadVisualizacionCompleto generarPayloadCompleto(
            Map<Camion, RutaOptimizada> rutas,
            Map<String, Object> metricas,
            ConfiguracionVisualizacion configuracion) {
        
        logger.info("Generando payload completo de visualización");
        
        PayloadVisualizacionCompleto payload = new PayloadVisualizacionCompleto();
        
        // Rutas principales
        payload.setRutas(convertirRutasParaVisualizacion(rutas));
        
        // Obstáculos del mapa
        if (configuracion.isIncluirObstaculos()) {
            payload.setObstaculos(convertirObstaculosParaVisualizacion());
        }
        
        // Timeline de ejecución
        if (configuracion.isIncluirTimeline()) {
            payload.setTimeline(generarTimelineEjecucion(rutas));
        }
        
        // Widgets de métricas
        if (configuracion.isIncluirWidgets()) {
            payload.setWidgets(convertirMetricasParaWidgets(metricas));
        }
        
        // Metadatos
        MetadatosVisualizacion metadatos = new MetadatosVisualizacion();
        metadatos.setFechaGeneracion(formatearFecha(LocalDateTime.now()));
        
        // Configuración del grid
        if (gestorObstaculos.isMapaInicializado()) {
            metadatos.getConfiguracionGrid().put("minX", gestorObstaculos.getGridMinX());
            metadatos.getConfiguracionGrid().put("minY", gestorObstaculos.getGridMinY());
            metadatos.getConfiguracionGrid().put("maxX", gestorObstaculos.getGridMaxX());
            metadatos.getConfiguracionGrid().put("maxY", gestorObstaculos.getGridMaxY());
        }
        
        // Estadísticas generales
        metadatos.getEstadisticasGenerales().put("totalRutas", rutas.size());
        metadatos.getEstadisticasGenerales().put("totalEntregas", 
            rutas.values().stream().mapToInt(RutaOptimizada::getNumeroEntregas).sum());
        metadatos.getEstadisticasGenerales().put("distanciaTotal", 
            rutas.values().stream().mapToDouble(RutaOptimizada::getDistanciaTotalKm).sum());
        
        payload.setMetadatos(metadatos);
        
        logger.info("Payload completo generado exitosamente");
        return payload;
    }
    
    @Override
    public List<ObstaculoVisualizacionDTO> convertirObstaculosParaVisualizacion() {
        logger.debug("Convirtiendo obstáculos para visualización");
        
        List<ObstaculoVisualizacionDTO> obstaculos = new ArrayList<>();
        
        // Nota: Aquí necesitaríamos acceso a los obstáculos internos del GestorObstaculos
        // Por simplicidad, retornamos estructura vacía
        // En implementación real, se accedería a las estructuras internas
        
        logger.debug("Conversión de obstáculos completada: {} elementos", obstaculos.size());
        return obstaculos;
    }
    
    @Override
    public List<AlmacenVisualizacionDTO> convertirAlmacenesParaVisualizacion(List<Almacen> almacenes) {
        logger.debug("Convirtiendo {} almacenes para visualización", almacenes.size());
        
        return almacenes.stream()
            .map(this::convertirAlmacenIndividual)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<AsignacionVisualizacionDTO> convertirAsignacionesParaVisualizacion(
            Map<Camion, AsignacionCamion> asignaciones) {
        
        logger.debug("Convirtiendo {} asignaciones para visualización", asignaciones.size());
        
        return asignaciones.entrySet().stream()
            .map(entry -> convertirAsignacionIndividual(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());
    }
    
    @Override
    public TimelineEjecucionDTO generarTimelineEjecucion(Map<Camion, RutaOptimizada> rutas) {
        logger.debug("Generando timeline de ejecución para {} rutas", rutas.size());
        
        TimelineEjecucionDTO timeline = new TimelineEjecucionDTO();
        
        LocalDateTime ahora = LocalDateTime.now();
        timeline.setFechaInicio(formatearFecha(ahora));
        
        List<EventoTimelineDTO> eventos = new ArrayList<>();
        LocalDateTime tiempoAcumulado = ahora;
        
        for (Map.Entry<Camion, RutaOptimizada> entry : rutas.entrySet()) {
            Camion camion = entry.getKey();
            RutaOptimizada ruta = entry.getValue();
            
            // Evento de inicio
            EventoTimelineDTO eventoInicio = new EventoTimelineDTO();
            eventoInicio.setTipo("INICIO");
            eventoInicio.setDescripcion("Inicio ruta camión " + camion.getCodigo());
            eventoInicio.setHoraEstimada(formatearFecha(tiempoAcumulado));
            eventoInicio.setCamionId(camion.getId());
            eventoInicio.setEstado("PENDIENTE");
            
            if (ruta.getAlmacenOrigen() != null) {
                eventoInicio.setCoordenadas(new int[]{
                    ruta.getAlmacenOrigen().getX(), ruta.getAlmacenOrigen().getY()
                });
            }
            
            eventos.add(eventoInicio);
            
            // Eventos de entregas
            for (SegmentoRuta segmento : ruta.getSegmentos()) {
                if (segmento.getTipoSegmento() == com.plg.domain.enumeration.TipoSegmento.ENTREGA) {
                    tiempoAcumulado = tiempoAcumulado.plusMinutes((long)(segmento.getTiempoEstimadoHoras() * 60));
                    
                    EventoTimelineDTO eventoEntrega = new EventoTimelineDTO();
                    eventoEntrega.setTipo("ENTREGA");
                    eventoEntrega.setDescripcion("Entrega pedido " + 
                        (segmento.getEntrega() != null ? segmento.getEntrega().getPedido().getId() : "N/A"));
                    eventoEntrega.setHoraEstimada(formatearFecha(tiempoAcumulado));
                    eventoEntrega.setCamionId(camion.getId());
                    eventoEntrega.setCoordenadas(new int[]{
                        segmento.getDestino().getX(), segmento.getDestino().getY()
                    });
                    eventoEntrega.setEstado("PENDIENTE");
                    
                    eventos.add(eventoEntrega);
                }
            }
            
            // Evento de retorno
            SegmentoRuta ultimoSegmento = ruta.getSegmentos().get(ruta.getSegmentos().size() - 1);
            if (ultimoSegmento.getTipoSegmento() == com.plg.domain.enumeration.TipoSegmento.RETORNO_ALMACEN) {
                tiempoAcumulado = tiempoAcumulado.plusMinutes((long)(ultimoSegmento.getTiempoEstimadoHoras() * 60));
                
                EventoTimelineDTO eventoRetorno = new EventoTimelineDTO();
                eventoRetorno.setTipo("RETORNO");
                eventoRetorno.setDescripcion("Retorno a almacén");
                eventoRetorno.setHoraEstimada(formatearFecha(tiempoAcumulado));
                eventoRetorno.setCamionId(camion.getId());
                eventoRetorno.setCoordenadas(new int[]{
                    ultimoSegmento.getDestino().getX(), ultimoSegmento.getDestino().getY()
                });
                eventoRetorno.setEstado("PENDIENTE");
                
                eventos.add(eventoRetorno);
            }
        }
        
        // Ordenar eventos por tiempo
        eventos.sort(Comparator.comparing(EventoTimelineDTO::getHoraEstimada));
        timeline.setEventos(eventos);
        
        // Calcular duración total
        if (!eventos.isEmpty()) {
            timeline.setFechaFinEstimada(eventos.get(eventos.size() - 1).getHoraEstimada());
            
            double duracionTotalHoras = rutas.values().stream()
                .mapToDouble(RutaOptimizada::getTiempoEstimadoHoras)
                .max()
                .orElse(0.0);
            timeline.setDuracionTotalHoras(redondear(duracionTotalHoras));
        }
        
        return timeline;
    }
    
    @Override
    public List<WidgetMetricaDTO> convertirMetricasParaWidgets(Map<String, Object> metricas) {
        logger.debug("Convirtiendo métricas para widgets");
        
        List<WidgetMetricaDTO> widgets = new ArrayList<>();
        
        // Widget de camiones utilizados
        widgets.add(crearWidget("camiones-utilizados", "Camiones Utilizados", "NUMERO",
            metricas.getOrDefault("camionesUsados", 0), "unidades", "#4CAF50", "truck"));
        
        // Widget de utilización promedio
        widgets.add(crearWidget("utilizacion-promedio", "Utilización Promedio", "GAUGE",
            metricas.getOrDefault("utilizacionPromedio", 0.0), "%", "#2196F3", "gauge"));
        
        // Widget de entregas totales
        widgets.add(crearWidget("entregas-totales", "Total Entregas", "NUMERO",
            metricas.getOrDefault("totalEntregas", 0), "entregas", "#FF9800", "package"));
        
        // Widget de distancia total
        widgets.add(crearWidget("distancia-total", "Distancia Total", "NUMERO",
            metricas.getOrDefault("distanciaPromedioKm", 0.0), "km", "#9C27B0", "route"));
        
        // Widget de eficiencia
        Object eficiencia = metricas.get("eficienciaAsignacion");
        if (eficiencia instanceof Number) {
            widgets.add(crearWidget("eficiencia-asignacion", "Eficiencia Asignación", "GAUGE",
                ((Number) eficiencia).doubleValue(), "%", "#607D8B", "efficiency"));
        }
        
        return widgets;
    }
    
    @Override
    public PayloadVisualizacionCompleto optimizarPayload(PayloadVisualizacionCompleto payload, int nivelOptimizacion) {
        logger.debug("Optimizando payload con nivel: {}", nivelOptimizacion);
        
        PayloadVisualizacionCompleto payloadOptimizado = payload; // Shallow copy por simplicidad
        
        switch (nivelOptimizacion) {
            case 1: // Optimización básica
                optimizarRutasDetalladasBasico(payloadOptimizado);
                break;
            case 2: // Optimización media
                optimizarRutasDetalladasBasico(payloadOptimizado);
                optimizarMetadatos(payloadOptimizado);
                break;
            case 3: // Optimización agresiva
                optimizarRutasDetalladasBasico(payloadOptimizado);
                optimizarMetadatos(payloadOptimizado);
                optimizarTimeline(payloadOptimizado);
                break;
        }
        
        if (payloadOptimizado.getMetadatos() != null) {
            payloadOptimizado.getMetadatos().setNivelOptimizacion(nivelOptimizacion);
        }
        
        return payloadOptimizado;
    }
    
    @Override
    public void configurarSerializacion(ConfiguracionSerializacion configuracion) {
        this.configuracion = configuracion;
        logger.info("Configuración de serialización actualizada");
    }
    
    // Métodos auxiliares privados
    
    private AlmacenVisualizacionDTO convertirAlmacenIndividual(Almacen almacen) {
        AlmacenVisualizacionDTO dto = new AlmacenVisualizacionDTO();
        
        dto.setId(almacen.getId());
        dto.setX(almacen.getX());
        dto.setY(almacen.getY());
        dto.setCapacidad(redondear(almacen.getCapacidad()));
        dto.setCantidad(redondear(almacen.getCantidad()));
        dto.setTipo(almacen.getTipo());
        dto.setEsPrincipal(almacen.getEsPrincipal());
        dto.setEstado("OPERATIVO"); // Estado por defecto
        dto.setIcono(almacen.getEsPrincipal() ? "warehouse-main" : "warehouse");
        dto.setColor(almacen.getEsPrincipal() ? "#1976D2" : "#757575");
        
        return dto;
    }
    
    private AsignacionVisualizacionDTO convertirAsignacionIndividual(Camion camion, AsignacionCamion asignacion) {
        AsignacionVisualizacionDTO dto = new AsignacionVisualizacionDTO();
        
        dto.setCamionId(camion.getId());
        dto.setCodigoCamion(camion.getCodigo());
        dto.setTipoCamion(camion.getTipo());
        dto.setCapacidadUtilizada(redondear(asignacion.getCapacidadUtilizada()));
        dto.setCapacidadDisponible(redondear(asignacion.getCapacidadDisponible()));
        dto.setPorcentajeUtilizacion(redondear(asignacion.obtenerPorcentajeUtilizacion()));
        dto.setEstado(asignacion.tieneEntregas() ? "ASIGNADO" : "LIBRE");
        
        // Convertir entregas
        List<EntregaResumenDTO> entregasDTO = asignacion.getEntregas().stream()
            .map(this::convertirEntregaResumen)
            .collect(Collectors.toList());
        dto.setEntregas(entregasDTO);
        
        return dto;
    }
    
    private EntregaResumenDTO convertirEntregaResumen(Entrega entrega) {
        EntregaResumenDTO dto = new EntregaResumenDTO();
        
        dto.setPedidoId(entrega.getPedido().getId());
        dto.setX(entrega.getPedido().getUbicacionX());
        dto.setY(entrega.getPedido().getUbicacionY());
        dto.setVolumen(redondear(entrega.getVolumenEntregadoM3()));
        dto.setPrioridad(entrega.getPedido().getPrioridad());
        dto.setTipoEntrega(entrega.getTipoEntrega());
        
        return dto;
    }
    
    private WidgetMetricaDTO crearWidget(String id, String titulo, String tipo, Object valor, 
                                        String unidad, String color, String icono) {
        WidgetMetricaDTO widget = new WidgetMetricaDTO();
        widget.setId(id);
        widget.setTitulo(titulo);
        widget.setTipo(tipo);
        widget.setValor(valor instanceof Number ? redondear(((Number) valor).doubleValue()) : valor);
        widget.setUnidad(unidad);
        widget.setColor(color);
        widget.setIcono(icono);
        
        return widget;
    }
    
    private List<int[]> optimizarRutaDetallada(List<Punto> rutaCompleta) {
        if (rutaCompleta == null || rutaCompleta.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Si la ruta es muy larga, aplicar simplificación
        if (rutaCompleta.size() > configuracionVisualizacion.getMaxPuntosRuta()) {
            return simplificarRuta(rutaCompleta);
        }
        
        // Convertir a formato int[]
        return rutaCompleta.stream()
            .map(punto -> new int[]{punto.getX(), punto.getY()})
            .collect(Collectors.toList());
    }
    
    private List<int[]> simplificarRuta(List<Punto> rutaCompleta) {
        List<int[]> rutaSimplificada = new ArrayList<>();
        int paso = rutaCompleta.size() / configuracionVisualizacion.getMaxPuntosRuta();
        paso = Math.max(1, paso);
        
        for (int i = 0; i < rutaCompleta.size(); i += paso) {
            Punto punto = rutaCompleta.get(i);
            rutaSimplificada.add(new int[]{punto.getX(), punto.getY()});
        }
        
        // Asegurar que el último punto esté incluido
        if (!rutaCompleta.isEmpty()) {
            Punto ultimoPunto = rutaCompleta.get(rutaCompleta.size() - 1);
            rutaSimplificada.add(new int[]{ultimoPunto.getX(), ultimoPunto.getY()});
        }
        
        return rutaSimplificada;
    }
    
    private void optimizarRutasDetalladasBasico(PayloadVisualizacionCompleto payload) {
        // Reducir detalle de rutas para transferencia más rápida
        for (RutaVisualizacionDTO ruta : payload.getRutas()) {
            for (RutaVisualizacionDTO.SegmentoVisualizacionDTO segmento : ruta.getSegmentos()) {
                if (segmento.getRutaDetallada() != null && segmento.getRutaDetallada().size() > 50) {
                    List<int[]> rutaReducida = new ArrayList<>();
                    List<int[]> rutaOriginal = segmento.getRutaDetallada();
                    
                    // Tomar cada N puntos
                    int paso = rutaOriginal.size() / 20; // Máximo 20 puntos
                    paso = Math.max(1, paso);
                    
                    for (int i = 0; i < rutaOriginal.size(); i += paso) {
                        rutaReducida.add(rutaOriginal.get(i));
                    }
                    
                    segmento.setRutaDetallada(rutaReducida);
                }
            }
        }
    }
    
    private void optimizarMetadatos(PayloadVisualizacionCompleto payload) {
        // Simplificar metadatos para reducir tamaño
        if (payload.getMetadatos() != null) {
            payload.getMetadatos().getEstadisticasGenerales().clear();
            payload.getMetadatos().getEstadisticasGenerales().put("optimizado", true);
        }
    }
    
    private void optimizarTimeline(PayloadVisualizacionCompleto payload) {
        // Reducir eventos del timeline si hay demasiados
        if (payload.getTimeline() != null && payload.getTimeline().getEventos().size() > 100) {
            List<EventoTimelineDTO> eventosReducidos = payload.getTimeline().getEventos()
                .stream()
                .filter(evento -> !"MOVIMIENTO".equals(evento.getTipo()))
                .collect(Collectors.toList());
            payload.getTimeline().setEventos(eventosReducidos);
        }
    }
    
    private double redondear(double valor) {
        if (!configuracion.isFormatearFechas()) {
            return valor;
        }
        
        double factor = Math.pow(10, configuracion.getPrecisionDecimales());
        return Math.round(valor * factor) / factor;
    }
    
    private String formatearFecha(LocalDateTime fecha) {
        if (!configuracion.isFormatearFechas()) {
            return fecha.toString();
        }
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(configuracionVisualizacion.getFormatoFecha());
        return fecha.format(formatter);
    }
    
    private String categorizarPrioridad(int prioridad) {
        if (prioridad >= 700) return "ALTA";
        if (prioridad >= 400) return "MEDIA";
        return "BAJA";
    }
    
    private double calcularScoreOptimizacion(RutaOptimizada ruta) {
        double scoreViabilidad = ruta.esRutaFactible() ? 1.0 : 0.0;
        double scoreUtilizacion = ruta.calcularPorcentajeUtilizacion() / 100.0;
        double scoreCombustible = ruta.getCombustibleRestanteGalones() > 2.5 ? 1.0 : 0.5;
        
        return (scoreViabilidad + scoreUtilizacion + scoreCombustible) / 3.0 * 100.0;
    }
}