package com.plg.service.impl;

import com.plg.domain.*;
import com.plg.service.TransformadorDatosService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementación del transformador de datos
 * Convierte entre formatos manteniendo integridad
 */
@Service
public class TransformadorDatosServiceImpl implements TransformadorDatosService {
    
    private static final Logger logger = LoggerFactory.getLogger(TransformadorDatosServiceImpl.class);
    
    // Configuración de conversión geográfica (ejemplo para Lima, Perú)
    private static final double BASE_LATITUD = -12.0464; // Centro de Lima
    private static final double BASE_LONGITUD = -77.0428;
    private static final double FACTOR_GRID_A_GRADOS = 0.0001; // Factor de conversión
    
    @Override
    public Map<Camion, List<Punto>> convertirAsignacionesAPuntos(Map<Camion, AsignacionCamion> asignaciones) {
        logger.debug("Convirtiendo {} asignaciones a puntos", asignaciones.size());
        
        Map<Camion, List<Punto>> resultado = new HashMap<>();
        
        for (Map.Entry<Camion, AsignacionCamion> entry : asignaciones.entrySet()) {
            Camion camion = entry.getKey();
            AsignacionCamion asignacion = entry.getValue();
            
            List<Punto> puntos = asignacion.getEntregas().stream()
                .map(entrega -> new Punto(
                    entrega.getPedido().getUbicacionX(),
                    entrega.getPedido().getUbicacionY()
                ))
                .collect(Collectors.toList());
            
            resultado.put(camion, puntos);
        }
        
        logger.debug("Conversión completada: {} camiones con puntos asignados", resultado.size());
        return resultado;
    }
    
    @Override
    public List<Coordenada> convertirRutaACoordenadas(RutaOptimizada ruta) {
        logger.debug("Convirtiendo ruta de {} segmentos a coordenadas", ruta.getSegmentos().size());
        
        List<Coordenada> coordenadas = new ArrayList<>();
        
        // Agregar punto de origen (almacén inicial)
        if (ruta.getAlmacenOrigen() != null) {
            Coordenada origen = new Coordenada(
                ruta.getAlmacenOrigen().getX(),
                ruta.getAlmacenOrigen().getY(),
                "ORIGEN"
            );
            origen.setDescripcion("Almacén de origen: " + ruta.getAlmacenOrigen().getId());
            origen.getMetadatos().put("almacenId", ruta.getAlmacenOrigen().getId());
            origen.getMetadatos().put("capacidad", ruta.getAlmacenOrigen().getCapacidad());
            coordenadas.add(origen);
        }
        
        // Procesar cada segmento
        for (int i = 0; i < ruta.getSegmentos().size(); i++) {
            SegmentoRuta segmento = ruta.getSegmentos().get(i);
            
            // Determinar tipo de coordenada
            String tipo = switch (segmento.getTipoSegmento()) {
                case ENTREGA -> "ENTREGA";
                case RETORNO_ALMACEN -> "RETORNO";
                case MOVIMIENTO -> "MOVIMIENTO";
            };
            
            Coordenada coordenada = new Coordenada(
                segmento.getDestino().getX(),
                segmento.getDestino().getY(),
                tipo
            );
            
            // Agregar metadatos específicos
            coordenada.getMetadatos().put("orden", i + 1);
            coordenada.getMetadatos().put("distanciaKm", segmento.getDistanciaKm());
            coordenada.getMetadatos().put("tiempoEstimado", segmento.getTiempoEstimadoHoras());
            
            if (segmento.getEntrega() != null) {
                coordenada.setDescripcion("Entrega pedido: " + segmento.getEntrega().getPedido().getId());
                coordenada.getMetadatos().put("pedidoId", segmento.getEntrega().getPedido().getId());
                coordenada.getMetadatos().put("volumen", segmento.getEntrega().getVolumenEntregadoM3());
                coordenada.getMetadatos().put("prioridad", segmento.getEntrega().getPedido().getPrioridad());
            } else if (segmento.getAlmacen() != null) {
                coordenada.setDescripcion("Retorno almacén: " + segmento.getAlmacen().getId());
                coordenada.getMetadatos().put("almacenId", segmento.getAlmacen().getId());
            }
            
            coordenadas.add(coordenada);
        }
        
        logger.debug("Conversión completada: {} coordenadas generadas", coordenadas.size());
        return coordenadas;
    }
    
