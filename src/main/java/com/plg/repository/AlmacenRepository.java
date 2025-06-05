package com.plg.repository;

import com.plg.domain.Almacen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
@Repository
public interface AlmacenRepository extends JpaRepository<Almacen, Integer> {
}