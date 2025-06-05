package com.plg.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * DTO para transferir información del tiempo de simulación
 * Usado en comunicación REST y WebSocket
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SimulationTimeDTO {
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime simulationTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime realTime;
    
    private double accelerationFactor;
    private boolean running;
    private long tickCount;
    private long uptime; // segundos
    private int activeTasks;
    private int activeTriggers;
    
    // Campos adicionales para el frontend
    private String simulationTimeFormatted;
    private String realTimeFormatted;
    private String accelerationDescription;
    private String status;
    private double timeRatio; // simulationTime / realTime desde inicio
    
    // Información de sincronización
    private long lastSyncTimestamp;
    private double driftMs; // diferencia entre tiempo esperado y real
    
    // Métricas adicionales
    private SimulationMetricsDTO metrics;
    
    public SimulationTimeDTO() {
        this.lastSyncTimestamp = System.currentTimeMillis();
        updateFormattedFields();
    }
    
    // Getters y Setters
    public LocalDateTime getSimulationTime() {
        return simulationTime;
    }
    
    public void setSimulationTime(LocalDateTime simulationTime) {
        this.simulationTime = simulationTime;
        updateFormattedFields();
    }
    
    public LocalDateTime getRealTime() {
        return realTime;
    }
    
    public void setRealTime(LocalDateTime realTime) {
        this.realTime = realTime;
        updateFormattedFields();
    }
    
    public double getAccelerationFactor() {
        return accelerationFactor;
    }
    
    public void setAccelerationFactor(double accelerationFactor) {
        this.accelerationFactor = accelerationFactor;
        updateFormattedFields();
    }
    
    public boolean isRunning() {
        return running;
    }
    
    public void setRunning(boolean running) {
        this.running = running;
        updateFormattedFields();
    }
    
    public long getTickCount() {
        return tickCount;
    }
    
    public void setTickCount(long tickCount) {
        this.tickCount = tickCount;
    }
    
    public long getUptime() {
        return uptime;
    }
    
    public void setUptime(long uptime) {
        this.uptime = uptime;
    }
    
    public int getActiveTasks() {
        return activeTasks;
    }
    
    public void setActiveTasks(int activeTasks) {
        this.activeTasks = activeTasks;
    }
    
    public int getActiveTriggers() {
        return activeTriggers;
    }
    
    public void setActiveTriggers(int activeTriggers) {
        this.activeTriggers = activeTriggers;
    }
    
    public String getSimulationTimeFormatted() {
        return simulationTimeFormatted;
    }
    
    public void setSimulationTimeFormatted(String simulationTimeFormatted) {
        this.simulationTimeFormatted = simulationTimeFormatted;
    }
    
    public String getRealTimeFormatted() {
        return realTimeFormatted;
    }
    
    public void setRealTimeFormatted(String realTimeFormatted) {
        this.realTimeFormatted = realTimeFormatted;
    }
    
    public String getAccelerationDescription() {
        return accelerationDescription;
    }
    
    public void setAccelerationDescription(String accelerationDescription) {
        this.accelerationDescription = accelerationDescription;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public double getTimeRatio() {
        return timeRatio;
    }
    
    public void setTimeRatio(double timeRatio) {
        this.timeRatio = timeRatio;
    }
    
    public long getLastSyncTimestamp() {
        return lastSyncTimestamp;
    }
    
    public void setLastSyncTimestamp(long lastSyncTimestamp) {
        this.lastSyncTimestamp = lastSyncTimestamp;
    }
    
    public double getDriftMs() {
        return driftMs;
    }
    
    public void setDriftMs(double driftMs) {
        this.driftMs = driftMs;
    }
    
    public SimulationMetricsDTO getMetrics() {
        return metrics;
    }
    
    public void setMetrics(SimulationMetricsDTO metrics) {
        this.metrics = metrics;
    }
    
    /**
     * Actualizar campos formateados automáticamente
     */
    private void updateFormattedFields() {
        DateTimeFormatter displayFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        
        if (simulationTime != null) {
            this.simulationTimeFormatted = simulationTime.format(displayFormatter);
        }
        
        if (realTime != null) {
            this.realTimeFormatted = realTime.format(displayFormatter);
        }
        
        // Descripción de aceleración user-friendly
        this.accelerationDescription = generateAccelerationDescription(accelerationFactor);
        
        // Status general
        this.status = running ? "RUNNING" : "PAUSED";
    }
    
    /**
     * Generar descripción user-friendly del factor de aceleración
     */
    private String generateAccelerationDescription(double factor) {
        if (factor == 1.0) {
            return "Tiempo real";
        } else if (factor < 1.0) {
            return String.format("%.1fx más lento", 1.0 / factor);
        } else if (factor <= 10.0) {
            return String.format("%.1fx más rápido", factor);
        } else if (factor == 60.0) {
            return "1 minuto real = 1 hora simulada";
        } else if (factor == 360.0) {
            return "1 minuto real = 6 horas simuladas";
        } else if (factor == 1440.0) {
            return "1 minuto real = 1 día simulado";
        } else {
            return String.format("%.0fx más rápido", factor);
        }
    }
    
    /**
     * Crear copia para sincronización
     */
    public SimulationTimeDTO createSyncCopy() {
        SimulationTimeDTO copy = new SimulationTimeDTO();
        copy.setSimulationTime(this.simulationTime);
        copy.setRealTime(LocalDateTime.now());
        copy.setAccelerationFactor(this.accelerationFactor);
        copy.setRunning(this.running);
        copy.setTickCount(this.tickCount);
        copy.setUptime(this.uptime);
        copy.setActiveTasks(this.activeTasks);
        copy.setActiveTriggers(this.activeTriggers);
        copy.setTimeRatio(this.timeRatio);
        return copy;
    }
    
    @Override
    public String toString() {
        return String.format("SimulationTimeDTO{simulationTime=%s, accelerationFactor=%.1fx, running=%s, ticks=%d}", 
                           simulationTimeFormatted, accelerationFactor, running, tickCount);
    }
    
    /**
     * DTO interno para métricas de simulación
     */
    public static class SimulationMetricsDTO {
        private long totalTicks;
        private long totalSimulatedTimeMs;
        private long totalRealTimeMs;
        private double averageAcceleration;
        private double cpuUsage;
        private double memoryUsage;
        private int connectionsCount;
        
        // Getters y Setters
        public long getTotalTicks() {
            return totalTicks;
        }
        
        public void setTotalTicks(long totalTicks) {
            this.totalTicks = totalTicks;
        }
        
        public long getTotalSimulatedTimeMs() {
            return totalSimulatedTimeMs;
        }
        
        public void setTotalSimulatedTimeMs(long totalSimulatedTimeMs) {
            this.totalSimulatedTimeMs = totalSimulatedTimeMs;
        }
        
        public long getTotalRealTimeMs() {
            return totalRealTimeMs;
        }
        
        public void setTotalRealTimeMs(long totalRealTimeMs) {
            this.totalRealTimeMs = totalRealTimeMs;
        }
        
        public double getAverageAcceleration() {
            return averageAcceleration;
        }
        
        public void setAverageAcceleration(double averageAcceleration) {
            this.averageAcceleration = averageAcceleration;
        }
        
        public double getCpuUsage() {
            return cpuUsage;
        }
        
        public void setCpuUsage(double cpuUsage) {
            this.cpuUsage = cpuUsage;
        }
        
        public double getMemoryUsage() {
            return memoryUsage;
        }
        
        public void setMemoryUsage(double memoryUsage) {
            this.memoryUsage = memoryUsage;
        }
        
        public int getConnectionsCount() {
            return connectionsCount;
        }
        
        public void setConnectionsCount(int connectionsCount) {
            this.connectionsCount = connectionsCount;
        }
    }
}