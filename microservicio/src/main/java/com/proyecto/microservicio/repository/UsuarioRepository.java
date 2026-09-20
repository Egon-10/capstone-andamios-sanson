package com.proyecto.microservicio.repository;
import java.util.List;
import com.proyecto.microservicio.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRepository
        extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByCorreo(String correo);


    @Query("""
SELECT u
FROM Usuario u
WHERE
(
    :rol IS NULL
    OR
    u.rol.nombre = :rol
)
ORDER BY u.nombre
""")
List<Usuario> obtenerReporteUsuarios(
        @Param("rol")
        String rol
);
}