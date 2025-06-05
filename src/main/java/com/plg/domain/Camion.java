package com.plg.domain;

import com.plg.domain.enumeration.EstadoCamion;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "camion")
public class Camion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_camion")
    private Integer id;


    @Column(name = "codigo")
    private String codigo;

    @Column(name = "tipo")
    private String tipo;

    @Column(name = "peso_bruto")
    private double pesoBruto;

    @Column(name = "max_carga_m3")
    private Integer maxCargaM3;

    @Column(name = "consumo_galones")
    private Integer consumoGalones;

    @Column(name = "velocidad_kmph")
    private Integer velocidadKmph;

    @Transient
    private int ubicacionX;

    @Transient
    private int ubicacionY;
    
    @Transient
    private EstadoCamion estado;

}