    @Override
    public List<Punto> convertirEntregasAPuntos(List<Entrega> entregas) {
        return entregas.stream()
            .map(entrega -> new Punto(
                entrega.getPedido().getUbicacionX(),
                entrega.getPedido().getUbicacionY()
            ))
            .collect(Collectors.toList());
    }
    
    @Override
    public Map<String, List<Entrega>> agruparEntregas(List<Entrega> entregas, CriterioAgrupacion criterio) {
        logger.debug("Agrupando {} entregas por criterio: {}", entregas.size(), criterio);
        
        return switch (criterio) {
            case POR_PRIORIDAD -> entregas.stream()
                .collect(Collectors.groupingBy(e -> 
                    categorizarPrioridad(e.getPedido().getPrioridad())));
                    
            case POR_VOLUMEN -> entregas.stream()
                .collect(Collectors.groupingBy(e -> 
                    categorizarVolumen(e.getVolumenEntregadoM3())));
                    
            case POR_UBICACION -> entregas.stream()
                .collect(Collectors.groupingBy(e -> 
                    categorizarUbicacion(e.getPedido().getUbicacionX(), e.getPedido().getUbicacionY())));
                    
            case POR_TIPO_ENTREGA -> entregas.stream()
                .collect(Collectors.groupingBy(e -> 
                    e.getTipoEntrega() != null ? e.getTipoEntrega() : "SIN_TIPO"));
                    
            case POR_CAMION_ASIGNADO -> entregas.stream()
                .collect(Collectors.groupingBy(e -> 
                    e.getCamion() != null ? "Camión_" + e.getCamion().getId() : "SIN_ASIGNAR"));
        };
    }
    
    @Override
    public CoordenadaGeografica convertirGridAGeografica(Punto punto) {
        // Conversión simple basada en offset y escalado
        double latitud = BASE_LATITUD + (punto.getY() * FACTOR_GRID_A_GRADOS);
        double longitud = BASE_LONGITUD + (punto.getX() * FACTOR_GRID_A_GRADOS);
        
        return new CoordenadaGeografica(latitud, longitud);
    }
    
    @Override
    public DatosNormalizados normalizarDatos(DatosOriginales datos) {
        logger.debug("Normalizando datos originales");
        
        DatosNormalizados resultado = new DatosNormalizados();
        
        // Normalizar pedidos
        if (datos.getPedidos() != null) {
            List<Pedido> pedidosNormalizados = datos.getPedidos().stream()
                .map(this::normalizarPedido)
                .collect(Collectors.toList());
            resultado.setPedidosNormalizados(pedidosNormalizados);
            resultado.getTransformacionesAplicadas().put("pedidosNormalizados", pedidosNormalizados.size());
        }
        
        // Normalizar camiones
        if (datos.getCamiones() != null) {
            List<Camion> camionesNormalizados = datos.getCamiones().stream()
                .map(this::normalizarCamion)
                .collect(Collectors.toList());
            resultado.setCamionesNormalizados(camionesNormalizados);
            resultado.getTransformacionesAplicadas().put("camionesNormalizados", camionesNormalizados.size());
        }
        
        // Normalizar almacenes
        if (datos.getAlmacenes() != null) {
            List<Almacen> almacenesNormalizados = datos.getAlmacenes().stream()
                .map(this::normalizarAlmacen)
                .collect(Collectors.toList());
            resultado.setAlmacenesNormalizados(almacenesNormalizados);
            resultado.getTransformacionesAplicadas().put("almacenesNormalizados", almacenesNormalizados.size());
        }
        
        resultado.getTransformacionesAplicadas().put("fechaNormalizacion", LocalDateTime.now().toString());
        
        logger.debug("Normalización completada");
        return resultado;
    }
    
