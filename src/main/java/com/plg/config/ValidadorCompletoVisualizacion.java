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
 * Validador de completitud para DTOs de visualización
 * Asegura que todos los metadatos necesarios estén presentes
 */
@Configuration
public class ValidadorCompletoVisualizacion {
    
    /**
     * Validador para RutaVisualizacionDTO
     * Verifica que todos los campos necesarios para frontend estén presentes
     */
    public static class ValidadorRutaVisualizacion {
        
        public static ResultadoValidacion validarCompletitud(com.plg.dto.RutaVisualizacionDTO ruta) {
            ResultadoValidacion resultado = new ResultadoValidacion();
            
            // Validaciones obligatorias
            if (ruta.getCamionId() <= 0) {
                resultado.agregarError("ID de camión inválido");
            }
            
            if (ruta.getCodigoCamion() == null || ruta.getCodigoCamion().trim().isEmpty()) {
                resultado.agregarError("Código de camión requerido");
            }
            
            if (ruta.getTipoCamion() == null) {
                resultado.agregarError("Tipo de camión requerido");
            }
            
            if (ruta.getColor() == null || !ruta.getColor().matches("^#[0-9A-Fa-f]{6}$")) {
                resultado.agregarAdvertencia("Color inválido, usando color por defecto");
            }
            
            // Validaciones de métricas
            if (ruta.getDistanciaTotalKm() < 0) {
                resultado.agregarError("Distancia total inválida");
            }
            
            if (ruta.getTiempoEstimadoHoras() < 0) {
                resultado.agregarError("Tiempo estimado inválido");
            }
            
            if (ruta.getCombustibleNecesario() < 0) {
                resultado.agregarError("Combustible necesario inválido");
            }
            
            if (ruta.getPorcentajeUtilizacion() < 0 || ruta.getPorcentajeUtilizacion() > 100) {
                resultado.agregarAdvertencia("Porcentaje de utilización fuera de rango normal");
            }
            
            // Validaciones de segmentos
            if (ruta.getSegmentos() == null || ruta.getSegmentos().isEmpty()) {
                resultado.agregarError("Ruta sin segmentos");
            } else {
                for (int i = 0; i < ruta.getSegmentos().size(); i++) {
                    com.plg.dto.RutaVisualizacionDTO.SegmentoVisualizacionDTO segmento = ruta.getSegmentos().get(i);
                    
                    if (segmento.getOrigen() == null || segmento.getOrigen().length != 2) {
                        resultado.agregarError("Segmento " + i + " sin coordenadas de origen válidas");
                    }
                    
                    if (segmento.getDestino() == null || segmento.getDestino().length != 2) {
                        resultado.agregarError("Segmento " + i + " sin coordenadas de destino válidas");
                    }
                    
                    if (segmento.getTipoSegmento() == null) {
                        resultado.agregarError("Segmento " + i + " sin tipo definido");
                    }
                    
                    if (segmento.getOrden() <= 0) {
                        resultado.agregarAdvertencia("Segmento " + i + " sin orden válido");
                    }
                }
            }
            
            // Validaciones de metadatos
            if (ruta.getMetadatos() == null) {
                resultado.agregarAdvertencia("Sin metadatos adicionales");
            }
            
            resultado.setValido(resultado.getErrores().isEmpty());
            return resultado;
        }
    }
    
    /**
     * Resultado de validación con errores y advertencias
     */
    public static class ResultadoValidacion {
        private boolean valido = true;
        private java.util.List<String> errores = new java.util.ArrayList<>();
        private java.util.List<String> advertencias = new java.util.ArrayList<>();
        
        public void agregarError(String error) {
            errores.add(error);
            valido = false;
        }
        
        public void agregarAdvertencia(String advertencia) {
            advertencias.add(advertencia);
        }
        
        // Getters
        public boolean isValido() { return valido; }
        public void setValido(boolean valido) { this.valido = valido; }
        public java.util.List<String> getErrores() { return errores; }
        public java.util.List<String> getAdvertencias() { return advertencias; }
        
        public boolean tieneProblemas() {
            return !errores.isEmpty() || !advertencias.isEmpty();
        }
        
        public String getResumen() {
            if (valido && advertencias.isEmpty()) {
                return "Validación exitosa";
            } else if (valido) {
                return "Válido con " + advertencias.size() + " advertencias";
            } else {
                return "Inválido: " + errores.size() + " errores, " + advertencias.size() + " advertencias";
            }
        }
    }
}