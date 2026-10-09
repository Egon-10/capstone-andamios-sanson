package com.proyecto.microservicio.repository;

import com.proyecto.microservicio.model.Acceso;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AccesoRepository extends JpaRepository<Acceso, Long> {

    /**
     * HU-32: consulta de la bitácora de accesos con filtros combinables. El
     * texto se busca en el identificador escrito y en la dirección IP.
     */
    @Query(value = """
        SELECT a FROM Acceso a
        LEFT JOIN FETCH a.usuario u
        WHERE (:texto IS NULL
               OR LOWER(a.identificador) LIKE :texto ESCAPE '!'
               OR a.ip LIKE :texto ESCAPE '!')
          AND (:usuarioId IS NULL OR u.id = :usuarioId)
          AND (:resultado IS NULL OR a.resultado = :resultado)
          AND (:desde IS NULL OR a.fecha >= :desde)
          AND (:hasta IS NULL OR a.fecha < :hasta)
        """,
        countQuery = """
        SELECT COUNT(a) FROM Acceso a
        LEFT JOIN a.usuario u
        WHERE (:texto IS NULL
               OR LOWER(a.identificador) LIKE :texto ESCAPE '!'
               OR a.ip LIKE :texto ESCAPE '!')
          AND (:usuarioId IS NULL OR u.id = :usuarioId)
          AND (:resultado IS NULL OR a.resultado = :resultado)
          AND (:desde IS NULL OR a.fecha >= :desde)
          AND (:hasta IS NULL OR a.fecha < :hasta)
        """)
    Page<Acceso> buscar(@Param("texto") String texto,
                        @Param("usuarioId") Long usuarioId,
                        @Param("resultado") String resultado,
                        @Param("desde") LocalDateTime desde,
                        @Param("hasta") LocalDateTime hasta,
                        Pageable pagina);

    /** Fallos registrados desde una hora dada: lo usa el resumen de seguridad. */
    long countByResultadoAndFechaGreaterThanEqual(String resultado, LocalDateTime desde);

    /** HU-34: los dos últimos accesos de un resultado dado, del más reciente al más antiguo. */
    List<Acceso> findTop2ByUsuario_IdAndResultadoOrderByFechaDescIdDesc(Long usuarioId, String resultado);

    /** HU-34: intentos de un resultado dado posteriores a una fecha. */
    long countByUsuario_IdAndResultadoAndFechaAfter(Long usuarioId, String resultado, LocalDateTime fecha);
}
