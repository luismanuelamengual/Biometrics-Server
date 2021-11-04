package com.biometrics;

import com.biometrics.controllers.LivenessController;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.neogroup.warp.data.DataObject;

import java.io.File;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class LivenessTest extends BaseTest {

    protected LivenessController liveness = new LivenessController();

    @TestFactory
    @DisplayName("Real Tests")
    Stream<DynamicTest> testRealLivenessSessions() {
        File dir = new File("src/test/resources/liveness/real/");
        return Arrays.stream(dir.listFiles()).map(file -> DynamicTest.dynamicTest(file.getName(), () -> {
            testLivenessResponse(file, true);
        }));
    }

    @TestFactory
    @DisplayName("Fake Tests")
    Stream<DynamicTest> testFakeLivenessSessions() {
        File dir = new File("src/test/resources/liveness/fake/");
        return Arrays.stream(dir.listFiles()).map(file -> DynamicTest.dynamicTest(file.getName(), () -> {
            testLivenessResponse(file, false);
        }));
    }

    private void testLivenessResponse(File livenessFolder, boolean liveness) {
        System.out.println("Liveness data \"" + livenessFolder + "\" should be " + (liveness? "REAL" : "FAKE (SPOOF)"));
        try {
            byte[] imageBytes = FileUtils.readFileToByteArray(new File(livenessFolder + "/image.jpeg"));
            byte[] zoomedImageBytes = FileUtils.readFileToByteArray(new File(livenessFolder + "/zoomedImage.jpeg"));
            DataObject response = this.liveness.verifyLiveness(imageBytes, zoomedImageBytes);
            System.out.println(response);
            assertEquals(liveness, response.get("liveness"));
        } catch (Exception exception) {
            fail("Liveness test to folder \"" + livenessFolder + "\" failed !!");
        }
    }
}
