# Multi-stage build для уменьшения размера образа
FROM gradle:8.14-jdk-21-and-24 AS build

WORKDIR /app

# Копируем файлы для сборки
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle gradle
COPY src src

# Собираем приложение
RUN gradle build -x test --no-daemon

# Финальный образ
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Копируем собранный jar из стадии сборки
COPY --from=build /app/build/libs/*.jar app.jar

# Создаем пользователя для безопасности
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Открываем порт приложения
EXPOSE 8080

# Настройки JVM для оптимизации в контейнере
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Запуск приложения
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
