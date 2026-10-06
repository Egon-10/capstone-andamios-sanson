package com.proyecto.microservicio.dto;

import com.proyecto.microservicio.model.AjusteInventario;
import com.proyecto.microservicio.model.Usuario;

import java.time.LocalDateTime;

/** HU-16: ajuste tal como se devuelve al cliente. */
public record AjusteResponse(
        Long id,
        Long productoId,
        String producto,
        String sku,
        Integer stockSistema,
        Integer stockFisico,
        Integer diferencia,
        String observacion,
        String estado,
        String usuarioSolicita,
        String usuarioAprueba,
        LocalDateTime fechaSolicitud,
        LocalDateTime fechaResolucion,
        String motivoRechazo,
        Long movimientoId) {

    public static AjusteResponse de(AjusteInventario a) {
        return new AjusteResponse(
                a.getId(),
                a.getProducto() == null ? null : a.getProducto().getId(),
                a.getProducto() == null ? null : a.getProducto().getNombre(),
                a.getProducto() == null ? null : a.getProducto().getSku(),
                a.getStockSistema(),
                a.getStockFisico(),
                a.getDiferencia(),
                a.getObservacion(),
                a.getEstado(),
                nombreDe(a.getUsuarioSolicita()),
                nombreDe(a.getUsuarioAprueba()),
                a.getFechaSolicitud(),
                a.getFechaResolucion(),
                a.getMotivoRechazo(),
                a.getMovimiento() == null ? null : a.getMovimiento().getId());
    }

    private static String nombreDe(Usuario u) {
        if (u == null) {
            return null;
        }
        String apellidos = u.getApellidos();
        return apellidos == null || apellidos.isBlank()
                ? u.getNombre()
                : u.getNombre() + " " + apellidos;
    }
}
