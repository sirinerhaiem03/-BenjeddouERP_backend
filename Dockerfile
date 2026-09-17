# ══════════════════════════════════════════════════════
# STAGE 1 — Build avec Maven (télécharge les dépendances, compile, package le .jar)
# ══════════════════════════════════════════════════════
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copie d'abord pom.xml seul pour profiter du cache Docker
# (si les dépendances ne changent pas, ce layer n'est pas rebuild à chaque fois)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copie le reste du code source et build
COPY src ./src
RUN mvn clean package -DskipTests -B

# ══════════════════════════════════════════════════════
# STAGE 2 — Image finale légère, juste le JRE + le .jar compilé
# ══════════════════════════════════════════════════════
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copie uniquement le .jar généré à l'étape précédente (image finale beaucoup plus légère)
COPY --from=build /app/target/*.jar app.jar

# Render fournit sa propre variable PORT — l'app doit écouter dessus
# (déjà géré dans application-prod.properties via server.port=${PORT:8080})
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
