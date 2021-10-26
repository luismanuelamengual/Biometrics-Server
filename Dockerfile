FROM adoptopenjdk/openjdk15:ubuntu-jre
ARG JAR_FILE
RUN apt-get update \
  && apt-get install -y tesseract-ocr \
  && mkdir /opt/app \
  && apt-get clean \
  && apt-get autoremove
COPY ${JAR_FILE} /opt/app/Biometrics.jar
CMD ["java", "-jar", "/opt/app/Biometrics.jar"]