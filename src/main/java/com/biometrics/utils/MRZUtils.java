package com.biometrics.utils;

import com.biometrics.ResponseException;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.util.LoadLibs;

import java.awt.image.BufferedImage;
import java.util.*;

public class MRZUtils {

    private static final Tesseract tesseract;
    private static final int[] MRZ_WEIGHTS = {7, 3, 1};
    private static final int CURRENT_YEAR_VALUE;
    private static final int CURRENT_YEAR_CENTURY;
    private static final TimeZone GMT_TIME_ZONE = TimeZone.getTimeZone("GMT");
    private static final String ID_ARG_PREFIX = "IDARG";

    static {
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        CURRENT_YEAR_VALUE = currentYear % 100;
        CURRENT_YEAR_CENTURY = currentYear - CURRENT_YEAR_VALUE;
        tesseract = new Tesseract();
        tesseract.setDatapath(LoadLibs.extractTessResources("tessdata").getAbsolutePath());
        tesseract.setLanguage("spa");
        tesseract.setTessVariable("debug_file", "/dev/null");
        tesseract.setTessVariable("tessedit_char_whitelist", "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<");
    }

    public static String readCode(BufferedImage image) {
        String mrzCode = null;
        try {
            String mrzCodeText = tesseract.doOCR(image);
            if (mrzCodeText != null && !mrzCodeText.isEmpty() && mrzCodeText.length() > 40 && mrzCodeText.indexOf("<<") > 0) {
                mrzCodeText = mrzCodeText.replace(" ", "");
                if (mrzCodeText.startsWith("1ID")) {
                    mrzCodeText = mrzCodeText.replaceFirst("1ID", "ID");
                }
                if (mrzCodeText.startsWith("1D")) {
                    mrzCodeText = mrzCodeText.replaceFirst("1D", "ID");
                }
                if (mrzCodeText.startsWith(ID_ARG_PREFIX)) {

                    StringBuilder mrzCodeBuilder = new StringBuilder();
                    mrzCodeBuilder.append(ID_ARG_PREFIX);
                    String[] sections = mrzCodeText.split("\n");

                    //Linea 1
                    String currentLine = sections[0];
                    int currentIndex = currentLine.indexOf('<');
                    for (int i = 5; i < currentIndex; i++) {
                        mrzCodeBuilder.append(readDigit(currentLine.charAt(i)));
                    }
                    mrzCodeBuilder.append('<');
                    mrzCodeBuilder.append(readDigit(currentLine.charAt(currentIndex + 1)));
                    for (int i = mrzCodeBuilder.length(); i < 30; i++) {
                        mrzCodeBuilder.append('<');
                    }

                    //Linea 2
                    currentLine = sections[1];
                    for (int i = 0; i <= 6; i++) {
                        mrzCodeBuilder.append(readDigit(currentLine.charAt(i)));
                    }
                    mrzCodeBuilder.append(currentLine.charAt(7));
                    for (int i = 8; i <= 14; i++) {
                        mrzCodeBuilder.append(readDigit(currentLine.charAt(i)));
                    }
                    currentIndex = currentLine.indexOf('<', 15);
                    for (int i = 15; i < currentIndex; i++) {
                        mrzCodeBuilder.append(readLetter(currentLine.charAt(i)));
                    }
                    for (int i = mrzCodeBuilder.length(); i < 59; i++) {
                        mrzCodeBuilder.append('<');
                    }
                    mrzCodeBuilder.append(readDigit(currentLine.charAt(currentLine.length() - 1)));

                    //Linea 3
                    currentLine = sections[2];
                    for (int i = 0; i < currentLine.length(); i++) {
                        char character = currentLine.charAt(i);
                        if (character != '<') {
                            mrzCodeBuilder.append(readLetter(character));
                        } else {
                            mrzCodeBuilder.append(character);
                        }
                    }
                    int currentLength = mrzCodeBuilder.length();
                    if (currentLength < 90) {
                        for (int i = 0; i < (90 - currentLength); i++) {
                            mrzCodeBuilder.append('<');
                        }
                    } else if (currentLength > 90) {
                        mrzCodeBuilder.delete(90, currentLength);
                    }

                    mrzCode = mrzCodeBuilder.toString();
                }
            }
        } catch (Exception ex) {}
        return mrzCode;
    }

