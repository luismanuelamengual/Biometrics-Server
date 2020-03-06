# Biometrics-Server

## Sending Requests

Add the follwing header for requests

<pre>
Authorization: Bearer {api key}
</pre>

Use the following api Key for test purposes

<pre>
eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJhdXRoMCIsImNsaWVudCI6InRlc3QifQ.5SY_UQaaILYpryU0qNBuPrPTPkF79UhTCNFElXjzUyY
</pre>

## Configuring OpenCV

Download openCV library (version 4.1.2) from the following link

<pre>
https://github.com/opencv/opencv/archive/4.1.2.zip
</pre>

Make sure you have the JAVA_HOME environment variable is set

<pre>
export JAVA_HOME=/usr/lib/jvm/java-13-oracle 
</pre>

Install required building tools

<pre>
sudo apt-get install build-essential cmake git libgtk2.0-dev pkg-config libavcodec-dev libavformat-dev libswscale-dev python-dev python-numpy libtbb2 libtbb-dev libjpeg-dev libpng-dev libtiff-dev libjasper-dev libdc1394-22-dev ant
</pre>

Build the library

<pre>
cd opencv-4.1.2
mkdir build
cd build
cmake -D BUILD_SHARED_LIBS=OFF -D BUILD_EXAMPLES=OFF -D INSTALL_PYTHON_EXAMPLES=OFF -D INSTALL_C_EXAMPLES=OFF -D BUILD_PERF_TESTS=OFF -D BUILD_TESTS=OFF ..
make -j8
</pre>

Bind OpenCV to the java application. Add the following to the VM options

<pre>
Djava.library.path=~/opencv/build/lib
</pre>

## Configuring Amazon

1. Create a folder .aws in the home directory
2. Create a file "config" in .aws folder with the following content
   
<pre>
   [default]
   region = us-west-2
</pre>

3. Create a file "credentials" in .aws folder with the following content

<pre>
    [default]
    aws_access_key_id = {access_key_id}
    aws_secret_access_key = {secrect_access_key}
</pre>