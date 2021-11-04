package com.biometrics;

import com.biometrics.utils.OpenCVUtils;

import java.io.IOException;
import java.util.Objects;

public abstract class BaseTest {

    public BaseTest() {
        OpenCVUtils.initializeLibrary();
    }

    protected byte[] getImageFromResource(String fileName) throws IOException {
        return Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(fileName)).readAllBytes();
    }
}
