# Biometrics-Server

## Building image

<pre>
mvn package
</pre>

## Pushing image to docker hub

<pre>
mvn dockerfile:push
</pre>

## Running image

Running the image standalone

<pre>
sudo docker run -it -p 8080:8080 --rm luismanuelamengual/biometrics-server::{TAG}
</pre>

Running the image with Fluent support

<pre>
sudo docker run -it -p 8080:8080 --log-driver fluentd --log-opt tag="biometrics" --rm luismanuelamengual/biometrics-server::{TAG}
</pre>

## Running FluentD image

Create a Dockerfile with the following content

<pre>
FROM fluent/fluentd
RUN ["gem", "install", "fluent-plugin-logzio", "--no-rdoc", "--no-ri"]
COPY td-agent.conf /fluentd/etc/fluent.conf
ENTRYPOINT ["fluentd", "-c", "/fluentd/etc/fluent.conf"]
</pre>

Build the image with the following content

<pre>
sudo docker build -t fluentd .
</pre>

Run the image with the following command

<pre>
sudo docker run -it -d -p 24224:24224 fluentd:latest
</pre>

## Configuring Amazon

Create a folder .aws in the home directory
Create a file "config" in .aws folder with the following content
   
<pre>
   [default]
   region = us-west-2
</pre>

Create a file "credentials" in .aws folder with the following content

<pre>
    [default]
    aws_access_key_id = {access_key_id}
    aws_secret_access_key = {secrect_access_key}
</pre>

## Configuring Tesseract

<pre>
sudo apt install tesseract-ocr
</pre>

## Sending Requests

Add the follwing header for requests

<pre>
Authorization: Bearer {api key}
</pre>

Use the following api Key for test purposes

<pre>
eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJhdXRoMCIsImNsaWVudCI6InRlc3QifQ.5SY_UQaaILYpryU0qNBuPrPTPkF79UhTCNFElXjzUyY
</pre>