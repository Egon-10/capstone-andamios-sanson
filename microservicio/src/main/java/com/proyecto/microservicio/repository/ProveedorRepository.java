package com.proyecto.microservicio.repository;

import com.proyecto.microservicio.model.Proveedor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProveedorRepository
        extends JpaRepository<Proveedor, Long> {
}