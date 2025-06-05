package com.plg.service;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Comparator;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.plg.domain.Cliente;
import com.plg.domain.Pedido;
import com.plg.repository.PedidoRepository;
import com.plg.repository.PedidoSpecifications;
import org.slf4j.Logger;
import lombok.Data;

@Service
public class PedidoService {

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private ClienteService clienteService;
    
    @Autowired
    private SimulationTimeService simulationTimeService;

    private static final Logger logger = LoggerFactory.getLogger(PedidoService.class);
    
    public void procesarArchivoPedidos(MultipartFile file, YearMonth periodo) throws IOException {
        List<Pedido> pedidos = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String linea;

            while ((linea = reader.readLine()) != null) {
                // Ejemplo: 01d00h24m:16,13,c-198,3m3,4h
                String[] partes = linea.split(":");
                if (partes.length != 2) continue;

                String[] tiempo = partes[0].split("d|h|m");
                int dia = Integer.parseInt(tiempo[0]);
                int hora = Integer.parseInt(tiempo[1]);
                int minuto = Integer.parseInt(tiempo[2]);

                LocalDateTime fechaHora = periodo.atDay(dia).atTime(hora, minuto);

                String[] datos = partes[1].split(",");

                int x = Integer.parseInt(datos[0].trim());
                int y = Integer.parseInt(datos[1].trim());

                int idCliente = Integer.parseInt(datos[2].replace("c-", "").trim());
                double volumen = Double.parseDouble(datos[3].replace("m3", "").trim());
                int horasLimite = Integer.parseInt(datos[4].replace("h", "").trim());

                Cliente cliente = clienteService.buscarOCrearCliente(idCliente);

                Pedido pedido = new Pedido();
                pedido.setFechaHoraRegistro(fechaHora);
                pedido.setUbicacionX(x);
                pedido.setUbicacionY(y);
                pedido.setCliente(cliente);
                pedido.setVolumenM3(volumen);
                pedido.setHorasLimite(horasLimite);
                pedido.setPrioridad(calcularPrioridadPedido(fechaHora, horasLimite));

                pedidos.add(pedido);
            }
        }

