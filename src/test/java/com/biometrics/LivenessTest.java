package com.biometrics;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.neogroup.warp.data.DataObject;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class LivenessTest extends BaseTest {

    @Test
    public void testLiveness1() {
        testLivenessResponse("src/test/resources/liveness/liveness1/", true);
    }

    @Test
    public void testLiveness2() {
        testLivenessResponse("src/test/resources/liveness/liveness2/", true);
    }

    @Test
    public void testLiveness3() {
        testLivenessResponse("src/test/resources/liveness/liveness3/", false);
    }

    private void testLivenessResponse(String livenessFolder, boolean liveness) {
        try {
            byte[] imageBytes = FileUtils.readFileToByteArray(new File(livenessFolder + "image.jpeg"));
            byte[] zoomedImageBytes = FileUtils.readFileToByteArray(new File(livenessFolder + "zoomedImage.jpeg"));
            DataObject response = api.checkLiveness3d(imageBytes, zoomedImageBytes);
            assertEquals(liveness, response.get("liveness"));
        } catch (Exception exception) {
            fail("Liveness test to folder \"" + livenessFolder + "\" failed !!");
        }
    }
}
