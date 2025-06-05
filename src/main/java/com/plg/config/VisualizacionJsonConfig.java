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
 * Configuración específica para serialización JSON optimizada para frontend
 * Garantiza formato consistente y eficiente para visualización
 */
@Configuration
public class VisualizacionJsonConfig {
    
    /**
     * ObjectMapper optimizado para DTOs de visualización
     * Configurado para máxima eficiencia y consistencia con frontend
     */
    @Bean("objectMapperVisualizacion")
    public ObjectMapper objectMapperVisualizacion() {
        return Jackson2ObjectMapperBuilder.json()
            // Módulo para fechas modernas
            .modules(new JavaTimeModule())
            
            // Naming strategy para frontend (camelCase)
            .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
            
            // Exclusión de campos nulos para reducir payload
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            
            // Desactivar fechas como timestamps para legibilidad
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            
            // Formato legible para debugging
            .featuresToEnable(SerializationFeature.INDENT_OUTPUT)
            
            // Tolerancia a propiedades desconocidas
            .failOnUnknownProperties(false)
            
            .build();
    }
    
    /**
     * ObjectMapper específico para datos compactos (móvil/bajo bandwidth)
     */
    @Bean("objectMapperCompacto")
    public ObjectMapper objectMapperCompacto() {
        return Jackson2ObjectMapperBuilder.json()
            .modules(new JavaTimeModule())
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            
            // Máxima compresió,n
            .serializationInclusion(JsonInclude.Include.NON_EMPTY)
            .featuresToDisable(
                SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                SerializationFeature.INDENT_OUTPUT // Sin formato para menor tamaño
            )
            .failOnUnknownProperties(false)
            .build();
    }
}




