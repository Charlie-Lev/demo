package com.plg.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.plg.domain.enumeration.EstadoPedido;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
@Table(name = "pedido", indexes = {  // Mantén "pedido" como en tu BD actual
    @Index(name = "idx_pedido_fecha", columnList = "fechaHoraRegistro"),
    @Index(name = "idx_pedido_prioridad", columnList = "prioridad"),
    @Index(name = "idx_pedido_volumen", columnList = "volumenM3"),
    @Index(name = "idx_pedido_cliente", columnList = "id_cliente"),
    @Index(name = "idx_pedido_coordenadas", columnList = "ubicacionX, ubicacionY")
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Pedido {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pedido")
    private Integer id;

    @Column(name = "fecha_hora_registro")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime fechaHoraRegistro;

    @Column(name = "ubicacion_x")
    private Integer ubicacionX;

    @Column(name = "ubicacion_y")
    private Integer ubicacionY;

    @Column(name = "volumen_m3")
    private double volumenM3;

    @Column(name = "horas_limite")
    private Integer horasLimite;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cliente")  // Mantén tu nombre actual
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Cliente cliente;

    @Column(name = "prioridad", columnDefinition = "int default 0")
    private int prioridad; 

    @Transient
    private EstadoPedido estado;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Entrega> entregas = new ArrayList<>();
    // Campo calculado para evitar joins innecesarios en consultas
    @Column(name = "cliente_codigo")
    private String clienteCodigo;
    public Pedido() {}

    public Pedido(Integer id, int ubicacionX, int ubicacionY, double volumenM3) {
        this.id = id;
        this.ubicacionX = ubicacionX;
        this.ubicacionY = ubicacionY;
        this.volumenM3 = volumenM3;
    }

    @PrePersist
    @PreUpdate
    private void sincronizarClienteCodigo() {
        if (cliente != null) {
            this.clienteCodigo = cliente.getCodigo();
        }
    }

    public boolean isAtendido() {
        if (this.entregas == null || this.entregas.isEmpty()) {
            return false;
        }
        
        double volumenEntregado = this.entregas.stream()
            .mapToDouble(Entrega::getVolumenEntregadoM3)
            .sum();
        
        return volumenEntregado >= this.volumenM3;
    }

    public Pedido clone() {
        Pedido copia = new Pedido();
        copia.setId(this.getId());
        copia.setFechaHoraRegistro(this.getFechaHoraRegistro());
        copia.setUbicacionX(this.getUbicacionX());
        copia.setUbicacionY(this.getUbicacionY());
        copia.setVolumenM3(this.getVolumenM3());
        copia.setHorasLimite(this.getHorasLimite());
        copia.setCliente(this.getCliente());
        copia.setPrioridad(this.getPrioridad());
        return copia;
    }
}