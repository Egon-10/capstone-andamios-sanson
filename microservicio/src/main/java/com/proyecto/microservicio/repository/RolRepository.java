package com.proyecto.microservicio.repository;

import com.proyecto.microservicio.model.Rol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RolRepository
        extends JpaRepository<Rol, Long> {
}