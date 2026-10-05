package com.proyecto.microservicio.dto;

import com.proyecto.microservicio.model.MotivoMovimiento;

/** HU-14: motivo tal como se ofrece al cliente. */
public record MotivoResponse(String codigo, String nombre, String aplicaA, boolean exigeNota) {

    public static MotivoResponse de(MotivoMovimiento m) {
        return new MotivoResponse(m.getCodigo(), m.getNombre(), m.getAplicaA(), m.isExigeNota());
    }
}
