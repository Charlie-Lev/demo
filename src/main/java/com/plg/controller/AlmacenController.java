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

import com.plg.domain.Almacen;
import com.plg.service.AlmacenService;
@RestController
@RequestMapping("/api/almacenes")
public class AlmacenController {

    @Autowired
    private AlmacenService almacenService;

    @GetMapping
    public List<Almacen> obtenerAlmacenes() {
        return almacenService.obtenerTodos();
    }

    @PostMapping("/cargar")
    public ResponseEntity<String> cargarDesdeArchivo(@RequestParam("file") MultipartFile file) {
        try {
            almacenService.procesarArchivo(file);
            return ResponseEntity.ok("Archivo procesado y almacenes guardados.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al procesar archivo.");
        }
    }
}