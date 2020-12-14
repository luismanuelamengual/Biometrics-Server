package com.biometrics;

import org.junit.jupiter.api.Test;
import org.neogroup.warp.data.DataObject;

import static org.junit.jupiter.api.Assertions.*;

public class MRZScanTest extends BaseTest {

    @Test
    public void testMRZ1() throws Exception {
        testMRZResponse("documents/mrz/mrz1.jpeg");
    }

    @Test
    public void testMRZ2() throws Exception {
        testMRZResponse("documents/mrz/mrz2.jpeg");
    }

    @Test
    public void testMRZ3() throws Exception {
        testMRZResponse("documents/mrz/mrz3.jpeg");
    }

    @Test
    public void testMRZ4() throws Exception {
        testMRZResponse("documents/mrz/mrz4.jpeg");
    }

    @Test
    public void testMRZ5() throws Exception {
        testMRZResponse("documents/mrz/mrz5.jpeg");
    }

    @Test
    public void testMRZ6() throws Exception {
        testMRZResponse("documents/mrz/mrz6.jpeg");
    }

    @Test
    public void testMRZ7() throws Exception {
        testMRZResponse("documents/mrz/mrz7.jpeg");
    }

    @Test
    public void testMRZ8() throws Exception {
        testMRZResponse("documents/mrz/mrz8.jpeg");
    }

    @Test
    public void testMRZ9() throws Exception {
        testMRZResponse("documents/mrz/mrz9.jpeg");
    }

    @Test
    public void testMRZ10() throws Exception {
        testMRZResponse("documents/mrz/mrz10.jpeg");
    }

    @Test
    public void testMRZ11() throws Exception {
        testMRZResponse("documents/mrz/mrz11.jpeg");
    }

    @Test
    public void testMRZ12() throws Exception {
        testMRZResponse("documents/mrz/mrz12.jpeg");
    }

    @Test
    public void testMRZ13() throws Exception {
        testMRZResponse("documents/mrz/mrz13.jpeg");
    }

    private void testMRZResponse(String resourceName) throws Exception {
        try {
            DataObject response = api.scanMRZ(getImageFromResource(resourceName));
            assertTrue(response.has("raw"), "MRZ \"raw\" field was not found on file \"" + resourceName + "\" !!");
            assertTrue(response.has("information"), "MRZ \"information\" field was not found on file \"" + resourceName + "\" !!");
        } catch (ResponseException exception) {
            fail("MRZ data was not found on \"" + resourceName + "\" !!");
        }
    }
}
