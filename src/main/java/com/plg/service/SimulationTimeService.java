package com.plg.service;

import com.plg.domain.SimulationState;
import com.plg.dto.SimulationTimeDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Servicio de tiempo de simulación acelerado
 * Administra un reloj interno independiente del tiempo real
 * Sincroniza con frontend vía WebSocket
 */
@Service
public class SimulationTimeService {
    
    private static final Logger logger = LoggerFactory.getLogger(SimulationTimeService.class);
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private volatile boolean simulationRunning = false;
    private volatile double accelerationFactor = 1.0; // 1.0 = tiempo real, 60.0 = 1 min real = 1 hora sim
    private AtomicReference<LocalDateTime> simulationTime = new AtomicReference<>(LocalDateTime.now());
    private AtomicReference<LocalDateTime> lastRealTime = new AtomicReference<>(LocalDateTime.now());
    private AtomicLong tickCounter = new AtomicLong(0);
    
    private SimulationConfig config = new SimulationConfig();
    private AtomicReference<LocalDateTime> simulationStartTime = new AtomicReference<>(LocalDateTime.now());

    private SimulationState persistentState;
    
    private Map<String, ScheduledTask> scheduledTasks = new ConcurrentHashMap<>();
    private Map<String, TriggerCondition> triggers = new ConcurrentHashMap<>();
    
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private SimulationMetrics metrics = new SimulationMetrics();
    
    @PostConstruct
    public void inicializar() {
        logger.info("🕒 Inicializando SimulationTimeService");
        LocalDateTime initialTime = LocalDateTime.now();
        simulationStartTime.set(initialTime);

        cargarEstadoPersistente();

        iniciarBroadcasting();
        
        logger.info("✅ SimulationTimeService inicializado - Factor: {}x, Estado: {}", 
                   accelerationFactor, simulationRunning ? "RUNNING" : "PAUSED");
    }