    @Override
    public EstructuraJerarquicaRutas crearEstructuraJerarquica(Map<Camion, RutaOptimizada> rutas) {
        logger.debug("Creando estructura jerárquica para {} rutas", rutas.size());
        
        EstructuraJerarquicaRutas estructura = new EstructuraJerarquicaRutas();
        
        // Procesar cada ruta
        for (Map.Entry<Camion, RutaOptimizada> entry : rutas.entrySet()) {
            Camion camion = entry.getKey();
            RutaOptimizada ruta = entry.getValue();
            
            NodoRuta nodoRuta = new NodoRuta(camion);
            
            // Convertir segmentos
            for (SegmentoRuta segmento : ruta.getSegmentos()) {
                NodoSegmento nodoSegmento = new NodoSegmento();
                nodoSegmento.setOrigen(segmento.getOrigen());
                nodoSegmento.setDestino(segmento.getDestino());
                nodoSegmento.setTipoSegmento(segmento.getTipoSegmento().toString());
                nodoSegmento.setRutaDetallada(segmento.getRutaDetallada());
                
                // Propiedades del segmento
                nodoSegmento.getPropiedades().put("distanciaKm", segmento.getDistanciaKm());
                nodoSegmento.getPropiedades().put("tiempoEstimado", segmento.getTiempoEstimadoHoras());
                nodoSegmento.getPropiedades().put("orden", segmento.getOrdenEnRuta());
                
                if (segmento.getEntrega() != null) {
                    nodoSegmento.getPropiedades().put("pedidoId", segmento.getEntrega().getPedido().getId());
                    nodoSegmento.getPropiedades().put("volumen", segmento.getEntrega().getVolumenEntregadoM3());
                }
                
                nodoRuta.getSegmentos().add(nodoSegmento);
            }
            
            // Métricas del nodo
            nodoRuta.getMetricas().put("distanciaTotal", ruta.getDistanciaTotalKm());
            nodoRuta.getMetricas().put("tiempoTotal", ruta.getTiempoEstimadoHoras());
            nodoRuta.getMetricas().put("combustibleNecesario", ruta.getCombustibleNecesarioGalones());
            nodoRuta.getMetricas().put("numeroEntregas", ruta.getNumeroEntregas());
            nodoRuta.getMetricas().put("utilizacion", ruta.calcularPorcentajeUtilizacion());
            nodoRuta.getMetricas().put("esViable", ruta.esRutaFactible());
            
            estructura.getNodosPorCamion().put(camion, nodoRuta);
        }
        
        // Calcular métricas globales
        MetricasGlobales metricasGlobales = calcularMetricasGlobalesInternas(rutas);
        estructura.setMetricasGlobales(metricasGlobales);
        
        logger.debug("Estructura jerárquica creada con {} nodos", estructura.getNodosPorCamion().size());
        return estructura;
    }
    
    @Override
    public MetricasAgregadas calcularMetricasAgregadas(Map<Camion, RutaOptimizada> rutas) {
        logger.debug("Calculando métricas agregadas para {} rutas", rutas.size());
        
        MetricasAgregadas metricas = new MetricasAgregadas();
        
        if (rutas.isEmpty()) {
            return metricas;
        }
        
        // Métricas básicas
        metricas.setTotalCamiones(rutas.size());
        
        metricas.setDistanciaTotalKm(rutas.values().stream()
            .mapToDouble(RutaOptimizada::getDistanciaTotalKm)
            .sum());
            
        metricas.setTiempoTotalHoras(rutas.values().stream()
            .mapToDouble(RutaOptimizada::getTiempoEstimadoHoras)
            .sum());
            
        metricas.setCombustibleTotalGalones(rutas.values().stream()
            .mapToDouble(RutaOptimizada::getCombustibleNecesarioGalones)
            .sum());
            
        metricas.setTotalEntregas(rutas.values().stream()
            .mapToInt(RutaOptimizada::getNumeroEntregas)
            .sum());
            
        metricas.setEficienciaPromedio(rutas.values().stream()
            .mapToDouble(RutaOptimizada::calcularPorcentajeUtilizacion)
            .average()
            .orElse(0.0));
        
        // Métricas detalladas
        metricas.getMetricasDetalladas().put("rutasViables", 
            rutas.values().stream().filter(RutaOptimizada::esRutaFactible).count());
        metricas.getMetricasDetalladas().put("rutasProblematicas", 
            rutas.values().stream().filter(r -> !r.esRutaFactible()).count());
        metricas.getMetricasDetalladas().put("distanciaPromedioPorCamion", 
            metricas.getDistanciaTotalKm() / metricas.getTotalCamiones());
        metricas.getMetricasDetalladas().put("entregasPromedioPorCamion", 
            (double) metricas.getTotalEntregas() / metricas.getTotalCamiones());
        
        return metricas;
    }
    
