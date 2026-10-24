package com.sems.shared.persistence;

import java.util.List;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Agrega las claves foraneas que faltan DENTRO de cada bounded context.
 *
 * <p>El esquema lo gestiona {@code ddl-auto=update}, que crea tablas y columnas
 * pero no estas restricciones, porque las entidades guardan los identificadores
 * como campos escalares y no como asociaciones. Declararlas con
 * {@code @ManyToOne} las crearia, pero a cambio meteria la entidad relacionada
 * en el grafo de objetos: con {@code open-in-view} en false eso abre la puerta a
 * fallos de carga perezosa, y los mapeadores de respuesta tendrian que cambiar.
 * No merece la pena para lo unico que se busca aqui, que es integridad en la
 * base. Por eso se aplican con SQL explicito, siguiendo el mismo patron de
 * arranque que ya usan RoleDataInitializer y PlanSeeder.
 *
 * <p>Las relaciones ENTRE modulos se quedan deliberadamente fuera. Una
 * restriccion cruzada acoplaria los esquemas de dos contextos e impediria
 * extraer uno a su propio servicio; esa integridad la garantiza el dominio.
 *
 * <p>El modulo de energia tambien se queda fuera, pero por otro motivo: sus
 * identificadores son varchar y em_energy_meters.id es uuid, asi que la clave
 * de las lecturas hacia el medidor es imposible sin convertir los tipos, lo que
 * exige migrar datos. Queda como deuda tecnica.
 *
 * <p>Es idempotente y no interrumpe el arranque: si una restriccion ya existe la
 * salta, y si hay filas huerfanas que la impidan avisa con el conteo y sigue, en
 * lugar de dejar la aplicacion sin levantar.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ForeignKeyInitializer implements ApplicationRunner {

    /**
     * Una clave foranea por declarar.
     *
     * @param table      tabla hija
     * @param column     columna que referencia
     * @param target     tabla padre
     * @param targetKey  columna referenciada
     * @param onDelete   CASCADE, SET NULL o RESTRICT
     * @param nullable   si la columna admite nulos, para no contar esas filas
     *                   como huerfanas
     */
    private record ForeignKey(String table, String column, String target, String targetKey,
                              String onDelete, boolean nullable) {

        String constraintName() {
            return "fk_" + table + "_" + column;
        }
    }

    // CASCADE  : la hija no existe sin la padre.
    // SET NULL : la columna admite nulos y hay que conservar el registro.
    // RESTRICT : borrar la padre teniendo hijas seria un accidente.
    private static final List<ForeignKey> CLAVES = List.of(
            // --- Organizations
            new ForeignKey("og_sites", "organization_id", "og_organizations", "organization_id", "RESTRICT", false),
            new ForeignKey("og_zones", "site_id", "og_sites", "site_id", "RESTRICT", false),
            new ForeignKey("og_memberships", "organization_id", "og_organizations", "organization_id", "CASCADE", false),
            new ForeignKey("og_memberships", "site_id", "og_sites", "site_id", "SET NULL", true),
            // --- Device Management
            new ForeignKey("dm_device_bindings", "device_id", "dm_devices", "device_id", "CASCADE", false),
            new ForeignKey("dm_device_configurations", "device_id", "dm_devices", "device_id", "CASCADE", false),
            new ForeignKey("dm_device_events", "device_id", "dm_devices", "device_id", "CASCADE", false),
            // --- Alerts
            new ForeignKey("al_notification_logs", "alert_id", "al_alerts", "alert_id", "SET NULL", true),
            new ForeignKey("al_alerts", "threshold_id", "al_thresholds", "threshold_id", "SET NULL", true),
            new ForeignKey("al_alerts", "inactivity_rule_id", "al_inactivity_rules", "inactivity_rule_id", "SET NULL", true),
            // --- Payments
            new ForeignKey("pm_invoices", "payment_id", "pm_payments", "payment_id", "RESTRICT", false),
            new ForeignKey("pm_payments", "payment_method_id", "pm_payment_methods", "payment_method_id", "SET NULL", true),
            // --- Identity & Access Management
            new ForeignKey("iam_refresh_tokens", "user_id", "iam_users", "user_id", "CASCADE", false),
            new ForeignKey("iam_user_auth_tokens", "user_id", "iam_users", "user_id", "CASCADE", false),
            // --- Subscriptions
            new ForeignKey("sb_subscriptions", "plan_id", "sb_subscription_plans", "plan_id", "RESTRICT", false),
            new ForeignKey("sb_plan_features", "plan_id", "sb_subscription_plans", "plan_id", "CASCADE", false));

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        // Las consultas de abajo leen pg_constraint, que solo existe en
        // PostgreSQL. Las pruebas levantan el contexto sobre H2, asi que sin
        // esta comprobacion el arranque fallaria ahi.
        if (!esPostgres(jdbc)) {
            log.info("La base no es PostgreSQL: no se declaran las claves foraneas intramodulo");
            return;
        }
        int creadas = 0;
        int existentes = 0;
        int bloqueadas = 0;

        for (ForeignKey fk : CLAVES) {
            if (!tablaExiste(jdbc, fk.table()) || !tablaExiste(jdbc, fk.target())) {
                log.warn("Clave foranea {}: falta alguna de las tablas, se omite", fk.constraintName());
                continue;
            }
            if (restriccionExiste(jdbc, fk)) {
                existentes++;
                continue;
            }
            long huerfanas = contarHuerfanas(jdbc, fk);
            if (huerfanas > 0) {
                // No se borra nada por iniciativa propia: eso lo decide una
                // persona mirando los datos.
                log.warn("Clave foranea {} no se pudo crear: {} fila(s) de {}.{} apuntan a un {} "
                                + "que no existe. Revisar esas filas y reiniciar.",
                        fk.constraintName(), huerfanas, fk.table(), fk.column(), fk.target());
                bloqueadas++;
                continue;
            }
            // El indice sobre la columna hija lo necesita cualquier consulta que
            // siga la relacion, y Postgres no lo crea solo al declarar la clave.
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_" + fk.table() + "_" + fk.column()
                    + " ON " + fk.table() + " (" + fk.column() + ")");
            jdbc.execute("ALTER TABLE " + fk.table()
                    + " ADD CONSTRAINT " + fk.constraintName()
                    + " FOREIGN KEY (" + fk.column() + ")"
                    + " REFERENCES " + fk.target() + " (" + fk.targetKey() + ")"
                    + " ON DELETE " + fk.onDelete());
            log.info("Clave foranea creada: {}.{} -> {} (ON DELETE {})",
                    fk.table(), fk.column(), fk.target(), fk.onDelete());
            creadas++;
        }

        log.info("Claves foraneas intramodulo: {} creadas, {} ya existian, {} bloqueadas por filas huerfanas",
                creadas, existentes, bloqueadas);
    }

    private boolean esPostgres(JdbcTemplate jdbc) {
        try {
            String producto = jdbc.execute(
                    (org.springframework.jdbc.core.ConnectionCallback<String>) conexion ->
                            conexion.getMetaData().getDatabaseProductName());
            return producto != null && producto.toLowerCase().contains("postgres");
        } catch (RuntimeException e) {
            log.warn("No se pudo identificar el motor de base de datos: {}", e.getMessage());
            return false;
        }
    }

    private boolean tablaExiste(JdbcTemplate jdbc, String tabla) {
        Boolean existe = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM information_schema.tables"
                        + " WHERE table_schema = current_schema() AND table_name = ?)",
                Boolean.class, tabla);
        return Boolean.TRUE.equals(existe);
    }

    private boolean restriccionExiste(JdbcTemplate jdbc, ForeignKey fk) {
        // Se busca por la relacion, no por el nombre: si Hibernate ya creo la
        // misma clave con su nombre generado, no hay que duplicarla.
        Boolean existe = jdbc.queryForObject(
                "SELECT EXISTS ("
                        + " SELECT 1 FROM pg_constraint c"
                        + " JOIN pg_class child ON child.oid = c.conrelid"
                        + " JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)"
                        + " WHERE c.contype = 'f' AND child.relname = ? AND a.attname = ?)",
                Boolean.class, fk.table(), fk.column());
        return Boolean.TRUE.equals(existe);
    }

    private long contarHuerfanas(JdbcTemplate jdbc, ForeignKey fk) {
        String nulos = fk.nullable() ? " c." + fk.column() + " IS NOT NULL AND" : "";
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + fk.table() + " c WHERE" + nulos
                        + " NOT EXISTS (SELECT 1 FROM " + fk.target() + " p"
                        + " WHERE p." + fk.targetKey() + " = c." + fk.column() + ")",
                Long.class);
        return total == null ? 0L : total;
    }
}
