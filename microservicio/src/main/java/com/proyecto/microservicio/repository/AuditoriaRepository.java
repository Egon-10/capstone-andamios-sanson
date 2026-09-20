package com.proyecto.microservicio.repository;

import com.proyecto.microservicio.model.Auditoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditoriaRepository
        extends JpaRepository<Auditoria, Long> {

    List<Auditoria> findAllByOrderByFechaDesc();

    @Query("""
SELECT a
FROM Auditoria a
WHERE
(:accion IS NULL OR a.accion LIKE %:accion%)
AND
(:inicio IS NULL OR a.fecha >= :inicio)
AND
(:fin IS NULL OR a.fecha <= :fin)
ORDER BY a.fecha DESC
""")
List<Auditoria> obtenerReporteAuditoria(
        @Param("accion") String accion,
        @Param("inicio") LocalDateTime inicio,
        @Param("fin") LocalDateTime fin
);

}