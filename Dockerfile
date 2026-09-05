# =============================================================================
#  SEMS - Backend (Spring Boot 3 / Java 21)
#
#  Construccion en dos etapas: la imagen final solo lleva el runtime, no el JDK
#  ni el codigo fuente.
# =============================================================================

# ------------------------------------------------------------ etapa de build
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Se usa el `mvn` que ya trae la imagen, NO ./mvnw.
#
# El wrapper existe para maquinas que no tienen Maven instalado; esta imagen si
# lo tiene, asi que ahi solo estorba. Y estorba de verdad: el wrapper necesita
# el bit de ejecucion, que se pierde al pasar el proyecto por Windows o por un
# comprimido, y entonces la build muere con "./mvnw: Permission denied".
#
# Primero solo el pom: mientras no cambien las dependencias, Docker reutiliza
# la capa del go-offline y la build tarda segundos en vez de minutos.
COPY pom.xml ./
RUN mvn -B -q -DskipTests -Dmaven.wagon.http.retryHandler.count=5 dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests -Dmaven.wagon.http.retryHandler.count=5 clean package

# ---------------------------------------------------------- etapa de runtime
FROM eclipse-temurin:21-jre
WORKDIR /app

# Usuario sin privilegios: si alguien logra ejecutar codigo dentro del
# contenedor, no lo hace como root.
RUN useradd --create-home --uid 1001 sems
USER sems

COPY --from=build --chown=sems:sems /workspace/target/*.jar app.jar

# El proveedor de hosting inyecta PORT; la aplicacion lo lee en
# server.port=${SERVER_PORT:${PORT:8080}} y cae a 8080 si no viene.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
