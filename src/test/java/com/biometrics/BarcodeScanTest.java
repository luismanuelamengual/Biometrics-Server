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
