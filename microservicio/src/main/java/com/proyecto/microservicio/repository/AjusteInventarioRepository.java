package com.proyecto.microservicio.repository;

import com.proyecto.microservicio.model.AjusteInventario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/** HU-16: ajustes de inventario por conteo físico. */
@Repository
public interface AjusteInventarioRepository extends JpaRepository<AjusteInventario, Long> {

    List<AjusteInventario> findByEstadoOrderByFechaSolicitudAsc(String estado);

    List<AjusteInventario> findByProductoIdOrderByFechaSolicitudDesc(Long productoId);

    List<AjusteInventario> findAllByOrderByFechaSolicitudDesc();

    /** HU-23: cuantos conteos esperan aprobacion. */
    long countByEstado(String estado);

    /**
     * Comprueba si el producto ya tiene un conteo sin resolver. Dos ajustes
     * pendientes sobre el mismo producto se calcularían contra el mismo stock
     * de sistema y al aprobarse sumarían dos correcciones, dejando el
     * inventario peor de lo que estaba.
     */
    @Query("""
            SELECT COUNT(a) > 0
            FROM AjusteInventario a
            WHERE a.producto.id = :productoId
              AND a.estado = 'PENDIENTE'
            """)
    boolean tienePendiente(@Param("productoId") Long productoId);
}
