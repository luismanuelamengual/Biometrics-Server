package com.biometrics;

import com.biometrics.controllers.ApiController;
import org.junit.jupiter.api.Test;
import org.neogroup.warp.data.DataObject;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class BarcodeScanTest extends BaseTest {

    protected ApiController api = new ApiController();

    @Test
    public void testBarcode1() {
        testBarcodeResponse("documents/barcode/barcode1.jpeg");
    }

    @Test
    public void testBarcode3() {
        testBarcodeResponse("documents/barcode/barcode3.jpeg");
    }

    @Test
    public void testBarcode4() {
        testBarcodeResponse("documents/barcode/barcode4.jpeg");
    }

    @Test
    public void testBarcode5() {
        testBarcodeResponse("documents/barcode/barcode5.jpeg");
    }

    @Test
    public void testBarcode6() {
        testBarcodeResponse("documents/barcode/barcode6.jpeg");
    }

    @Test
    public void testBarcode7() {
        testBarcodeResponse("documents/barcode/barcode7.jpeg");
    }

    @Test
    public void testBarcode8() {
        testBarcodeResponse("documents/barcode/barcode8.jpeg");
    }

    @Test
    public void testBarcode9() {
        testBarcodeResponse("documents/barcode/barcode9.jpeg");
    }

    @Test
    public void testBarcode10() {
        testBarcodeResponse("documents/barcode/barcode10.jpeg");
    }

    @Test
    public void testBarcode11() {
        testBarcodeResponse("documents/barcode/barcode11.jpeg");
    }

    @Test
    public void testBarcode12() {
        testBarcodeResponse("documents/barcode/barcode12.jpeg");
    }

    @Test
    public void testBarcode13() {
        testBarcodeResponse("documents/barcode/barcode13.jpeg");
    }

    @Test
    public void testBarcode14() {
        testBarcodeResponse("documents/barcode/barcode14.jpeg");
    }

    @Test
    public void testBarcode15() {
        testBarcodeResponse("documents/barcode/barcode15.jpeg");
    }

    @Test
    public void testBarcode16() {
        testBarcodeResponse("documents/barcode/barcode16.jpeg");
    }

    @Test
    public void testBarcode17() {
        testBarcodeResponse("documents/barcode/barcode17.jpeg");
    }

    @Test
    public void testBarcode18() {
        testBarcodeResponse("documents/barcode/barcode18.jpeg");
    }

    private void testBarcodeResponse(String resourceName) {
        try {
            DataObject response = api.scanBarcode(getImageFromResource(resourceName));
            assertTrue(response.has("raw"), "Barcode \"raw\" field was not found on file \"" + resourceName + "\" !!");
            assertTrue(response.has("information"), "Barcode \"information\" field was not found on file \"" + resourceName + "\" !!");
        } catch (Exception exception) {
            fail("Barcode data was not found on \"" + resourceName + "\" !!");
        }
    }
}
