package com.proyecto.microservicio.repository;

import com.proyecto.microservicio.model.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByCorreo(String correo);

    Optional<Usuario> findByCorreoIgnoreCase(String correo);

    Optional<Usuario> findByNombreUsuario(String nombreUsuario);

    boolean existsByCorreoIgnoreCase(String correo);

    boolean existsByCorreoIgnoreCaseAndIdNot(String correo, Long id);

    boolean existsByNombreUsuario(String nombreUsuario);

    boolean existsByNumeroDocumento(String numeroDocumento);

    /**
     * HU-30: búsqueda de usuarios con filtros combinables. El texto se busca
     * en nombres, apellidos, correo, nombre de usuario y documento. Una cuenta
     * sin estado es anterior al Sprint 1 y se considera activa.
     */
    @Query(value = """
        SELECT u FROM Usuario u
        LEFT JOIN FETCH u.rol r
        WHERE (:texto IS NULL
               OR LOWER(u.nombre) LIKE :texto ESCAPE '!'
               OR LOWER(u.apellidos) LIKE :texto ESCAPE '!'
               OR LOWER(u.correo) LIKE :texto ESCAPE '!'
               OR LOWER(u.nombreUsuario) LIKE :texto ESCAPE '!'
               OR LOWER(u.numeroDocumento) LIKE :texto ESCAPE '!')
          AND (:rolId IS NULL OR r.id = :rolId)
          AND (:estado IS NULL OR COALESCE(u.estado, 'ACTIVO') = :estado)
        """,
        countQuery = """
        SELECT COUNT(u) FROM Usuario u
        LEFT JOIN u.rol r
        WHERE (:texto IS NULL
               OR LOWER(u.nombre) LIKE :texto ESCAPE '!'
               OR LOWER(u.apellidos) LIKE :texto ESCAPE '!'
               OR LOWER(u.correo) LIKE :texto ESCAPE '!'
               OR LOWER(u.nombreUsuario) LIKE :texto ESCAPE '!'
               OR LOWER(u.numeroDocumento) LIKE :texto ESCAPE '!')
          AND (:rolId IS NULL OR r.id = :rolId)
          AND (:estado IS NULL OR COALESCE(u.estado, 'ACTIVO') = :estado)
        """)
    Page<Usuario> buscar(@Param("texto") String texto,
                         @Param("rolId") Long rolId,
                         @Param("estado") String estado,
                         Pageable pagina);

    /** HU-31: cuentas activas de un rol, para no dejar el sistema sin administrador. */
    @Query("""
        SELECT COUNT(u) FROM Usuario u
        WHERE u.rol.nombre = :rol
          AND COALESCE(u.estado, 'ACTIVO') = 'ACTIVO'
        """)
    long contarActivosConRol(@Param("rol") String rol);

    /** Lista corta para filtros (por ejemplo, la bitácora por usuario). */
    List<Usuario> findAllByOrderByNombreAscApellidosAsc();
}