    private static char readLetter(char character) {
        if (!Character.isLetter(character)) {
            switch (character) {
                case '0': character = 'O'; break;
                case '1': character = 'I'; break;
                case '4': character = 'A'; break;
                case '6': character = 'G'; break;
                case '7': character = 'T'; break;
                case '8': character = 'B'; break;
                case '<': character = 'C'; break;
                default: throw new RuntimeException("Invalid letter character \"" + character + "\"");
            }
        }
        return character;
    }

    private static char readDigit(char character) {
        if (!Character.isDigit(character)) {
            switch (character) {
                case 'D': character = '0'; break;
                case 'O': character = '0'; break;
                case 'A': character = '4'; break;
                case 'B': character = '8'; break;
                case 'I': character = '1'; break;
                case 'G': character = '6'; break;
                case 'T': character = '7'; break;
                case '<': character = '6'; break;
                default: throw new RuntimeException("Invalid digit character \"" + character + "\"");
            }
        }
        return character;
    }

    private static char readSeparator(char character) {
        if (character != '<') {
            switch (character) {
                case 'C': character = '<'; break;
                default: throw new RuntimeException("Invalid separator character \"" + character + "\"");
            }
        }
        return character;
    }

    public static Map<String, Object> parseCode(String mrzCode) {
        Map<String, Object> documentData = null;
        if (mrzCode.startsWith(ID_ARG_PREFIX)) {
            if (mrzCode.length() != 90) {
                throw new ResponseException("Invalid MRZ code !!");
            }

            String section1 = mrzCode.substring(0, 30);
            String section2 = mrzCode.substring(30, 60);
            String section3 = mrzCode.substring(60);
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
            char mrzChecksum = section2.charAt(section2.length()-1);
            if (Character.isDigit(mrzChecksum)) {
                String mrzField = documentField + '<' + documentCheckSum + birthDateField + birthDateCheckSum + expirationDateField + expirationDateCheckSum;
                char mrzCalculatedChecksum = calculateMRZChecksumDigitChar(mrzField);
                if (mrzChecksum != mrzCalculatedChecksum) {
                    throw new RuntimeException("Failed mrz checksum");
                }
            }
            String[] name = section3.split("<<");
            String lastNameField = name[0].replace("<", " ");
            String firstNameField = name[1].replace("<", " ");

            documentData = new HashMap<>();
            documentData.put(Document.DOCUMENT_NUMBER_FIELD, formatDocumentNumber(documentField));
            documentData.put(Document.BIRTH_DATE_FIELD, formatDate(birthDateField));
            documentData.put(Document.EXPIRATION_DATE_FIELD, formatDate(expirationDateField, true));
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

    private static long formatDate(final String text) {
        return formatDate(text, false);
    }

    private static long formatDate(final String text, final boolean acceptsFutureDates) {
        int yearValue = Integer.parseInt(text.substring(0,2));
        int year = (!acceptsFutureDates && yearValue > MRZUtils.CURRENT_YEAR_VALUE ? (MRZUtils.CURRENT_YEAR_CENTURY - 100) : MRZUtils.CURRENT_YEAR_CENTURY) + yearValue;
        int month = Integer.parseInt(text.substring(2,4)) - 1;
        int dayOfMonth = Integer.parseInt(text.substring(4,6));
        Calendar calendar = new GregorianCalendar(year, month, dayOfMonth);
        calendar.setTimeZone(MRZUtils.GMT_TIME_ZONE);
        return calendar.getTimeInMillis();
    }
}
