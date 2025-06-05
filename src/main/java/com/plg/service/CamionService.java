package com.plg.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.plg.domain.Camion;
import com.plg.repository.CamionRepository;

@Service
public class CamionService {

    @Autowired
    private CamionRepository camionRepository;
    private static final Logger logger = LoggerFactory.getLogger(CamionService.class);

    public void procesarArchivoCamiones(MultipartFile file) throws IOException {
        List<Camion> camiones = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String linea;
            while ((linea = reader.readLine()) != null) {
                // Ignorar cabecera si es necesario
                if (linea.trim().startsWith("codigo")) continue;

                String[] partes = linea.split("[,;|\\t]");
                if (partes.length >= 6) {
                    Camion c = new Camion();
                    c.setCodigo(partes[0].trim());
                    c.setTipo(partes[1].trim());
                    c.setPesoBruto(Double.parseDouble(partes[2].trim()));          
                    c.setMaxCargaM3(Integer.parseInt(partes[3].trim()));            
                    c.setConsumoGalones(Integer.parseInt(partes[4].trim()));        
                    c.setVelocidadKmph(Integer.parseInt(partes[5].trim()));         
                    camiones.add(c);
                }
            }
        }

        camionRepository.saveAll(camiones);
    }

    public List<Camion> obtenerTodos() {
        return camionRepository.findAll();
    }

    public void eliminarTodos() {
        try {
            camionRepository.deleteAll();
            logger.info("✅ Todos los camiones eliminados correctamente");
        } catch (Exception e) {
            logger.error("❌ Error eliminando camiones: {}", e.getMessage(), e);
            throw new RuntimeException("Error eliminando camiones", e);
        }
    }

    /**
     * Guarda un camión individual
     */
    public Camion guardar(Camion camion) {
        try {
            Camion camionGuardado = camionRepository.save(camion);
            logger.debug("✅ Camión guardado: {} ({})", camionGuardado.getCodigo(), camionGuardado.getTipo());
            return camionGuardado;
        } catch (Exception e) {
            logger.error("❌ Error guardando camión {}: {}", camion.getCodigo(), e.getMessage(), e);
            throw new RuntimeException("Error guardando camión", e);
        }
    }
}
