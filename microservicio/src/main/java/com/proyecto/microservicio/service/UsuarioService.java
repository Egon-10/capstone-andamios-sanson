package com.proyecto.microservicio.service;

import com.proyecto.microservicio.model.LoginDTO;
import com.proyecto.microservicio.model.Usuario;
import com.proyecto.microservicio.repository.UsuarioRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UsuarioService {

    private final UsuarioRepository repository;

    public UsuarioService(UsuarioRepository repository) {
        this.repository = repository;
    }

    public List<Usuario> listar() {
        return repository.findAll();
    }

    public Usuario obtener(Long id) {
        return repository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Usuario no encontrado"));
    }

    public Usuario guardar(Usuario usuario) {
        return repository.save(usuario);
    }

    public void eliminar(Long id) {
        repository.deleteById(id);
    }

    public Usuario login(LoginDTO login) {

        Usuario usuario = repository
                .findByCorreo(login.getCorreo())
                .orElseThrow(() ->
                        new RuntimeException("Correo incorrecto"));

        if (!usuario.getPassword()
                .equals(login.getPassword())) {

            throw new RuntimeException(
                    "Contraseña incorrecta");
        }

        return usuario;
    }
}