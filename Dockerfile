FROM adoptopenjdk/openjdk15:ubuntu-jre
ARG JAR_FILE
ARG AWS_ACCESS_KEY_ID
ARG AWS_ACCESS_KEY
RUN apt-get update \
  && apt-get install -y tesseract-ocr \
  && mkdir /opt/app \
  && mkdir ~/.aws \
  && printf "[default]\nregion = us-west-2" > ~/.aws/config \
  && printf "[default]\naws_access_key_id = ${AWS_ACCESS_KEY_ID}\naws_secret_access_key = ${AWS_ACCESS_KEY}" > ~/.aws/credentials \
  && apt-get clean \
  && apt-get autoremove
COPY ${JAR_FILE} /opt/app/Biometrics.jar
EXPOSE 80
CMD ["java", "-jar", "/opt/app/Biometrics.jar"]