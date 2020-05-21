package com.biometrics.utils;

import java.util.*;
import java.util.regex.Pattern;

public class PDF417Parser {

    private static TimeZone GMT_TIME_ZONE = TimeZone.getTimeZone("GMT");
    private static Pattern PDF417_PATTERN_TYPE_1 = Pattern.compile("^\\d+@(?:[a-zA-Z]|\\s)+@(?:[a-zA-Z]|\\s)+@(?:M|F)@\\d+@\\w@\\d\\d\\/\\d\\d\\/\\d{4}@");
    private static Pattern PDF417_PATTERN_TYPE_2 = Pattern.compile("^@\\d+\\s*@[A-Z]@\\d@(?:[a-zA-Z]|\\s)+@(?:[a-zA-Z]|\\s)+@(?:[a-zA-Z]|\\s)+@\\d\\d\\/\\d\\d\\/\\d{4}@(?:M|F)@");

    public static Map<String, Object> parseCode(String pdf417code) {
        pdf417code = pdf417code.trim();
        Map<String, Object>  documentData = null;
        if (PDF417_PATTERN_TYPE_1.matcher(pdf417code).find()){
            String[] fields = pdf417code.split("@");
            documentData = new HashMap<>();
            documentData.put(Document.FIRST_NAME_FIELD, formatName(fields[2]));
            documentData.put(Document.LAST_NAME_FIELD, formatName(fields[1]));
            documentData.put(Document.DOCUMENT_NUMBER_FIELD, formatDocumentNumber(fields[4]));
            documentData.put(Document.GENDER_PROPERTY_FIELD, fields[3]);
            documentData.put(Document.BIRTH_DATE_FIELD, formatDate(fields[6]));
            documentData.put(Document.NATIONAL_IDENTFICATION_NUMBER_FIELD, formatNationalIdentificationNumber(fields[0]));
        } else if (PDF417_PATTERN_TYPE_2.matcher(pdf417code).find()) {
            String[] fields = pdf417code.split("@");
            documentData = new HashMap<>();
            documentData.put(Document.FIRST_NAME_FIELD, formatName(fields[5]));
            documentData.put(Document.LAST_NAME_FIELD, formatName(fields[4]));
            documentData.put(Document.DOCUMENT_NUMBER_FIELD, formatDocumentNumber(fields[1]));
            documentData.put(Document.GENDER_PROPERTY_FIELD, fields[8]);
            documentData.put(Document.BIRTH_DATE_FIELD, formatDate(fields[7]));
            documentData.put(Document.NATIONAL_IDENTFICATION_NUMBER_FIELD, formatNationalIdentificationNumber(fields[10]));
        } else {
            throw new RuntimeException ("Unrecognized pdf417 code \"" + pdf417code + "\"");
        }
        return documentData;
    }

    private static int formatDocumentNumber(final String text) {
        return Integer.parseInt(text.replaceAll("[^\\d.]", ""));
    }

    private static long formatNationalIdentificationNumber(final String text) {
        return Long.parseLong(text.replaceAll("[^\\d.]", ""));
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
