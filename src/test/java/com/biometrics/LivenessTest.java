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

    @Test
    public void testLiveness4() {
        testLivenessResponse("src/test/resources/liveness/liveness4/", false);
    }

    @Test
    public void testLiveness5() {
        testLivenessResponse("src/test/resources/liveness/liveness5/", false);
    }

    @Test
    public void testLiveness6() {
        testLivenessResponse("src/test/resources/liveness/liveness6/", false);
    }

    @Test
    public void testLiveness7() {
        testLivenessResponse("src/test/resources/liveness/liveness7/", false);
    }

    @Test
    public void testLiveness8() {
        testLivenessResponse("src/test/resources/liveness/liveness8/", false);
    }

    @Test
    public void testLiveness9() {
        testLivenessResponse("src/test/resources/liveness/liveness9/", true);
    }

    @Test
    public void testLiveness10() {
        testLivenessResponse("src/test/resources/liveness/liveness10/", true);
    }

    @Test
    public void testLiveness11() {
        testLivenessResponse("src/test/resources/liveness/liveness11/", true);
    }

    @Test
    public void testLiveness12() {
        testLivenessResponse("src/test/resources/liveness/liveness12/", false);
    }

    @Test
    public void testLiveness13() {
        testLivenessResponse("src/test/resources/liveness/liveness13/", false);
    }

    @Test
    public void testLiveness14() {
        testLivenessResponse("src/test/resources/liveness/liveness14/", false);
    }

    @Test
    public void testLiveness15() {
        testLivenessResponse("src/test/resources/liveness/liveness15/", false);
    }

    @Test
    public void testLiveness16() {
        testLivenessResponse("src/test/resources/liveness/liveness16/", false);
    }

    @Test
    public void testLiveness17() {
        testLivenessResponse("src/test/resources/liveness/liveness17/", true);
    }

    @Test
    public void testLiveness18() {
        testLivenessResponse("src/test/resources/liveness/liveness18/", true);
    }

    @Test
    public void testLiveness19() {
        testLivenessResponse("src/test/resources/liveness/liveness19/", true);
    }

    @Test
    public void testLiveness20() {
        testLivenessResponse("src/test/resources/liveness/liveness20/", false);
    }

    @Test
    public void testLiveness21() {
        testLivenessResponse("src/test/resources/liveness/liveness21/", false);
    }

    @Test
    public void testLiveness22() {
        testLivenessResponse("src/test/resources/liveness/liveness22/", true);
    }

    @Test
    public void testLiveness23() {
        testLivenessResponse("src/test/resources/liveness/liveness23/", false);
    }

    @Test
    public void testLiveness24() {
        testLivenessResponse("src/test/resources/liveness/liveness24/", true);
    }

    @Test
    public void testLiveness25() {
        testLivenessResponse("src/test/resources/liveness/liveness25/", true);
    }

    @Test
    public void testLiveness26() {
        testLivenessResponse("src/test/resources/liveness/liveness26/", false);
    }

    @Test
    public void testLiveness27() {
        testLivenessResponse("src/test/resources/liveness/liveness27/", false);
    }

    @Test
    public void testLiveness28() {
        testLivenessResponse("src/test/resources/liveness/liveness28/", false);
    }

    @Test
    public void testLiveness29() {
        testLivenessResponse("src/test/resources/liveness/liveness29/", false);
    }

    @Test
    public void testLiveness30() {
        testLivenessResponse("src/test/resources/liveness/liveness30/", false);
    }

    @Test
    public void testLiveness31() {
        testLivenessResponse("src/test/resources/liveness/liveness31/", true);
    }

    private void testLivenessResponse(String livenessFolder, boolean liveness) {
        System.out.println("Liveness data \"" + livenessFolder + "\" should be " + (liveness? "REAL" : "FAKE (SPOOF)"));
        try {
            byte[] imageBytes = FileUtils.readFileToByteArray(new File(livenessFolder + "image.jpeg"));
            byte[] zoomedImageBytes = FileUtils.readFileToByteArray(new File(livenessFolder + "zoomedImage.jpeg"));
            DataObject response = api.checkLiveness3d(imageBytes, zoomedImageBytes);
            assertEquals(liveness, response.get("liveness"));
            System.out.println(response);
        } catch (Exception exception) {
            fail("Liveness test to folder \"" + livenessFolder + "\" failed !!");
        }
    }
}
