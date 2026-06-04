## AdoptOpenJDK 停止发布 OpenJDK 二进制，而 Eclipse Temurin 是它的延伸，提供更好的稳定性
FROM eclipse-temurin:21-jre

RUN mkdir -p /yudao-module-iot-server
WORKDIR /yudao-module-iot-server

COPY ./target/yudao-module-iot-server.jar app.jar

ENV TZ=Asia/Shanghai JAVA_OPTS="-Xms512m -Xmx512m"

EXPOSE 48091

CMD java ${JAVA_OPTS} -Djava.security.egd=file:/dev/./urandom -jar app.jar
