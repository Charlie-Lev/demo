package com.plg.service;

import com.plg.domain.Cliente;
import com.plg.repository.ClienteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class ClienteService {
    
    private static final Logger logger = LoggerFactory.getLogger(ClienteService.class);
    
    @Autowired
    private ClienteRepository clienteRepository;

    public boolean estaVacia() {
        return clienteRepository.count() == 0;
    }

    public Cliente buscarOCrearCliente(int idCliente) {
        return clienteRepository.findById(idCliente)
                .orElseGet(() -> {
                    logger.info("🆕 Creando cliente automáticamente: {}", idCliente);
                    Cliente nuevoCliente = new Cliente();
                    nuevoCliente.setId(idCliente);
                    nuevoCliente.setTipo("c");
                    nuevoCliente.setCodigo("CLI-" + String.format("%03d", idCliente));
                    return clienteRepository.save(nuevoCliente);
                });
    }
    
    public void cargarClientesDesdeArchivo() throws IOException {
        String rutaArchivo = "dataset/cliente/cliente.txt";
        
        logger.info("📁 Cargando clientes desde: {}", rutaArchivo);
        
        try {
            ClassPathResource resource = new ClassPathResource(rutaArchivo);
            
            if (!resource.exists()) {
                logger.warn("⚠️ Archivo no encontrado: {}. Creando clientes automáticamente.", rutaArchivo);
                crearClientesAutomaticos();
                return;
            }
            
            List<Cliente> clientes = new ArrayList<>();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                
                String linea;
                int lineaNumero = 0;
                
                while ((linea = reader.readLine()) != null) {
                    lineaNumero++;
                    linea = linea.trim();
                    
                    if (linea.isEmpty() || linea.startsWith("#")) {
                        continue;
                    }
                    
                    try {
                        Cliente cliente = parsearLineaCliente(linea);
                        if (cliente != null) {
                            clientes.add(cliente);
                        }
                    } catch (Exception e) {
                        logger.error("❌ Error parseando línea {}: '{}' - {}", 
                                    lineaNumero, linea, e.getMessage());
                    }
                }
            }
            
            if (!clientes.isEmpty()) {
                clienteRepository.saveAll(clientes);
                logger.info("✅ Cargados {} clientes desde archivo", clientes.size());
            } else {
                logger.warn("⚠️ No se encontraron clientes válidos en el archivo. Creando automáticamente.");
                crearClientesAutomaticos();
            }
            
        } catch (IOException e) {
            logger.error("❌ Error leyendo archivo de clientes: {}", e.getMessage());
            logger.info("🔄 Creando clientes automáticamente como respaldo");
            crearClientesAutomaticos();
        }
    }
    
    private Cliente parsearLineaCliente(String linea) {
        String[] partes = null;
        
        if (linea.contains(";")) {
            partes = linea.split(";");
        } else if (linea.contains(",")) {
            partes = linea.split(",");
        } else if (linea.contains("|")) {
            partes = linea.split("\\|");
        } else if (linea.contains(" ")) {
            partes = linea.split("\\s+");
        } else {
            try {
                int id = Integer.parseInt(linea.trim());
                Cliente cliente = new Cliente();
                cliente.setId(id);
                cliente.setCodigo("CLI-" + String.format("%03d", id));
                cliente.setTipo("c");
                return cliente;
            } catch (NumberFormatException e) {
                logger.warn("⚠️ Línea no válida (no se puede parsear como ID): {}", linea);
                return null;
            }
        }
        
        if (partes.length >= 1) {
            try {
                int id = Integer.parseInt(partes[0].trim());
                String codigo = partes.length >= 2 ? partes[1].trim() : ("CLI-" + String.format("%03d", id));
                
                Cliente cliente = new Cliente();
                cliente.setId(id);
                cliente.setCodigo(codigo);
                cliente.setTipo("c");
                
                return cliente;
                
            } catch (NumberFormatException e) {
                logger.warn("⚠️ ID no válido en línea: {}", linea);
                return null;
            }
        }
        
        return null;
    }

    private void crearClientesAutomaticos() {
        logger.info("🤖 Creando clientes automáticamente...");
        
        List<Cliente> clientes = new ArrayList<>();
        
        for (int i = 1; i <= 300; i++) {
            if (!clienteRepository.existsById(i)) {
                Cliente cliente = new Cliente();
                cliente.setId(i);
                cliente.setCodigo("CLI-" + String.format("%03d", i));
                cliente.setTipo("c");
                clientes.add(cliente);
            }
        }
        
        if (!clientes.isEmpty()) {
            clienteRepository.saveAll(clientes);
            logger.info("✅ Creados {} clientes automáticamente", clientes.size());
        }
    }
    
    public List<Cliente> obtenerTodos() {
        return clienteRepository.findAll();
    }

    public long contarTotal() {
        return clienteRepository.count();
    }
}