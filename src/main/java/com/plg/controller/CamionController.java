package com.plg.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.plg.domain.Camion;
import com.plg.service.CamionService;
@RestController
@RequestMapping("/api/camiones")
public class CamionController {

    @Autowired
    private CamionService camionService;

    @PostMapping("/cargar")
    public ResponseEntity<String> cargarDesdeArchivo(@RequestParam("file") MultipartFile file) {
        try {
            camionService.procesarArchivoCamiones(file);
            return ResponseEntity.ok("Camiones cargados exitosamente.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al procesar archivo de camiones.");
        }
    }

    @GetMapping
    public List<Camion> obtenerCamiones() {
        return camionService.obtenerTodos();
    }
}