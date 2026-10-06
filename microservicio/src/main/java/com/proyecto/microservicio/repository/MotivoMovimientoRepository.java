package com.proyecto.microservicio.repository;

import com.proyecto.microservicio.model.MotivoMovimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** HU-14: catálogo de motivos tipificados de movimiento. */
@Repository
public interface MotivoMovimientoRepository extends JpaRepository<MotivoMovimiento, String> {

    List<MotivoMovimiento> findByActivoTrueOrderByAplicaAAscNombreAsc();

    List<MotivoMovimiento> findByAplicaAAndActivoTrueOrderByNombreAsc(String aplicaA);
}
