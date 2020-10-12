package com.biometrics;

import org.neogroup.warp.WarpApplication;

import static org.neogroup.warp.Warp.getLogger;

public class Main {

    public static void main(String[] args) {
        initializeOpenCV();
        WarpApplication application = new WarpApplication(8080, true);
        application.start();
    }

    private static void initializeOpenCV() {
        getLogger().info("Initializing OpenCV library ...");
        try {
            nu.pattern.OpenCV.loadShared();
        } catch (Throwable ex) {
            nu.pattern.OpenCV.loadLocally();
        }
    }
}
