package com.plg.service.impl;

import com.plg.domain.*;
import com.plg.service.ValidadorDatosService;
import com.plg.service.util.GestorObstaculos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
/**
 * Implementación del validador de datos de entrada
 * Verifica integridad y coherencia antes del procesamiento
 */
@Service
public class ValidadorDatosServiceImpl implements ValidadorDatosService {
    
    private static final Logger logger = LoggerFactory.getLogger(ValidadorDatosServiceImpl.class);
    
    @Autowired
    private GestorObstaculos gestorObstaculos;
    
    // Configuración
    private ConfiguracionValidacion configuracion;
    
    // Tipos de camión válidos
    private static final Set<String> TIPOS_CAMION_VALIDOS = Set.of("TA", "TB", "TC", "TD");
    
    public ValidadorDatosServiceImpl() {
        this.configuracion = new ConfiguracionValidacion();
    }
    
    @Override
    public ResultadoValidacionPedido validarPedido(Pedido pedido) {
        logger.debug("Validando pedido: {}", pedido != null ? pedido.getId() : "null");
        
        ResultadoValidacionPedido resultado = new ResultadoValidacionPedido();
        
        if (pedido == null) {
            resultado.agregarError("Pedido es nulo");
            resultado.setPedidoValido(false);
            return resultado;
        }
        
        // Validar ID
        if (pedido.getId() == null) {
            resultado.agregarError("ID de pedido es nulo");
        }
        
        // Validar coordenadas
        if (pedido.getUbicacionX() == null || pedido.getUbicacionY() == null) {
            resultado.agregarError("Coordenadas de pedido son nulas");
        } else {
            if (!sonCoordenadasValidas(pedido.getUbicacionX(), pedido.getUbicacionY())) {
                resultado.agregarError(String.format("Coordenadas fuera de límites: (%d,%d)", 
                    pedido.getUbicacionX(), pedido.getUbicacionY()));
            }
            
            // Validar accesibilidad si está configurado
            if (configuracion.isValidarAccesibilidadPuntos()) {
                Punto punto = new Punto(pedido.getUbicacionX(), pedido.getUbicacionY());
                if (!esPuntoAccesible(punto)) {
                    resultado.agregarError("Ubicación de pedido no es accesible (obstruida)");
                }
            }
        }
        
        // Validar volumen
        if (pedido.getVolumenM3() <= 0) {
            resultado.agregarError("Volumen debe ser positivo: " + pedido.getVolumenM3());
        } else {
            if (pedido.getVolumenM3() < configuracion.getVolumenMinimoM3()) {
                resultado.agregarAdvertencia(String.format("Volumen muy pequeño: %.2f m³ (mínimo recomendado: %.2f m³)",
                    pedido.getVolumenM3(), configuracion.getVolumenMinimoM3()));
            }
            
            if (pedido.getVolumenM3() > configuracion.getVolumenMaximoM3()) {
                resultado.agregarError(String.format("Volumen excede máximo: %.2f m³ (máximo: %.2f m³)",
                    pedido.getVolumenM3(), configuracion.getVolumenMaximoM3()));
            }
        }
        
        // Validar tiempo límite
        if (pedido.getHorasLimite() != null && pedido.getHorasLimite() <= 0) {
            resultado.agregarAdvertencia("Horas límite debe ser positivo: " + pedido.getHorasLimite());
        }
        
        // Validar fecha de registro
        if (pedido.getFechaHoraRegistro() != null) {
            LocalDateTime ahora = LocalDateTime.now();
            if (pedido.getFechaHoraRegistro().isAfter(ahora)) {
                resultado.agregarAdvertencia("Fecha de registro en el futuro");
            }
            
            // Si hay tiempo límite, verificar que no esté vencido
            if (pedido.getHorasLimite() != null) {
                LocalDateTime fechaLimite = pedido.getFechaHoraRegistro().plusHours(pedido.getHorasLimite());
                if (fechaLimite.isBefore(ahora)) {
                    resultado.agregarAdvertencia("Pedido ya venció");
                }
            }
        }
        
        // Calcular metadatos
        resultado.getMetadatos().put("volumen", pedido.getVolumenM3());
        resultado.getMetadatos().put("coordenadas", String.format("(%d,%d)", 
            pedido.getUbicacionX(), pedido.getUbicacionY()));
        resultado.getMetadatos().put("prioridad", pedido.getPrioridad());
        
        // Determinar validez
        resultado.setPedidoValido(resultado.getErrores().isEmpty());
        
        return resultado;
    }
    
