package com.sems.energy.application;

import com.sems.energy.domain.model.entities.EnergyMeter;
import com.sems.energy.domain.repositories.EnergyMeterRepository;
import com.sems.energy.domain.repositories.EnergyReadingRepository;
import com.sems.shared.errors.AppException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Entrada de lecturas enviadas por el propio medidor.
 *
 * <p>Es la puerta que faltaba. La otra, {@code POST /api/v1/energy-readings},
 * exige el JWT de la persona dueña del {@code user_id}, asi que solo sirve para
 * que alguien registre a mano lo que leyo; un aparato no puede usarla sin
 * llevar encima las credenciales de su dueno. Aqui el medidor se identifica con
 * su propia clave y el usuario se deduce de el: el cuerpo no lo trae, y por
 * tanto no puede mentir sobre a quien pertenece el consumo.
 *
 * <p>El lote se acepta por partes a proposito. Un medidor que recupera la
 * conexion vuelca lo que tenia guardado, y que una lectura mal formada tire las
 * demas obligaria al aparato a resolver un problema que no sabe resolver. Cada
 * lectura entra en su propia transaccion, se cuentan las aceptadas, las
 * repetidas y las rechazadas, y la respuesta dice cual fallo y por que.
 */
@Service
@RequiredArgsConstructor
public class ReadingIngestionService {

    /** Frecuencia nominal de la red peruana. */
    private static final double FRECUENCIA_NOMINAL_HZ = 60d;

    /** Una lectura tal como la manda el aparato, antes de validarse. */
    public record IncomingReading(Double energyKwh, Double powerWatts, Double voltage,
                                  Double current, Double frequency, Instant timestamp,
                                  String deviceId, String readingType, String phase) {
    }

    /** Lectura que no entro, con su posicion en el lote y el motivo. */
    public record RejectedReading(int index, String reason) {
    }

    public record IngestionResult(UUID meterId, int accepted, int duplicated,
                                  List<RejectedReading> rejected) {
    }

    private final MeterCredentialService credenciales;
    private final EnergyCommandService commands;
    private final EnergyReadingRepository readings;
    private final EnergyMeterRepository meters;
    private final TransactionTemplate transaccion;

    /**
     * Registra el lote que envia un medidor autenticado por su credencial.
     *
     * <p>No es transaccional a proposito: cada lectura va en su propia
     * transaccion, y envolverlas todas en una sola haria que el rechazo de una
     * marcase la transaccion compartida para deshacerse, tirando tambien las
     * que si eran validas.
     */
    public IngestionResult ingest(String apiKey, List<IncomingReading> lote) {
        EnergyMeter medidor = credenciales.resolve(apiKey);
        if (!medidor.isActive()) {
            throw AppException.conflict("the meter is not active");
        }
        if (lote == null || lote.isEmpty()) {
            throw AppException.validation("readings is required");
        }

        String meterId = medidor.getId().toString();
        int aceptadas = 0;
        int repetidas = 0;
        List<RejectedReading> rechazadas = new ArrayList<>();

        for (int i = 0; i < lote.size(); i++) {
            IncomingReading lectura = lote.get(i);
            if (lectura == null) {
                rechazadas.add(new RejectedReading(i, "the reading is empty"));
                continue;
            }
            // Sin marca de tiempo se usa la de llegada: un aparato sin reloj en
            // hora es comun, y rechazarle el dato seria peor que fecharlo aqui.
            Instant marca = lectura.timestamp() == null ? Instant.now() : lectura.timestamp();

            try {
                if (registrar(medidor, meterId, lectura, marca)) {
                    aceptadas++;
                } else {
                    repetidas++;
                }
            } catch (AppException e) {
                // El resto del lote sigue su curso: el aparato no puede hacer
                // nada con un error que afecta a una sola de sus lecturas, pero
                // quien lea la respuesta si.
                rechazadas.add(new RejectedReading(i, e.getMessage()));
            }
        }
        return new IngestionResult(medidor.getId(), aceptadas, repetidas, List.copyOf(rechazadas));
    }

    /**
     * Guarda una lectura si no estaba ya; devuelve si entro.
     *
     * <p>Comprobar y escribir van en la misma transaccion y con el medidor
     * reservado. Sueltos dejaban un hueco: un medidor que no recibe la
     * respuesta reintenta el lote mientras el primer envio sigue en curso, los
     * dos comprueban antes de que ninguno haya escrito y el consumo entra dos
     * veces. La reserva es por medidor, asi que no frena a los demas.
     *
     * <p>La lectura la identifican los tres juntos: medidor, equipo y marca de
     * tiempo. Con solo medidor y marca, un medidor que reporta varios equipos a
     * la misma hora veia como se guardaba el primero y los demas se contaban
     * como repetidos.
     *
     * <p>No se resuelve con una restriccion unica en la tabla porque el equipo
     * es nulo en las lecturas del local entero, y para la base dos nulos nunca
     * son iguales.
     */
    private boolean registrar(EnergyMeter medidor, String meterId, IncomingReading lectura,
            Instant marca) {
        return Boolean.TRUE.equals(transaccion.execute(estado -> {
            meters.lockForWriting(medidor.getId());
            if (readings.existsByMeterDeviceAndTimestamp(meterId, lectura.deviceId(), marca)) {
                return false;
            }
            commands.recordReading(medidor.getUserId(), meterId, lectura.deviceId(),
                    valor(lectura.powerWatts()), valor(lectura.voltage()),
                    valor(lectura.current()), frecuencia(lectura.frequency()),
                    valor(lectura.energyKwh()), marca,
                    lectura.readingType() == null ? "DEVICE" : lectura.readingType(),
                    lectura.phase());
            return true;
        }));
    }

    private static double valor(Double medida) {
        return medida == null ? 0d : medida;
    }

    /**
     * Frecuencia de la red, 60 Hz en Peru cuando el aparato no la reporta.
     *
     * <p>El dominio exige una frecuencia fisicamente posible (45-65 Hz), asi
     * que dejarla en cero rechazaria la lectura de cualquier medidor que solo
     * sepa contar kWh, que son la mayoria. La nominal no se usa para calcular
     * nada: se guarda como dato, y el aparato que mida la de verdad la manda y
     * sustituye a esta.
     */
    private static double frecuencia(Double medida) {
        return medida == null ? FRECUENCIA_NOMINAL_HZ : medida;
    }
}
