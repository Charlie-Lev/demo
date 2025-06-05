package com.plg.service.util;

import com.plg.domain.Punto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Gestor para manejo del mapa cartesiano con obstáculos
 * Maneja líneas horizontales, verticales y polígonos abiertos
 */
@Component
public class GestorObstaculos {
    
    private static final Logger logger = LoggerFactory.getLogger(GestorObstaculos.class);
    
    // Configuración del grid
    private int gridMinX = 0;
    private int gridMinY = 0;
    private int gridMaxX = 1000;
    private int gridMaxY = 1000;
    
    // Almacenamiento de obstáculos
    private Set<Punto> puntosObstruidos;
    private List<LineaObstaculo> lineasHorizontales;
    private List<LineaObstaculo> lineasVerticales;
    private List<PoligonoAbierto> poligonosAbiertos;
    
    // Cache de validaciones
    private Map<Punto, Boolean> cacheValidacionPuntos;
    private boolean mapaInicializado;
    
    public GestorObstaculos() {
        this.puntosObstruidos = new HashSet<>();
        this.lineasHorizontales = new ArrayList<>();
        this.lineasVerticales = new ArrayList<>();
        this.poligonosAbiertos = new ArrayList<>();
        this.cacheValidacionPuntos = new HashMap<>();
        this.mapaInicializado = false;
    }
    
    /**
     * Inicializa el mapa con configuración desde archivo
     */
    public void inicializarMapa(String rutaArchivoConfiguracion) {
        logger.info("Inicializando mapa de obstáculos desde: {}", rutaArchivoConfiguracion);
        
        try {
            cargarConfiguracionDesdeArchivo(rutaArchivoConfiguracion);
            procesarObstaculos();
            generarPuntosObstruidos();
            this.mapaInicializado = true;
            
            logger.info("Mapa inicializado. Puntos obstruidos: {}, Grid: ({},{}) a ({},{})", 
                puntosObstruidos.size(), gridMinX, gridMinY, gridMaxX, gridMaxY);
                
        } catch (Exception e) {
            logger.error("Error inicializando mapa de obstáculos: {}", e.getMessage(), e);
            throw new RuntimeException("Fallo en inicialización de mapa", e);
        }
    }
    
    /**
     * Inicialización programática para testing
     */
    public void inicializarMapaProgramatico(int minX, int minY, int maxX, int maxY) {
        this.gridMinX = minX;
        this.gridMinY = minY;
        this.gridMaxX = maxX;
        this.gridMaxY = maxY;
        this.mapaInicializado = true;
        
        logger.debug("Mapa inicializado programáticamente: ({},{}) a ({},{})", minX, minY, maxX, maxY);
    }
    
    /**
     * Verifica si un punto es válido (no obstruido y dentro de límites)
     */
    public boolean esPuntoValido(Punto punto) {
        if (!mapaInicializado) {
            throw new IllegalStateException("Mapa no inicializado");
        }
        
        // Verificar cache primero
        Boolean resultadoCache = cacheValidacionPuntos.get(punto);
        if (resultadoCache != null) {
            return resultadoCache;
        }
        
        // Verificar límites del grid
        if (!punto.estaEnLimites(gridMinX, gridMinY, gridMaxX, gridMaxY)) {
            cacheValidacionPuntos.put(punto.clonar(), false);
            return false;
        }
        
        // Verificar si está obstruido
        boolean esValido = !puntosObstruidos.contains(punto);
        cacheValidacionPuntos.put(punto.clonar(), esValido);
        
        return esValido;
    }
    
    /**
     * Obtiene todos los puntos adyacentes válidos para movimiento
     */
    public List<Punto> obtenerPuntosAdyacentesValidos(Punto punto) {
        List<Punto> puntosValidos = new ArrayList<>();
        
        for (Punto adyacente : punto.obtenerPuntosAdyacentes()) {
            if (esPuntoValido(adyacente)) {
                puntosValidos.add(adyacente);
            }
        }
        
        return puntosValidos;
    }
    
