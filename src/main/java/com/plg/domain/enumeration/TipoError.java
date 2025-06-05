package com.plg.domain.enumeration;

/**
 * Tipos de errores en el cálculo A*
 */
public enum TipoError {
    RUTA_NO_EXISTE,          // No hay camino posible entre origen y destino
    TIMEOUT,                 // Se agotó el tiempo de cálculo
    MEMORIA_INSUFICIENTE,    // No hay memoria suficiente para el cálculo
    ORIGEN_INVALIDO,         // Punto de origen no válido
    DESTINO_INVALIDO,        // Punto de destino no válido
    CONFIGURACION_INVALIDA,  // Configuración del algoritmo inválida
    ERROR_INTERNO,           // Error interno del algoritmo
    MAPA_NO_DISPONIBLE      // Mapa de obstáculos no inicializado
}