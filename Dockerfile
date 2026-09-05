FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Se usa el `mvn` que ya trae la imagen, NO ./mvnw. El wrapper necesita el bit
# de ejecucion, que se pierde al pasar el proyecto por Windows o por un
# comprimido, y entonces la build muere con "./mvnw: Permission denied".
COPY pom.xml ./
RUN mvn -B -q -DskipTests -Dmaven.wagon.http.retryHandler.count=5 dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests -Dmaven.wagon.http.retryHandler.count=5 clean package

FROM eclipse-temurin:21-jre
WORKDIR /app

# Usuario sin privilegios: si alguien logra ejecutar codigo dentro del
# contenedor, no lo hace como root.
RUN useradd --create-home --uid 1001 sems
USER sems

COPY --from=build --chown=sems:sems /workspace/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]