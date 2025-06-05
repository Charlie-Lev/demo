// package com.plg.controller;

// import com.plg.service.RutaReporteService;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.http.ResponseEntity;
// import org.springframework.web.bind.annotation.*;

// /**
//  * Controller para generar reportes de rutas
//  */
// @RestController
// @RequestMapping("/api/reportes")
// @CrossOrigin(origins = "*")
// public class RutaReporteController {
    
//     private static final Logger logger = LoggerFactory.getLogger(RutaReporteController.class);
    
//     @Autowired
//     private RutaReporteService rutaReporteService;
    
//     /**
//      * Genera reporte completo de pedidos críticos
//      */
//     @GetMapping("/criticos")
//     public ResponseEntity<String> generarReporteCriticos(
//             @RequestParam(defaultValue = "20") int limite) {
        
//         try {
//             logger.info("🚨 Solicitando reporte de pedidos críticos con límite: {}", limite);
            
//             String reporte = rutaReporteService.generarReportePedidosCriticos(limite);
            
//             logger.info("✅ Reporte de pedidos críticos generado exitosamente");
//             return ResponseEntity.ok(reporte);
            
//         } catch (Exception e) {
//             logger.error("❌ Error generando reporte de críticos: {}", e.getMessage(), e);
//             return ResponseEntity.status(500).body("❌ Error: " + e.getMessage());
//         }
//     }
    
//     /**
//      * Genera reporte simplificado (formato exacto solicitado)
//      */
//     @GetMapping("/criticos/simple")
//     public ResponseEntity<String> generarReporteSimple(
//             @RequestParam(defaultValue = "20") int limite) {
        
//         try {
//             logger.info("📋 Solicitando reporte simplificado de pedidos críticos con límite: {}", limite);
            
//             String reporte = rutaReporteService.generarReporteSimplificado(limite);
            
//             logger.info("✅ Reporte simplificado de pedidos críticos generado exitosamente");
//             return ResponseEntity.ok(reporte);
            
//         } catch (Exception e) {
//             logger.error("❌ Error generando reporte simple de críticos: {}", e.getMessage(), e);
//             return ResponseEntity.status(500).body("❌ Error: " + e.getMessage());
//         }
//     }
//     @GetMapping("/criticos/filtrado")
//     public ResponseEntity<String> generarReporteFiltrado(
//             @RequestParam(defaultValue = "20") int limite) {
        
//         try {
//             logger.info("🔍 Solicitando reporte filtrado de rutas con pedidos críticos con límite: {}", limite);
            
//             String reporte = rutaReporteService.generarReporteFiltrado(limite);
            
//             logger.info("✅ Reporte filtrado de pedidos críticos generado exitosamente");
//             return ResponseEntity.ok(reporte);
            
//         } catch (Exception e) {
//             logger.error("❌ Error generando reporte filtrado de críticos: {}", e.getMessage(), e);
//             return ResponseEntity.status(500).body("❌ Error: " + e.getMessage());
//         }
//     }
//     /**
//      * DIAGNÓSTICO: Genera reporte de asignación de pedidos críticos (sin rutas)
//      * Para analizar si el problema está en planificación de pedidos o de rutas
//      */
//     @GetMapping("/criticos/diagnostico")
//     public ResponseEntity<String> diagnosticarPlanificacionPedidos(
//             @RequestParam(defaultValue = "20") int limite) {
        
//         try {
//             logger.info("🔍 DIAGNÓSTICO: Analizando planificación de pedidos críticos con límite: {}", limite);
            
//             String diagnostico = rutaReporteService.generarDiagnosticoPlanificacionPedidos(limite);
            
//             logger.info("✅ Diagnóstico de planificación de pedidos completado");
//             return ResponseEntity.ok(diagnostico);
            
//         } catch (Exception e) {
//             logger.error("❌ Error en diagnóstico de planificación: {}", e.getMessage(), e);
//             return ResponseEntity.status(500).body("❌ Error en diagnóstico: " + e.getMessage());
//         }
//     }

//     /**
//      * DIAGNÓSTICO: Analiza la planificación de rutas desde asignaciones existentes
//      */
//     @GetMapping("/criticos/diagnostico-rutas")
//     public ResponseEntity<String> diagnosticarPlanificacionRutas(
//             @RequestParam(defaultValue = "20") int limite) {
        
//         try {
//             logger.info("🗺️ DIAGNÓSTICO: Analizando planificación de rutas críticas con límite: {}", limite);
            
//             String diagnostico = rutaReporteService.generarDiagnosticoRutas(limite);
            
//             logger.info("✅ Diagnóstico de planificación de rutas completado");
//             return ResponseEntity.ok(diagnostico);
            
//         } catch (Exception e) {
//             logger.error("❌ Error en diagnóstico de rutas: {}", e.getMessage(), e);
//             return ResponseEntity.status(500).body("❌ Error en diagnóstico de rutas: " + e.getMessage());
//         }
//     }

//     /**
//      * DIAGNÓSTICO: Verifica el estado y funcionamiento del servicio A*
//      */
//     @GetMapping("/diagnostico-aestrella")
//     public ResponseEntity<String> diagnosticarAEstrella() {
        
//         try {
//             logger.info("🎯 DIAGNÓSTICO: Verificando servicio A*...");
            
//             String diagnostico = rutaReporteService.generarDiagnosticoAEstrella();
            
//             logger.info("✅ Diagnóstico A* completado");
//             return ResponseEntity.ok(diagnostico);
            
//         } catch (Exception e) {
//             logger.error("❌ Error en diagnóstico A*: {}", e.getMessage(), e);
//             return ResponseEntity.status(500).body("❌ Error en diagnóstico A*: " + e.getMessage());
//         }
//     }
//     /**
//      * DIAGNÓSTICO: Analiza cómo los bloqueos activos afectan rutas específicas
//      */
//     @GetMapping("/diagnostico-bloqueos-rutas")
//     public ResponseEntity<String> diagnosticarBloqueos() {
        
//         try {
//             logger.info("🚧 DIAGNÓSTICO: Analizando impacto de bloqueos en rutas...");
            
//             String diagnostico = rutaReporteService.generarDiagnosticoBloqueos();
            
//             logger.info("✅ Diagnóstico de bloqueos completado");
//             return ResponseEntity.ok(diagnostico);
            
//         } catch (Exception e) {
//             logger.error("❌ Error en diagnóstico de bloqueos: {}", e.getMessage(), e);
//             return ResponseEntity.status(500).body("❌ Error en diagnóstico de bloqueos: " + e.getMessage());
//         }
//     }
//     @GetMapping("/diagnostico-ruta-especifica")
//     public ResponseEntity<String> diagnosticarRutaEspecifica(
//             @RequestParam(defaultValue = "12") int origenX,
//             @RequestParam(defaultValue = "8") int origenY,
//             @RequestParam(defaultValue = "16") int destinoX,
//             @RequestParam(defaultValue = "13") int destinoY) {
        
//         try {
//             logger.info("🔍 DIAGNÓSTICO: Analizando ruta específica ({},{}) → ({},{})", 
//                     origenX, origenY, destinoX, destinoY);
            
//             String diagnostico = rutaReporteService.generarDiagnosticoRutaEspecifica(
//                 origenX, origenY, destinoX, destinoY);
            
//             logger.info("✅ Diagnóstico de ruta específica completado");
//             return ResponseEntity.ok(diagnostico);
            
//         } catch (Exception e) {
//             logger.error("❌ Error en diagnóstico de ruta específica: {}", e.getMessage(), e);
//             return ResponseEntity.status(500).body("❌ Error: " + e.getMessage());
//         }
//     }
// }