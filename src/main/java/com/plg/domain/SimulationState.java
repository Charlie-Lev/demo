package com.plg.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entidad para persistir el estado del tiempo de simulación
 * Permite recuperar el estado después de reinicios del sistema
 */
@Entity
@Table(name = "simulation_state")
public class SimulationState {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "simulation_time", nullable = false)
    private LocalDateTime simulationTime;
    
    @Column(name = "real_time_reference", nullable = false)
    private LocalDateTime realTimeReference;
    
    @Column(name = "acceleration_factor", nullable = false)
    private Double accelerationFactor;
    
    @Column(name = "is_running", nullable = false)
    private Boolean running;
    
    @Column(name = "tick_count", nullable = false)
    private Long tickCount;
    
    @Column(name = "total_simulated_time_ms", nullable = false)
    private Long totalSimulatedTimeMs;
    
    @Column(name = "total_real_time_ms", nullable = false)
    private Long totalRealTimeMs;
    
    @Column(name = "session_id")
    private String sessionId;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "last_update", nullable = false)
    private LocalDateTime lastUpdate;
    
    @Column(name = "version", nullable = false)
    private Integer version;
    
    // Campos adicionales para recuperación de estado
    @Column(name = "active_tasks_count")
    private Integer activeTasksCount;
    
    @Column(name = "active_triggers_count")
    private Integer activeTriggersCount;
    
    @Column(name = "last_auto_save", nullable = true)
    private LocalDateTime lastAutoSave;
    
    @Column(name = "config_snapshot", columnDefinition = "TEXT")
    private String configSnapshot; // JSON con configuración
    
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
    
    // Constructores
    public SimulationState() {
        this.createdAt = LocalDateTime.now();
        this.lastUpdate = LocalDateTime.now();
        this.version = 1;
        this.tickCount = 0L;
        this.totalSimulatedTimeMs = 0L;
        this.totalRealTimeMs = 0L;
        this.activeTasksCount = 0;
        this.activeTriggersCount = 0;
    }
    
    public SimulationState(LocalDateTime simulationTime, double accelerationFactor, boolean running) {
        this();
        this.simulationTime = simulationTime;
        this.realTimeReference = LocalDateTime.now();
        this.accelerationFactor = accelerationFactor;
        this.running = running;
    }
    
    // Getters y Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public LocalDateTime getSimulationTime() {
        return simulationTime;
    }
    
    public void setSimulationTime(LocalDateTime simulationTime) {
        this.simulationTime = simulationTime;
        updateLastUpdate();
    }
    
    public LocalDateTime getRealTimeReference() {
        return realTimeReference;
    }
    
    public void setRealTimeReference(LocalDateTime realTimeReference) {
        this.realTimeReference = realTimeReference;
        updateLastUpdate();
    }
    
    public Double getAccelerationFactor() {
        return accelerationFactor;
    }
    
    public void setAccelerationFactor(Double accelerationFactor) {
        this.accelerationFactor = accelerationFactor;
        updateLastUpdate();
    }
    
    public Boolean getRunning() {
        return running;
    }
    
    public void setRunning(Boolean running) {
        this.running = running;
        updateLastUpdate();
    }
    
    public Long getTickCount() {
        return tickCount;
    }
    
    public void setTickCount(Long tickCount) {
        this.tickCount = tickCount;
        updateLastUpdate();
    }
    
    public Long getTotalSimulatedTimeMs() {
        return totalSimulatedTimeMs;
    }
    
    public void setTotalSimulatedTimeMs(Long totalSimulatedTimeMs) {
        this.totalSimulatedTimeMs = totalSimulatedTimeMs;
        updateLastUpdate();
    }
    
    public Long getTotalRealTimeMs() {
        return totalRealTimeMs;
    }
    
    public void setTotalRealTimeMs(Long totalRealTimeMs) {
        this.totalRealTimeMs = totalRealTimeMs;
        updateLastUpdate();
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getLastUpdate() {
        return lastUpdate;
    }
    
    public void setLastUpdate(LocalDateTime lastUpdate) {
        this.lastUpdate = lastUpdate;
    }
    
    public Integer getVersion() {
        return version;
    }
    
    public void setVersion(Integer version) {
        this.version = version;
    }
    
    public Integer getActiveTasksCount() {
        return activeTasksCount;
    }
    
    public void setActiveTasksCount(Integer activeTasksCount) {
        this.activeTasksCount = activeTasksCount;
        updateLastUpdate();
    }
    
    public Integer getActiveTriggersCount() {
        return activeTriggersCount;
    }
    
    public void setActiveTriggersCount(Integer activeTriggersCount) {
        this.activeTriggersCount = activeTriggersCount;
        updateLastUpdate();
    }
    
    public LocalDateTime getLastAutoSave() {
        return lastAutoSave;
    }
    
    public void setLastAutoSave(LocalDateTime lastAutoSave) {
        this.lastAutoSave = lastAutoSave;
    }
    
    public String getConfigSnapshot() {
        return configSnapshot;
    }
    
    public void setConfigSnapshot(String configSnapshot) {
        this.configSnapshot = configSnapshot;
        updateLastUpdate();
    }
    
    public String getNotes() {
        return notes;
    }
    
    public void setNotes(String notes) {
        this.notes = notes;
        updateLastUpdate();
    }
    
    // Métodos de utilidad
    
    /**
     * Actualizar timestamp de última modificación automáticamente
     */
    private void updateLastUpdate() {
        this.lastUpdate = LocalDateTime.now();
    }
    
    /**
     * Incrementar versión para control de cambios
     */
    public void incrementVersion() {
        this.version++;
        updateLastUpdate();
    }
    
    /**
     * Calcular tiempo de simulación actualizado basado en referencia real
     */
    public LocalDateTime calculateCurrentSimulationTime() {
        if (!running || realTimeReference == null) {
            return simulationTime;
        }
        
        LocalDateTime now = LocalDateTime.now();
        long realElapsedMs = java.time.Duration.between(realTimeReference, now).toMillis();
        long simulatedElapsedMs = (long)(realElapsedMs * accelerationFactor);
        
        return simulationTime.plusNanos(simulatedElapsedMs * 1_000_000);
    }
    
    /**
     * Verificar si el estado necesita sincronización
     */
    public boolean needsSync() {
        if (lastUpdate == null) return true;
        
        long minutesSinceUpdate = java.time.Duration.between(lastUpdate, LocalDateTime.now()).toMinutes();
        return minutesSinceUpdate > 5; // Sync cada 5 minutos máximo
    }
    
    /**
     * Crear snapshot del estado actual
     */
    public SimulationStateSnapshot createSnapshot() {
        SimulationStateSnapshot snapshot = new SimulationStateSnapshot();
        snapshot.setSimulationTime(calculateCurrentSimulationTime());
        snapshot.setAccelerationFactor(accelerationFactor);
        snapshot.setRunning(running);
        snapshot.setTickCount(tickCount);
        snapshot.setSnapshotTime(LocalDateTime.now());
        return snapshot;
    }
    
    /**
     * Validar consistencia del estado
     */
    public boolean isValid() {
        return simulationTime != null 
            && accelerationFactor != null 
            && accelerationFactor > 0 
            && running != null
            && tickCount != null 
            && tickCount >= 0;
    }
    
    @Override
    public String toString() {
        return String.format("SimulationState{id=%d, simulationTime=%s, accelerationFactor=%.1f, running=%s, ticks=%d}", 
                           id, simulationTime, accelerationFactor, running, tickCount);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        SimulationState that = (SimulationState) o;
        return id != null && id.equals(that.id);
    }
    
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
    
    /**
     * Clase interna para snapshots de estado
     */
    public static class SimulationStateSnapshot {
        private LocalDateTime simulationTime;
        private Double accelerationFactor;
        private Boolean running;
        private Long tickCount;
        private LocalDateTime snapshotTime;
        
        // Getters y Setters
        public LocalDateTime getSimulationTime() {
            return simulationTime;
        }
        
        public void setSimulationTime(LocalDateTime simulationTime) {
            this.simulationTime = simulationTime;
        }
        
        public Double getAccelerationFactor() {
            return accelerationFactor;
        }
        
        public void setAccelerationFactor(Double accelerationFactor) {
            this.accelerationFactor = accelerationFactor;
        }
        
        public Boolean getRunning() {
            return running;
        }
        
        public void setRunning(Boolean running) {
            this.running = running;
        }
        
        public Long getTickCount() {
            return tickCount;
        }
        
        public void setTickCount(Long tickCount) {
            this.tickCount = tickCount;
        }
        
        public LocalDateTime getSnapshotTime() {
            return snapshotTime;
        }
        
        public void setSnapshotTime(LocalDateTime snapshotTime) {
            this.snapshotTime = snapshotTime;
        }
    }
}