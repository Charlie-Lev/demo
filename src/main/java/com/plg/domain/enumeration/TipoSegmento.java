package com.plg.domain.enumeration;


/**
 * Tipos de segmentos de ruta
 */
public enum TipoSegmento {
    ENTREGA(3),           // Segmento hacia punto de entrega
    MOVIMIENTO(2),        // Movimiento simple entre puntos
    RETORNO_ALMACEN(1);   // Retorno a almacén
    
    private final int prioridad;
    
    TipoSegmento(int prioridad) {
        this.prioridad = prioridad;
    }
    
    public int getPrioridad() {
        return prioridad;
    }
}