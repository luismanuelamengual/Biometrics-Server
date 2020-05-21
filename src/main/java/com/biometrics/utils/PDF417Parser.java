package com.biometrics.utils;

import java.util.HashMap;
import java.util.Map;

public class PDF417Parser {

    public static Map<String, Object> parseCode(String pdf417code) {
        String[] dataTokens = pdf417code.split("@");
        Map<String, Object> documentData = new HashMap<>();
        documentData.put(Document.FIRST_NAME_FIELD, formatName(dataTokens[2]));
        documentData.put(Document.LAST_NAME_FIELD, formatName(dataTokens[1]));
        documentData.put(Document.DOCUMENT_NUMBER_FIELD, dataTokens[4]);
        documentData.put(Document.GENDER_PROPERTY_FIELD, dataTokens[3]);
        documentData.put(Document.BIRTH_DATE_FIELD, formatDate(dataTokens[6]));
        documentData.put(Document.NATIONAL_IDENTFICATION_NUMBER_FIELD, dataTokens[0]);
        return documentData;
    }

    private static String formatName(final String text) {
        char[] chars = text.toLowerCase().toCharArray();
        boolean found = false;
        for (int i = 0; i < chars.length; i++) {
            if (!found && Character.isLetter(chars[i])) {
                chars[i] = Character.toUpperCase(chars[i]);
                found = true;
            } else if (Character.isWhitespace(chars[i]) || chars[i]=='.' || chars[i]=='\'') {
                found = false;
            }
        }
        return String.valueOf(chars);
    }

    private static int formatDate(final String text) {
        return 0;
    }
}
