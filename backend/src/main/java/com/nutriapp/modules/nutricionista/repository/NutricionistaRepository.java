package com.nutriapp.modules.nutricionista.repository;

import com.nutriapp.modules.nutricionista.entity.Nutricionista;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NutricionistaRepository extends JpaRepository<Nutricionista, UUID> {

    Optional<Nutricionista> findByKeycloakUserIdAndDeletedAtIsNull(String keycloakUserId);

    Optional<Nutricionista> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);
}
