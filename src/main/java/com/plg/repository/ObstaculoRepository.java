package com.plg.repository;

import com.plg.domain.Obstaculo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ObstaculoRepository extends JpaRepository<Obstaculo, Integer> {
    
    List<Obstaculo> findByActivoTrue();
    
    List<Obstaculo> findByTipo(String tipo);
    
    @Query("SELECT o FROM Obstaculo o WHERE o.activo = true AND o.tipo = :tipo")
    List<Obstaculo> findObstaculosActivosPorTipo(String tipo);
    
    void deleteByTipo(String tipo);
}