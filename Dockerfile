FROM gradle:8.14-jdk17 AS build
WORKDIR /app
COPY . .
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/build/libs/*.jar /app/school-wallet.jar
RUN chown -R app:app /app
USER app
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app/school-wallet.jar"]
