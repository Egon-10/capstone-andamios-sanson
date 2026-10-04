package com.proyecto.microservicio.config;

import com.proyecto.microservicio.model.Rol;
import com.proyecto.microservicio.model.Roles;
import com.proyecto.microservicio.model.Usuario;
import com.proyecto.microservicio.repository.RolRepository;
import com.proyecto.microservicio.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Prepara los datos mínimos al iniciar:
 *  1. Crea los roles del sistema si no existen.
 *  2. Cifra con BCrypt las contraseñas que aún estén en texto plano (migración de SEG-BD-01).
 *  3. Si no existe ningún usuario y se configuró app.admin.*, crea el administrador inicial.
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final RolRepository roles;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final String adminCorreo;
    private final String adminPassword;

    public DataInitializer(RolRepository roles, UsuarioRepository usuarios, PasswordEncoder encoder,
                           @Value("${app.admin.correo:}") String adminCorreo,
                           @Value("${app.admin.password:}") String adminPassword) {
        this.roles = roles;
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.adminCorreo = adminCorreo;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (String nombre : List.of(Roles.ADMINISTRADOR, Roles.GERENTE, Roles.ENCARGADO)) {
            if (roles.findByNombreIgnoreCase(nombre).isEmpty()) {
                roles.save(new Rol(null, nombre));
                log.info("Rol creado: {}", nombre);
            }
        }

        int migrados = 0;
        for (Usuario u : usuarios.findAll()) {
            if (u.getPassword() != null && !esHashBcrypt(u.getPassword())) {
                u.setPassword(encoder.encode(u.getPassword()));
                usuarios.save(u);
                migrados++;
            }
        }
        if (migrados > 0) {
            log.info("Contraseñas migradas a BCrypt: {}", migrados);
        }

        if (usuarios.count() == 0 && !adminCorreo.isBlank() && !adminPassword.isBlank()) {
            Usuario admin = new Usuario();
            admin.setNombre("Administrador");
            admin.setCorreo(adminCorreo);
            admin.setNombreUsuario("admin");
            admin.setPassword(encoder.encode(adminPassword));
            admin.setEstado(Usuario.ESTADO_ACTIVO);
            admin.setArea("ADMINISTRACION");
            admin.setFechaCreacion(LocalDateTime.now());
            admin.setRol(roles.findByNombreIgnoreCase(Roles.ADMINISTRADOR).orElseThrow());
            usuarios.save(admin);
            log.info("Administrador inicial creado: {}", adminCorreo);
        }
    }

    static boolean esHashBcrypt(String valor) {
        return valor.matches("^\\$2[aby]\\$\\d{2}\\$.{53}$");
    }
}
