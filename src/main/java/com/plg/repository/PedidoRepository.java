package com.plg.repository;

import com.plg.domain.Pedido;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PedidoRepository extends JpaRepository<Pedido, Integer>, JpaSpecificationExecutor<Pedido> {
    
    /**
     * Búsqueda optimizada con fetch join para evitar N+1
     */
    @EntityGraph(attributePaths = {"cliente"})
    Page<Pedido> findAll(Specification<Pedido> spec, Pageable pageable);
    
    /**
     * Búsqueda de texto libre optimizada
     */
    @Query("SELECT p FROM Pedido p LEFT JOIN FETCH p.cliente c " +
           "WHERE LOWER(c.codigo) LIKE LOWER(CONCAT('%', :texto, '%')) " +
           "OR CAST(p.id AS string) LIKE CONCAT('%', :texto, '%') " +
           "OR CAST(p.volumenM3 AS string) LIKE CONCAT('%', :texto, '%')")
    Page<Pedido> buscarPorTexto(@Param("texto") String texto, Pageable pageable);
    
    /**
     * Consultas de agregación para estadísticas
     */
    @Query("SELECT COALESCE(SUM(p.volumenM3), 0) FROM Pedido p")
    Double sumarVolumenTotal();
    
    @Query("SELECT COUNT(p) FROM Pedido p WHERE p.prioridad >= :prioridad")
    Long contarPorPrioridad(@Param("prioridad") Integer prioridad);
    
    @Query("SELECT COUNT(p) FROM Pedido p WHERE DATE(p.fechaHoraRegistro) = CURRENT_DATE")
    Long contarPedidosHoy();
    
    /**
     * Consulta optimizada para top pedidos por volumen
     */
    @Query("SELECT p FROM Pedido p LEFT JOIN FETCH p.cliente " +
           "ORDER BY p.volumenM3 DESC")
    Page<Pedido> findTopPorVolumen(Pageable pageable);
    
    /**
     * Consulta optimizada para pedidos urgentes
     */
    @Query("SELECT p FROM Pedido p LEFT JOIN FETCH p.cliente " +
           "WHERE p.prioridad >= :prioridad " +
           "ORDER BY p.prioridad DESC, p.fechaHoraRegistro ASC")
    Page<Pedido> findPedidosUrgentes(@Param("prioridad") Integer prioridad, Pageable pageable);
    
    /**
     * Consulta para pedidos por rango de fechas (optimizada)
     */
    @Query("SELECT p FROM Pedido p LEFT JOIN FETCH p.cliente " +
           "WHERE p.fechaHoraRegistro BETWEEN :fechaInicio AND :fechaFin")
    Page<Pedido> findByFechaRange(
        @Param("fechaInicio") LocalDateTime fechaInicio,
        @Param("fechaFin") LocalDateTime fechaFin,
        Pageable pageable
    );


    // En PedidoRepository.java agregar estos métodos:
       @Query("SELECT p FROM Pedido p WHERE p.id NOT IN " +
              "(SELECT DISTINCT e.pedido.id FROM Entrega e WHERE e.pedido.id IS NOT NULL)")
       List<Pedido> findPedidosNoAsignados();

       @Query("SELECT COUNT(p) FROM Pedido p WHERE p.prioridad >= :prioridadMinima")
       Long contarPorPrioridad(@Param("prioridadMinima") int prioridadMinima);

}