package com.biometrics.utils;

import java.util.HashMap;
import java.util.Map;

public class MRZParser {

    private static int[] MRZ_WEIGHTS = {7, 3, 1};

    public static Map<String, Object> parseCode(String mrzCode) {
        Map<String, Object> documentData = null;
        if (mrzCode.length() == 90) {
            String section1 = mrzCode.substring(0, 30);
            String section2 = mrzCode.substring(30, 60);
            String section3 = mrzCode.substring(60, 90);
            int documentSeparatorIndex = section1.indexOf("<");
            String documentField = section1.substring(5, documentSeparatorIndex);
            char documentCheckSum = section1.charAt(documentSeparatorIndex+1);
            char documentCalculatedCheckSum = calculateMRZChecksumDigitChar(documentField);
            if (documentCheckSum != documentCalculatedCheckSum) {
                throw new RuntimeException("Failed document checksum");
            }
            String birthDateField = section2.substring(0, 6);
            char birthDateCheckSum = section2.charAt(6);
            char birthDateCalculatedCheckSum = calculateMRZChecksumDigitChar(birthDateField);
            if (birthDateCheckSum != birthDateCalculatedCheckSum) {
                throw new RuntimeException("Failed birth date checksum");
            }
            String genderField = section2.substring(7,8);
            String expirationDateField = section2.substring(8, 14);
            char expirationDateCheckSum = section2.charAt(14);
            char expirationDateCalculatedCheckSum = calculateMRZChecksumDigitChar(expirationDateField);
            if (expirationDateCheckSum != expirationDateCalculatedCheckSum) {
                throw new RuntimeException("Failed expiration date checksum");
            }
            String mrzField = documentField + '<' + documentCheckSum + birthDateField + birthDateCheckSum + expirationDateField + expirationDateCheckSum;
            char mrzChecksum = section2.charAt(29);
            char mrzCalculatedChecksum = calculateMRZChecksumDigitChar(mrzField);
            if (mrzChecksum != mrzCalculatedChecksum) {
                throw new RuntimeException("Failed mrz checksum");
            }
            String[] name = section3.split("<<");
            String lastNameField = name[0].replace("<", " ");
            String firstNameField = name[1].replace("<", " ");

            documentData = new HashMap<>();
            documentData.put(Document.DOCUMENT_NUMBER_FIELD, formatDocumentNumber(documentField));
            documentData.put(Document.BIRTH_DATE_FIELD, formatDate(birthDateField));
            documentData.put(Document.EXPIRATION_DATE_FIELD, formatDate(expirationDateField));
            documentData.put(Document.GENDER_PROPERTY_FIELD, genderField);
            documentData.put(Document.FIRST_NAME_FIELD, formatName(firstNameField));
            documentData.put(Document.LAST_NAME_FIELD, formatName(lastNameField));
        } else {
            throw new RuntimeException ("Unrecognized mrz code \"" + mrzCode + "\"");
        }
        return documentData;
    }

    private static int calculateMRZChecksumDigit(String text) {
        int result = 0;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            int characterValue;
            if (character == '<') {
                characterValue = 0;
            } else if (character >= '0' && character <= '9') {
                characterValue = character - '0';
            } else if (character >= 'A' && character <= 'Z') {
                characterValue = character - 'A' + 10;
            } else {
                throw new RuntimeException("Unrecognized character \"" + character + "\" in MRZ ");
            }
            result += characterValue * MRZ_WEIGHTS[i % MRZ_WEIGHTS.length];
        }
        return result % 10;
    }

    private static char calculateMRZChecksumDigitChar(String text) {
        return (char) ('0' + calculateMRZChecksumDigit(text));
    }

    private static int formatDocumentNumber(final String text) {
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

    private static int formatDate(final String text) {
        return 0;
    }
}
