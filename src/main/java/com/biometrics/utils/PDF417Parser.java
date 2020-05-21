package com.biometrics.utils;

import java.util.*;

public class PDF417Parser {

    private static TimeZone GMT_TIME_ZONE = TimeZone.getTimeZone("GMT");

    public static Map<String, Object> parseCode(String pdf417code) {
        String[] dataTokens = pdf417code.split("@");
        Map<String, Object> documentData = new HashMap<>();
        documentData.put(Document.FIRST_NAME_FIELD, formatName(dataTokens[2]));
        documentData.put(Document.LAST_NAME_FIELD, formatName(dataTokens[1]));
        documentData.put(Document.DOCUMENT_NUMBER_FIELD, formatDocumentNumber(dataTokens[4]));
        documentData.put(Document.GENDER_PROPERTY_FIELD, dataTokens[3]);
        documentData.put(Document.BIRTH_DATE_FIELD, formatDate(dataTokens[6]));
        documentData.put(Document.NATIONAL_IDENTFICATION_NUMBER_FIELD, formatNationalIdentificationNumber(dataTokens[0]));
        return documentData;
    }

    private static int formatDocumentNumber(final String text) {
        return Integer.parseInt(text.replaceAll("[^\\d.]", ""));
    }

    private static int formatNationalIdentificationNumber(final String text) {
        return Integer.parseInt(text.replaceAll("[^\\d.]", ""));
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

    private static long formatDate(final String text) {
        int dayOfMonth = Integer.parseInt(text.substring(0,2));
        int month = Integer.parseInt(text.substring(3,5)) - 1;
        int year = Integer.parseInt(text.substring(6));
        Calendar calendar = new GregorianCalendar(year, month, dayOfMonth);
        calendar.setTimeZone(PDF417Parser.GMT_TIME_ZONE);
        return calendar.getTimeInMillis();
    }
}
