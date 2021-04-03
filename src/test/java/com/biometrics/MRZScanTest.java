package com.biometrics;

import org.junit.jupiter.api.Test;
import org.neogroup.warp.data.DataObject;

import static org.junit.jupiter.api.Assertions.*;

public class MRZScanTest extends BaseTest {

    @Test
    public void testMRZ1() {
        testMRZResponse("documents/mrz/mrz1.jpeg");
    }

    @Test
    public void testMRZ2() {
        testMRZResponse("documents/mrz/mrz2.jpeg");
    }

    @Test
    public void testMRZ3() {
        testMRZResponse("documents/mrz/mrz3.jpeg");
    }

    @Test
    public void testMRZ4() {
        testMRZResponse("documents/mrz/mrz4.jpeg");
    }

    @Test
    public void testMRZ5() {
        testMRZResponse("documents/mrz/mrz5.jpeg");
    }

    @Test
    public void testMRZ6() {
        testMRZResponse("documents/mrz/mrz6.jpeg");
    }

    @Test
    public void testMRZ7() {
        testMRZResponse("documents/mrz/mrz7.jpeg");
    }

    @Test
    public void testMRZ8() {
        testMRZResponse("documents/mrz/mrz8.jpeg");
    }

    @Test
    public void testMRZ9() {
        testMRZResponse("documents/mrz/mrz9.jpeg");
    }

    @Test
    public void testMRZ10() {
        testMRZResponse("documents/mrz/mrz10.jpeg");
    }

    @Test
    public void testMRZ11() {
        testMRZResponse("documents/mrz/mrz11.jpeg");
    }

    @Test
    public void testMRZ12() {
        testMRZResponse("documents/mrz/mrz12.jpeg");
    }

    @Test
    public void testMRZ13() {
        testMRZResponse("documents/mrz/mrz13.jpeg");
    }

    @Test
    public void testMRZ15() {
        testMRZResponse("documents/mrz/mrz15.jpeg");
    }

    @Test
    public void testMRZ16() {
        testMRZResponse("documents/mrz/mrz16.jpeg");
    }

    @Test
    public void testMRZ17() {
        testMRZResponse("documents/mrz/mrz17.jpeg");
    }

    @Test
    public void testMRZ18() {
        testMRZResponse("documents/mrz/mrz18.jpeg");
    }

    @Test
    public void testMRZ19() {
        testMRZResponse("documents/mrz/mrz19.jpeg");
    }

    @Test
    public void testMRZ21() {
        testMRZResponse("documents/mrz/mrz21.jpeg");
    }

    @Test
    public void testMRZ22() {
        testMRZResponse("documents/mrz/mrz22.jpeg");
    }

    private void testMRZResponse(String resourceName) {
        try {
            DataObject response = api.scanMRZ(getImageFromResource(resourceName));
            assertTrue(response.has("raw"), "MRZ \"raw\" field was not found on file \"" + resourceName + "\" !!");
            assertTrue(response.has("information"), "MRZ \"information\" field was not found on file \"" + resourceName + "\" !!");
        } catch (Exception exception) {
            fail("MRZ data was not found on \"" + resourceName + "\" !!");
        }
    }
}
