package com.globant.biometrics.utils;

import com.dynamsoft.barcode.BarcodeReader;
import com.dynamsoft.barcode.TextResult;

public final class DynamsoftUtils {

    public static String readDocumentDataFromImageBarcode(byte[] imageBytes) throws Exception {
        String documentText = null;
        BarcodeReader dbr = new BarcodeReader();
        TextResult[] result = dbr.decodeFileInMemory(imageBytes, "");
        if (result != null && result.length > 0) {
            TextResult barcodeData = result[0];
            int dataLimitIndex = barcodeData.barcodeText.indexOf("***");
            if (dataLimitIndex > 0) {
                documentText = barcodeData.barcodeText.substring(0, dataLimitIndex);
            }
        }
        return documentText;
    }
}
