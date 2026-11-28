FROM gradle:8.14-jdk17 AS build
WORKDIR /app
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle
RUN ./gradlew --version --no-daemon
COPY . .
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app \
    && mkdir -p /app/uploads/documents \
    && chown -R app:app /app
COPY --from=build /app/build/libs/*.jar /app/school-wallet.jar
RUN chown app:app /app/school-wallet.jar
USER app
ENV APP_DOCUMENTS_DIR=/app/uploads/documents
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app/school-wallet.jar"]
