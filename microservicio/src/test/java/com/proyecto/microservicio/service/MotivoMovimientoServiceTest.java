package com.proyecto.microservicio.service;

import com.proyecto.microservicio.exception.ReglaNegocioException;
import com.proyecto.microservicio.model.MotivoMovimiento;
import com.proyecto.microservicio.repository.MotivoMovimientoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** CP-15: motivo tipificado obligatorio en cada movimiento (HU-14). */
class MotivoMovimientoServiceTest {

    private MotivoMovimientoRepository repositorio;
    private MotivoMovimientoService servicio;

    private static MotivoMovimiento motivo(String codigo, String aplicaA, boolean exigeNota, boolean activo) {
        MotivoMovimiento m = new MotivoMovimiento();
        m.setCodigo(codigo);
        m.setNombre(codigo.toLowerCase());
        m.setAplicaA(aplicaA);
        m.setExigeNota(exigeNota);
        m.setActivo(activo);
        return m;
    }

    @BeforeEach
    void preparar() {
        repositorio = mock(MotivoMovimientoRepository.class);
        servicio = new MotivoMovimientoService(repositorio);
        when(repositorio.findById("COMPRA")).thenReturn(Optional.of(motivo("COMPRA", "ENTRADA", false, true)));
        when(repositorio.findById("MERMA")).thenReturn(Optional.of(motivo("MERMA", "SALIDA", true, true)));
        when(repositorio.findById("VIEJO")).thenReturn(Optional.of(motivo("VIEJO", "SALIDA", false, false)));
    }

    @Test
    @DisplayName("CP-15: acepta un motivo activo del tipo correcto, sin distinguir mayusculas")
    void motivoValido() {
        assertEquals("COMPRA", servicio.validar(" compra ", "ENTRADA", null).getCodigo());
    }

    @Test
    @DisplayName("CP-15: rechaza un motivo que no existe")
    void motivoInexistente() {
        when(repositorio.findById("INVENTADO")).thenReturn(Optional.empty());
        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> servicio.validar("INVENTADO", "ENTRADA", null));
        assertEquals("motivo", ex.getCampo());
    }

    @Test
    @DisplayName("CP-15: rechaza un motivo vacio")
    void motivoVacio() {
        when(repositorio.findById("")).thenReturn(Optional.empty());
        assertThrows(ReglaNegocioException.class, () -> servicio.validar(null, "ENTRADA", null));
    }

    @Test
    @DisplayName("CP-15: rechaza un motivo dado de baja")
    void motivoInactivo() {
        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> servicio.validar("VIEJO", "SALIDA", null));
        assertTrue(ex.getMessage().contains("baja"));
    }

    @Test
    @DisplayName("CP-15: rechaza un motivo de salida en una entrada")
    void motivoDeOtroTipo() {
        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> servicio.validar("MERMA", "ENTRADA", "nota"));
        assertTrue(ex.getMessage().contains("salida"));
    }

    @Test
    @DisplayName("CP-15: exige observacion cuando el motivo la pide")
    void exigeNota() {
        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> servicio.validar("MERMA", "SALIDA", "   "));
        assertEquals("observacion", ex.getCampo());
        assertNotNull(servicio.validar("MERMA", "SALIDA", "Dos planchas dobladas"));
    }

    @Test
    @DisplayName("CP-16: los motivos de anulacion no se pueden elegir a mano")
    void motivoDeAnulacionReservado() {
        assertThrows(ReglaNegocioException.class,
                () -> servicio.validar("ANULACION_ENTRADA", "ENTRADA", null));
        verify(repositorio, never()).findById("ANULACION_ENTRADA");
    }

    @Test
    @DisplayName("CP-16: el sistema usa el motivo de anulacion que corresponde al tipo")
    void motivoDeAnulacion() {
        MotivoMovimiento entrada = motivo("ANULACION_ENTRADA", "ENTRADA", false, true);
        MotivoMovimiento salida = motivo("ANULACION_SALIDA", "SALIDA", false, true);
        when(repositorio.findById("ANULACION_ENTRADA")).thenReturn(Optional.of(entrada));
        when(repositorio.findById("ANULACION_SALIDA")).thenReturn(Optional.of(salida));

        assertEquals(entrada, servicio.motivoDeAnulacion("ENTRADA"));
        assertEquals(salida, servicio.motivoDeAnulacion("SALIDA"));
    }

    @Test
    @DisplayName("Avisa si falta cargar el motivo de anulacion en la base")
    void motivoDeAnulacionAusente() {
        when(repositorio.findById("ANULACION_SALIDA")).thenReturn(Optional.empty());
        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> servicio.motivoDeAnulacion("SALIDA"));
        assertTrue(ex.getMessage().contains("03-sprint2"));
    }

    @Test
    @DisplayName("La lista que se ofrece al cliente no incluye los motivos de anulacion")
    void listasSinMotivosDeAnulacion() {
        List<MotivoMovimiento> todos = List.of(
                motivo("COMPRA", "ENTRADA", false, true),
                motivo("ANULACION_ENTRADA", "ENTRADA", false, true));
        when(repositorio.findByActivoTrueOrderByAplicaAAscNombreAsc()).thenReturn(todos);
        when(repositorio.findByAplicaAAndActivoTrueOrderByNombreAsc("ENTRADA")).thenReturn(todos);

        assertEquals(List.of("COMPRA"),
                servicio.listarSeleccionables().stream().map(MotivoMovimiento::getCodigo).toList());
        assertEquals(List.of("COMPRA"),
                servicio.listarPorTipo("entrada").stream().map(MotivoMovimiento::getCodigo).toList());
    }
}
