package com.proyecto.microservicio.repository;

import com.proyecto.microservicio.model.Proveedor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProveedorRepository
        extends JpaRepository<Proveedor, Long> {

    List<Proveedor> findByActivoTrueOrderByNombreAsc();

    List<Proveedor> findAllByOrderByNombreAsc();

    boolean existsByRuc(String ruc);

    boolean existsByRucAndIdNot(String ruc, Long id);
}
