package com.plg.repository;

import com.plg.domain.Pedido;
import com.plg.domain.Cliente;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import java.time.LocalDateTime;

/**
 * Especificaciones JPA para filtros dinámicos de pedidos
 */
public class PedidoSpecifications {
    
    public static Specification<Pedido> clienteContiene(String clienteTexto) {
        return (root, query, criteriaBuilder) -> {
            Join<Pedido, Cliente> clienteJoin = root.join("cliente", JoinType.LEFT);
            return criteriaBuilder.like(
                criteriaBuilder.lower(clienteJoin.get("codigo")), 
                "%" + clienteTexto.toLowerCase() + "%"
            );
        };
    }
    
    public static Specification<Pedido> volumenMayorIgual(Double volumen) {
        return (root, query, criteriaBuilder) -> 
            criteriaBuilder.greaterThanOrEqualTo(root.get("volumenM3"), volumen);
    }
    
    public static Specification<Pedido> volumenMenorIgual(Double volumen) {
        return (root, query, criteriaBuilder) -> 
            criteriaBuilder.lessThanOrEqualTo(root.get("volumenM3"), volumen);
    }
    
    public static Specification<Pedido> prioridadMayorIgual(Integer prioridad) {
        return (root, query, criteriaBuilder) -> 
            criteriaBuilder.greaterThanOrEqualTo(root.get("prioridad"), prioridad);
    }
    
    public static Specification<Pedido> fechaMayorIgual(LocalDateTime fecha) {
        return (root, query, criteriaBuilder) -> 
            criteriaBuilder.greaterThanOrEqualTo(root.get("fechaHoraRegistro"), fecha);
    }
    
    public static Specification<Pedido> fechaMenorIgual(LocalDateTime fecha) {
        return (root, query, criteriaBuilder) -> 
            criteriaBuilder.lessThanOrEqualTo(root.get("fechaHoraRegistro"), fecha);
    }
    
    public static Specification<Pedido> coordenadasEnRango(Integer xMin, Integer xMax, Integer yMin, Integer yMax) {
        return (root, query, criteriaBuilder) -> {
            return criteriaBuilder.and(
                criteriaBuilder.between(root.get("x"), xMin, xMax),
                criteriaBuilder.between(root.get("y"), yMin, yMax)
            );
        };
    }
    //fechaDesde
    public static Specification<Pedido> fechaDesde(LocalDateTime fechaDesde) {
        return (root, query, criteriaBuilder) -> 
            criteriaBuilder.greaterThanOrEqualTo(root.get("fechaHoraRegistro"), fechaDesde);
    }
    //fechaHasta
    public static Specification<Pedido> fechaHasta(LocalDateTime fechaHasta) {
        return (root, query, criteriaBuilder) -> 
            criteriaBuilder.lessThanOrEqualTo(root.get("fechaHoraRegistro"), fechaHasta);
    }

}