    @Override
    public ResumenEjecutivo generarResumenEjecutivo(Map<Camion, AsignacionCamion> asignaciones,
                                                   Map<Camion, RutaOptimizada> rutas) {
        
        logger.debug("Generando resumen ejecutivo");
        
        ResumenEjecutivo resumen = new ResumenEjecutivo();
        
        // Estadísticas básicas
        if (asignaciones != null) {
            int totalPedidos = asignaciones.values().stream()
                .mapToInt(AsignacionCamion::obtenerNumeroEntregas)
                .sum();
            resumen.setTotalPedidosAtendidos(totalPedidos);
        }
        
        if (rutas != null) {
            resumen.setTotalCamionesUtilizados(rutas.size());
            
            double tiempoTotal = rutas.values().stream()
                .mapToDouble(RutaOptimizada::getTiempoEstimadoHoras)
                .sum();
            resumen.setTiempoTotalOperacion(tiempoTotal);
            
            // Estimación de coste (simplificada)
            double combustibleTotal = rutas.values().stream()
                .mapToDouble(RutaOptimizada::getCombustibleNecesarioGalones)
                .sum();
            double costoEstimado = combustibleTotal * 4.5; // Precio galón estimado
            resumen.setCosteTotalEstimado(costoEstimado);
            
            // Determinar estado general
            long rutasViables = rutas.values().stream()
                .filter(RutaOptimizada::esRutaFactible)
                .count();
            
            if (rutasViables == rutas.size()) {
                resumen.setEstadoGeneral("EXCELENTE - Todas las rutas son viables");
            } else if (rutasViables >= rutas.size() * 0.8) {
                resumen.setEstadoGeneral("BUENO - Mayoría de rutas viables");
            } else if (rutasViables >= rutas.size() * 0.5) {
                resumen.setEstadoGeneral("REGULAR - Algunas rutas problemáticas");
            } else {
                resumen.setEstadoGeneral("CRÍTICO - Muchas rutas no viables");
            }
        }
        
        // KPIs clave
        if (asignaciones != null && rutas != null) {
            double eficienciaPromedio = rutas.values().stream()
                .mapToDouble(RutaOptimizada::calcularPorcentajeUtilizacion)
                .average()
                .orElse(0.0);
            
            resumen.getKpisClaves().put("eficienciaFlota", String.format("%.1f%%", eficienciaPromedio));
            resumen.getKpisClaves().put("costoPorEntrega", 
                resumen.getTotalPedidosAtendidos() > 0 ? 
                    resumen.getCosteTotalEstimado() / resumen.getTotalPedidosAtendidos() : 0);
            resumen.getKpisClaves().put("tiempoPorEntrega", 
                resumen.getTotalPedidosAtendidos() > 0 ? 
                    resumen.getTiempoTotalOperacion() / resumen.getTotalPedidosAtendidos() : 0);
        }
        
        // Recomendaciones básicas
        generarRecomendaciones(resumen, asignaciones, rutas);
        
        return resumen;
    }
    
    // Métodos auxiliares privados
    
    private String categorizarPrioridad(int prioridad) {
        if (prioridad >= 700) return "ALTA";
        if (prioridad >= 400) return "MEDIA";
        return "BAJA";
    }
    
    private String categorizarVolumen(double volumen) {
        if (volumen >= 10.0) return "GRANDE";
        if (volumen >= 5.0) return "MEDIANO";
        return "PEQUEÑO";
    }
    
    private String categorizarUbicacion(int x, int y) {
        // Dividir grid en cuadrantes
        if (x < 500 && y < 500) return "SUROESTE";
        if (x >= 500 && y < 500) return "SURESTE";
        if (x < 500 && y >= 500) return "NOROESTE";
        return "NORESTE";
    }
    
    private Pedido normalizarPedido(Pedido pedido) {
        Pedido normalizado = pedido.clone();
        
        // Asegurar coordenadas válidas
        if (normalizado.getUbicacionX() == null) normalizado.setUbicacionX(0);
        if (normalizado.getUbicacionY() == null) normalizado.setUbicacionY(0);
        
        // Asegurar volumen positivo
        if (normalizado.getVolumenM3() <= 0) normalizado.setVolumenM3(0.1);
        
        // Asegurar prioridad válida
        if (normalizado.getPrioridad() <= 0) normalizado.setPrioridad(100);
        
        return normalizado;
    }
    
