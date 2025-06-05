package com.plg.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DTO optimizado para visualización de rutas en frontend
 * Estructura JSON-friendly con datos esenciales para renderizado
 */
public class RutaVisualizacionDTO {
    
    // Información del camión
    private int camionId;
    private String codigoCamion;
    private String tipoCamion;
    private int capacidadMaxima;
    private String color;
    
    // Métricas de la ruta
    private double distanciaTotalKm;
    private double tiempoEstimadoHoras;
    private double combustibleNecesario;
    private double combustibleRestante;
    private int numeroEntregas;
    private double porcentajeUtilizacion;
    private boolean esViable;
    
    // Estado y observaciones
    private String estado;
    private String observaciones;
    
    // Segmentos de la ruta
    private List<SegmentoVisualizacionDTO> segmentos;
    
    // Metadatos adicionales
    private Map<String, Object> metadatos;
    
    public RutaVisualizacionDTO() {
        this.segmentos = new ArrayList<>();
        this.metadatos = new HashMap<>();
    }
    
    // Getters y setters para información del camión
    public int getCamionId() { return camionId; }
    public void setCamionId(int camionId) { this.camionId = camionId; }
    
    public String getCodigoCamion() { return codigoCamion; }
    public void setCodigoCamion(String codigoCamion) { this.codigoCamion = codigoCamion; }
    
    public String getTipoCamion() { return tipoCamion; }
    public void setTipoCamion(String tipoCamion) { this.tipoCamion = tipoCamion; }
    
    public int getCapacidadMaxima() { return capacidadMaxima; }
    public void setCapacidadMaxima(int capacidadMaxima) { this.capacidadMaxima = capacidadMaxima; }
    
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    
    // Getters y setters para métricas
    public double getDistanciaTotalKm() { return distanciaTotalKm; }
    public void setDistanciaTotalKm(double distanciaTotalKm) { this.distanciaTotalKm = distanciaTotalKm; }
    
    public double getTiempoEstimadoHoras() { return tiempoEstimadoHoras; }
    public void setTiempoEstimadoHoras(double tiempoEstimadoHoras) { this.tiempoEstimadoHoras = tiempoEstimadoHoras; }
    
    public double getCombustibleNecesario() { return combustibleNecesario; }
    public void setCombustibleNecesario(double combustibleNecesario) { this.combustibleNecesario = combustibleNecesario; }
    
    public double getCombustibleRestante() { return combustibleRestante; }
    public void setCombustibleRestante(double combustibleRestante) { this.combustibleRestante = combustibleRestante; }
    
    public int getNumeroEntregas() { return numeroEntregas; }
    public void setNumeroEntregas(int numeroEntregas) { this.numeroEntregas = numeroEntregas; }
    
    public double getPorcentajeUtilizacion() { return porcentajeUtilizacion; }
    public void setPorcentajeUtilizacion(double porcentajeUtilizacion) { this.porcentajeUtilizacion = porcentajeUtilizacion; }
    
    public boolean isEsViable() { return esViable; }
    public void setEsViable(boolean esViable) { this.esViable = esViable; }
    
    // Getters y setters para estado
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    
    // Getters y setters para estructura
    public List<SegmentoVisualizacionDTO> getSegmentos() { return segmentos; }
    public void setSegmentos(List<SegmentoVisualizacionDTO> segmentos) { this.segmentos = segmentos; }
    
    public Map<String, Object> getMetadatos() { return metadatos; }
    public void setMetadatos(Map<String, Object> metadatos) { this.metadatos = metadatos; }
    
    /**
     * DTO para segmentos individuales de ruta
     */
    public static class SegmentoVisualizacionDTO {
        
        // Puntos del segmento
        private int[] origen;           // [x, y]
        private int[] destino;          // [x, y]
        private String tipoSegmento;    // "ENTREGA", "RETORNO_ALMACEN", "MOVIMIENTO"
        private int orden;
        
        // Métricas del segmento
        private double distanciaKm;
        private double tiempoEstimadoHoras;
        
        // Información específica
        private Integer pedidoId;       // Solo para entregas
        private Double volumenEntrega;  // Solo para entregas
        private Integer prioridad;      // Solo para entregas
        private String tipoEntrega;     // "COMPLETA", "PARCIAL"
        private Integer almacenId;      // Solo para retornos
        
        // Visualización
        private String color;
        private List<int[]> rutaDetallada; // Secuencia completa de puntos A*
        
        public SegmentoVisualizacionDTO() {
            this.rutaDetallada = new ArrayList<>();
        }
        
        // Getters y setters para puntos
        public int[] getOrigen() { return origen; }
        public void setOrigen(int[] origen) { this.origen = origen; }
        
        public int[] getDestino() { return destino; }
        public void setDestino(int[] destino) { this.destino = destino; }
        
        public String getTipoSegmento() { return tipoSegmento; }
        public void setTipoSegmento(String tipoSegmento) { this.tipoSegmento = tipoSegmento; }
        
        public int getOrden() { return orden; }
        public void setOrden(int orden) { this.orden = orden; }
        
        // Getters y setters para métricas
        public double getDistanciaKm() { return distanciaKm; }
        public void setDistanciaKm(double distanciaKm) { this.distanciaKm = distanciaKm; }
        
        public double getTiempoEstimadoHoras() { return tiempoEstimadoHoras; }
        public void setTiempoEstimadoHoras(double tiempoEstimadoHoras) { this.tiempoEstimadoHoras = tiempoEstimadoHoras; }
        
        // Getters y setters para información específica
        public Integer getPedidoId() { return pedidoId; }
        public void setPedidoId(Integer pedidoId) { this.pedidoId = pedidoId; }
        
