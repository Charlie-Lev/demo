package com.plg.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
/**
 * Utilitarios para optimización de payloads JSON
 */
@Configuration
public class OptimizadorPayloadJson {
    
    /**
     * Optimizador automático basado en tamaño de datos
     */
    public static class OptimizadorAutomatico {
        
        private static final int LIMITE_PAYLOAD_PEQUENO_KB = 100;
        private static final int LIMITE_PAYLOAD_MEDIO_KB = 500;
        private static final int LIMITE_PAYLOAD_GRANDE_KB = 1000;
        
        /**
         * Determina nivel de optimización automáticamente según tamaño
         */
        public static int determinarNivelOptimizacion(Object payload) {
            try {
                // Estimar tamaño del payload
                String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);
                int tamanoKB = json.length() / 1024;
                
                if (tamanoKB <= LIMITE_PAYLOAD_PEQUENO_KB) {
                    return 0; // Sin optimización
                } else if (tamanoKB <= LIMITE_PAYLOAD_MEDIO_KB) {
                    return 1; // Optimización básica
                } else if (tamanoKB <= LIMITE_PAYLOAD_GRANDE_KB) {
                    return 2; // Optimización media
                } else {
                    return 3; // Optimización agresiva
                }
                
            } catch (Exception e) {
                return 1; // Optimización básica por defecto
            }
        }
        
        /**
         * Aplica filtros según el dispositivo de destino
         */
        public static com.plg.service.SerializadorVisualizacionService.ConfiguracionVisualizacion 
                configurarSegunDispositivo(String userAgent) {
            
            com.plg.service.SerializadorVisualizacionService.ConfiguracionVisualizacion config = 
                new com.plg.service.SerializadorVisualizacionService.ConfiguracionVisualizacion();
            
            if (userAgent != null && userAgent.toLowerCase().contains("mobile")) {
                // Configuración para móviles (datos reducidos)
                config.setIncluirRutasDetalladas(false);
                config.setIncluirObstaculos(false);
                config.setIncluirTimeline(true);
                config.setIncluirWidgets(true);
                config.setMaxPuntosRuta(100); // Reducido para móviles
                
            } else {
                // Configuración para desktop (datos completos)
                config.setIncluirRutasDetalladas(true);
                config.setIncluirObstaculos(true);
                config.setIncluirTimeline(true);
                config.setIncluirWidgets(true);
                config.setMaxPuntosRuta(1000);
            }
            
            return config;
        }
    }
    
    /**
     * Compresión específica para elementos comunes
     */
    public static class CompresorEspecializado {
        
        /**
         * Comprime coordenadas repetitivas en rutas detalladas
         */
        public static java.util.List<int[]> comprimirCoordenadas(java.util.List<int[]> coordenadas) {
            if (coordenadas == null || coordenadas.size() <= 2) {
                return coordenadas;
            }
            
            java.util.List<int[]> comprimidas = new java.util.ArrayList<>();
            comprimidas.add(coordenadas.get(0)); // Primer punto siempre incluido
            
            for (int i = 1; i < coordenadas.size() - 1; i++) {
                int[] anterior = coordenadas.get(i - 1);
                int[] actual = coordenadas.get(i);
                int[] siguiente = coordenadas.get(i + 1);
                
                // Solo incluir si cambia dirección significativamente
                if (!esPuntoRedundante(anterior, actual, siguiente)) {
                    comprimidas.add(actual);
                }
            }
            
            comprimidas.add(coordenadas.get(coordenadas.size() - 1)); // Último punto siempre incluido
            
            return comprimidas;
        }
        
        private static boolean esPuntoRedundante(int[] anterior, int[] actual, int[] siguiente) {
            // Si el punto actual está en línea recta entre anterior y siguiente
            int dx1 = actual[0] - anterior[0];
            int dy1 = actual[1] - anterior[1];
            int dx2 = siguiente[0] - actual[0];
            int dy2 = siguiente[1] - actual[1];
            
            // Producto cruzado para detectar colinealidad
            return Math.abs(dx1 * dy2 - dy1 * dx2) <= 1; // Tolerancia de 1 unidad
        }
    }
}