    @Override
    public Map<Pedido, ResultadoValidacionPedido> validarPedidos(List<Pedido> pedidos) {
        logger.debug("Validando {} pedidos", pedidos != null ? pedidos.size() : 0);
        
        Map<Pedido, ResultadoValidacionPedido> resultados = new HashMap<>();
        
        if (pedidos == null || pedidos.isEmpty()) {
            logger.warn("Lista de pedidos vacía o nula");
            return resultados;
        }
        
        for (Pedido pedido : pedidos) {
            ResultadoValidacionPedido resultado = validarPedido(pedido);
            resultados.put(pedido, resultado);
        }
        
        // Log resumen
        long pedidosValidos = resultados.values().stream()
            .filter(ResultadoValidacionPedido::isPedidoValido)
            .count();
        
        logger.debug("Validación pedidos completada: {}/{} válidos", pedidosValidos, pedidos.size());
        
        return resultados;
    }
    
    @Override
    public ResultadoValidacionCamion validarCamion(Camion camion) {
        logger.debug("Validando camión: {}", camion != null ? camion.getId() : "null");
        
        ResultadoValidacionCamion resultado = new ResultadoValidacionCamion();
        
        if (camion == null) {
            resultado.agregarError("Camión es nulo");
            resultado.setCamionValido(false);
            return resultado;
        }
        
        // Validar ID
        if (camion.getId() == null) {
            resultado.agregarError("ID de camión es nulo");
        }
        
        // Validar tipo
        if (camion.getTipo() == null || camion.getTipo().trim().isEmpty()) {
            resultado.agregarError("Tipo de camión es nulo o vacío");
        } else {
            String tipoUpper = camion.getTipo().toUpperCase();
            if (!TIPOS_CAMION_VALIDOS.contains(tipoUpper)) {
                resultado.agregarError("Tipo de camión inválido: " + camion.getTipo() + 
                    " (válidos: " + TIPOS_CAMION_VALIDOS + ")");
            }
        }
        
        // Validar capacidad
        if (camion.getMaxCargaM3() == null || camion.getMaxCargaM3() <= 0) {
            resultado.agregarError("Capacidad máxima debe ser positiva: " + camion.getMaxCargaM3());
        } else {
            // Calcular factor de eficiencia basado en tipo
            double factorEficiencia = calcularFactorEficiencia(camion);
            resultado.setFactorEficiencia(factorEficiencia);
            
            if (factorEficiencia < 0.7) {
                resultado.agregarAdvertencia("Camión con baja eficiencia: " + factorEficiencia);
            }
        }
        
        // Validar velocidad si está presente
        if (camion.getVelocidadKmph() != null && camion.getVelocidadKmph() <= 0) {
            resultado.agregarAdvertencia("Velocidad debe ser positiva: " + camion.getVelocidadKmph());
        }
        
        // Validar consumo de galones si está presente
        if (camion.getConsumoGalones() != null && camion.getConsumoGalones() <= 0) {
            resultado.agregarAdvertencia("Consumo debe ser positivo: " + camion.getConsumoGalones());
        }
        
        // Determinar validez
        resultado.setCamionValido(resultado.getErrores().isEmpty());
        
        return resultado;
    }
    
    @Override
    public Map<Camion, ResultadoValidacionCamion> validarCamiones(List<Camion> camiones) {
        logger.debug("Validando {} camiones", camiones != null ? camiones.size() : 0);
        
        Map<Camion, ResultadoValidacionCamion> resultados = new HashMap<>();
        
        if (camiones == null || camiones.isEmpty()) {
            logger.warn("Lista de camiones vacía o nula");
            return resultados;
        }
        
        for (Camion camion : camiones) {
            ResultadoValidacionCamion resultado = validarCamion(camion);
            resultados.put(camion, resultado);
        }
        
        // Detectar IDs duplicados
        Set<Integer> idsVistos = new HashSet<>();
        for (Camion camion : camiones) {
            if (camion.getId() != null) {
                if (idsVistos.contains(camion.getId())) {
                    resultados.get(camion).agregarError("ID duplicado: " + camion.getId());
                }
                idsVistos.add(camion.getId());
            }
        }
        
        return resultados;
    }
    
