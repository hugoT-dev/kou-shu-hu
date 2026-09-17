FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY docker/maven-settings.xml /root/.m2/settings.xml
COPY pom.xml .
COPY yl-common/pom.xml yl-common/pom.xml
COPY yl-gateway/pom.xml yl-gateway/pom.xml
COPY yl-asr/pom.xml yl-asr/pom.xml
COPY yl-nursing/pom.xml yl-nursing/pom.xml
COPY yl-common/src yl-common/src
COPY yl-gateway/src yl-gateway/src
RUN mvn -B -pl yl-gateway -am -Dmaven.test.skip=true package \
    && cp yl-gateway/target/yl-gateway-*.jar /tmp/app.jar

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
COPY --from=build /tmp/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
