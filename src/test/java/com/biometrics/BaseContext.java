package com.biometrics;

import com.biometrics.api.v1.ApiController;
import com.biometrics.utils.OpenCVUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.varia.NullAppender;

import java.io.IOException;
import java.util.Objects;

public abstract class BaseContext {

    protected ApiController api;

    public BaseContext() {
        OpenCVUtils.initializeLibrary();
        Logger.getRootLogger().setLevel(Level.OFF);
        Logger.getRootLogger().removeAllAppenders();
        Logger.getRootLogger().addAppender(new NullAppender());
        api = new ApiController();
    }

    protected byte[] getImageFromResource(String fileName) throws IOException {
        return Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(fileName)).readAllBytes();
    }
}
