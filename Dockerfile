FROM luismanuelamengual/biometrics-base:1.0.1
RUN mkdir /opt/app
COPY Biometrics*-with-dependencies.jar /opt/app/Biometrics.jar
CMD ["java", "-Djava.library.path=/opt/opencv/build/lib", "-jar", "/opt/app/Biometrics.jar"]