    @Override
    public ResultadoValidacionAlmacen validarAlmacen(Almacen almacen) {
        logger.debug("Validando almacén: {}", almacen != null ? almacen.getId() : "null");
        
        ResultadoValidacionAlmacen resultado = new ResultadoValidacionAlmacen();
        
        if (almacen == null) {
            resultado.agregarError("Almacén es nulo");
            resultado.setAlmacenValido(false);
            return resultado;
        }
        
        // Validar coordenadas
        if (!sonCoordenadasValidas(almacen.getX(), almacen.getY())) {
            resultado.agregarError(String.format("Coordenadas de almacén fuera de límites: (%d,%d)", 
                almacen.getX(), almacen.getY()));
        }
        
        // Validar accesibilidad
        boolean esAccesible = esPuntoAccesible(new Punto(almacen.getX(), almacen.getY()));
        resultado.setEsAccesible(esAccesible);
        
        if (!esAccesible) {
            resultado.agregarError("Almacén en ubicación no accesible (obstruida)");
        }
        
        // Validar capacidad
        if (almacen.getCapacidad() <= 0) {
            resultado.agregarError("Capacidad de almacén debe ser positiva: " + almacen.getCapacidad());
        }
        
        // Validar tipo si está presente
        if (almacen.getTipo() != null && almacen.getTipo().trim().isEmpty()) {
            resultado.agregarAdvertencia("Tipo de almacén vacío");
        }
        
        // Determinar validez
        resultado.setAlmacenValido(resultado.getErrores().isEmpty());
        
        return resultado;
    }
    
    @Override
    public boolean sonCoordenadasValidas(int x, int y) {
        return x >= configuracion.getGridMinX() && x <= configuracion.getGridMaxX() &&
               y >= configuracion.getGridMinY() && y <= configuracion.getGridMaxY();
    }
    
    @Override
    public boolean esPuntoAccesible(Punto punto) {
        if (!configuracion.isValidarAccesibilidadPuntos()) {
            return true; // Si no está configurado para validar, asumir accesible
        }
        
        try {
            return gestorObstaculos.esPuntoValido(punto);
        } catch (Exception e) {
            logger.warn("Error validando accesibilidad del punto {}: {}", punto, e.getMessage());
            return false; // En caso de error, asumir no accesible por seguridad
        }
    }
    
    @Override
    public ResultadoValidacionCoherencia validarCoherenciaGeneral(List<Pedido> pedidos, 
                                                                List<Camion> camiones, 
                                                                List<Almacen> almacenes) {
        
        logger.debug("Validando coherencia general del sistema");
        
        ResultadoValidacionCoherencia resultado = new ResultadoValidacionCoherencia();
        
        // Validar que hay elementos básicos
        if (pedidos == null || pedidos.isEmpty()) {
            resultado.agregarProblema("No hay pedidos para procesar");
        }
        
        if (camiones == null || camiones.isEmpty()) {
            resultado.agregarProblema("No hay camiones disponibles");
        }
        
        if (almacenes == null || almacenes.isEmpty()) {
            resultado.agregarProblema("No hay almacenes operativos");
        }
        
        if (pedidos != null && camiones != null && !pedidos.isEmpty() && !camiones.isEmpty()) {
            // Validar capacidad total vs demanda total
            double volumenTotalPedidos = pedidos.stream()
                .mapToDouble(Pedido::getVolumenM3)
                .sum();
            
            double capacidadTotalCamiones = camiones.stream()
                .filter(c -> c.getMaxCargaM3() != null)
                .mapToDouble(c -> c.getMaxCargaM3().doubleValue())
                .sum();
            
            resultado.getEstadisticasValidacion().put("volumenTotalPedidos", (int) volumenTotalPedidos);
            resultado.getEstadisticasValidacion().put("capacidadTotalCamiones", (int) capacidadTotalCamiones);
            
            if (volumenTotalPedidos > capacidadTotalCamiones) {
                resultado.agregarProblema(String.format(
                    "Demanda excede capacidad total: %.2f m³ > %.2f m³", 
                    volumenTotalPedidos, capacidadTotalCamiones));
            }
            
            // Validar distribución geográfica
            if (configuracion.isValidarCoherenciaEstricta()) {
                validarDistribucionGeografica(pedidos, almacenes, resultado);
            }
        }
        
        // Estadísticas finales
        resultado.getEstadisticasValidacion().put("totalPedidos", pedidos != null ? pedidos.size() : 0);
        resultado.getEstadisticasValidacion().put("totalCamiones", camiones != null ? camiones.size() : 0);
        resultado.getEstadisticasValidacion().put("totalAlmacenes", almacenes != null ? almacenes.size() : 0);
        resultado.getEstadisticasValidacion().put("problemasDetectados", resultado.getProblemasDetectados().size());
        
        // Determinar coherencia
        resultado.setCoherenciaValida(resultado.getProblemasDetectados().isEmpty());
        
        return resultado;
    }
    