    /**
     * Verifica si existe un camino directo entre dos puntos (sin obstáculos)
     */
    public boolean existeCaminoDirecto(Punto origen, Punto destino) {
        // Para grid cartesiano, verificar línea Manhattan
        int deltaX = Integer.compare(destino.getX(), origen.getX());
        int deltaY = Integer.compare(destino.getY(), origen.getY());
        
        Punto actual = origen.clonar();
        
        // Movimiento horizontal primero
        while (actual.getX() != destino.getX()) {
            actual.setX(actual.getX() + deltaX);
            if (!esPuntoValido(actual)) {
                return false;
            }
        }
        
        // Movimiento vertical después
        while (actual.getY() != destino.getY()) {
            actual.setY(actual.getY() + deltaY);
            if (!esPuntoValido(actual)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Agrega obstáculo puntual
     */
    public void agregarObstaculoPuntual(Punto punto) {
        puntosObstruidos.add(punto.clonar());
        cacheValidacionPuntos.clear(); // Invalidar cache
        logger.debug("Agregado obstáculo puntual en: {}", punto);
    }
    
    /**
     * Agrega línea horizontal como obstáculo
     */
    public void agregarLineaHorizontal(int y, int xInicio, int xFin) {
        LineaObstaculo linea = new LineaObstaculo(TipoLinea.HORIZONTAL, y, xInicio, xFin);
        lineasHorizontales.add(linea);
        
        // Generar puntos obstruidos para esta línea
        for (int x = Math.min(xInicio, xFin); x <= Math.max(xInicio, xFin); x++) {
            puntosObstruidos.add(new Punto(x, y));
        }
        
        cacheValidacionPuntos.clear();
        logger.debug("Agregada línea horizontal: y={}, x=[{},{}]", y, xInicio, xFin);
    }
    
    /**
     * Agrega línea vertical como obstáculo
     */
    public void agregarLineaVertical(int x, int yInicio, int yFin) {
        LineaObstaculo linea = new LineaObstaculo(TipoLinea.VERTICAL, x, yInicio, yFin);
        lineasVerticales.add(linea);
        
        // Generar puntos obstruidos para esta línea
        for (int y = Math.min(yInicio, yFin); y <= Math.max(yInicio, yFin); y++) {
            puntosObstruidos.add(new Punto(x, y));
        }
        
        cacheValidacionPuntos.clear();
        logger.debug("Agregada línea vertical: x={}, y=[{},{}]", x, yInicio, yFin);
    }
    
    /**
     * Agrega polígono abierto (secuencia de líneas conectadas)
     */
    public void agregarPoligonoAbierto(List<Punto> vertices) {
        if (vertices.size() < 2) {
            logger.warn("Polígono abierto requiere al menos 2 vértices");
            return;
        }
        
        PoligonoAbierto poligono = new PoligonoAbierto(new ArrayList<>(vertices));
        poligonosAbiertos.add(poligono);
        
        // Generar obstáculos para cada segmento del polígono
        for (int i = 0; i < vertices.size() - 1; i++) {
            Punto p1 = vertices.get(i);
            Punto p2 = vertices.get(i + 1);
            generarObstaculosSegmento(p1, p2);
        }
        
        cacheValidacionPuntos.clear();
        logger.debug("Agregado polígono abierto con {} vértices", vertices.size());
    }
    
    /**
     * Obtiene estadísticas del mapa
     */
    public EstadisticasMapa obtenerEstadisticas() {
        EstadisticasMapa stats = new EstadisticasMapa();
        stats.setGridMinX(gridMinX);
        stats.setGridMinY(gridMinY);
        stats.setGridMaxX(gridMaxX);
        stats.setGridMaxY(gridMaxY);
        stats.setPuntosObstruidos(puntosObstruidos.size());
        stats.setLineasHorizontales(lineasHorizontales.size());
        stats.setLineasVerticales(lineasVerticales.size());
        stats.setPoligonosAbiertos(poligonosAbiertos.size());
        stats.setTamanoCacheValidacion(cacheValidacionPuntos.size());
        
        // Calcular porcentaje de obstáculos
        long totalPuntos = (long)(gridMaxX - gridMinX + 1) * (gridMaxY - gridMinY + 1);
        stats.setPorcentajeObstruccion((double)puntosObstruidos.size() / totalPuntos * 100.0);
        
        return stats;
    }
    
    /**
     * Limpia todos los obstáculos
     */
    public void limpiarObstaculos() {
        puntosObstruidos.clear();
        lineasHorizontales.clear();
        lineasVerticales.clear();
        poligonosAbiertos.clear();
        cacheValidacionPuntos.clear();
        logger.info("Obstáculos limpiados");
    }
    
    // Métodos privados
    
    private void cargarConfiguracionDesdeArchivo(String rutaArchivo) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(rutaArchivo))) {
            String linea;
            
            while ((linea = reader.readLine()) != null) {
                linea = linea.trim();
                if (linea.isEmpty() || linea.startsWith("#")) {
                    continue; // Ignorar comentarios y líneas vacías
                }
                
                procesarLineaConfiguracion(linea);
            }
        }
    }
    
