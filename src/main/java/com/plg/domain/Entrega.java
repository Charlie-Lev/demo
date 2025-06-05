package com.plg.domain;

import com.plg.domain.enumeration.EstadoPedido;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "entrega")
public class Entrega {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Generación automática del ID
    @Column(name = "id_entrega")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_pedido") // Relación con Pedido
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_camion") // Relación con Camion
    private Camion camion;

    @Column(name = "fecha_hora_recepcion")
    private LocalDateTime fechaHoraRecepcion;

    @Column(name = "fecha_hora_entrega")
    private LocalDateTime fechaHoraEntrega;

    @Column(name = "volumen_entregado_m3")
    private double volumenEntregadoM3;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado")
    private EstadoPedido estado;

    @Column(name = "tipo_entrega")
    private String tipoEntrega; // Ejemplo: "PARCIAL" o "COMPLETA"

    public Entrega() {}
}