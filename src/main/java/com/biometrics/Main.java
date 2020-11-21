package com.biometrics;

import org.neogroup.warp.WarpApplication;

import java.util.Date;
import java.util.logging.ConsoleHandler;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

import static org.neogroup.warp.Warp.getLogger;

public class Main {

    public static void main(String[] args) {
        initializeLogger();
        initializeOpenCV();
        WarpApplication application = new WarpApplication(80);
        application.start();
    }

    private static void initializeLogger() {
        getLogger().setUseParentHandlers(false);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new SimpleFormatter() {
            private static final String format = "[%1$tF %1$tT] [%2$-1s] %3$s %n";

            @Override
            public synchronized String format(LogRecord lr) {
                return String.format(format, new Date(lr.getMillis()), lr.getLevel().getLocalizedName(), lr.getMessage());
            }
        });
        getLogger().addHandler(handler);
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
