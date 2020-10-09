FROM luismanuelamengual/biometrics-base:1.0.1
RUN mkdir /opt/app
ARG JAR_FILE
COPY target/${JAR_FILE} /opt/app/Biometrics.jar
CMD ["java", "-Djava.library.path=/opt/opencv/build/lib", "-jar", "/opt/app/Biometrics.jar"]