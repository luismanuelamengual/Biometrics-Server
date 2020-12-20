package com.biometrics.utils;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.util.LoadLibs;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.awt.image.BufferedImage;
import java.util.*;

import static org.neogroup.warp.Warp.getLogger;
import static org.opencv.core.Core.ROTATE_180;
import static org.opencv.core.CvType.CV_32F;

public class MRZUtils {

    private static final String EMPTY = "";
    private static final String EMPTY_SPACE = " ";
    private static final String RETURN = "\n";
    private static final String ID_PREFIX = "ID";
    private static final String ID_ARG_PREFIX = "IDARG";
    private static final String[] ID_FAIL_PREFIXES = {"1ID", "1D", "IO"};
    private static final String FILLER_SEPARATOR = "<<";
    private static final char FILLER = '<';
    private static final char SPACE = ' ';
    private static final char ZERO = '0';
    private static final char ONE = '1';
    private static final char TWO = '2';
    private static final char FOUR = '4';
    private static final char FIVE = '5';
    private static final char SIX = '6';
    private static final char SEVEN = '7';
    private static final char EIGHT = '8';
    private static final char NINE = '9';
    private static final char D = 'D';
    private static final char O = 'O';
    private static final char A = 'A';
    private static final char B = 'B';
    private static final char C = 'C';
    private static final char I = 'I';
    private static final char G = 'G';
    private static final char T = 'T';
    private static final char S = 'S';
    private static final char Z = 'Z';

    private static final Tesseract tesseract;
    private static final int[] MRZ_WEIGHTS = {7, 3, 1};
    private static final TimeZone GMT_TIME_ZONE = TimeZone.getTimeZone("GMT");

    static {
        tesseract = new Tesseract();
        tesseract.setDatapath(LoadLibs.extractTessResources("tessdata").getAbsolutePath());
        tesseract.setLanguage("spa");
        tesseract.setTessVariable("debug_file", "/dev/null");
        tesseract.setTessVariable("tessedit_char_whitelist", "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<");
    }

    public static String readCode (byte[] imageBytes) {
        String mrzCode = null;
        if (imageBytes.length > 0) {
            Mat mrzMat = detectCode(OpenCVUtils.getImage(imageBytes));
            if (mrzMat != null) {
                mrzCode = readCode(OpenCVUtils.getBufferedImage(mrzMat));
                if (mrzCode == null) {
                    Core.rotate(mrzMat, mrzMat, ROTATE_180);
                    mrzCode = readCode(OpenCVUtils.getBufferedImage(mrzMat));
                }
            }
        }
        return mrzCode;
    }

    public static Map<String, Object> parseCode(String mrzCode) {
        Map<String, Object> documentData = null;
        try {
            if (mrzCode.startsWith(ID_ARG_PREFIX)) {
                String section1 = mrzCode.substring(0, 30);
                String section2 = mrzCode.substring(30, 60);
                String section3 = mrzCode.substring(60);
                String documentField = section1.substring(5, section1.indexOf("<"));
                String birthDateField = section2.substring(0, 6);
                String genderField = section2.substring(7, 8);
                String expirationDateField = section2.substring(8, 14);
                String[] name = section3.split(FILLER_SEPARATOR);
                String lastNameField = name[0].replace(FILLER, SPACE);
                String firstNameField = name[1].replace(FILLER, SPACE);
                documentData = new HashMap<>();
                documentData.put(Document.DOCUMENT_NUMBER_FIELD, formatDocumentNumber(documentField));
                documentData.put(Document.BIRTH_DATE_FIELD, formatDate(birthDateField));
                documentData.put(Document.EXPIRATION_DATE_FIELD, formatDate(expirationDateField, true));
                documentData.put(Document.GENDER_PROPERTY_FIELD, genderField);
                documentData.put(Document.FIRST_NAME_FIELD, formatName(firstNameField));
                documentData.put(Document.LAST_NAME_FIELD, formatName(lastNameField));
            } else {
                throw new RuntimeException("Unrecognized mrz code type");
            }
        } catch (Exception ex) {
            getLogger().warning("MRZ code \"" + mrzCode + "\" could not be parsed: " + ex.getMessage());
        }
        return documentData;
    }

