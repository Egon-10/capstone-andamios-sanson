package com.proyecto.microservicio.config;

import com.proyecto.microservicio.model.Rol;
import com.proyecto.microservicio.model.Usuario;
import com.proyecto.microservicio.repository.RolRepository;
import com.proyecto.microservicio.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Migración de contraseñas en texto plano a BCrypt (SEG-BD-01). */
class DataInitializerTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    @DisplayName("Distingue un hash BCrypt de una contraseña en texto plano")
    void detectaHash() {
        assertTrue(DataInitializer.esHashBcrypt(encoder.encode("Clave2026")));
        assertFalse(DataInitializer.esHashBcrypt("Clave2026"));
    }

    @Test
    @DisplayName("Cifra las contraseñas existentes que estén en texto plano, sin cambiar las ya cifradas")
    void migraContrasenasPlanas() {
        RolRepository roles = mock(RolRepository.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        when(roles.findByNombreIgnoreCase(anyString())).thenReturn(Optional.of(new Rol(1L, "ADMINISTRADOR")));

        Usuario plano = new Usuario(1L, "Ana", "ana@andamios.pe", "admin123", null);
        String hashPrevio = encoder.encode("otra");
        Usuario cifrado = new Usuario(2L, "Luis", "luis@andamios.pe", hashPrevio, null);
        when(usuarios.findAll()).thenReturn(List.of(plano, cifrado));
        when(usuarios.count()).thenReturn(2L);

        new DataInitializer(roles, usuarios, encoder, "", "").run(null);

        assertTrue(encoder.matches("admin123", plano.getPassword()));
        assertEquals(hashPrevio, cifrado.getPassword());
        verify(usuarios).save(plano);
        verify(usuarios, never()).save(cifrado);
        verify(roles, never()).save(any(Rol.class));
    }
}
