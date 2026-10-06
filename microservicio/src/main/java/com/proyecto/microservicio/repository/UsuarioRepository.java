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

    Optional<Usuario> findByCorreoIgnoreCase(String correo);

    Optional<Usuario> findByNombreUsuario(String nombreUsuario);

    boolean existsByCorreoIgnoreCase(String correo);

    boolean existsByCorreoIgnoreCaseAndIdNot(String correo, Long id);

    boolean existsByNombreUsuario(String nombreUsuario);

    boolean existsByNumeroDocumento(String numeroDocumento);


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