    private static String readCode(BufferedImage image) {
        String mrzCode = null;
        String mrzCodeText = null;
        try {
            mrzCodeText = tesseract.doOCR(image);
            if (mrzCodeText != null && !mrzCodeText.isEmpty() && mrzCodeText.length() > 40 && mrzCodeText.indexOf("<<") > 0) {
                mrzCodeText = mrzCodeText.replace(EMPTY_SPACE, EMPTY);
                for (String idFailPrefix : ID_FAIL_PREFIXES) {
                    if (mrzCodeText.startsWith(idFailPrefix)) {
                        mrzCodeText = mrzCodeText.replaceFirst(idFailPrefix, ID_PREFIX);
                    }
                }
                if (mrzCodeText.startsWith(ID_ARG_PREFIX)) {

                    StringBuilder mrzCodeBuilder = new StringBuilder();
                    mrzCodeBuilder.append(ID_ARG_PREFIX);
                    String[] sections = mrzCodeText.split(RETURN);

                    //Linea 1
                    String currentLine = sections[0];
                    int currentIndex = currentLine.indexOf(FILLER);
                    for (int i = 5; i < currentIndex; i++) {
                        mrzCodeBuilder.append(readDigit(currentLine.charAt(i)));
                    }
                    String documentField = mrzCodeBuilder.substring(5, currentIndex);
                    char documentCalculatedCheckSum = calculateMRZChecksumDigitChar(documentField);
                    mrzCodeBuilder.append(FILLER);
                    char documentCheckSum = readDigit(currentLine.charAt(currentIndex + 1));
                    mrzCodeBuilder.append(documentCheckSum);
                    for (int i = mrzCodeBuilder.length(); i < 30; i++) {
                        mrzCodeBuilder.append(FILLER);
                    }

                    //Linea 2
                    currentLine = sections[1];
                    for (int i = 0; i <= 5; i++) {
                        mrzCodeBuilder.append(readDigit(currentLine.charAt(i)));
                    }
                    String birthDateField = mrzCodeBuilder.substring(mrzCodeBuilder.length() - 6);
                    char birthDateCalculatedCheckSum = calculateMRZChecksumDigitChar(birthDateField);
                    char birthDateCheckSum = readDigit(currentLine.charAt(6));
                    mrzCodeBuilder.append(birthDateCheckSum);
                    mrzCodeBuilder.append(currentLine.charAt(7));
                    for (int i = 8; i <= 13; i++) {
                        mrzCodeBuilder.append(readDigit(currentLine.charAt(i)));
                    }
                    String expirationDateField = mrzCodeBuilder.substring(mrzCodeBuilder.length() - 6);
                    char expirationDateCalculatedCheckSum = calculateMRZChecksumDigitChar(expirationDateField);
                    char expirationDateCheckSum = readDigit(currentLine.charAt(14));
                    mrzCodeBuilder.append(expirationDateCheckSum);
                    currentIndex = currentLine.indexOf(FILLER, 15);
                    for (int i = 15; i < currentIndex; i++) {
                        mrzCodeBuilder.append(readLetter(currentLine.charAt(i)));
                    }
                    for (int i = mrzCodeBuilder.length(); i < 59; i++) {
                        mrzCodeBuilder.append('<');
                    }
                    char mrzChecksum = currentLine.charAt(currentLine.length() - 1);
                    mrzCodeBuilder.append(mrzChecksum);

                    //Linea 3
                    currentLine = sections[2];
                    for (int i = 0; i < currentLine.length(); i++) {
                        char character = currentLine.charAt(i);
                        if (character != FILLER) {
                            mrzCodeBuilder.append(readLetter(character));
                        } else {
                            mrzCodeBuilder.append(character);
                        }
                    }
                    int currentLength = mrzCodeBuilder.length();
                    if (currentLength < 90) {
                        for (int i = 0; i < (90 - currentLength); i++) {
                            mrzCodeBuilder.append(FILLER);
                        }
                    } else if (currentLength > 90) {
                        mrzCodeBuilder.delete(90, currentLength);
                    }

                    //Chequeos de checksum
                    if (documentCheckSum != documentCalculatedCheckSum) {
                        throw new RuntimeException("Failed document checksum");
                    }
                    if (birthDateCheckSum != birthDateCalculatedCheckSum) {
                        throw new RuntimeException("Failed birth date checksum");
                    }
                    if (expirationDateCheckSum != expirationDateCalculatedCheckSum) {
                        throw new RuntimeException("Failed expiration date checksum");
                    }

                    String mrzField = documentField + '<' + documentCheckSum + birthDateField + birthDateCheckSum + expirationDateField + expirationDateCheckSum;
                    char mrzCalculatedChecksum = calculateMRZChecksumDigitChar(mrzField);
                    if (Character.isDigit(mrzChecksum)) {
                        if (mrzChecksum != mrzCalculatedChecksum) {
                            throw new RuntimeException("Failed mrz checksum");
                        }
                    } else {
                        mrzCodeBuilder.setCharAt(59, mrzCalculatedChecksum);
                    }
                    mrzCode = mrzCodeBuilder.toString();
                } else {
                    throw new RuntimeException("Unrecognized mrz code type");
                }
            }
        } catch (Exception ex) {
            if (mrzCodeText != null) {
                getLogger().warning("MRZ code \"" + mrzCodeText.replaceAll("\n","" ) + "\" could not be processed: " + ex.getMessage());
            }
        }
        return mrzCode;
    }