        pedidoRepository.saveAll(pedidos);
    }

    public List<Pedido> obtenerTodos() {
        return pedidoRepository.findAll();
    }

    public Page<Pedido> obtenerPedidosPaginados(
            int page, 
            int size, 
            String sortBy, 
            String sortDir,
            String filtroCliente,
            Double volumenMin,
            Double volumenMax,
            Integer prioridadMin,
            LocalDateTime fechaDesde,
            LocalDateTime fechaHasta) {
        
        Sort sort = sortDir.equalsIgnoreCase("desc") 
            ? Sort.by(sortBy).descending() 
            : Sort.by(sortBy).ascending();
        
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Specification<Pedido> spec = Specification.where(null);
        
        if (filtroCliente != null && !filtroCliente.trim().isEmpty()) {
            spec = spec.and(PedidoSpecifications.clienteContiene(filtroCliente));
        }
        
        if (volumenMin != null) {
            spec = spec.and(PedidoSpecifications.volumenMayorIgual(volumenMin));
        }
        
        if (volumenMax != null) {
            spec = spec.and(PedidoSpecifications.volumenMenorIgual(volumenMax));
        }
        
        if (prioridadMin != null) {
            spec = spec.and(PedidoSpecifications.prioridadMayorIgual(prioridadMin));
        }
        
        if (fechaDesde != null) {
            spec = spec.and(PedidoSpecifications.fechaDesde(fechaDesde));
        }
        
        if (fechaHasta != null) {
            spec = spec.and(PedidoSpecifications.fechaHasta(fechaHasta));
        }
        
        return pedidoRepository.findAll(spec, pageable);
    }

    public Page<Pedido> buscarPedidos(String textoBusqueda, Pageable pageable) {
        return pedidoRepository.buscarPorTexto(textoBusqueda, pageable);
    }

    public ResumenPedidos obtenerResumen() {
        ResumenPedidos resumen = new ResumenPedidos();
        resumen.setTotalPedidos(pedidoRepository.count());
        resumen.setVolumenTotal(pedidoRepository.sumarVolumenTotal());
        resumen.setPedidosUrgentes(pedidoRepository.contarPorPrioridad(700));
        resumen.setPedidosHoy(pedidoRepository.contarPedidosHoy());
        return resumen;
    }

    public List<Pedido> obtenerPedidosCriticos(LocalDateTime horaEntrada, int limitePedidos) {
        LocalDateTime horaInicioSimulacion = simulationTimeService.getSimulationStartTime();
        List<Pedido> pedidosNoAsignados = obtenerPedidosNoAsignados();
        
        List<PedidoCritico> pedidosCriticos = pedidosNoAsignados.stream()
            .filter(pedido -> pedido.getHorasLimite() != null && pedido.getHorasLimite() > 0)
            .filter(pedido -> esPedidoValidoParaSimulacion(pedido, horaInicioSimulacion, horaEntrada))
            .map(pedido -> {
                LocalDateTime tiempoLimite = calcularTiempoLimite(pedido);
                double horasRestantes = calcularHorasRestantes(horaEntrada, tiempoLimite);
                double indiceCriticidad = calcularIndiceCriticidad(horasRestantes, pedido);
                return new PedidoCritico(pedido, tiempoLimite, horasRestantes, indiceCriticidad);
            })
            .filter(pc -> pc.getHorasRestantes() > 0) // Solo pedidos que aún tienen tiempo
            .sorted(Comparator.comparing(PedidoCritico::getIndiceCriticidad).reversed())
            .limit(limitePedidos)
            .collect(Collectors.toList());
        if (!pedidosCriticos.isEmpty()) {
            logger.info("🚨 {} pedidos críticos detectados en hora: {} (filtrados por inicio simulación: {})", 
                pedidosCriticos.size(), horaEntrada, horaInicioSimulacion);
            pedidosCriticos.stream().limit(5).forEach(pc -> 
                logger.warn("Pedido crítico ID: {}, Registro: {}, Horas restantes: {:.2f}, Criticidad: {:.2f}", 
                    pc.getPedido().getId(), pc.getPedido().getFechaHoraRegistro(), 
                    pc.getHorasRestantes(), pc.getIndiceCriticidad())
            );
        }
        return pedidosCriticos.stream()
            .map(PedidoCritico::getPedido)
            .collect(Collectors.toList());
    }

    private boolean esPedidoValidoParaSimulacion(Pedido pedido, LocalDateTime horaInicioSimulacion, LocalDateTime horaActual) {
        LocalDateTime fechaRegistro = pedido.getFechaHoraRegistro();
        
        if (fechaRegistro == null) {
            logger.warn("⚠️ Pedido {} sin fecha de registro, se excluye", pedido.getId());
            return false;
        }
        
        if (fechaRegistro.isBefore(horaInicioSimulacion)) {
            logger.debug("❌ Pedido {} registrado antes del inicio simulación ({} < {}), se excluye", 
                        pedido.getId(), fechaRegistro, horaInicioSimulacion);
            return false;
        }
        
        if (fechaRegistro.isAfter(horaActual)) {
            logger.debug("❌ Pedido {} es futuro ({} > {}), se excluye", 
                        pedido.getId(), fechaRegistro, horaActual);
            return false;
        }
        
        return true;
    }

    public List<Pedido> obtenerPedidosCriticos(int limitePedidos) {
        LocalDateTime horaSimulacion = simulationTimeService.getCurrentSimulationTime();
        return obtenerPedidosCriticos(horaSimulacion, limitePedidos);
    }

    public List<Pedido> obtenerPedidosCriticos() {
        return obtenerPedidosCriticos(20); // Máximo 20 pedidos críticos
    }

    public EstadisticasPedidosCriticos obtenerEstadisticasCriticos(LocalDateTime horaEntrada) {
        LocalDateTime horaInicioSimulacion = simulationTimeService.getSimulationStartTime();

        List<Pedido> pedidosNoAsignados = obtenerPedidosNoAsignados().stream()
        .filter(p -> esPedidoValidoParaSimulacion(p, horaInicioSimulacion, horaEntrada))
        .collect(Collectors.toList());
        
        EstadisticasPedidosCriticos stats = new EstadisticasPedidosCriticos();
        stats.setTotalPedidosNoAsignados(pedidosNoAsignados.size());
        
        long pedidosVencidos = pedidosNoAsignados.stream()
            .filter(p -> p.getHorasLimite() != null)
            .filter(p -> calcularHorasRestantes(horaEntrada, calcularTiempoLimite(p)) <= 0)
            .count();
        
        long pedidosUrgentes = pedidosNoAsignados.stream()
            .filter(p -> p.getHorasLimite() != null)
            .filter(p -> {
                double horas = calcularHorasRestantes(horaEntrada, calcularTiempoLimite(p));
                return horas > 0 && horas <= 2.0; // Menos de 2 horas
            })
            .count();
        
        long pedidosCriticos = pedidosNoAsignados.stream()
            .filter(p -> p.getHorasLimite() != null)
            .filter(p -> {
                double horas = calcularHorasRestantes(horaEntrada, calcularTiempoLimite(p));
                return horas > 2.0 && horas <= 6.0; // Entre 2 y 6 horas
            })
            .count();
        
        stats.setPedidosVencidos((int) pedidosVencidos);
        stats.setPedidosUrgentes((int) pedidosUrgentes);
        stats.setPedidosCriticos((int) pedidosCriticos);
        stats.setHoraAnalisis(horaEntrada);
        return stats;
    }

    private List<Pedido> obtenerPedidosNoAsignados() {
        return pedidoRepository.findPedidosNoAsignados();
    }

    private LocalDateTime calcularTiempoLimite(Pedido pedido) {
        if (pedido.getFechaHoraRegistro() == null || pedido.getHorasLimite() == null) {
            return LocalDateTime.now().plusDays(1); // Valor por defecto
        }
        return pedido.getFechaHoraRegistro().plusHours(pedido.getHorasLimite());
    }

    private double calcularHorasRestantes(LocalDateTime horaActual, LocalDateTime tiempoLimite) {
        if (horaActual.isAfter(tiempoLimite)) {
            return 0.0; // Ya venció
        }
        
        long minutosRestantes = java.time.Duration.between(horaActual, tiempoLimite).toMinutes();
        return minutosRestantes / 60.0;
    }

    private double calcularIndiceCriticidad(double horasRestantes, Pedido pedido) {
        if (horasRestantes <= 0) {
            double horasVencidas = Math.abs(horasRestantes);
            return 1000.0 + (horasVencidas * 50); // Máximo + penalización creciente
        }
        if (horasRestantes <= 1.0) {
            return 900.0 + (1.0 - horasRestantes) * 100; // Entre 900-1000
        }
        if (horasRestantes <= 4.0) {
            return 700.0 + (4.0 - horasRestantes) * 50; // Entre 700-900
        }
        
        if (horasRestantes <= 12.0) {
            return 400.0 + (12.0 - horasRestantes) * 25; // Entre 400-700
        }
        
        double factorTiempo = Math.max(0, 1.0 - (horasRestantes / 48.0)); // Normalizar a 48h
        return 100 + (factorTiempo * 300);
    }

    private int calcularPrioridadPedido(LocalDateTime fechaRegistro, Integer horasLimite) {
        if (horasLimite == null || horasLimite <= 0) {
            return 100; // Prioridad base
        }
        if (horasLimite <= 4) return 900;      // Muy urgente
        else if (horasLimite <= 8) return 700; // Urgente
        else if (horasLimite <= 24) return 500; // Medio
        else if (horasLimite <= 48) return 300; // Normal
        else return 100; // Bajo
    }
    @Data
    public static class ResumenPedidos {
        private long totalPedidos;
        private double volumenTotal;
        private long pedidosUrgentes;
        private long pedidosHoy;
    }
    @Data
    public static class EstadisticasPedidosCriticos {
        private int totalPedidosNoAsignados;
        private int pedidosVencidos;
        private int pedidosUrgentes;
        private int pedidosCriticos;
        private LocalDateTime horaAnalisis;
        
        public int getTotalProblematicos() {
            return pedidosVencidos + pedidosUrgentes + pedidosCriticos;
        }
    }
    @Data
    private static class PedidoCritico {
        private final Pedido pedido;
        private final LocalDateTime tiempoLimite;
        private final double horasRestantes;
        private final double indiceCriticidad;
        
        public PedidoCritico(Pedido pedido, LocalDateTime tiempoLimite, 
                           double horasRestantes, double indiceCriticidad) {
            this.pedido = pedido;
            this.tiempoLimite = tiempoLimite;
            this.horasRestantes = horasRestantes;
            this.indiceCriticidad = indiceCriticidad;
        }
        
    }
    public void eliminarTodos() {
        try {
            pedidoRepository.deleteAll();
            logger.info("✅ Todos los pedidos eliminados correctamente");
        } catch (Exception e) {
            logger.error("❌ Error eliminando pedidos: {}", e.getMessage(), e);
            throw new RuntimeException("Error eliminando pedidos", e);
        }
    }
    public Pedido guardar(Pedido pedido) {
        try {
            if (pedido.getCliente() != null && pedido.getCliente().getId() != null) {
                Cliente cliente = clienteService.buscarOCrearCliente(pedido.getCliente().getId());
                pedido.setCliente(cliente);
            }
            Pedido pedidoGuardado = pedidoRepository.save(pedido);
            logger.debug("✅ Pedido guardado: ID {} - {} m³ - Prioridad: {}", 
                        pedidoGuardado.getId(), 
                        pedidoGuardado.getVolumenM3(), 
                        pedidoGuardado.getPrioridad());
            return pedidoGuardado;
            
        } catch (Exception e) {
            logger.error("❌ Error guardando pedido ID {}: {}", pedido.getId(), e.getMessage(), e);
            throw new RuntimeException("Error guardando pedido", e);
        }
    }
}