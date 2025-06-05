package com.plg.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;

import com.plg.domain.Almacen;
import com.plg.repository.AlmacenRepository;

@Service
public class AlmacenService {
    @Autowired
    private AlmacenRepository almacenRepository;
    private static final Logger logger = LoggerFactory.getLogger(AlmacenService.class);
    public List<Almacen> obtenerTodos() {
        return almacenRepository.findAll();
    }
    public void procesarArchivo(MultipartFile file) throws IOException {
        List<Almacen> almacenes = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String linea;
            while ((linea = reader.readLine()) != null) {
                // soporte para , ; | o tabulador como separadores
                String[] partes = linea.split("[,;|\\t]");
                if (partes.length >= 6) {
                    Almacen a = new Almacen();
                    a.setTipo(partes[0].trim());
                    a.setX(Integer.parseInt(partes[1].trim()));
                    a.setY(Integer.parseInt(partes[2].trim()));
                    a.setCapacidad(Double.parseDouble(partes[3].trim()));
                    a.setHora_abastecimiento(LocalTime.parse(partes[4].trim())); // formato HH:mm
                    a.setEsPrincipal(Integer.parseInt(partes[5].trim()));
                    almacenes.add(a);
                }
            }
        }
        almacenRepository.saveAll(almacenes);
    }

    /**
     * Elimina todos los almacenes
     */
    public void eliminarTodos() {
        try {
            almacenRepository.deleteAll();
            logger.info("✅ Todos los almacenes eliminados correctamente");
        } catch (Exception e) {
            logger.error("❌ Error eliminando almacenes: {}", e.getMessage(), e);
            throw new RuntimeException("Error eliminando almacenes", e);
        }
    }

    /**
     * Guarda un almacén individual
     */
    public Almacen guardar(Almacen almacen) {
        try {
            Almacen almacenGuardado = almacenRepository.save(almacen);
            logger.debug("✅ Almacén guardado: {} en ({},{})", 
                        almacenGuardado.getTipo(), 
                        almacenGuardado.getX(), 
                        almacenGuardado.getY());
            return almacenGuardado;
        } catch (Exception e) {
            logger.error("❌ Error guardando almacén {}: {}", almacen.getTipo(), e.getMessage(), e);
            throw new RuntimeException("Error guardando almacén", e);
        }
    }

}