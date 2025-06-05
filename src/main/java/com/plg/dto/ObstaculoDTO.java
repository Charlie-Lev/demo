package com.plg.dto;

import com.plg.domain.Obstaculo;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Data
public class ObstaculoDTO {
    private Integer id;
    private String tipo;
    private Integer coordenadaX;
    private Integer coordenadaY;
    private Integer coordenadaX2;
    private Integer coordenadaY2;
    private String puntosPoligono;
    private String descripcion;
    private Boolean activo;
    private String fechaInicio;
    private String fechaFin;
    private boolean estaActivoAhora;
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    
    public static ObstaculoDTO fromEntity(Obstaculo obstaculo) {
        ObstaculoDTO dto = new ObstaculoDTO();
        
        dto.setId(obstaculo.getId());
        dto.setTipo(obstaculo.getTipo());
        dto.setCoordenadaX(obstaculo.getCoordenadaX());
        dto.setCoordenadaY(obstaculo.getCoordenadaY());
        dto.setCoordenadaX2(obstaculo.getCoordenadaX2());
        dto.setCoordenadaY2(obstaculo.getCoordenadaY2());
        dto.setPuntosPoligono(obstaculo.getPuntosPoligono());
        dto.setDescripcion(obstaculo.getDescripcion());
        dto.setActivo(obstaculo.getActivo());
        
        // Convertir timestamps a fechas legibles
        if (obstaculo.getTimestampInicio() != null) {
            LocalDateTime fechaInicio = LocalDateTime.ofEpochSecond(
                obstaculo.getTimestampInicio(), 0, ZoneId.systemDefault().getRules().getOffset(LocalDateTime.now()));
            dto.setFechaInicio(fechaInicio.format(FORMATTER));
        }
        
        if (obstaculo.getTimestampFin() != null) {
            LocalDateTime fechaFin = LocalDateTime.ofEpochSecond(
                obstaculo.getTimestampFin(), 0, ZoneId.systemDefault().getRules().getOffset(LocalDateTime.now()));
            dto.setFechaFin(fechaFin.format(FORMATTER));
        }
        
        // Verificar si está activo ahora
        long ahora = System.currentTimeMillis() / 1000;
        dto.setEstaActivoAhora(obstaculo.estaActivoEn(ahora));
        
        return dto;
    }
}