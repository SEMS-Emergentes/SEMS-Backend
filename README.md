# SEMS Backend

Monolito modular del Smart Energy Management System. Un unico desplegable que
contiene los bounded contexts del sistema como modulos independientes.

## Arquitectura

```
com.sems
├── shared/          nucleo comun: bus de eventos de dominio y contrato de errores
├── iam/             identidad, acceso, verificacion y recuperacion de cuenta
├── devices/         registro, vinculacion y configuracion de dispositivos
├── energy/          medidores, lecturas, consumo y tarifa electrica
├── analytics/       proyecciones, recomendaciones, anomalias y rankings
├── alerts/          umbrales, reglas de inactividad y notificaciones
├── payments/        cobros, comprobantes y webhooks de Stripe
└── subscriptions/   planes y suscripciones de usuario
```

Cada modulo tiene las cuatro capas separadas:

- `domain/` — agregados, entidades, value objects y puertos. Sin dependencias de framework.
- `application/` — casos de uso. Orquesta, no contiene reglas de negocio.
- `infrastructure/` — persistencia, integraciones externas, adaptadores.
- `interfaces/` — controladores REST y contratos JSON.

## Comunicacion entre modulos

No hay message broker. Los eventos de dominio viajan en proceso mediante
`DomainEventBus`, y los consumidores los reciben con
`@TransactionalEventListener(AFTER_COMMIT)`: un modulo solo reacciona a un cambio
que ya se confirmo en base de datos.

Equivalencia con el disenio anterior de microservicios:

| Topic de Kafka | Evento de dominio |
|---|---|
| `iam.events` | `UserRegistered`, `UserLoggedIn`, `VerificationRequested`, `PasswordResetRequested` |
| `device.events` | `DeviceRegistered`, `DeviceLinked`, `DeviceUnlinked`, `DeviceStatusUpdated` |
| `energy.events` | `ReadingProcessed` |
| `alerts.events` | `AlertTriggered` |
| `payments.events` | `PaymentProcessed` |
| `subscriptions.events` | `SubscriptionChanged` |

Si en el futuro hiciera falta un broker, se anade un adaptador en
`DomainEventBus` y ni los emisores ni los consumidores cambian.

## Ejecutar en local

```bash
cp .env.example .env      # completa los valores
./mvnw spring-boot:run
```

- API: `http://localhost:8080/api/v1`
- Documentacion: `http://localhost:8080/swagger-ui.html`
- Salud: `http://localhost:8080/actuator/health`
- Metricas: `http://localhost:8080/actuator/prometheus`

## Pruebas

```bash
./mvnw test                 # ejecuta la suite
./mvnw test jacoco:report   # informe de cobertura en target/site/jacoco
```

## Docker

```bash
docker build -t sems-backend .
docker run --env-file .env -p 8080:8080 sems-backend
```

## Notas de despliegue

- El health check del proveedor debe apuntar a `/actuator/health`.
- La ruta `/api/v1/webhooks/**` esta abierta sin token: Stripe se autentica por
  la firma del cuerpo. Si se cierra, ningun cobro se confirma.
- Los planes Starter, Business y Enterprise se cargan solos la primera vez que
  arranca contra una base vacia.
- Los cobros son simulados: `POST /api/v1/subscriptions/purchase` valida la
  tarjeta y la "cobra" sin pasarela. `4242 4242 4242 4242` se aprueba y
  `4000 0000 0000 0002` se rechaza. Un plan de pago solo se activa por esa ruta.
