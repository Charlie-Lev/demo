package com.plg.domain;

import java.time.LocalTime;
import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "almacen")
public class Almacen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_almacen")
    private int id;

    @Column(name = "capacidad")
    private double capacidad;// la capacidad maxima de GLP en el almacen

    @Transient
    private double cantidad;// la cantidad actual de GLP en el almacen

    @Column(name = "ubicacion_x")
    private int x;

    @Column(name = "ubicacion_y")
    private int y;

    @Column(name = "tipo")
    private String tipo;

    @Column(name = "hora_abastecimiento")
    private LocalTime hora_abastecimiento;

    @Column(name = "es_principal")
    private int esPrincipal;

    public Almacen() {
        // Constructor por defecto
    }

    public Almacen(int id, int x, int y, boolean esPrincipal, double capacidad) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.esPrincipal = esPrincipal ? 1 : 0; // Convertir booleano a entero
        this.capacidad = capacidad;
        this.cantidad = capacidad; // Inicializar cantidad como 0 por defecto
    }

    public boolean getEsPrincipal() {
        return esPrincipal == 1; // Devuelve true si esPrincipal es 1, false en caso contrario
    }
        // ✅ Método adicional para inicializar después de cargar desde BD
    @PostLoad
    public void inicializarCantidad() {
        if (this.cantidad == 0.0) {
            this.cantidad = this.capacidad;
        }
    }
}