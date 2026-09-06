package com.sems.devices.infrastructure.persistence.jpa.repositories;

import com.sems.devices.infrastructure.persistence.jpa.entities.DeviceJpaEntity;
import com.sems.devices.domain.model.valueobjects.DeviceStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceJpaRepository extends JpaRepository<DeviceJpaEntity, UUID> {

    Optional<DeviceJpaEntity> findByExternalDeviceCode(String externalDeviceCode);

    boolean existsByExternalDeviceCode(String externalDeviceCode);

    List<DeviceJpaEntity> findByUserId(UUID userId);

    /**
     * Dispositivos vigentes del usuario: los borrados no salen.
     *
     * <p>El borrado es logico. Marca el dispositivo como REMOVED y conserva la
     * fila, porque de ella cuelgan las lecturas y el consumo historico. Pero
     * conservar la fila no es motivo para seguir ensenandola: si aparece en el
     * listado, el usuario borra un dispositivo, desaparece de la pantalla y
     * vuelve al recargar; y ademas sigue ocupando cupo, asi que con el plan
     * Free (3 dispositivos) basta con dar de alta y borrar tres para quedarse
     * sin poder anadir ninguno mas, para siempre.</p>
     */
    List<DeviceJpaEntity> findByUserIdAndStatusNot(UUID userId, DeviceStatus status);
}
