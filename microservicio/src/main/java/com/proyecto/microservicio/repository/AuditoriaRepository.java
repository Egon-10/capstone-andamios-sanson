package com.proyecto.microservicio.repository;

import com.proyecto.microservicio.model.Auditoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditoriaRepository extends JpaRepository<Auditoria, Long> {

    /** Últimas acciones, para el panel de inicio. */
    List<Auditoria> findTop10ByOrderByFechaDescIdDesc();

    /**
     * HU-33: consulta de la bitácora con filtros combinables.
     *
     * Cada filtro es opcional: un parámetro nulo no restringe. El texto se
     * busca en la acción y en el detalle, y llega ya en minúsculas, con los
     * comodines del usuario escapados con ! y rodeado de %. El rango de fechas es
     * semiabierto: desde inclusive, hasta exclusive.
     */
    @Query(value = """
        SELECT a FROM Auditoria a
        LEFT JOIN a.usuario u
        WHERE (:texto IS NULL
               OR LOWER(a.accion) LIKE :texto ESCAPE '!'
               OR LOWER(a.detalle) LIKE :texto ESCAPE '!')
          AND (:usuarioId IS NULL OR u.id = :usuarioId)
          AND (:desde IS NULL OR a.fecha >= :desde)
          AND (:hasta IS NULL OR a.fecha < :hasta)
        """,
        countQuery = """
        SELECT COUNT(a) FROM Auditoria a
        LEFT JOIN a.usuario u
        WHERE (:texto IS NULL
               OR LOWER(a.accion) LIKE :texto ESCAPE '!'
               OR LOWER(a.detalle) LIKE :texto ESCAPE '!')
          AND (:usuarioId IS NULL OR u.id = :usuarioId)
          AND (:desde IS NULL OR a.fecha >= :desde)
          AND (:hasta IS NULL OR a.fecha < :hasta)
        """)
    Page<Auditoria> buscar(@Param("texto") String texto,
                           @Param("usuarioId") Long usuarioId,
                           @Param("desde") LocalDateTime desde,
                           @Param("hasta") LocalDateTime hasta,
                           Pageable pagina);
}
