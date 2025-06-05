package com.plg.domain;

import jakarta.persistence.*;
import lombok.Data;
@Data
@Entity
@Table(name = "cliente")
public class Cliente {

    @Id
    @Column(name = "id_cliente")
    private Integer id;

    @Column(name = "tipo", length = 1, columnDefinition = "CHAR(1) DEFAULT 'c'")
    private String tipo;

    @Column(name = "codigo", length = 15)
    private String codigo;

    // Constructor vacío
    public Cliente() {}

}