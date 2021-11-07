#!/bin/bash
set -e

echo "DEPLOYING TO HEROKU !!"
heroku login
heroku container:login
heroku container:push web --arg JAR_FILE=target/Biometrics-1.5.3-jar-with-dependencies.jar -a biometrics-server
heroku container:release web -a biometrics-server
echo "DEPLOY TO HEROKU SUCCESS !!"