    @Scheduled(fixedRate = 100) // 10 veces por segundo para suavidad
    public void tick() {
        if (!simulationRunning) {
            return;
        }
        
        lock.writeLock().lock();
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime lastReal = lastRealTime.get();
            
            long realMillisElapsed = java.time.Duration.between(lastReal, now).toMillis();
            
            long simulatedMillisElapsed = (long)(realMillisElapsed * accelerationFactor);
            
            LocalDateTime currentSimTime = simulationTime.get();
            LocalDateTime newSimTime = currentSimTime.plusNanos(simulatedMillisElapsed * 1_000_000);
            
            simulationTime.set(newSimTime);
            lastRealTime.set(now);
            
            long currentTick = tickCounter.incrementAndGet();
            
            metrics.updateTick(simulatedMillisElapsed, realMillisElapsed);
            
            verificarTareasProgramadas(newSimTime, currentTick);
            
            verificarTriggers(newSimTime, currentTick);
            
            if (currentTick % config.getPersistenciaIntervalTicks() == 0) {
                persistirEstado();
            }
            
        } catch (Exception e) {
            logger.error("Error en tick de simulación: {}", e.getMessage(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Scheduled(fixedRate = 1000)
    public void broadcastTime() {
        lock.readLock().lock();
        try {
            SimulationTimeDTO timeDTO = createTimeDTO();
            
            messagingTemplate.convertAndSend("/topic/simulation-time", timeDTO);
            
            if (config.isBroadcastMetrics()) {
                Map<String, Object> metricsData = metrics.getSnapshot();
                messagingTemplate.convertAndSend("/topic/simulation-metrics", metricsData);
            }
            
        } catch (Exception e) {
            logger.error("Error en broadcasting de tiempo: {}", e.getMessage(), e);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public synchronized void startSimulation() {
        if (simulationRunning) {
            logger.warn("La simulación ya está en ejecución");
            return;
        }
        
        logger.info("▶️ Iniciando simulación - Factor: {}x", accelerationFactor);
        
        simulationRunning = true;
        lastRealTime.set(LocalDateTime.now());

        notificarCambioEstado("STARTED", "Simulación iniciada");

        ejecutarTriggersPorEvento("SIMULATION_START");
        
        persistirEstado();
    }
    
    public synchronized void pauseSimulation() {
        if (!simulationRunning) {
            logger.warn("La simulación ya está pausada");
            return;
        }
        
        logger.info("⏸️ Pausando simulación");
        
        simulationRunning = false;
        
        notificarCambioEstado("PAUSED", "Simulación pausada");
        ejecutarTriggersPorEvento("SIMULATION_PAUSE");
        persistirEstado();
    }

    public synchronized void resetSimulation() {
        logger.info("🔄 Reiniciando simulación");
        
        simulationRunning = false;
        simulationTime.set(LocalDateTime.now());
        lastRealTime.set(LocalDateTime.now());
        tickCounter.set(0);
        metrics.reset();
        
        limpiarTareasTemporales();
        notificarCambioEstado("RESET", "Simulación reiniciada");
        persistirEstado();
    }
    

    public synchronized void setAccelerationFactor(double factor) {
        if (factor <= 0 || factor > config.getMaxAccelerationFactor()) {
            throw new IllegalArgumentException("Factor de aceleración debe estar entre 0.1 y " + 
                                             config.getMaxAccelerationFactor());
        }
        
        logger.info("⚡ Ajustando factor de aceleración: {}x → {}x", accelerationFactor, factor);
        
        this.accelerationFactor = factor;

        lastRealTime.set(LocalDateTime.now());

        notificarCambioEstado("ACCELERATION_CHANGED", 
                            String.format("Factor ajustado a %.1fx", factor));
        
        persistirEstado();
    }

    public synchronized void setSimulationTime(LocalDateTime newTime) {
        logger.info("🕐 Estableciendo tiempo de simulación: {}", 
                newTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        simulationTime.set(newTime);
        lastRealTime.set(LocalDateTime.now());
        
        verificarTareasProgramadas(newTime, tickCounter.get());
        
        notificarCambioEstado("TIME_SET", 
                            "Tiempo establecido: " + newTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
        persistirEstado();
        
        logger.info("✅ Tiempo establecido - Inicio permanece: {}, Actual ahora: {}", 
                simulationStartTime.get(), simulationTime.get());
    }
        
    public void scheduleTask(String taskId, LocalDateTime scheduledTime, 
                           Runnable task, boolean recurring, String recurringPattern) {
        
        ScheduledTask scheduledTask = new ScheduledTask();
        scheduledTask.setId(taskId);
        scheduledTask.setScheduledTime(scheduledTime);
        scheduledTask.setTask(task);
        scheduledTask.setRecurring(recurring);
        scheduledTask.setRecurringPattern(recurringPattern);
        scheduledTask.setActive(true);
        scheduledTask.setCreatedTime(LocalDateTime.now());
        
        scheduledTasks.put(taskId, scheduledTask);
        
        logger.info("📅 Tarea programada: {} para {}", taskId, scheduledTime);
    }

    public void addTrigger(String triggerId, TriggerCondition condition) {
        triggers.put(triggerId, condition);
        logger.info("🎯 Trigger agregado: {}", triggerId);
    }
    
    public SimulationTimeDTO getCurrentState() {
        lock.readLock().lock();
        try {
            return createTimeDTO();
        } finally {
            lock.readLock().unlock();
        }
    }

    public Map<String, Object> getMetrics() {
        return metrics.getSnapshot();
    }

    private SimulationTimeDTO createTimeDTO() {
        SimulationTimeDTO dto = new SimulationTimeDTO();
        dto.setSimulationTime(simulationTime.get());
        dto.setRealTime(LocalDateTime.now());
        dto.setAccelerationFactor(accelerationFactor);
        dto.setRunning(simulationRunning);
        dto.setTickCount(tickCounter.get());
        dto.setUptime(metrics.getUptimeSeconds());
        dto.setActiveTasks(scheduledTasks.size());
        dto.setActiveTriggers(triggers.size());
        return dto;
    }
    
    private void verificarTareasProgramadas(LocalDateTime currentSimTime, long currentTick) {
        for (ScheduledTask task : scheduledTasks.values()) {
            if (!task.isActive()) continue;
            
            if (currentSimTime.isAfter(task.getScheduledTime()) || 
                currentSimTime.isEqual(task.getScheduledTime())) {
                
                try {
                    logger.info("⚙️ Ejecutando tarea programada: {}", task.getId());
                    task.getTask().run();
                    task.setLastExecuted(currentSimTime);
                    task.setExecutionCount(task.getExecutionCount() + 1);
                    
                    // Si es recurrente, reprogramar
                    if (task.isRecurring()) {
                        reprogramarTarea(task);
                    } else {
                        task.setActive(false);
                    }
                    
                } catch (Exception e) {
                    logger.error("Error ejecutando tarea {}: {}", task.getId(), e.getMessage(), e);
                }
            }
        }
    }
    
    private void verificarTriggers(LocalDateTime currentSimTime, long currentTick) {
        for (Map.Entry<String, TriggerCondition> entry : triggers.entrySet()) {
            try {
                TriggerCondition condition = entry.getValue();
                if (condition.shouldTrigger(currentSimTime, currentTick, simulationRunning)) {
                    logger.info("🎯 Ejecutando trigger: {}", entry.getKey());
                    condition.execute();
                }
            } catch (Exception e) {
                logger.error("Error ejecutando trigger {}: {}", entry.getKey(), e.getMessage(), e);
            }
        }
    }
    
    private void reprogramarTarea(ScheduledTask task) {
        LocalDateTime nextExecution = calcularProximaEjecucion(task.getScheduledTime(), 
                                                             task.getRecurringPattern());
        task.setScheduledTime(nextExecution);
        logger.debug("Tarea {} reprogramada para: {}", task.getId(), nextExecution);
    }
    
    private LocalDateTime calcularProximaEjecucion(LocalDateTime lastExecution, String pattern) {
        switch (pattern.toUpperCase()) {
            case "HOURLY":
                return lastExecution.plusHours(1);
            case "DAILY":
                return lastExecution.plusDays(1);
            case "WEEKLY":
                return lastExecution.plusWeeks(1);
            case "MONTHLY":
                return lastExecution.plusMonths(1);
            default:
                // Patrón personalizado (ej: "30MIN", "2HOURS")
                return parseCustomPattern(lastExecution, pattern);
        }
    }
    
    private LocalDateTime parseCustomPattern(LocalDateTime base, String pattern) {
        // Implementación simplificada - se puede expandir
        if (pattern.endsWith("MIN")) {
            int minutes = Integer.parseInt(pattern.replace("MIN", ""));
            return base.plusMinutes(minutes);
        } else if (pattern.endsWith("HOURS")) {
            int hours = Integer.parseInt(pattern.replace("HOURS", ""));
            return base.plusHours(hours);
        }
        return base.plusHours(1); // Default
    }
    
    private void ejecutarTriggersPorEvento(String evento) {
        for (Map.Entry<String, TriggerCondition> entry : triggers.entrySet()) {
            if (entry.getValue().getEventType().equals(evento)) {
                try {
                    entry.getValue().execute();
                } catch (Exception e) {
                    logger.error("Error ejecutando trigger de evento {}: {}", evento, e.getMessage());
                }
            }
        }
    }
    
    private void limpiarTareasTemporales() {
        scheduledTasks.entrySet().removeIf(entry -> 
            !entry.getValue().isPermanent());
        logger.debug("Tareas temporales limpiadas");
    }
    
    private void notificarCambioEstado(String tipo, String mensaje) {
        try {
            Map<String, Object> notificacion = new HashMap<>();
            notificacion.put("tipo", "SIMULATION_STATE_CHANGE");
            notificacion.put("subTipo", tipo);
            notificacion.put("mensaje", mensaje);
            notificacion.put("timestamp", LocalDateTime.now());
            notificacion.put("simulationTime", simulationTime.get());
            notificacion.put("running", simulationRunning);
            notificacion.put("accelerationFactor", accelerationFactor);
            
            messagingTemplate.convertAndSend("/topic/simulation-events", notificacion);
            
        } catch (Exception e) {
            logger.error("Error notificando cambio de estado: {}", e.getMessage());
        }
    }
    
    private void iniciarBroadcasting() {
        logger.info("📡 Iniciando broadcasting de tiempo de simulación");
    }
    
    private void cargarEstadoPersistente() {
        // En implementación real, cargar desde base de datos
        persistentState = new SimulationState();
        persistentState.setSimulationTime(LocalDateTime.now());
        persistentState.setAccelerationFactor(1.0);
        persistentState.setRunning(false);
    }
    
    private void persistirEstado() {
        try {
            persistentState.setSimulationTime(simulationTime.get());
            persistentState.setAccelerationFactor(accelerationFactor);
            persistentState.setRunning(simulationRunning);
            persistentState.setLastUpdate(LocalDateTime.now());
            
            // En implementación real, guardar en base de datos
            
        } catch (Exception e) {
            logger.error("Error persistiendo estado: {}", e.getMessage());
        }
    }
    
    // Clases internas
    
    public static class SimulationConfig {
        private double maxAccelerationFactor = 1000.0;
        private boolean broadcastMetrics = true;
        private long persistenciaIntervalTicks = 100;
        
        // Getters y setters
        public double getMaxAccelerationFactor() { return maxAccelerationFactor; }
        public void setMaxAccelerationFactor(double maxAccelerationFactor) { this.maxAccelerationFactor = maxAccelerationFactor; }
        
        public boolean isBroadcastMetrics() { return broadcastMetrics; }
        public void setBroadcastMetrics(boolean broadcastMetrics) { this.broadcastMetrics = broadcastMetrics; }
        
        public long getPersistenciaIntervalTicks() { return persistenciaIntervalTicks; }
        public void setPersistenciaIntervalTicks(long persistenciaIntervalTicks) { this.persistenciaIntervalTicks = persistenciaIntervalTicks; }
    }
    @Data
    public static class ScheduledTask {
        private String id;
        private LocalDateTime scheduledTime;
        private Runnable task;
        private boolean recurring;
        private String recurringPattern;
        private boolean active;
        private boolean permanent = false;
        private LocalDateTime createdTime;
        private LocalDateTime lastExecuted;
        private int executionCount = 0;
        
    }
    
    public interface TriggerCondition {
        boolean shouldTrigger(LocalDateTime simulationTime, long tickCount, boolean isRunning);
        void execute();
        String getEventType();
    }
    
    private static class SimulationMetrics {
        private AtomicLong totalTicks = new AtomicLong(0);
        private AtomicLong totalSimulatedTime = new AtomicLong(0);
        private AtomicLong totalRealTime = new AtomicLong(0);
        private LocalDateTime startTime = LocalDateTime.now();
        
        public void updateTick(long simulatedMillis, long realMillis) {
            totalTicks.incrementAndGet();
            totalSimulatedTime.addAndGet(simulatedMillis);
            totalRealTime.addAndGet(realMillis);
        }
        
        public void reset() {
            totalTicks.set(0);
            totalSimulatedTime.set(0);
            totalRealTime.set(0);
            startTime = LocalDateTime.now();
        }
        
        public long getUptimeSeconds() {
            return java.time.Duration.between(startTime, LocalDateTime.now()).getSeconds();
        }
        
        public Map<String, Object> getSnapshot() {
            Map<String, Object> snapshot = new HashMap<>();
            snapshot.put("totalTicks", totalTicks.get());
            snapshot.put("totalSimulatedTimeMs", totalSimulatedTime.get());
            snapshot.put("totalRealTimeMs", totalRealTime.get());
            snapshot.put("uptimeSeconds", getUptimeSeconds());
            snapshot.put("averageAcceleration", 
                        totalRealTime.get() > 0 ? (double)totalSimulatedTime.get() / totalRealTime.get() : 0.0);
            return snapshot;
        }
    }
    public void setSimulationStartTime(LocalDateTime startTime) {
        logger.info("🏁 Estableciendo tiempo de INICIO de simulación: {}", startTime);
        
        this.simulationStartTime.set(startTime);
        this.simulationTime.set(startTime);
        this.lastRealTime.set(LocalDateTime.now());

        this.tickCounter.set(0);
        
        logger.info("✅ Tiempo de inicio establecido - Inicio: {}, Actual: {}", 
                simulationStartTime.get(), simulationTime.get());
        
        persistirEstado();
    }
    
    public LocalDateTime getCurrentSimulationTime() {
        return simulationTime.get();
    }
    public LocalDateTime getSimulationStartTime() {
        return simulationStartTime.get();
    }
    public synchronized void jumpToTime(LocalDateTime newTime) {
        logger.info("⏭️ Saltando a tiempo específico: {}", newTime);
        
        // Solo cambiar tiempo actual, mantener inicio intacto
        simulationTime.set(newTime);
        lastRealTime.set(LocalDateTime.now());
        
        // Verificar tareas y notificar
        verificarTareasProgramadas(newTime, tickCounter.get());
        notificarCambioEstado("TIME_JUMP", 
                            "Tiempo saltado a: " + newTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
        persistirEstado();
        
        logger.info("✅ Salto completado - Inicio: {}, Actual: {}", 
                simulationStartTime.get(), simulationTime.get());
    }
}