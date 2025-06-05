// package com.plg.service.adapter;

// import com.plg.domain.Obstaculo;
// import com.plg.service.util.GestorObstaculos;
// import com.plg.service.util.GestorObstaculosTemporales;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.stereotype.Component;

// import java.io.File;
// import java.io.FileWriter;
// import java.io.IOException;
// import java.nio.file.Files;
// import java.nio.file.Path;
// import java.util.List;

// /**
//  * ✅ ADAPTER: Permite que GestorObstaculos existente funcione con obstáculos de BD
//  * 
//  * Este adapter convierte obstáculos de la base de datos al formato que espera
//  * el GestorObstaculos original (archivo de texto).
//  */
// @Component
// public class GestorObstaculosAdapter implements ConfigurableGestorObstaculos {
    
//     private static final Logger logger = LoggerFactory.getLogger(GestorObstaculosAdapter.class);
    
//     @Autowired
//     private GestorObstaculos gestorObstaculosOriginal;
    
//     @Autowired
//     private GestorObstaculosTemporales gestorTemporales;
    
//     private Path archivoTemporal;
    
//     /**
//      * ✅ Implementa la interfaz ConfigurableGestorObstaculos
//      */
//     @Override
//     public void inicializarDesdeObstaculos(List<Obstaculo> obstaculos) {
//         try {
//             logger.info("🔧 Adaptando {} obstáculos de BD a formato de archivo...", obstaculos.size());
            
//             // 1. Crear archivo temporal con obstáculos
//             archivoTemporal = crearArchivoTemporalObstaculos(obstaculos);
            
//             // 2. Inicializar el gestor original con el archivo temporal
//             if (gestorObstaculosOriginal != null) {
//                 gestorObstaculosOriginal.inicializarMapa(archivoTemporal.toString());
//                 logger.info("✅ GestorObstaculos original inicializado con {} obstáculos", obstaculos.size());
//             }
            
//         } catch (Exception e) {
//             logger.error("❌ Error adaptando obstáculos de BD: {}", e.getMessage(), e);
//             throw new RuntimeException("Fallo en adapter de obstáculos", e);
//         }
//     }
    
//     /**
//      * ✅ Crea archivo temporal con formato compatible con GestorObstaculos original
//      */
//     private Path crearArchivoTemporalObstaculos(List<Obstaculo> obstaculos) throws IOException {
//         Path tempFile = Files.createTempFile("obstaculos_bd_", ".txt");
//         logger.debug("📄 Creando archivo temporal: {}", tempFile);
        
//         try (FileWriter writer = new FileWriter(tempFile.toFile())) {
//             // Escribir header
//             writer.write("# Obstáculos generados desde base de datos\n");
//             writer.write("# Formato: TIPO X Y [X2 Y2] [PUNTOS_POLIGONO]\n");
//             writer.write("# Generado automáticamente - no editar\n\n");
            
//             // Escribir cada obstáculo
//             for (Obstaculo obs : obstaculos) {
//                 String linea = convertirObstaculoALinea(obs);
//                 if (linea != null && !linea.trim().isEmpty()) {
//                     writer.write(linea + "\n");
//                 }
//             }
            
//             writer.flush();
//         }
        
//         logger.debug("✅ Archivo temporal creado con {} obstáculos", obstaculos.size());
//         return tempFile;
//     }
    
//     /**
//      * ✅ Convierte un obstáculo de BD a línea de archivo
//      */
//     private String convertirObstaculoALinea(Obstaculo obs) {
//         if (obs == null || obs.getTipo() == null) {
//             return null;
//         }
        
//         StringBuilder sb = new StringBuilder();
        
//         switch (obs.getTipo().toUpperCase()) {
//             case "PUNTO":
//                 if (obs.getCoordenadaX() != null && obs.getCoordenadaY() != null) {
//                     sb.append("PUNTO ").append(obs.getCoordenadaX()).append(" ").append(obs.getCoordenadaY());
//                 }
//                 break;
                
