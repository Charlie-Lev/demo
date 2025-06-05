package com.plg.domain.enumeration;

public enum EstadoCamion {
    DISPONIBLE, // El camión está disponible para asignación de rutas
    EN_RUTA,    // El camión está en ruta realizando entregas
    AVERIADO,   // El camión está averiado
    EN_MANTENIMIENTO, // El camión está en mantenimiento
    EN_ALMACEN   // El camión está en la planta esperando carga o mantenimiento
}