package com.globant.biometrics.utils;

import com.dynamsoft.barcode.BarcodeReader;
import com.dynamsoft.barcode.TextResult;
import com.globant.biometrics.models.DocumentData;

public final class DynamsoftUtils {

    public static DocumentData readDocumentDataFromImageBarcode(byte[] imageBytes) throws Exception {
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
        DocumentData documentData = null;
        if (documentText != null && !documentText.isEmpty()) {
            String[] dataTokens = documentText.split("@");
            documentData = new DocumentData();
            documentData.setNationalIdentificationNumber(dataTokens[0]);
            documentData.setLastName(dataTokens[1]);
            documentData.setFirstName(dataTokens[2]);
            documentData.setGender(dataTokens[3]);
            documentData.setDocumentNumber(dataTokens[4]);
            documentData.setBirthDate(dataTokens[6]);
        }
        return  documentData;
    }
}
