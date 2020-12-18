package com.biometrics.utils;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.pdf417.PDF417Reader;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.regex.Pattern;

import static org.neogroup.warp.Warp.getLogger;
import static org.opencv.core.CvType.CV_64F;

public class PDF417Utils {

    private static final PDF417Reader pdf417Reader;
    private static final TimeZone GMT_TIME_ZONE = TimeZone.getTimeZone("GMT");
    private static final String NAME_PATTERN = "(?:[a-zA-Z]|\\s|`)+";
    private static final String NUMBER_PATTERN = "\\d+";
    private static final String GENDER_PATTERN = "(?:M|F)";
    private static final String DATE_PATTERN = "\\d\\d\\/\\d\\d\\/\\d{4}";
    private static final String UPPER_CASE_LETTER_PATTERN = "[A-Z]";
    private static final String DIGIT_PATTERN = "\\d";
    private static final Pattern PDF417_PATTERN_TYPE_1 = Pattern.compile("^" + NUMBER_PATTERN + "@" + NAME_PATTERN + "@" + NAME_PATTERN + "@" + GENDER_PATTERN + "@" + NUMBER_PATTERN + "@" + UPPER_CASE_LETTER_PATTERN + "@" + DATE_PATTERN);
    private static final Pattern PDF417_PATTERN_TYPE_2 = Pattern.compile("^@" + NUMBER_PATTERN + "\\s*@" + UPPER_CASE_LETTER_PATTERN +"@" + DIGIT_PATTERN + "@" + NAME_PATTERN + "@" + NAME_PATTERN + "@" + NAME_PATTERN + "@" + DATE_PATTERN + "@" + GENDER_PATTERN + "@");

    static {
        pdf417Reader = new PDF417Reader();
    }

    public static String readCode(byte[] imageBytes) {
        String pdf417Code = null;
        if (imageBytes.length > 0) {
            Mat image = OpenCVUtils.getImage(imageBytes);
            List<Mat> barcodeImages = detectCode(image);
            for (Mat barcodeImage : barcodeImages) {
                pdf417Code = readCode(OpenCVUtils.getBufferedImage(barcodeImage));
                if (pdf417Code != null) {
                    break;
                }
            }
        }
        return pdf417Code;
    }

    public static Map<String, Object> parseCode(String pdf417code) {
        Map<String, Object> documentData = null;
        pdf417code = pdf417code.trim();
        try {
            if (PDF417_PATTERN_TYPE_1.matcher(pdf417code).find()) {
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
                throw new RuntimeException("Unrecognized pdf417 type");
            }
        } catch (Exception ex) {
            getLogger().warning("PDF417 code \"" + pdf417code + "\" could not be parsed: " + ex.getMessage());
        }
        return documentData;
    }

    private static String readCode(BufferedImage image) {
        String pdf417Code = null;
        try {
            LuminanceSource source = new BufferedImageLuminanceSource(image);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            Result result = pdf417Reader.decode(bitmap, new EnumMap<>(DecodeHintType.class));
            if (result != null) {
                String resultText = result.getText();
                if (resultText != null && !resultText.isEmpty()) {
                    pdf417Code = resultText;
                }
            }
        } catch (Exception ex) {}
        return pdf417Code;
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
        calendar.setTimeZone(PDF417Utils.GMT_TIME_ZONE);
        return calendar.getTimeInMillis();
    }

    private static List<Mat> detectCode(Mat src){
        List<Mat> barcodeImageCandidates = new ArrayList<>();
        Mat image = new Mat();
        OpenCVUtils.grayScale(src, image);
        OpenCVUtils.resize(image, image,800, 800, 0, 0);
        Imgproc.GaussianBlur(image, image, new Size(13, 13), 0);
        Imgproc.threshold(image, image, 90, 255, Imgproc.THRESH_BINARY_INV);
        Imgproc.dilate(image, image, new Mat(), new Point(-1, -1), 14);
        Imgproc.erode(image, image, new Mat(), new Point(-1, -1), 9);
        List<MatOfPoint> contours = new ArrayList<>();
        List<RotatedRect> rotatedRects = new ArrayList<>();
        Imgproc.findContours(image, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        for (MatOfPoint contour : contours) {
            MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
            RotatedRect rect = Imgproc.minAreaRect(contour2f);
            double rectAspectRatioWidth = rect.size.width / rect.size.height;
            double rectAspectRatioHeight = rect.size.height / rect.size.width;
            double aspectRatio = Math.max(rectAspectRatioWidth, rectAspectRatioHeight);
            if (aspectRatio > 2.5 && aspectRatio <= 5) {
                rotatedRects.add(rect);
            }
        }

        if (!rotatedRects.isEmpty()) {
            Mat transformedImg = new Mat();
            Size originalImageSize = src.size();
            Size imageSize = image.size();
            double xMultiplier = originalImageSize.width / imageSize.width;
            double yMultiplier = originalImageSize.height / imageSize.height;
            for (RotatedRect rect : rotatedRects) {
                double rectWidth = Math.max(rect.size.width, rect.size.height) * xMultiplier * 1.2;
                double rectHeight = Math.min(rect.size.width, rect.size.height) * yMultiplier * 1.1;
                Size holderSize = new Size(rectWidth, rectWidth);
                OpenCVUtils.translate(src, transformedImg, (holderSize.width / 2) - rect.center.x * xMultiplier, (holderSize.height / 2) - rect.center.y * yMultiplier, holderSize);
                OpenCVUtils.rotate(transformedImg, transformedImg, new Point(holderSize.width/2, holderSize.height/2), rect.size.width > rect.size.height ? 180 + rect.angle : 90 + rect.angle, holderSize);
                barcodeImageCandidates.add(transformedImg.submat(new Rect(0,(int)((holderSize.height / 2) - (rectHeight / 2)), (int)rectWidth, (int)rectHeight)));
            }
        }
        return barcodeImageCandidates;
    }
}
