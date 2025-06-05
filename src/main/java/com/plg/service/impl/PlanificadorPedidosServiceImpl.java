package com.plg.service.impl;

import com.plg.domain.*;
import com.plg.domain.enumeration.EstadoPedido;
import com.plg.service.PlanificadorPedidosService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PlanificadorPedidosServiceImpl implements PlanificadorPedidosService {
    
    private static final Logger logger = LoggerFactory.getLogger(PlanificadorPedidosServiceImpl.class);
    
    // Configuración del algoritmo
    private static final double FRAGMENTO_MINIMO = 0.5; // m3 mínimo para fragmentar
    //private static final double UMBRAL_CASI_LLENO = 0.8; // 80% de capacidad
    private static final int PRIORIDAD_MAXIMA = 1000;
    
    @Override
    public Map<Camion, AsignacionCamion> planificarPedidos(List<Pedido> pedidos, List<Camion> camiones) {
        logger.info("Iniciando planificación para {} pedidos y {} camiones", pedidos.size(), camiones.size());
        
        // 1. Calcular prioridades y ordenar pedidos
        List<Pedido> pedidosOrdenados = ordenarPedidosPorPrioridad(pedidos);
        
        // 2. Inicializar asignaciones de camiones
        List<AsignacionCamion> asignaciones = camiones.stream()
            .map(AsignacionCamion::new)
            .collect(Collectors.toList());
        
        // 3. Algoritmo principal de asignación
        for (Pedido pedido : pedidosOrdenados) {
            List<Entrega> entregas = asignarPedido(pedido, asignaciones);
            
            if (entregas.isEmpty()) {
                logger.warn("No se pudo asignar el pedido {} - sin capacidad disponible", pedido.getId());
            } else {
                logger.debug("Pedido {} asignado en {} entregas", pedido.getId(), entregas.size());
            }
        }
        // 🔍 LOG ESTADO FINAL DE ASIGNACIONES
        logger.info("🚛 ESTADO FINAL DE ASIGNACIONES:");
        for (int i = 0; i < asignaciones.size(); i++) {
            AsignacionCamion asignacion = asignaciones.get(i);
            logger.info("   Camión {} ({}): {} entregas - Utilizado: {:.1f}/{} m³ ({:.1f}%)",
                asignacion.getCamion().getCodigo(),
                asignacion.getCamion().getTipo(),
                asignacion.obtenerNumeroEntregas(),
                asignacion.getCapacidadUtilizada(),
                asignacion.getCamion().getMaxCargaM3(),
                asignacion.obtenerPorcentajeUtilizacion());
                
            // 🔍 LOG ENTREGAS POR CAMIÓN
            if (asignacion.tieneEntregas()) {
                logger.info("     Entregas en este camión:");
                for (int j = 0; j < asignacion.getEntregas().size(); j++) {
                    Entrega e = asignacion.getEntregas().get(j);
                    logger.info("       [{}] Pedido {} - Vol: {:.1f} m³", 
                        j+1, e.getPedido().getId(), e.getVolumenEntregadoM3());
                }
            } else {
                logger.info("     ⚠️ Sin entregas asignadas");
            }
        }
        
        // 4. Optimizar asignaciones (opcional)
        asignaciones = optimizarAsignaciones(asignaciones);
        
        // 5. Convertir a mapa resultado
        Map<Camion, AsignacionCamion> resultado = asignaciones.stream()
            .filter(AsignacionCamion::tieneEntregas)
            .collect(Collectors.toMap(AsignacionCamion::getCamion, asignacion -> asignacion));
        
        logger.info("Planificación completada. {} camiones asignados", resultado.size());
        return resultado;
    }
    
    @Override
    public List<Entrega> asignarPedido(Pedido pedido, List<AsignacionCamion> asignaciones) {
        List<Entrega> entregasCreadas = new ArrayList<>();
        double volumenRestante = pedido.getVolumenM3();
        
        // Ordenar asignaciones por criterio compuesto: prioridad + capacidad disponible
        List<AsignacionCamion> asignacionesOrdenadas = ordenarAsignacionesPorCriterio(asignaciones);
        
        for (AsignacionCamion asignacion : asignacionesOrdenadas) {
            if (volumenRestante <= 0) break;
            
            double capacidadDisponible = asignacion.getCapacidadDisponible();
            
            if (capacidadDisponible < FRAGMENTO_MINIMO) {
                continue; // No vale la pena usar esta capacidad
            }
            
            double volumenParaAsignar = Math.min(volumenRestante, capacidadDisponible);
            
            // Crear entrega
            Entrega entrega = crearEntrega(pedido, asignacion.getCamion(), volumenParaAsignar);
            
            if (asignacion.agregarEntrega(entrega)) {
                entregasCreadas.add(entrega);
                volumenRestante -= volumenParaAsignar;
                
                logger.debug("Entrega creada: {} m3 del pedido {} al camión {}", 
                    volumenParaAsignar, pedido.getId(), asignacion.getCamion().getId());
            }
        }
        
        // Si queda volumen sin asignar, intentar optimización adicional
        if (volumenRestante > 0) {
            entregasCreadas.addAll(manejarVolumenRestante(pedido, volumenRestante, asignaciones));
        }
        
        return entregasCreadas;
    }
    
    @Override
    public int calcularPrioridad(Pedido pedido) {
        if (pedido.getFechaHoraRegistro() == null || pedido.getHorasLimite() == null) {
            return 1; // Prioridad mínima si faltan datos
        }
        
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime fechaLimite = pedido.getFechaHoraRegistro().plusHours(pedido.getHorasLimite());
        
        long horasRestantes = ChronoUnit.HOURS.between(ahora, fechaLimite);
        
        if (horasRestantes <= 0) {
            return PRIORIDAD_MAXIMA; // Máxima prioridad para pedidos vencidos
        }
        
        if (horasRestantes <= 1) {
            return 900; // Muy urgente
        } else if (horasRestantes <= 4) {
            return 700; // Urgente
        } else if (horasRestantes <= 12) {
            return 500; // Normal alto
        } else if (horasRestantes <= 24) {
            return 300; // Normal
        } else {
            return 100; // Baja prioridad
        }
    }
    
    @Override
    public List<Entrega> fragmentarPedido(Pedido pedido, List<Double> volumenesDisponibles) {
        List<Entrega> fragmentos = new ArrayList<>();
        double volumenRestante = pedido.getVolumenM3();
        
        // Ordenar volúmenes disponibles de mayor a menor (Best Fit Decreasing)
        List<Double> volumenesOrdenados = volumenesDisponibles.stream()
            .filter(v -> v >= FRAGMENTO_MINIMO)
            .sorted(Comparator.reverseOrder())
            .collect(Collectors.toList());
        
        int indiceVolumen = 0;
        while (volumenRestante > 0 && indiceVolumen < volumenesOrdenados.size()) {
            double volumenDisponible = volumenesOrdenados.get(indiceVolumen);
            double volumenFragmento = Math.min(volumenRestante, volumenDisponible);
            
            if (volumenFragmento >= FRAGMENTO_MINIMO) {
                // Crear fragmento (sin camión específico en esta etapa)
                Entrega fragmento = new Entrega();
                fragmento.setPedido(pedido);
                fragmento.setVolumenEntregadoM3(volumenFragmento);
                fragmento.setEstado(EstadoPedido.PENDIENTE);
                fragmento.setTipoEntrega(volumenFragmento >= pedido.getVolumenM3() ? "COMPLETA" : "PARCIAL");
                
                fragmentos.add(fragmento);
                volumenRestante -= volumenFragmento;
            }
            
            indiceVolumen++;
        }
        
        return fragmentos;
    }
    
    @Override
    public List<AsignacionCamion> optimizarAsignaciones(List<AsignacionCamion> asignaciones) {
        logger.debug("Iniciando optimización de asignaciones");
        
        // Algoritmo de optimización local: intercambio de entregas entre camiones
        boolean mejoraEncontrada = true;
        int iteraciones = 0;
        final int MAX_ITERACIONES = 10;
        
        while (mejoraEncontrada && iteraciones < MAX_ITERACIONES) {
            mejoraEncontrada = false;
            iteraciones++;
            
            for (int i = 0; i < asignaciones.size(); i++) {
                for (int j = i + 1; j < asignaciones.size(); j++) {
                    if (intentarIntercambio(asignaciones.get(i), asignaciones.get(j))) {
                        mejoraEncontrada = true;
                    }
                }
            }
        }
        
        logger.debug("Optimización completada en {} iteraciones", iteraciones);
        return asignaciones;
    }
    
    @Override
    public Map<String, Object> obtenerEstadisticasPlanificacion(List<AsignacionCamion> asignaciones) {
        Map<String, Object> estadisticas = new HashMap<>();
        
        int camionesUsados = (int) asignaciones.stream().filter(AsignacionCamion::tieneEntregas).count();
        double utilizacionPromedio = asignaciones.stream()
            .filter(AsignacionCamion::tieneEntregas)
            .mapToDouble(AsignacionCamion::obtenerPorcentajeUtilizacion)
            .average()
            .orElse(0.0);
        
        int totalEntregas = asignaciones.stream()
            .mapToInt(AsignacionCamion::obtenerNumeroEntregas)
            .sum();
        
        double volumenTotalAsignado = asignaciones.stream()
            .mapToDouble(AsignacionCamion::getCapacidadUtilizada)
            .sum();
        
        estadisticas.put("camionesUsados", camionesUsados);
        estadisticas.put("utilizacionPromedio", Math.round(utilizacionPromedio * 100.0) / 100.0);
        estadisticas.put("totalEntregas", totalEntregas);
        estadisticas.put("volumenTotalAsignado", volumenTotalAsignado);
        estadisticas.put("eficienciaAsignacion", calcularEficienciaAsignacion(asignaciones));
        
        return estadisticas;
    }
    
    // Métodos auxiliares privados
    
    private List<Pedido> ordenarPedidosPorPrioridad(List<Pedido> pedidos) {
        return pedidos.stream()
            .peek(pedido -> {
                // CORRECCIÓN: Solo calcular prioridad si no está ya establecida
                if (pedido.getPrioridad() == 0) {  // 0 es el valor por defecto
                    pedido.setPrioridad(calcularPrioridad(pedido));
                }
            })
            .sorted((p1, p2) -> Integer.compare(p2.getPrioridad(), p1.getPrioridad()))
            .collect(Collectors.toList());
    }
    
    private List<AsignacionCamion> ordenarAsignacionesPorCriterio(List<AsignacionCamion> asignaciones) {
        return asignaciones.stream()
            .sorted((a1, a2) -> {
                // Criterio 1: Prioridad promedio (descendente)
                int comparacionPrioridad = Integer.compare(a2.getPrioridadPromedio(), a1.getPrioridadPromedio());
                if (comparacionPrioridad != 0) return comparacionPrioridad;
                
                // Criterio 2: Capacidad disponible (descendente)
                return Double.compare(a2.getCapacidadDisponible(), a1.getCapacidadDisponible());
            })
            .collect(Collectors.toList());
    }
    
    private Entrega crearEntrega(Pedido pedido, Camion camion, double volumen) {
        Entrega entrega = new Entrega();
        entrega.setPedido(pedido);
        entrega.setCamion(camion);
        entrega.setVolumenEntregadoM3(volumen);
        entrega.setEstado(EstadoPedido.PENDIENTE);
        entrega.setFechaHoraRecepcion(LocalDateTime.now());
        entrega.setTipoEntrega(volumen >= pedido.getVolumenM3() ? "COMPLETA" : "PARCIAL");
        
        return entrega;
    }
    
    private List<Entrega> manejarVolumenRestante(Pedido pedido, double volumenRestante, List<AsignacionCamion> asignaciones) {
        List<Entrega> entregasAdicionales = new ArrayList<>();
        
        // Estrategia 1: Buscar camiones con capacidad pequeña pero útil
        for (AsignacionCamion asignacion : asignaciones) {
            if (volumenRestante <= 0) break;
            
            double capacidadMinima = Math.min(volumenRestante, FRAGMENTO_MINIMO);
            
            if (asignacion.getCapacidadDisponible() >= capacidadMinima) {
                double volumenParaAsignar = Math.min(volumenRestante, asignacion.getCapacidadDisponible());
                
                Entrega entrega = crearEntrega(pedido, asignacion.getCamion(), volumenParaAsignar);
                
                if (asignacion.agregarEntrega(entrega)) {
                    entregasAdicionales.add(entrega);
                    volumenRestante -= volumenParaAsignar;
                }
            }
        }
        
        if (volumenRestante > 0) {
            logger.warn("Quedaron {} m3 sin asignar del pedido {}", volumenRestante, pedido.getId());
        }
        
        return entregasAdicionales;
    }
    
    private boolean intentarIntercambio(AsignacionCamion asignacion1, AsignacionCamion asignacion2) {
        // Lógica simplificada de intercambio
        // En una implementación completa, se evaluarían todas las combinaciones posibles
        
        if (asignacion1.getEntregas().isEmpty() || asignacion2.getEntregas().isEmpty()) {
            return false;
        }
        
        // Por ahora, solo logueamos la posibilidad - implementación completa requiere más lógica
        logger.debug("Evaluando intercambio entre camiones {} y {}", 
            asignacion1.getCamion().getId(), asignacion2.getCamion().getId());
        
        return false; // Sin intercambio por simplicidad
    }
    
    private double calcularEficienciaAsignacion(List<AsignacionCamion> asignaciones) {
        if (asignaciones.isEmpty()) return 0.0;
        
        double sumaUtilizacion = asignaciones.stream()
            .filter(AsignacionCamion::tieneEntregas)
            .mapToDouble(AsignacionCamion::obtenerPorcentajeUtilizacion)
            .sum();
        
        long camionesUsados = asignaciones.stream()
            .filter(AsignacionCamion::tieneEntregas)
            .count();
        
        return camionesUsados > 0 ? sumaUtilizacion / camionesUsados : 0.0;
    }
    @Override
    public void reiniciar() {
        logger.info("Reiniciando PlanificadorPedidosService");
        // Aquí puedes agregar lógica de reinicio si es necesario
        // Por ejemplo, limpiar cachés, resetear contadores, etc.
        logger.info("PlanificadorPedidosService reiniciado");
    }
}