    private void procesarLineaConfiguracion(String linea) {
        String[] partes = linea.split(":");
        if (partes.length < 2) return;
        
        String tipo = partes[0].trim().toUpperCase();
        String datos = partes[1].trim();
        
        switch (tipo) {
            case "GRID" -> procesarConfiguracionGrid(datos);
            case "PUNTO" -> procesarObstaculoPunto(datos);
            case "LINEA_H" -> procesarLineaHorizontal(datos);
            case "LINEA_V" -> procesarLineaVertical(datos);
            case "POLIGONO" -> procesarPoligono(datos);
            default -> logger.warn("Tipo de configuración desconocido: {}", tipo);
        }
    }
    
    private void procesarConfiguracionGrid(String datos) {
        String[] coords = datos.split(",");
        if (coords.length == 4) {
            gridMinX = Integer.parseInt(coords[0].trim());
            gridMinY = Integer.parseInt(coords[1].trim());
            gridMaxX = Integer.parseInt(coords[2].trim());
            gridMaxY = Integer.parseInt(coords[3].trim());
        }
    }
    
    private void procesarObstaculoPunto(String datos) {
        String[] coords = datos.split(",");
        if (coords.length == 2) {
            int x = Integer.parseInt(coords[0].trim());
            int y = Integer.parseInt(coords[1].trim());
            agregarObstaculoPuntual(new Punto(x, y));
        }
    }
    
    private void procesarLineaHorizontal(String datos) {
        String[] coords = datos.split(",");
        if (coords.length == 3) {
            int y = Integer.parseInt(coords[0].trim());
            int xInicio = Integer.parseInt(coords[1].trim());
            int xFin = Integer.parseInt(coords[2].trim());
            agregarLineaHorizontal(y, xInicio, xFin);
        }
    }
    
    private void procesarLineaVertical(String datos) {
        String[] coords = datos.split(",");
        if (coords.length == 3) {
            int x = Integer.parseInt(coords[0].trim());
            int yInicio = Integer.parseInt(coords[1].trim());
            int yFin = Integer.parseInt(coords[2].trim());
            agregarLineaVertical(x, yInicio, yFin);
        }
    }
    
    private void procesarPoligono(String datos) {
        String[] coordenadas = datos.split(";");
        List<Punto> vertices = new ArrayList<>();
        
        for (String coordenada : coordenadas) {
            String[] xy = coordenada.trim().split(",");
            if (xy.length == 2) {
                int x = Integer.parseInt(xy[0].trim());
                int y = Integer.parseInt(xy[1].trim());
                vertices.add(new Punto(x, y));
            }
        }
        
        if (!vertices.isEmpty()) {
            agregarPoligonoAbierto(vertices);
        }
    }
    
    private void procesarObstaculos() {
        // Procesar líneas y polígonos ya se hace en los métodos agregar
        logger.debug("Procesamiento de obstáculos completado");
    }
    
    private void generarPuntosObstruidos() {
        // Los puntos ya se generan en los métodos agregar
        logger.debug("Generación de puntos obstruidos completada. Total: {}", puntosObstruidos.size());
    }
    
    private void generarObstaculosSegmento(Punto p1, Punto p2) {
        // Generar línea entre dos puntos usando algoritmo de Bresenham simplificado
        int dx = Math.abs(p2.getX() - p1.getX());
        int dy = Math.abs(p2.getY() - p1.getY());
        
        int x = p1.getX();
        int y = p1.getY();
        
        int stepX = (p1.getX() < p2.getX()) ? 1 : -1;
        int stepY = (p1.getY() < p2.getY()) ? 1 : -1;
        
        if (dx > dy) {
            // Línea más horizontal
            int error = dx / 2;
            while (x != p2.getX()) {
                puntosObstruidos.add(new Punto(x, y));
                error -= dy;
                if (error < 0) {
                    y += stepY;
                    error += dx;
                }
                x += stepX;
            }
        } else {
            // Línea más vertical
            int error = dy / 2;
            while (y != p2.getY()) {
                puntosObstruidos.add(new Punto(x, y));
                error -= dx;
                if (error < 0) {
                    x += stepX;
                    error += dy;
                }
                y += stepY;
            }
        }
        
        // Agregar punto final
        puntosObstruidos.add(p2.clonar());
    }
    