    @Override
    public boolean esAsignacionFactible(AsignacionCamion asignacion) {
        if (asignacion == null || asignacion.getCamion() == null) {
            return false;
        }
        
        // Validar que la capacidad utilizada no exceda la máxima
        double capacidadMaxima = asignacion.getCamion().getMaxCargaM3();
        return asignacion.getCapacidadUtilizada() <= capacidadMaxima;
    }
    
    @Override
    public void configurarValidacion(ConfiguracionValidacion configuracion) {
        this.configuracion = configuracion;
        logger.info("Configuración de validación actualizada");
    }
    
    // Métodos auxiliares privados
    
    private double calcularFactorEficiencia(Camion camion) {
        if (camion.getTipo() == null || camion.getMaxCargaM3() == null) {
            return 0.5; // Factor neutro por defecto
        }
        
        // Factor basado en tipo de camión (TA > TB > TC > TD en eficiencia)
        double factorTipo = switch (camion.getTipo().toUpperCase()) {
            case "TA" -> 1.0;  // Más eficiente
            case "TB" -> 0.9;
            case "TC" -> 0.8;
            case "TD" -> 0.7;  // Menos eficiente
            default -> 0.5;
        };
        
        // Factor basado en capacidad (mayor capacidad = mayor eficiencia)
        double factorCapacidad = Math.min(1.0, camion.getMaxCargaM3() / 20.0);
        
        return (factorTipo + factorCapacidad) / 2.0;
    }
    
    private void validarDistribucionGeografica(List<Pedido> pedidos, List<Almacen> almacenes, 
                                             ResultadoValidacionCoherencia resultado) {
        
        if (pedidos.isEmpty() || almacenes.isEmpty()) {
            return;
        }
        
        // Calcular centroide de pedidos
        double centroideX = pedidos.stream()
            .filter(p -> p.getUbicacionX() != null)
            .mapToInt(Pedido::getUbicacionX)
            .average()
            .orElse(0);
        
        double centroideY = pedidos.stream()
            .filter(p -> p.getUbicacionY() != null)
            .mapToInt(Pedido::getUbicacionY)
            .average()
            .orElse(0);
        
        // Verificar si hay almacenes cerca del centroide
        Punto centroide = new Punto((int) centroideX, (int) centroideY);
        
        boolean hayAlmacenCerca = almacenes.stream()
            .anyMatch(almacen -> {
                Punto puntoAlmacen = new Punto(almacen.getX(), almacen.getY());
                return centroide.distanciaManhattanHasta(puntoAlmacen) < 200; // Umbral configurable
            });
        
        if (!hayAlmacenCerca) {
            resultado.agregarProblema(String.format(
                "No hay almacenes cerca del centroide de pedidos: (%.0f,%.0f)", 
                centroideX, centroideY));
        }
        
        // Verificar dispersión de pedidos
        double dispersion = pedidos.stream()
            .filter(p -> p.getUbicacionX() != null && p.getUbicacionY() != null)
            .mapToDouble(p -> {
                Punto punto = new Punto(p.getUbicacionX(), p.getUbicacionY());
                return centroide.distanciaManhattanHasta(punto);
            })
            .average()
            .orElse(0);
        
        if (dispersion > 300) { // Umbral configurable
            resultado.agregarProblema(String.format(
                "Pedidos muy dispersos geográficamente: dispersión promedio = %.0f", dispersion));
        }
    }
}