        public Double getVolumenEntrega() { return volumenEntrega; }
        public void setVolumenEntrega(Double volumenEntrega) { this.volumenEntrega = volumenEntrega; }
        
        public Integer getPrioridad() { return prioridad; }
        public void setPrioridad(Integer prioridad) { this.prioridad = prioridad; }
        
        public String getTipoEntrega() { return tipoEntrega; }
        public void setTipoEntrega(String tipoEntrega) { this.tipoEntrega = tipoEntrega; }
        
        public Integer getAlmacenId() { return almacenId; }
        public void setAlmacenId(Integer almacenId) { this.almacenId = almacenId; }
        
        // Getters y setters para visualización
        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }
        
        public List<int[]> getRutaDetallada() { return rutaDetallada; }
        public void setRutaDetallada(List<int[]> rutaDetallada) { this.rutaDetallada = rutaDetallada; }
        
        /**
         * Métodos de utilidad para el frontend
         */
        public boolean esEntrega() {
            return "ENTREGA".equals(tipoSegmento);
        }
        
        public boolean esRetorno() {
            return "RETORNO_ALMACEN".equals(tipoSegmento);
        }
        
        public boolean esMovimiento() {
            return "MOVIMIENTO".equals(tipoSegmento);
        }
        
        public String getDescripcionCorta() {
            if (esEntrega() && pedidoId != null) {
                return "Entrega #" + pedidoId;
            } else if (esRetorno() && almacenId != null) {
                return "Retorno Almacén #" + almacenId;
            } else {
                return "Movimiento";
            }
        }
        
        public Map<String, Object> getInformacionCompleta() {
            Map<String, Object> info = new HashMap<>();
            info.put("tipo", tipoSegmento);
            info.put("orden", orden);
            info.put("distanciaKm", distanciaKm);
            info.put("tiempoHoras", tiempoEstimadoHoras);
            
            if (esEntrega()) {
                info.put("pedidoId", pedidoId);
                info.put("volumen", volumenEntrega);
                info.put("prioridad", prioridad);
                info.put("tipoEntrega", tipoEntrega);
            } else if (esRetorno()) {
                info.put("almacenId", almacenId);
            }
            
            return info;
        }
        
        @Override
        public String toString() {
            return String.format("Segmento[%s: (%d,%d)→(%d,%d) %.2fkm]",
                tipoSegmento,
                origen != null ? origen[0] : 0, origen != null ? origen[1] : 0,
                destino != null ? destino[0] : 0, destino != null ? destino[1] : 0,
                distanciaKm);
        }
    }
    
    /**
     * Métodos de utilidad para el frontend
     */
    public boolean tieneProblemasViabilidad() {
        return !esViable;
    }
    
    public String getNivelUtilizacion() {
        if (porcentajeUtilizacion >= 90) return "ALTA";
        if (porcentajeUtilizacion >= 70) return "MEDIA";
        if (porcentajeUtilizacion >= 50) return "NORMAL";
        return "BAJA";
    }
    
    public String getNivelCombustible() {
        if (combustibleRestante <= 2.5) return "CRITICO";
        if (combustibleRestante <= 5.0) return "BAJO";
        if (combustibleRestante <= 10.0) return "MEDIO";
        return "ALTO";
    }
    
    public List<SegmentoVisualizacionDTO> getSegmentosEntrega() {
        return segmentos.stream()
            .filter(SegmentoVisualizacionDTO::esEntrega)
            .toList();
    }
    
    public List<SegmentoVisualizacionDTO> getSegmentosRetorno() {
        return segmentos.stream()
            .filter(SegmentoVisualizacionDTO::esRetorno)
            .toList();
    }
    
    public Map<String, Object> getResumenEjecutivo() {
        Map<String, Object> resumen = new HashMap<>();
        resumen.put("camionInfo", String.format("%s (%s)", codigoCamion, tipoCamion));
        resumen.put("entregas", numeroEntregas);
        resumen.put("distancia", String.format("%.1f km", distanciaTotalKm));
        resumen.put("tiempo", String.format("%.1f h", tiempoEstimadoHoras));
        resumen.put("utilizacion", String.format("%.1f%%", porcentajeUtilizacion));
        resumen.put("combustible", String.format("%.1f gal", combustibleRestante));
        resumen.put("estado", estado);
        resumen.put("viable", esViable);
        
        return resumen;
    }
    
    public boolean requiereAtencionUrgente() {
        return !esViable || 
               combustibleRestante <= 2.5 || 
               "CRITICO".equals(getNivelCombustible()) ||
               porcentajeUtilizacion < 30;
    }
    
    public List<String> getAlertasActivas() {
        List<String> alertas = new ArrayList<>();
        
        if (!esViable) {
            alertas.add("Ruta no viable - Revisar restricciones");
        }
        
        if (combustibleRestante <= 2.5) {
            alertas.add("Combustible crítico - " + combustibleRestante + " gal restantes");
        }
        
        if (porcentajeUtilizacion < 30) {
            alertas.add("Baja utilización - " + porcentajeUtilizacion + "%");
        }
        
        if (tiempoEstimadoHoras > 12) {
            alertas.add("Ruta muy larga - " + tiempoEstimadoHoras + " horas");
        }
        
        return alertas;
    }
    
    @Override
    public String toString() {
        return String.format("RutaVisualizacion[Camión:%s, Entregas:%d, Dist:%.1fkm, Viable:%s]",
            codigoCamion, numeroEntregas, distanciaTotalKm, esViable);
    }
}