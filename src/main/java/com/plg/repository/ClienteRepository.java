package com.plg.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.plg.domain.Cliente;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Integer> {}