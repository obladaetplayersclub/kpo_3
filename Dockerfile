# Многоэтапная сборка
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Финальный образ
FROM eclipse-temurin:21-jre

# Устанавливаем unzip для распаковки JAR
RUN apt-get update && apt-get install -y unzip && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
RUN mkdir -p /app/files

EXPOSE 8080

# Простой запуск
# Если MAIN_CLASS указан, распаковываем JAR и запускаем нужный класс
# Иначе используем класс из манифеста
CMD if [ -z "$MAIN_CLASS" ]; then \
    java -jar -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE} app.jar; \
  else \
    mkdir -p /tmp/app && \
    cd /tmp/app && \
    unzip -q /app/app.jar && \
    java -cp "BOOT-INF/classes:BOOT-INF/lib/*" -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE} ${MAIN_CLASS}; \
  fi
