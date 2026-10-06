package com.proyecto.microservicio.repository;

import com.proyecto.microservicio.model.TokenRevocado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TokenRevocadoRepository extends JpaRepository<TokenRevocado, String> {
}
