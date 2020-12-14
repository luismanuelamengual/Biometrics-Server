package com.biometrics;

import org.junit.jupiter.api.Test;
import org.neogroup.warp.data.DataObject;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class BarcodeScanTest extends BaseTest {

    @Test
    public void testBarcode1() throws Exception {
        testBarcodeResponse("documents/barcode/barcode1.jpeg");
    }

    @Test
    public void testBarcode2() throws Exception {
        testBarcodeResponse("documents/barcode/barcode2.jpeg");
    }

    @Test
    public void testBarcode3() throws Exception {
        testBarcodeResponse("documents/barcode/barcode3.jpeg");
    }

    @Test
    public void testBarcode4() throws Exception {
        testBarcodeResponse("documents/barcode/barcode4.jpeg");
    }

    @Test
    public void testBarcode5() throws Exception {
        testBarcodeResponse("documents/barcode/barcode5.jpeg");
    }

    @Test
    public void testBarcode6() throws Exception {
        testBarcodeResponse("documents/barcode/barcode6.jpeg");
    }

    @Test
    public void testBarcode7() throws Exception {
        testBarcodeResponse("documents/barcode/barcode7.jpeg");
    }

    @Test
    public void testBarcode8() throws Exception {
        testBarcodeResponse("documents/barcode/barcode8.jpeg");
    }

    @Test
    public void testBarcode9() throws Exception {
        testBarcodeResponse("documents/barcode/barcode9.jpeg");
    }

    @Test
    public void testBarcode10() throws Exception {
        testBarcodeResponse("documents/barcode/barcode10.jpeg");
    }

    @Test
    public void testBarcode11() throws Exception {
        testBarcodeResponse("documents/barcode/barcode11.jpeg");
    }

    @Test
    public void testBarcode12() throws Exception {
        testBarcodeResponse("documents/barcode/barcode12.jpeg");
    }

    private void testBarcodeResponse(String resourceName) throws Exception {
        try {
            DataObject response = api.scanBarcode(getImageFromResource(resourceName));
            assertTrue(response.has("raw"), "Barcode \"raw\" field was not found on file \"" + resourceName + "\" !!");
            assertTrue(response.has("information"), "Barcode \"information\" field was not found on file \"" + resourceName + "\" !!");
        } catch (ResponseException exception) {
            fail("Barcode data was not found on \"" + resourceName + "\" !!");
        }
    }
}
