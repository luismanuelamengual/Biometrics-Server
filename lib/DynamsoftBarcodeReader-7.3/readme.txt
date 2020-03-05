Dynamsoft Barcode Reader Linux SDK is packaged as a tar.

ENVIRONMENT
- Linux x64

INSTALL:
1)Place the tar file into the directory you wish to install.
2)From a terminal to unpack the tar.
	$ tar -xf dbr-linux-{version number}.tar.gz
	$ cd ./Dynamsoft/BarcodeReader

Directory Structure after install:
./documents
./images
./include
./lib
./samples

How to run BarcodeReader sample:
From a terminal do the following:
1) $ cd ./samples/c++/ReadBarcode
2) $ make
3) $ ./ReadBarcode ../../../images/AllSupportedBarcodeTypes.tif

How to extend your trial license:
1. Open the "./samples/c++/Get Your Trial License Now.html" to retrieve a trial license.
2. Search for the string "reader.InitLicense" in the code and replace the old value with the new key.

If you run into any issues, please feel free to contact us at support@dynamsoft.com.

Copyright 2019 Dynamsoft Corporation. All rights reserved.