    // Getters para acceso controlado
    public int getGridMinX() { return gridMinX; }
    public int getGridMinY() { return gridMinY; }
    public int getGridMaxX() { return gridMaxX; }
    public int getGridMaxY() { return gridMaxY; }
    public boolean isMapaInicializado() { return mapaInicializado; }
    
    // Clases auxiliares
    
    private static class LineaObstaculo {
        private TipoLinea tipo;
        private int coordenadaFija; // Y para horizontal, X para vertical
        private int inicio;
        private int fin;
        
        public LineaObstaculo(TipoLinea tipo, int coordenadaFija, int inicio, int fin) {
            this.tipo = tipo;
            this.coordenadaFija = coordenadaFija;
            this.inicio = Math.min(inicio, fin);
            this.fin = Math.max(inicio, fin);
        }
        
        // Getters
        public TipoLinea getTipo() { return tipo; }
        public int getCoordenadaFija() { return coordenadaFija; }
        public int getInicio() { return inicio; }
        public int getFin() { return fin; }
    }
    
    private static class PoligonoAbierto {
        private List<Punto> vertices;
        
        public PoligonoAbierto(List<Punto> vertices) {
            this.vertices = vertices;
        }
        
        public List<Punto> getVertices() { return vertices; }
    }
    
    private enum TipoLinea {
        HORIZONTAL, VERTICAL
    }
    
    public static class EstadisticasMapa {
        private int gridMinX, gridMinY, gridMaxX, gridMaxY;
        private int puntosObstruidos;
        private int lineasHorizontales;
        private int lineasVerticales;
        private int poligonosAbiertos;
        private int tamanoCacheValidacion;
        private double porcentajeObstruccion;
        
        // Getters y setters
        public int getGridMinX() { return gridMinX; }
        public void setGridMinX(int gridMinX) { this.gridMinX = gridMinX; }
        
        public int getGridMinY() { return gridMinY; }
        public void setGridMinY(int gridMinY) { this.gridMinY = gridMinY; }
        
        public int getGridMaxX() { return gridMaxX; }
        public void setGridMaxX(int gridMaxX) { this.gridMaxX = gridMaxX; }
        
        public int getGridMaxY() { return gridMaxY; }
        public void setGridMaxY(int gridMaxY) { this.gridMaxY = gridMaxY; }
        
        public int getPuntosObstruidos() { return puntosObstruidos; }
        public void setPuntosObstruidos(int puntosObstruidos) { this.puntosObstruidos = puntosObstruidos; }
        
        public int getLineasHorizontales() { return lineasHorizontales; }
        public void setLineasHorizontales(int lineasHorizontales) { this.lineasHorizontales = lineasHorizontales; }
        
        public int getLineasVerticales() { return lineasVerticales; }
        public void setLineasVerticales(int lineasVerticales) { this.lineasVerticales = lineasVerticales; }
        
        public int getPoligonosAbiertos() { return poligonosAbiertos; }
        public void setPoligonosAbiertos(int poligonosAbiertos) { this.poligonosAbiertos = poligonosAbiertos; }
        
        public int getTamanoCacheValidacion() { return tamanoCacheValidacion; }
        public void setTamanoCacheValidacion(int tamanoCacheValidacion) { this.tamanoCacheValidacion = tamanoCacheValidacion; }
        
        public double getPorcentajeObstruccion() { return porcentajeObstruccion; }
        public void setPorcentajeObstruccion(double porcentajeObstruccion) { this.porcentajeObstruccion = porcentajeObstruccion; }
        
        @Override
        public String toString() {
            return String.format("Grid: (%d,%d) a (%d,%d), Obstáculos: %d (%.2f%%), Cache: %d",
                gridMinX, gridMinY, gridMaxX, gridMaxY, puntosObstruidos, porcentajeObstruccion, tamanoCacheValidacion);
        }
    }
}