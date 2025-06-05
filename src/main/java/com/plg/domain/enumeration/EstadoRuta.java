package com.plg.domain.enumeration;
/**
 * Estados posibles de una ruta
 */
public enum EstadoRuta {
    PLANIFICADA,     // Ruta creada pero no iniciada
    EN_EJECUCION,    // Ruta en progreso
    COMPLETADA,      // Ruta terminada exitosamente  
    CANCELADA,       // Ruta cancelada
    FALLIDA          // Ruta falló (avería, sin combustible, etc.)
}
