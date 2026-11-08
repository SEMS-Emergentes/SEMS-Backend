package com.sems.energy.domain.repositories;

import com.sems.energy.domain.model.entities.EnergyMeter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnergyMeterRepository {
    EnergyMeter save(EnergyMeter meter);
    Optional<EnergyMeter> findById(UUID id);
    Optional<EnergyMeter> findBySerial(String meterSerial);
    /** Los medidores vinculados a la cuenta. Los desvinculados no aparecen. */
    List<EnergyMeter> findByUserId(String userId);

    /** Medidores vigentes instalados en un local. */
    List<EnergyMeter> findBySiteId(String siteId);

    /** Medidores vigentes de una zona concreta. */
    List<EnergyMeter> findByZoneId(String zoneId);

    /**
     * El medidor que presenta esa credencial, si sigue vinculado.
     *
     * <p>Se busca por el hash, nunca por la clave en claro, y un medidor
     * desvinculado no aparece: su credencial deja de valer en el momento en que
     * se le saca de la cuenta.
     */
    Optional<EnergyMeter> findByCredentialHash(String credentialHash);
}
