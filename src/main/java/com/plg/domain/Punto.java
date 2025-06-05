package com.plg.domain;

import lombok.Data;

/**
 * Representa un punto en el grid cartesiano
 * Utilizado para navegación, pathfinding y posicionamiento
 */
@Data
public class Punto {
    
    private int x;
    private int y;
    
    public Punto() {}
    
    public Punto(int x, int y) {
        this.x = x;
        this.y = y;
    }
    
    /**
     * Calcula la distancia Manhattan hasta otro punto
     */
    public int distanciaManhattanHasta(Punto otro) {
        return Math.abs(this.x - otro.x) + Math.abs(this.y - otro.y);
    }
    
    /**
     * Calcula la distancia euclidiana hasta otro punto
     */
    public double distanciaEuclidianaHasta(Punto otro) {
        int dx = this.x - otro.x;
        int dy = this.y - otro.y;
        return Math.sqrt(dx * dx + dy * dy);
    }
    
    /**
     * Verifica si este punto es adyacente a otro (movimientos válidos)
     */
    public boolean esAdyacenteA(Punto otro) {
        int distancia = distanciaManhattanHasta(otro);
        return distancia == 1;
    }
    
    /**
     * Obtiene los puntos adyacentes válidos (arriba, abajo, izquierda, derecha)
     */
    public Punto[] obtenerPuntosAdyacentes() {
        return new Punto[] {
            new Punto(x, y + 1),    // Arriba
            new Punto(x, y - 1),    // Abajo
            new Punto(x - 1, y),    // Izquierda
            new Punto(x + 1, y)     // Derecha
        };
    }
    
    /**
     * Verifica si el punto está dentro de los límites del grid
     */
    public boolean estaEnLimites(int minX, int minY, int maxX, int maxY) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY;
    }
    
    /**
     * Crea una copia del punto
     */
    public Punto clonar() {
        return new Punto(this.x, this.y);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        Punto punto = (Punto) obj;
        return x == punto.x && y == punto.y;
    }
    
    @Override
    public int hashCode() {
        return 31 * x + y;
    }
    
    @Override
    public String toString() {
        return String.format("(%d,%d)", x, y);
    }
}