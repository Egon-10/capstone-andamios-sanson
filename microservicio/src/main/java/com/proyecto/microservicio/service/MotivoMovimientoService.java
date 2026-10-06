package com.proyecto.microservicio.service;

import com.proyecto.microservicio.exception.ReglaNegocioException;
import com.proyecto.microservicio.model.MotivoMovimiento;
import com.proyecto.microservicio.model.TiposMovimiento;
import com.proyecto.microservicio.repository.MotivoMovimientoRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * HU-14: resuelve y valida el motivo tipificado de un movimiento.
 *
 * Un motivo solo es aceptable si existe en el catálogo, está activo y
 * corresponde al tipo de movimiento que se está registrando: un motivo de
 * salida no puede usarse en una entrada. Cuando el motivo exige nota, el
 * movimiento debe traer además una observación que lo explique.
 */
@Service
public class MotivoMovimientoService {

    /** Motivos que el sistema se reserva para generar los asientos de anulación. */
    public static final String ANULACION_ENTRADA = "ANULACION_ENTRADA";
    public static final String ANULACION_SALIDA = "ANULACION_SALIDA";

    private final MotivoMovimientoRepository repository;

    public MotivoMovimientoService(MotivoMovimientoRepository repository) {
        this.repository = repository;
    }

    /**
     * Motivos que el cliente puede ofrecer al registrar un movimiento. Los dos
     * motivos de anulación quedan fuera: los asigna el servicio al revertir un
     * asiento y no se eligen a mano.
     */
    public List<MotivoMovimiento> listarSeleccionables() {
        return repository.findByActivoTrueOrderByAplicaAAscNombreAsc().stream()
                .filter(m -> !esDeAnulacion(m.getCodigo()))
                .toList();
    }

    public List<MotivoMovimiento> listarPorTipo(String tipo) {
        return repository.findByAplicaAAndActivoTrueOrderByNombreAsc(normalizar(tipo)).stream()
                .filter(m -> !esDeAnulacion(m.getCodigo()))
                .toList();
    }

    /**
     * Comprueba que el motivo sea válido para el tipo de movimiento indicado y
     * lo devuelve. Lanza una regla de negocio con el campo señalado cuando no
     * lo es, para que el cliente pueda marcar el control correspondiente.
     */
    public MotivoMovimiento validar(String codigo, String tipo, String observacion) {
        String buscado = normalizar(codigo);

        if (esDeAnulacion(buscado)) {
            throw new ReglaNegocioException("motivo",
                    "Los motivos de anulación los asigna el sistema y no se pueden elegir");
        }

        MotivoMovimiento motivo = repository.findById(buscado)
                .orElseThrow(() -> new ReglaNegocioException("motivo",
                        "El motivo " + buscado + " no existe en el catálogo"));

        if (!motivo.isActivo()) {
            throw new ReglaNegocioException("motivo",
                    "El motivo " + motivo.getNombre() + " está dado de baja");
        }

        if (!motivo.getAplicaA().equals(tipo)) {
            throw new ReglaNegocioException("motivo",
                    "El motivo " + motivo.getNombre() + " corresponde a un movimiento de "
                            + motivo.getAplicaA().toLowerCase(Locale.ROOT) + " y no a uno de "
                            + tipo.toLowerCase(Locale.ROOT));
        }

        if (motivo.isExigeNota() && (observacion == null || observacion.isBlank())) {
            throw new ReglaNegocioException("observacion",
                    "El motivo " + motivo.getNombre() + " exige una observación que lo explique");
        }

        return motivo;
    }

    /** Motivo con el que el sistema genera el asiento que revierte a otro (HU-15). */
    public MotivoMovimiento motivoDeAnulacion(String tipoDelAsientoCompensatorio) {
        String codigo = TiposMovimiento.ENTRADA.equals(tipoDelAsientoCompensatorio)
                ? ANULACION_ENTRADA
                : ANULACION_SALIDA;
        return repository.findById(codigo)
                .orElseThrow(() -> new ReglaNegocioException(
                        "No está cargado el motivo " + codigo + " en el catálogo. "
                                + "Ejecute la migración 03-sprint2-movimientos.sql"));
    }

    static boolean esDeAnulacion(String codigo) {
        return ANULACION_ENTRADA.equals(codigo) || ANULACION_SALIDA.equals(codigo);
    }

    private static String normalizar(String valor) {
        return valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
    }
}
