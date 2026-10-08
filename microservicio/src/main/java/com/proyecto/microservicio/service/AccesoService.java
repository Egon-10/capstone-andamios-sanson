package com.proyecto.microservicio.service;

import com.proyecto.microservicio.config.ZonaHoraria;
import com.proyecto.microservicio.dto.FiltroBitacora;
import com.proyecto.microservicio.model.Acceso;
import com.proyecto.microservicio.model.Usuario;
import com.proyecto.microservicio.repository.AccesoRepository;
import com.proyecto.microservicio.security.OrigenSolicitud;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

/**
 * HU-32: bitácora de accesos exitosos y fallidos.
 *
 * El registro va en una transacción propia (REQUIRES_NEW). Un inicio de
 * sesión fallido termina en una excepción que deshace la transacción del
 * login; si el registro del intento viajara en ella, se perdería justo el
 * dato que la historia pide conservar.
 */
@Service
public class AccesoService {

    private static final int LARGO_IDENTIFICADOR = 100;
    private static final Set<String> RESULTADOS = Set.of(Acceso.EXITOSO, Acceso.FALLIDO, Acceso.BLOQUEADO);
    private static final Sort RECIENTES_PRIMERO = Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("id"));

    private final AccesoRepository repository;

    public AccesoService(AccesoRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Acceso registrar(String identificador, Usuario usuario, String resultado, String motivo,
                            OrigenSolicitud origen) {
        OrigenSolicitud o = origen == null ? OrigenSolicitud.DESCONOCIDO : origen;
        Acceso a = new Acceso();
        a.setFecha(ZonaHoraria.ahora());
        a.setIdentificador(identificador(identificador));
        a.setUsuario(usuario);
        a.setResultado(resultado);
        a.setMotivo(motivo);
        a.setIp(o.ip());
        a.setAgente(o.agente());
        return repository.save(a);
    }

    @Transactional(readOnly = true)
    public Page<Acceso> buscar(FiltroBitacora filtro, int pagina, int tamano) {
        return repository.buscar(filtro.patronTexto(), filtro.usuarioId(), resultado(filtro.resultado()),
                filtro.inicio(), filtro.fin(),
                PageRequest.of(Math.max(pagina, 0), AuditoriaService.acotar(tamano), RECIENTES_PRIMERO));
    }

    /** Para el reporte: los intentos más recientes que cumplen el filtro, hasta el tope indicado. */
    @Transactional(readOnly = true)
    public Page<Acceso> buscarParaReporte(FiltroBitacora filtro, int tope) {
        return repository.buscar(filtro.patronTexto(), filtro.usuarioId(), resultado(filtro.resultado()),
                filtro.inicio(), filtro.fin(), PageRequest.of(0, tope, RECIENTES_PRIMERO));
    }

    /** Lo escrito como usuario, recortado y en minúsculas; nunca vacío. */
    static String identificador(String valor) {
        if (valor == null || valor.isBlank()) {
            return "(vacío)";
        }
        String limpio = valor.trim().toLowerCase(Locale.ROOT);
        return limpio.length() <= LARGO_IDENTIFICADOR ? limpio : limpio.substring(0, LARGO_IDENTIFICADOR);
    }

    /** Un resultado desconocido no filtra, en lugar de devolver una lista vacía engañosa. */
    static String resultado(String valor) {
        if (valor == null) {
            return null;
        }
        String r = valor.trim().toUpperCase(Locale.ROOT);
        return RESULTADOS.contains(r) ? r : null;
    }
}
