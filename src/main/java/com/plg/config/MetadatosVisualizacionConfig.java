
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
 * Configuración específica para metadatos de visualización
 */
@Configuration
public class MetadatosVisualizacionConfig {
    
    /**
     * Configuración estándar para metadatos que acompañan las rutas
     */
    public static class ConfiguracionMetadatos {
        
        // Formatos estándar para frontend
        public static final String FORMATO_FECHA_ESTANDAR = "yyyy-MM-dd HH:mm:ss";
        public static final String FORMATO_FECHA_COMPACTO = "dd/MM HH:mm";
        public static final String FORMATO_COORDENADAS = "[x,y]";
        
        // Precisión decimal para diferentes métricas
        public static final int PRECISION_DISTANCIA = 2;    // 12.34 km
        public static final int PRECISION_TIEMPO = 1;       // 2.5 horas  
        public static final int PRECISION_COMBUSTIBLE = 1;  // 15.7 galones
        public static final int PRECISION_PORCENTAJE = 1;   // 85.3%
        
        // Límites para optimización automática
        public static final int MAX_PUNTOS_RUTA_DETALLADA = 500;
        public static final int MAX_EVENTOS_TIMELINE = 200;
        public static final int MAX_SEGMENTOS_POR_RUTA = 50;
        
        // Colores estándar para elementos (compatibles con frontend)
        public static final String COLOR_RUTA_VIABLE = "#4CAF50";      // Verde
        public static final String COLOR_RUTA_PROBLEMATICA = "#F44336"; // Rojo
        public static final String COLOR_RUTA_ADVERTENCIA = "#FF9800";  // Naranja
        public static final String COLOR_ENTREGA_ALTA = "#E91E63";      // Rosa intenso
        public static final String COLOR_ENTREGA_MEDIA = "#2196F3";     // Azul
        public static final String COLOR_ENTREGA_BAJA = "#9E9E9E";      // Gris
        public static final String COLOR_ALMACEN_PRINCIPAL = "#1976D2";  // Azul oscuro
        public static final String COLOR_ALMACEN_SECUNDARIO = "#757575"; // Gris oscuro
        
        // Iconos estándar (Font Awesome o Material Icons)
        public static final String ICONO_CAMION = "fa-truck";
        public static final String ICONO_ENTREGA = "fa-package";
        public static final String ICONO_ALMACEN = "fa-warehouse";
        public static final String ICONO_RUTA = "fa-route";
        public static final String ICONO_COMBUSTIBLE = "fa-gas-pump";
        public static final String ICONO_TIEMPO = "fa-clock";
        public static final String ICONO_DISTANCIA = "fa-road";
        
        // Estados estandarizados
        public static final String ESTADO_PLANIFICADA = "PLANIFICADA";
        public static final String ESTADO_EN_EJECUCION = "EN_EJECUCION";
        public static final String ESTADO_COMPLETADA = "COMPLETADA";
        public static final String ESTADO_CANCELADA = "CANCELADA";
        public static final String ESTADO_PROBLEMATICA = "PROBLEMATICA";
    }
}