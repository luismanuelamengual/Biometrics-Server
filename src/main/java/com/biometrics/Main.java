package com.biometrics;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.varia.NullAppender;
import org.neogroup.warp.WarpApplication;

import java.util.Date;
import java.util.logging.ConsoleHandler;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

import static org.neogroup.warp.Warp.getLogger;

public class Main {

    public static void main(String[] args) {
        initializeLogging();
        initializeOpenCV();
        WarpApplication application = new WarpApplication(args.length == 1 ? Integer.parseInt(args[0]) : 80);
        application.addClassPath("com.biometrics");
        application.start();
    }

    private static void initializeLogging() {
        Logger.getRootLogger().setLevel(Level.OFF);
        Logger.getRootLogger().removeAllAppenders();
        Logger.getRootLogger().addAppender(new NullAppender());
        getLogger().setUseParentHandlers(false);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new SimpleFormatter() {
            private static final String format = "[%1$tF %1$tT] %2$-1s: %3$s %n";

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
