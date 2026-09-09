package com.sems.organizations;

import static org.junit.jupiter.api.Assertions.*;

import com.sems.organizations.domain.model.aggregates.Organization;
import com.sems.organizations.domain.model.aggregates.Site;
import com.sems.organizations.domain.model.entities.Membership;
import com.sems.organizations.domain.model.entities.Zone;
import com.sems.organizations.domain.model.valueobjects.*;
import com.sems.shared.errors.AppException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Reglas de organizaciones, locales, zonas y permisos.
 *
 * <p>Los permisos son la parte delicada: una regla mal puesta no falla, deja ver
 * datos a quien no debe. Eso no lo detecta ninguna prueba de humo.
 */
class OrganizationDomainTest {

    private static Organization unaCadena() {
        return Organization.register("Supermercados Andinos S.A.C.", "MercaAndes", "20512345678",
                BusinessType.SUPERMARKET);
    }

    @Test
    @DisplayName("El RUC debe tener once digitos")
    void rucDeOnceDigitos() {
        assertThrows(AppException.class,
                () -> Organization.register("X", null, "20512345", BusinessType.SUPERMARKET));
        assertThrows(AppException.class,
                () -> Organization.register("X", null, "2051234567A", BusinessType.SUPERMARKET));
    }

    @Test
    @DisplayName("Una organizacion archivada no se reactiva")
    void archivadaNoSeReactiva() {
        Organization cadena = unaCadena();
        cadena.archive();
        assertThrows(AppException.class, cadena::reactivate);
    }

    @Test
    @DisplayName("Un local necesita potencia contratada mayor que cero")
    void potenciaContratadaPositiva() {
        assertThrows(AppException.class, () -> Site.register(UUID.randomUUID(), "T-001",
                "Miraflores", null, null, null, BigDecimal.ZERO, TariffCategory.MT2, false));
    }

    @Test
    @DisplayName("El exceso de potencia se calcula sobre lo contratado")
    void excesoDePotencia() {
        Site local = Site.register(UUID.randomUUID(), "T-001", "Miraflores", null, null, null,
                new BigDecimal("120"), TariffCategory.MT2, false);

        assertEquals(0, local.excesoDePotencia(new BigDecimal("150")).compareTo(new BigDecimal("30")));
        assertEquals(0, local.excesoDePotencia(new BigDecimal("120")).compareTo(BigDecimal.ZERO));
        assertEquals(0, local.excesoDePotencia(new BigDecimal("90")).compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("Solo la categoria BT5B no cobra potencia")
    void soloBt5bNoCobraPotencia() {
        assertFalse(TariffCategory.BT5B.cobraPorPotencia());
        assertTrue(TariffCategory.BT3.cobraPorPotencia());
        assertTrue(TariffCategory.MT2.cobraPorPotencia());
    }

    @Test
    @DisplayName("Una camara frigorifica funciona fuera de horario por defecto")
    void camaraFuncionaFueraDeHorario() {
        // Lo usa el modulo de alertas: consumo de madrugada en una camara es
        // normal y en la sala de ventas es un equipo olvidado encendido.
        Zone camara = Zone.register(UUID.randomUUID(), "Camaras", ZoneType.COLD_STORAGE, null);
        Zone sala = Zone.register(UUID.randomUUID(), "Sala", ZoneType.SALES_FLOOR, null);

        assertTrue(camara.isOperatesOffHours());
        assertFalse(sala.isOperatesOffHours());
    }

    @Test
    @DisplayName("Lo que diga el usuario manda sobre el valor deducido")
    void elUsuarioManda() {
        Zone sala = Zone.register(UUID.randomUUID(), "Sala 24h", ZoneType.SALES_FLOOR, true);
        assertTrue(sala.isOperatesOffHours());
    }

    @Test
    @DisplayName("Un administrador no puede quedar atado a un solo local")
    void adminNoSeAtaAUnLocal() {
        // Si se permitiera, la cadena se quedaria sin nadie capaz de dar de alta
        // el siguiente local.
        assertThrows(AppException.class, () -> Membership.grant(UUID.randomUUID(),
                UUID.randomUUID(), MembershipRole.ORG_ADMIN, UUID.randomUUID()));
    }

    @Test
    @DisplayName("Un supervisor sin local asignado se rechaza")
    void supervisorNecesitaLocal() {
        // Dejarlo pasar le daria acceso a toda la cadena por descuido.
        assertThrows(AppException.class, () -> Membership.grant(UUID.randomUUID(),
                UUID.randomUUID(), MembershipRole.SUPERVISOR, null));
    }

    @Test
    @DisplayName("El supervisor solo alcanza a su local")
    void supervisorSoloSuLocal() {
        UUID suLocal = UUID.randomUUID();
        Membership vinculo = Membership.grant(UUID.randomUUID(), UUID.randomUUID(),
                MembershipRole.SUPERVISOR, suLocal);

        assertTrue(vinculo.alcanzaAlLocal(suLocal));
        assertFalse(vinculo.alcanzaAlLocal(UUID.randomUUID()));
    }

    @Test
    @DisplayName("El administrador alcanza a cualquier local de su cadena")
    void adminAlcanzaTodo() {
        Membership vinculo = Membership.grant(UUID.randomUUID(), UUID.randomUUID(),
                MembershipRole.ORG_ADMIN, null);

        assertTrue(vinculo.alcanzaAlLocal(UUID.randomUUID()));
        assertTrue(vinculo.alcanzaAlLocal(UUID.randomUUID()));
    }

    @Test
    @DisplayName("El operario consulta pero no modifica")
    void operarioSoloConsulta() {
        Membership operario = Membership.grant(UUID.randomUUID(), UUID.randomUUID(),
                MembershipRole.OPERATOR, null);

        assertFalse(operario.puedeModificar());
        assertFalse(operario.puedeAdministrarLaOrganizacion());
    }

    @Test
    @DisplayName("El supervisor modifica pero no administra la cadena")
    void supervisorModificaNoAdministra() {
        Membership supervisor = Membership.grant(UUID.randomUUID(), UUID.randomUUID(),
                MembershipRole.SUPERVISOR, UUID.randomUUID());

        assertTrue(supervisor.puedeModificar());
        assertFalse(supervisor.puedeAdministrarLaOrganizacion());
    }

    @Test
    @DisplayName("Un vinculo revocado deja de dar acceso")
    void revocadoNoDaAcceso() {
        UUID local = UUID.randomUUID();
        Membership vinculo = Membership.grant(UUID.randomUUID(), UUID.randomUUID(),
                MembershipRole.SUPERVISOR, local);
        vinculo.revoke();

        assertFalse(vinculo.alcanzaAlLocal(local));
        assertFalse(vinculo.puedeModificar());
    }
}