    private static char readLetter(char character) {
        if (!Character.isLetter(character)) {
            switch (character) {
                case ZERO: character = O; break;
                case ONE: character = I; break;
                case TWO: character = Z; break;
                case FOUR: character = A; break;
                case FIVE: character = S; break;
                case SIX: character = G; break;
                case SEVEN: character = T; break;
                case EIGHT: character = B; break;
                case FILLER: character = C; break;
                default: throw new RuntimeException("Unexpected character \"" + character + "\"");
            }
        }
        return character;
    }

    private static char readDigit(char character) {
        if (!Character.isDigit(character)) {
            switch (character) {
                case D: character = ZERO; break;
                case O: character = ZERO; break;
                case A: character = FOUR; break;
                case S: character = FIVE; break;
                case B: character = EIGHT; break;
                case I: character = ONE; break;
                case Z: character = TWO; break;
                case G: character = SIX; break;
                case T: character = SEVEN; break;
                default: throw new RuntimeException("Unexpected character \"" + character + "\"");
            }
        }
        return character;
    }

    private static char readSeparator(char character) {
        if (character != FILLER) {
            switch (character) {
                case C: character = FILLER; break;
                default: throw new RuntimeException("Unexpected character \"" + character + "\"");
            }
        }
        return character;
    }

    private static int calculateMRZChecksumDigit(String text) {
        int result = 0;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            int characterValue;
            if (character == FILLER) {
                characterValue = 0;
            } else if (character >= ZERO && character <= NINE) {
                characterValue = character - ZERO;
            } else if (character >= A && character <= Z) {
                characterValue = character - A + 10;
            } else {
                throw new RuntimeException("Unrecognized character \"" + character + "\" in MRZ ");
            }
            result += characterValue * MRZ_WEIGHTS[i % MRZ_WEIGHTS.length];
        }
        return result % 10;
    }

    private static char calculateMRZChecksumDigitChar(String text) {
        return (char) (ZERO + calculateMRZChecksumDigit(text));
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
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        int currentYearValue = currentYear % 100;
        int currentYearCentury = currentYear - currentYearValue;
        int yearValue = Integer.parseInt(text.substring(0,2));
        int year = (!acceptsFutureDates && yearValue > currentYearValue ? (currentYearCentury - 100) : currentYearCentury) + yearValue;
        int month = Integer.parseInt(text.substring(2,4)) - 1;
        int dayOfMonth = Integer.parseInt(text.substring(4,6));
        Calendar calendar = new GregorianCalendar(year, month, dayOfMonth);
        calendar.setTimeZone(GMT_TIME_ZONE);
        return calendar.getTimeInMillis();
    }

    private static Mat detectCode(Mat src){
        Mat img = new Mat();
        OpenCVUtils.grayScale(src, img);
        double ratio = img.height() / 800.0;
        int width = (int) (img.size().width / ratio);
        int height = (int) (img.size().height / ratio);
        Size newSize = new Size(width, height);
        Mat resizedImg = new Mat(newSize, CvType.CV_8UC4);
        Imgproc.resize(img, resizedImg, newSize);
        Mat blur = new Mat();
        Imgproc.medianBlur(resizedImg, blur, 3);
        Mat morph = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(13, 5));
        Mat dilatedImg = new Mat();
        Imgproc.morphologyEx(blur, dilatedImg, Imgproc.MORPH_BLACKHAT, morph);
        blur.release();
        Mat gradX = new Mat();
        Imgproc.Sobel(dilatedImg, gradX, CV_32F, 1, 0);
        dilatedImg.release();
        Core.convertScaleAbs(gradX, gradX, 1, 0);
        Core.MinMaxLocResult minMax = Core.minMaxLoc(gradX);
        Core.convertScaleAbs(gradX, gradX, (255/(minMax.maxVal - minMax.minVal)), - ((minMax.minVal * 255) / (minMax.maxVal - minMax.minVal)));
        Imgproc.morphologyEx(gradX, gradX, Imgproc.MORPH_CLOSE, morph);
        Mat thresh = new Mat();
        Imgproc.threshold(gradX, thresh, 0, 255, Imgproc.THRESH_OTSU);
        gradX.release();
        morph.release();
        morph = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(21, 21));
        Imgproc.morphologyEx(thresh, thresh, Imgproc.MORPH_CLOSE, morph);
        Imgproc.erode(thresh, thresh, new Mat(), new Point(-1, -1), 4);
        morph.release();
        int col = (int) resizedImg.size().width;
        int p = (int) (resizedImg.size().width * 0.05);
        int row = (int) resizedImg.size().height;
        for(int i = 0; i < row; i++) {
            for(int j = 0; j < p; j++) {
                thresh.put(i, j, 0);
                thresh.put(i, col-j, 0);
            }
        }
        Mat dilated_edges = new Mat();
        Imgproc.dilate(thresh, dilated_edges, new Mat(), new Point(-1, -1), 16, 1, new Scalar(0,255,0));
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(dilated_edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        hierarchy.release();
        MatOfPoint contour = OpenCVUtils.getLargestContour(contours);

        MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
        RotatedRect rotatedRect = Imgproc.minAreaRect(contour2f);
        Mat mrzMat = null;

        double rectAspectRatioWidth = rotatedRect.size.width / rotatedRect.size.height;
        double rectAspectRatioHeight = rotatedRect.size.height / rotatedRect.size.width;
        double aspectRatio = Math.max(rectAspectRatioWidth, rectAspectRatioHeight);
        if (aspectRatio > 3) {
            double rectWidth = Math.max(rotatedRect.size.width, rotatedRect.size.height);
            double rectHeight = Math.min(rotatedRect.size.width, rotatedRect.size.height);
            Size holderSize = new Size(rectWidth, rectWidth);
            Mat transformedImg = new Mat();
            OpenCVUtils.translate(resizedImg, transformedImg, (holderSize.width / 2) - rotatedRect.center.x, (holderSize.height / 2) - rotatedRect.center.y, holderSize);
            OpenCVUtils.rotate(transformedImg, transformedImg, new Point(holderSize.width/2, holderSize.height/2), rotatedRect.size.width > rotatedRect.size.height ? rotatedRect.angle : 90 + rotatedRect.angle, holderSize);
            mrzMat = transformedImg.submat(new Rect(0,(int)((holderSize.height / 2) - (rectHeight / 2)), (int)rectWidth, (int)rectHeight));
        }

        if (mrzMat != null && !mrzMat.empty()) {
            Imgproc.adaptiveThreshold(mrzMat, mrzMat, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 17, 5);
            Imgproc.erode(mrzMat, mrzMat, new Mat(), new Point(-1, -1), 1);
            Imgproc.dilate(mrzMat, mrzMat, new Mat(), new Point(-1, -1), 1);
        }
        return mrzMat;
    }
}
