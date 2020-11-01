# Biometrics-Server

## Building image

mvn package

## Pushing image to docker hub

mvn dockerfile:push

## Running image

sudo docker run -it -p 8080:8080 --log-driver fluentd --log-opt tag="biometrics" --rm luismanuelamengual/biometrics-server::{TAG}

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