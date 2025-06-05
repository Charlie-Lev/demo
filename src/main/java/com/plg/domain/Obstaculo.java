package com.plg.domain;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "obstaculo")
public class Obstaculo {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_obstaculo")
    private Integer id;
    
    @Column(name = "tipo", length = 20)
    private String tipo; // "PUNTO", "LINEA_H", "LINEA_V", "POLIGONO", "BLOQUEO_TEMPORAL"
    
    @Column(name = "coordenada_x")
    private Integer coordenadaX;
    
    @Column(name = "coordenada_y") 
    private Integer coordenadaY;
    
    @Column(name = "coordenada_x2")
    private Integer coordenadaX2; // Para líneas: punto final X
    
    @Column(name = "coordenada_y2")
    private Integer coordenadaY2; // Para líneas: punto final Y
    
    @Column(name = "puntos_poligono", columnDefinition = "TEXT")
    private String puntosPoligono; // Para polígonos: "x1,y1,x2,y2,x3,y3"
    
    @Column(name = "descripcion")
    private String descripcion;
    
    @Column(name = "activo")
    private Boolean activo = true;
    
    // NUEVOS CAMPOS para bloqueos temporales
    @Column(name = "timestamp_inicio")
    private Long timestampInicio; // Timestamp de inicio del bloqueo
    
    @Column(name = "timestamp_fin")
    private Long timestampFin; // Timestamp de fin del bloqueo
    
    public Obstaculo() {}
    
    public Obstaculo(String tipo, int x, int y) {
        this.tipo = tipo;
        this.coordenadaX = x;
        this.coordenadaY = y;
        this.activo = true;
    }
    
    // Constructor para líneas
    public Obstaculo(String tipo, int x1, int y1, int x2, int y2) {
        this(tipo, x1, y1);
        this.coordenadaX2 = x2;
        this.coordenadaY2 = y2;
    }
    
    // Constructor para bloqueos temporales
    public Obstaculo(String tipo, String puntosPoligono, Long timestampInicio, Long timestampFin) {
        this.tipo = tipo;
        this.puntosPoligono = puntosPoligono;
        this.timestampInicio = timestampInicio;
        this.timestampFin = timestampFin;
        this.activo = true;
        
        // Establecer primer punto como coordenadas principales
        if (puntosPoligono != null && !puntosPoligono.isEmpty()) {
            String[] coords = puntosPoligono.split(",");
            if (coords.length >= 2) {
                this.coordenadaX = Integer.parseInt(coords[0].trim());
                this.coordenadaY = Integer.parseInt(coords[1].trim());
            }
        }
    }
    
    /**
     * Verifica si el bloqueo está activo en un momento específico
     */
    // public boolean estaActivoEn(long timestamp) {
    //     if (timestampInicio == null || timestampFin == null) {
    //         return activo != null && activo;
    //     }
    //     return (activo != null && activo) && 
    //            timestamp >= timestampInicio && 
    //            timestamp <= timestampFin;
    // }

    public boolean estaActivoEn(long timestamp) {
        // Verificar que ambos timestamps no sean nulos
        if (this.timestampInicio == null || this.timestampFin == null) {
            return false;
        }
        
        // El obstáculo está activo si:
        // timestamp >= timestampInicio AND timestamp < timestampFin
        return timestamp >= this.timestampInicio && timestamp < this.timestampFin;
    }

    /**
     * Verifica si el obstáculo está activo en un momento específico (sobrecarga con LocalDateTime)
     * @param momento Momento a verificar
     * @return true si el obstáculo está activo en ese momento
     */
    public boolean estaActivoEn(LocalDateTime momento) {
        long timestamp = momento.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        return estaActivoEn(timestamp);
    }
}