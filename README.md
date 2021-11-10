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
sudo docker run -it -d -p 80:80 --name biometrics-server --rm luismanuelamengual/biometrics-server:{TAG}
</pre>

Running the image with Fluent support

<pre>
sudo docker run -it -p 80:80 --name biometrics-server --log-driver fluentd --log-opt tag="biometrics" --rm luismanuelamengual/biometrics-server:{TAG}
</pre>

## Creating and running fluentd image

Create the image

<pre>
sudo docker build -t biometrics-logs-router .
</pre>

Run the image with the following command

<pre>
sudo docker run -it -d -p 24224:24224 biometrics-logs-router:latest
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

## Dump database to file (biometrics.sql)

pg_dump biometrics > biometrics.sql

## Reseting (Deleting) database in heroku

heroku pg:reset DATABASE -a biometrics-server

## Pushing local database to heroku

heroku pg:push biometrics DATABASE_URL -a biometrics-server

## Deploy on heroku

* mvn package
* heroku login
* heroku container:login
* heroku container:push web -a biometrics-server --arg JAR_FILE=target/Biometrics-1.4.1-jar-with-dependencies.jar
* heroku container:release web -a biometrics-server