//             case "LINEA_H":
//                 if (obs.getCoordenadaX() != null && obs.getCoordenadaY() != null && 
//                     obs.getCoordenadaX2() != null && obs.getCoordenadaY2() != null) {
//                     sb.append("LINEA_H ")
//                       .append(obs.getCoordenadaX()).append(" ").append(obs.getCoordenadaY()).append(" ")
//                       .append(obs.getCoordenadaX2()).append(" ").append(obs.getCoordenadaY2());
//                 }
//                 break;
                
//             case "LINEA_V":
//                 if (obs.getCoordenadaX() != null && obs.getCoordenadaY() != null && 
//                     obs.getCoordenadaX2() != null && obs.getCoordenadaY2() != null) {
//                     sb.append("LINEA_V ")
//                       .append(obs.getCoordenadaX()).append(" ").append(obs.getCoordenadaY()).append(" ")
//                       .append(obs.getCoordenadaX2()).append(" ").append(obs.getCoordenadaY2());
//                 }
//                 break;
                
//             case "POLIGONO":
//             case "BLOQUEO_TEMPORAL":
//                 if (obs.getPuntosPoligono() != null && !obs.getPuntosPoligono().trim().isEmpty()) {
//                     sb.append("POLIGONO ").append(obs.getPuntosPoligono());
//                 } else if (obs.getCoordenadaX() != null && obs.getCoordenadaY() != null) {
//                     // Fallback: usar como punto si no hay polígono definido
//                     sb.append("PUNTO ").append(obs.getCoordenadaX()).append(" ").append(obs.getCoordenadaY());
//                 }
//                 break;
                
//             default:
//                 logger.warn("⚠️ Tipo de obstáculo desconocido: {}", obs.getTipo());
//                 return null;
//         }
        
//         // Agregar descripción como comentario si existe
//         if (obs.getDescripcion() != null && !obs.getDescripcion().trim().isEmpty()) {
//             sb.append(" # ").append(obs.getDescripcion().replaceAll("\n", " "));
//         }
        
//         return sb.toString();
//     }
    
//     /**
//      * ✅ Actualiza obstáculos en tiempo real
//      */
//     public void actualizarObstaculosVigentes() {
//         try {
//             logger.debug("🔄 Actualizando obstáculos vigentes...");
            
//             // Obtener obstáculos actuales según tiempo de simulación
//             List<Obstaculo> obstaculosVigentes = gestorTemporales.obtenerObstaculosVigentes();
            
//             // Re-inicializar con nuevos obstáculos
//             inicializarDesdeObstaculos(obstaculosVigentes);
            
//             logger.debug("✅ Obstáculos actualizados: {} vigentes", obstaculosVigentes.size());
            
//         } catch (Exception e) {
//             logger.error("❌ Error actualizando obstáculos vigentes: {}", e.getMessage(), e);
//         }
//     }
    
//     /**
//      * ✅ Limpia recursos temporales
//      */
//     public void limpiarRecursos() {
//         if (archivoTemporal != null) {
//             try {
//                 Files.deleteIfExists(archivoTemporal);
//                 logger.debug("🧹 Archivo temporal eliminado: {}", archivoTemporal);
//             } catch (IOException e) {
//                 logger.warn("⚠️ No se pudo eliminar archivo temporal: {}", e.getMessage());
//             }
//         }
//     }
    
//     /**
//      * ✅ Delegación de métodos al gestor original
//      */
//     public boolean hayObstaculoEn(int x, int y) {
//         if (gestorObstaculosOriginal != null) {
//             return gestorObstaculosOriginal.hayObstaculoEn(x, y);
//         }
//         return false;
//     }
    
//     public boolean rutaEsValida(int x1, int y1, int x2, int y2) {
//         if (gestorObstaculosOriginal != null) {
//             return gestorObstaculosOriginal.rutaEsValida(x1, y1, x2, y2);
//         }
//         return true; // Si no hay gestor, asumir que es válida
//     }
    
//     /**
//      * ✅ Método de cleanup automático
//      */
//     @jakarta.annotation.PreDestroy
//     public void cleanup() {
//         limpiarRecursos();
//     }
// }

// /**
//  * ✅ Interfaz para gestores configurables (mover a archivo separado si es necesario)
//  */
// interface ConfigurableGestorObstaculos {
//     void inicializarDesdeObstaculos(List<Obstaculo> obstaculos);
// }