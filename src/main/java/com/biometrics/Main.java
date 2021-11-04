package com.biometrics;

import com.biometrics.utils.OpenCVUtils;
import org.neogroup.warp.WarpApplication;

import static org.neogroup.warp.Warp.getLogger;
import static org.neogroup.warp.Warp.setProperty;

public class Main {

    public static void main(String[] args) {
        initializeOpenCV();
        initializeProperties();
        int port = 80;
        if (args.length == 1) {
            port = Integer.parseInt(args[0]);
        } else {
            String portValue = System.getenv("PORT");
            if (portValue != null) {
                port = Integer.parseInt(portValue);
            }
        }
        WarpApplication application = new WarpApplication(port);
        application.addClassPath("com.biometrics");
        application.start();
    }

    private static void initializeOpenCV() {
        getLogger().info("Initializing OpenCV library ...");
        OpenCVUtils.initializeLibrary();
    }

    private static void initializeProperties() {
        Package biometricsPackage = Main.class.getPackage();
        String appName = biometricsPackage.getImplementationTitle();
        String appVersion = biometricsPackage.getSpecificationVersion();
        if (appName != null && appVersion != null) {
            setProperty("appName", appName);
            setProperty("appVersion", appVersion);
        }
    }
}
