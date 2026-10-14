package com.proyecto.microservicio.repository;

import com.proyecto.microservicio.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** HU-31: revoca de una vez todos los tokens de renovación vigentes de un usuario. */
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revocado = true WHERE t.usuario.id = :usuarioId AND t.revocado = false")
    int revocarTodosDe(@Param("usuarioId") Long usuarioId);
}
