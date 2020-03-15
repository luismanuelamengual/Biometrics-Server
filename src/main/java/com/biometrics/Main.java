package com.biometrics;

import org.neogroup.warp.WarpApplication;
import org.opencv.core.Core;

import static org.neogroup.warp.Warp.getLogger;

public class Main {

    public static void main(String[] args) {
        initializeOpenCV();
        WarpApplication application = new WarpApplication(8080);
        application.start();
    }

    private static void initializeOpenCV() {
        getLogger().info("Initializing OpenCV library ...");
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }
}
