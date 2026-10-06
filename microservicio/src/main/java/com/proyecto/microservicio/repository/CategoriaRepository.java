package com.proyecto.microservicio.repository;

import com.proyecto.microservicio.model.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoriaRepository
        extends JpaRepository<Categoria, Long> {

    List<Categoria> findByActivoTrueOrderByNombreAsc();

    List<Categoria> findAllByOrderByNombreAsc();

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
}
