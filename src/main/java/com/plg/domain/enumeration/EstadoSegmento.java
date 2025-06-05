package com.plg.domain.enumeration;
/**
 * Estados de ejecución de un segmento
 */
public enum EstadoSegmento {
    PENDIENTE,      // No iniciado
    EN_EJECUCION,   // En progreso
    COMPLETADO,     // Terminado exitosamente
    FALLIDO         // Falló (avería, obstáculo, etc.)
}