    private Camion normalizarCamion(Camion camion) {
        // Los camiones generalmente no necesitan normalización
        // pero aquí se podrían aplicar validaciones y correcciones
        return camion;
    }
    
    private Almacen normalizarAlmacen(Almacen almacen) {
        // Asegurar capacidad positiva
        if (almacen.getCapacidad() <= 0) {
            almacen.setCapacidad(100.0); // Capacidad por defecto
        }
        
        return almacen;
    }
    
    private MetricasGlobales calcularMetricasGlobalesInternas(Map<Camion, RutaOptimizada> rutas) {
        MetricasGlobales metricas = new MetricasGlobales();
        
        if (rutas.isEmpty()) {
            return metricas;
        }
        
        // Utilización promedio de flota
        double utilizacionPromedio = rutas.values().stream()
            .mapToDouble(RutaOptimizada::calcularPorcentajeUtilizacion)
            .average()
            .orElse(0.0);
        metricas.setUtilizacionPromedioFlota(utilizacionPromedio);
        
        // Densidad de rutas (entregas por kilómetro)
        double totalEntregas = rutas.values().stream()
            .mapToInt(RutaOptimizada::getNumeroEntregas)
            .sum();
        double totalDistancia = rutas.values().stream()
            .mapToDouble(RutaOptimizada::getDistanciaTotalKm)
            .sum();
        
        metricas.setDensidadRutas(totalDistancia > 0 ? totalEntregas / totalDistancia : 0.0);
        
        // Score de optimización (basado en eficiencia y viabilidad)
        long rutasViables = rutas.values().stream()
            .filter(RutaOptimizada::esRutaFactible)
            .count();
        double porcentajeViabilidad = (double) rutasViables / rutas.size();
        double scoreOptimizacion = (utilizacionPromedio / 100.0 + porcentajeViabilidad) / 2.0 * 100.0;
        metricas.setScoreOptimizacion(scoreOptimizacion);
        
        // Indicadores KPI adicionales
        metricas.getIndicadoresKPI().put("rutasViables", rutasViables);
        metricas.getIndicadoresKPI().put("rutasProblematicas", rutas.size() - rutasViables);
        metricas.getIndicadoresKPI().put("totalCamiones", rutas.size());
        metricas.getIndicadoresKPI().put("scoreCalidad", scoreOptimizacion >= 80 ? "EXCELENTE" : 
            scoreOptimizacion >= 60 ? "BUENO" : "MEJORABLE");
        
        return metricas;
    }
    
    private void generarRecomendaciones(ResumenEjecutivo resumen, 
                                       Map<Camion, AsignacionCamion> asignaciones,
                                       Map<Camion, RutaOptimizada> rutas) {
        
        if (rutas != null) {
            // Analizar eficiencia
            double eficienciaPromedio = rutas.values().stream()
                .mapToDouble(RutaOptimizada::calcularPorcentajeUtilizacion)
                .average()
                .orElse(0.0);
            
            if (eficienciaPromedio < 70.0) {
                resumen.getRecomendaciones().add(
                    "Optimizar carga de camiones - Eficiencia actual: " + String.format("%.1f%%", eficienciaPromedio));
            }
            
            // Analizar rutas problemáticas
            long rutasProblematicas = rutas.values().stream()
                .filter(r -> !r.esRutaFactible())
                .count();
            
            if (rutasProblematicas > 0) {
                resumen.getRecomendaciones().add(
                    "Revisar " + rutasProblematicas + " rutas con problemas de viabilidad");
            }
            
            // Analizar combustible
            double combustiblePromedio = rutas.values().stream()
                .mapToDouble(RutaOptimizada::getCombustibleRestanteGalones)
                .average()
                .orElse(0.0);
            
            if (combustiblePromedio < 5.0) {
                resumen.getRecomendaciones().add(
                    "Considerar rutas más cortas o reabastecimiento intermedio");
            }
        }
        
        if (resumen.getRecomendaciones().isEmpty()) {
            resumen.getRecomendaciones().add("Planificación óptima - No se detectaron problemas críticos");
        }
    }
}