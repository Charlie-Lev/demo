package com.plg.repository;

import com.plg.domain.Camion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
@Repository
public interface CamionRepository extends JpaRepository<Camion, Integer> {
}
