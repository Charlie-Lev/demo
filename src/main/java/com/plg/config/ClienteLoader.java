package com.plg.config;

import com.plg.service.ClienteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1) // Ejecutar temprano, antes de otros inicializadores
public class ClienteLoader implements CommandLineRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(ClienteLoader.class);
    
    @Autowired
    private ClienteService clienteService;
    
    @Override
    public void run(String... args) throws Exception {
        logger.info("🚀 Iniciando carga de clientes...");
        
        try {
            if (clienteService.estaVacia()) {
                logger.info("📋 Tabla de clientes vacía. Cargando datos iniciales...");
                clienteService.cargarClientesDesdeArchivo();
            } else {
                long totalClientes = clienteService.contarTotal();
                logger.info("✅ Clientes ya existentes en BD: {}", totalClientes);
            }
        } catch (Exception e) {
            logger.error("❌ Error cargando clientes: {}", e.getMessage(), e);
            // El sistema puede continuar, los clientes se crearán automáticamente cuando se necesiten
        }
        
        logger.info("🏁 Carga de clientes completada");
    }
}