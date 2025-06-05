package com.plg.controller;

import com.plg.service.ClienteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/clientes")
@CrossOrigin(origins = "*")
public class ClienteController {
    
    @Autowired
    private ClienteService clienteService;
    
    @GetMapping("/count")
    public ResponseEntity<Map<String, Object>> contarClientes() {
        Map<String, Object> response = new HashMap<>();
        response.put("total", clienteService.contarTotal());
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/recargar")
    public ResponseEntity<Map<String, Object>> recargarClientes() {
        try {
            clienteService.cargarClientesDesdeArchivo();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Clientes recargados exitosamente");
            response.put("total", clienteService.contarTotal());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @GetMapping
    public ResponseEntity<?> listarClientes() {
        return ResponseEntity.ok(clienteService.obtenerTodos());
    }
}