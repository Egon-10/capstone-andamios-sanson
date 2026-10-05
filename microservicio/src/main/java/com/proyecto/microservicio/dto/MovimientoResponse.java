package com.proyecto.microservicio.dto;

import com.proyecto.microservicio.model.Movimiento;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Movimiento tal como se devuelve al cliente.
 *
 * Se expone un registro propio en lugar de la entidad para no arrastrar las
 * relaciones completas de JPA ni filtrar datos del usuario que no hacen falta
 * en la pantalla de movimientos.
 */
public record MovimientoResponse(
        Long id,
        String tipo,
        String motivo,
        String motivoNombre,
        String observacion,
        Integer cantidad,
        LocalDateTime fecha,
        String estado,
        BigDecimal costoUnitario,
        BigDecimal valorizado,
        Integer saldoResultante,
        Long productoId,
        String producto,
        String sku,
        String usuario,
        Long movimientoOrigenId,
        LocalDateTime fechaAnulacion,
        String usuarioAnulacion) {

    public static MovimientoResponse de(Movimiento m) {
        return new MovimientoResponse(
                m.getId(),
                m.getTipo(),
                m.getMotivo() == null ? null : m.getMotivo().getCodigo(),
                m.getMotivo() == null ? null : m.getMotivo().getNombre(),
                m.getObservacion(),
                m.getCantidad(),
                m.getFecha(),
                m.getEstado(),
                m.getCostoUnitario(),
                m.valorizado(),
                m.getSaldoResultante(),
                m.getProducto() == null ? null : m.getProducto().getId(),
                m.getProducto() == null ? null : m.getProducto().getNombre(),
                m.getProducto() == null ? null : m.getProducto().getSku(),
                nombreDe(m.getUsuario()),
                m.getMovimientoOrigen() == null ? null : m.getMovimientoOrigen().getId(),
                m.getFechaAnulacion(),
                nombreDe(m.getUsuarioAnulacion()));
    }

    private static String nombreDe(com.proyecto.microservicio.model.Usuario u) {
        if (u == null) {
            return null;
        }
        String apellidos = u.getApellidos();
        return apellidos == null || apellidos.isBlank()
                ? u.getNombre()
                : u.getNombre() + " " + apellidos;
    }
}
