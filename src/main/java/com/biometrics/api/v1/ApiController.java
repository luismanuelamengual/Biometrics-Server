package com.biometrics.api.v1;

import com.biometrics.ResponseException;
import com.biometrics.utils.AmazonUtils;
import com.biometrics.utils.MRZUtils;
import com.biometrics.utils.OpenCVUtils;
import com.biometrics.utils.PDF417Utils;
import org.neogroup.warp.Request;
import org.neogroup.warp.controllers.ControllerComponent;
import org.neogroup.warp.controllers.routing.Body;
import org.neogroup.warp.controllers.routing.Parameter;
import org.neogroup.warp.controllers.routing.Post;
import org.neogroup.warp.data.Data;
import org.neogroup.warp.data.DataObject;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.neogroup.warp.Warp.getLogger;
import static org.opencv.core.CvType.CV_32F;
import static org.opencv.core.CvType.CV_64F;

@ControllerComponent("v1")
public class ApiController {

    private static final int FACE_MATCH_STATUS_CODE = 1;
    private static final int FACE_FOUND_STATUS_CODE = 0;
    private static final int FACE_NOT_CENTERED_STATUS_CODE = -1;
    private static final int FACE_TOO_CLOSE_STATUS_CODE = -2;
    private static final int FACE_TOO_FAR_AWAY_STATUS_CODE = -3;
    private static final int FACE_NOT_FOUND_STATUS_CODE = -4;

    private static final String MRZ_TYPE = "MRZ";
    private static final String PDF417_TYPE = "PDF417";

    private static final String FRONTAL_FACE_INSTRUCTION = "frontal_face";
    private static final String LEFT_PROFILE_FACE_INSTRUCTION = "left_profile_face";
    private static final String RIGHT_PROFILE_FACE_INSTRUCTION = "right_profile_face";

    private static final String MATCH_PROPERTY_NAME = "match";
    private static final String STATUS_PROPERTY_NAME = "status";
    private static final String SIMILARITY_PROPERTY_NAME = "similarity";
    private static final String TYPE_PROPERTY_NAME = "type";
    private static final String RAW_PROPERTY_NAME = "raw";
    private static final String INFORMATION_PROPERTY_NAME = "information";
    private static final String LIVENESS_PROPERTY_NAME = "liveness";

    private CascadeClassifier faceClassfier;
    private CascadeClassifier profileFaceClassifier;
    private CascadeClassifier eyeClassifier;
    private CascadeClassifier eyePairClassifier;

    public ApiController() {
        faceClassfier = OpenCVUtils.getClassfierFromResource("cascades/face.xml");
        profileFaceClassifier = OpenCVUtils.getClassfierFromResource("cascades/profile-face.xml");
        eyeClassifier = OpenCVUtils.getClassfierFromResource("cascades/eye.xml");
        eyePairClassifier = OpenCVUtils.getClassfierFromResource("cascades/eye-pair.xml");
    }

    @Post("check_liveness_instruction")
    public DataObject checkLivenessInstruction(@Parameter("instruction") String instruction, @Parameter("selfie") byte[] selfie) throws Exception {

        if (!instruction.equals(FRONTAL_FACE_INSTRUCTION) && !instruction.equals(LEFT_PROFILE_FACE_INSTRUCTION) && !instruction.equals(RIGHT_PROFILE_FACE_INSTRUCTION)) {
            throw new Exception("Unrecognized liveness instruction");
        }

        Mat image = OpenCVUtils.getImage(selfie);
        Rect frontalFaceRect = OpenCVUtils.detectBiggestFeatureRect(image, faceClassfier);
        Rect eyePairRect = OpenCVUtils.detectBiggestFeatureRect(image, eyePairClassifier);
        Rect faceRect = null;
        String faceInstruction = null;

        if (frontalFaceRect != null && eyePairRect != null && OpenCVUtils.containsRect(frontalFaceRect, eyePairRect)) {
            faceRect = frontalFaceRect;
            faceInstruction = FRONTAL_FACE_INSTRUCTION;
        } else {
            Rect[] eyeRects = OpenCVUtils.detectFeatureRects(image, eyeClassifier);
            Rect rightFaceRect = OpenCVUtils.detectBiggestFeatureRect(image, profileFaceClassifier);
            if (rightFaceRect != null && eyePairRect == null && OpenCVUtils.containerAnyRect(rightFaceRect, eyeRects)) {
                faceRect = rightFaceRect;
                faceInstruction = RIGHT_PROFILE_FACE_INSTRUCTION;
            } else {
                Core.flip(image, image, 1);
                Rect leftFaceRect = OpenCVUtils.detectBiggestFeatureRect(image, profileFaceClassifier);
                if (leftFaceRect != null) {
                    leftFaceRect.x = image.width() - leftFaceRect.x - leftFaceRect.width;
                }
                if (leftFaceRect != null && eyePairRect == null && OpenCVUtils.containerAnyRect(leftFaceRect, eyeRects)) {
                    faceRect = leftFaceRect;
                    faceInstruction = LEFT_PROFILE_FACE_INSTRUCTION;
                } else {
                    if (frontalFaceRect != null) {
                        faceRect = frontalFaceRect;
                    } else if (rightFaceRect != null) {
                        faceRect = rightFaceRect;
                    } else if (leftFaceRect != null) {
                        faceRect = leftFaceRect;
                    }
                }
            }
        }

        int status;
        if (faceRect == null) {
            status = FACE_NOT_FOUND_STATUS_CODE;
        } else {
            int maxXDifferential = 40;
            int maxYDifferential = 40;
            double minFaceZoomRatio = 0.45;
            double maxFaceZoomRatio = 0.85;
            if (faceInstruction == LEFT_PROFILE_FACE_INSTRUCTION || faceInstruction == RIGHT_PROFILE_FACE_INSTRUCTION || instruction == LEFT_PROFILE_FACE_INSTRUCTION || instruction == RIGHT_PROFILE_FACE_INSTRUCTION) {
                minFaceZoomRatio -= 0.15;
                maxFaceZoomRatio += 0.15;
                maxXDifferential += 15;
                maxYDifferential += 15;
            }

            int imageWidth = image.width();
            int imageHeight = image.height();
            int imageMiddleX = imageWidth / 2;
            int imageMiddleY = imageHeight / 2;
            int faceMiddleX = faceRect.x + (faceRect.width / 2);
            int faceMiddleY = faceRect.y + (faceRect.height / 2);
            int xDifferential = Math.abs(imageMiddleX - faceMiddleX);
            int yDifferential = Math.abs(imageMiddleY - faceMiddleY);
            if (xDifferential > maxXDifferential || yDifferential > maxYDifferential) {
                status = FACE_NOT_CENTERED_STATUS_CODE;
            } else {
                double xRatio = faceRect.width / (double)imageWidth;
                double yRatio = faceRect.height / (double)imageHeight;
                double ratio = Math.max(xRatio, yRatio);
                if (ratio < minFaceZoomRatio) {
                    status = FACE_TOO_FAR_AWAY_STATUS_CODE;
                } else if (ratio > maxFaceZoomRatio) {
                    status = FACE_TOO_CLOSE_STATUS_CODE;
                } else {
                    switch (instruction) {
                        case FRONTAL_FACE_INSTRUCTION:
                        case LEFT_PROFILE_FACE_INSTRUCTION:
                        case RIGHT_PROFILE_FACE_INSTRUCTION:
                            if (instruction.equals(faceInstruction)) {
                                status = FACE_MATCH_STATUS_CODE;
                            } else {
                                status = FACE_FOUND_STATUS_CODE;
                            }
                            break;
                        default:
                            status = FACE_FOUND_STATUS_CODE;
                    }
                }
            }
        }

        return Data.object()
                .set(MATCH_PROPERTY_NAME, status == FACE_MATCH_STATUS_CODE)
                .set(STATUS_PROPERTY_NAME, status);
    }

    @Post("check_liveness_images")
    public DataObject checkLivenessImages(Request request) throws Exception {
        boolean livenessStatusOk = true;
        int[] histSize = { 50, 60 };
        float[] ranges = { 0, 180, 0, 256 };
        int[] channels = { 0, 1 };
        int imagesCount = request.getInt("imagesCount");
        List<byte[]> imageBytesList = new ArrayList<>();
        List<Mat> imagesHist = new ArrayList<>();
        for (int i = 0; i < imagesCount; i++) {
            byte[] imageBytes = request.get("image" + (i + 1), byte[].class);
            Mat image = OpenCVUtils.getImage(imageBytes);
            Imgproc.calcHist(Arrays.asList(image), new MatOfInt(channels), new Mat(), image, new MatOfInt(histSize), new MatOfFloat(ranges), false);
            Core.normalize(image, image, 0, 1, Core.NORM_MINMAX);
            imagesHist.add(image);
            imageBytesList.add(imageBytes);
        }

        for (int i = 1; i < imagesCount; i++) {
            double histSimilarity = Imgproc.compareHist(imagesHist.get(0), imagesHist.get(i), Imgproc.HISTCMP_CORREL);
            if (histSimilarity < 0.6) {
                livenessStatusOk = false;
                break;
            }

            float similarity = AmazonUtils.compareFaces(imageBytesList.get(0), imageBytesList.get(i));
            if (similarity <= 0) {
                livenessStatusOk = false;
                break;
            }
        }
        return Data.object().set(LIVENESS_PROPERTY_NAME, livenessStatusOk);
    }

    @Post("verify_identity")
    public DataObject verifyIdentity(@Parameter("selfie") byte[] selfie, @Parameter("documentFront") byte[] documentFront, @Parameter("documentBack") byte[] documentBack) {
        Mat selfieMat = OpenCVUtils.detectBiggestFeature(OpenCVUtils.getImage(selfie), faceClassfier);
        if (selfieMat == null || selfieMat.empty()) {
            throw new ResponseException("No face found in selfie");
        }
        Mat documentFaceMat = OpenCVUtils.detectBiggestFeature(OpenCVUtils.getImage(documentFront), faceClassfier);
        if (documentFaceMat == null || documentFaceMat.empty()) {
            documentFaceMat = OpenCVUtils.detectBiggestFeature(OpenCVUtils.getImage(documentBack), faceClassfier);
        }
        if (documentFaceMat == null || documentFaceMat.empty()) {
            throw new ResponseException ("No face found in document");
        }

        byte[] selfieBytes = OpenCVUtils.getImageBytes(selfieMat);
        byte[] documentSelfieBytes = OpenCVUtils.getImageBytes(documentFaceMat);
        boolean match = false;
        float similarity = AmazonUtils.compareFaces(selfieBytes, documentSelfieBytes);
        if (similarity > 0) {
            match = true;
        }
        return Data.object()
                .set(MATCH_PROPERTY_NAME, match)
                .set(SIMILARITY_PROPERTY_NAME, similarity);
    }

    @Post("scan_document_data")
    public DataObject scanDocument(@Parameter("documentFront") byte[] documentFront, @Parameter("documentBack") byte[] documentBack) throws Exception {

        DataObject response = null;
        String pdf417RawText = getPDF417CodeFromImage(documentFront);
        if (pdf417RawText == null) {
            pdf417RawText = getPDF417CodeFromImage(documentBack);
        }

        if (pdf417RawText != null) {
            try {
                Map<String, Object> documentInformation = PDF417Utils.parseCode(pdf417RawText);
                if (documentInformation != null && !documentInformation.isEmpty()){
                    response = Data.object()
                            .set(TYPE_PROPERTY_NAME, PDF417_TYPE)
                            .set(RAW_PROPERTY_NAME, pdf417RawText)
                            .set(INFORMATION_PROPERTY_NAME, documentInformation);
                }
            } catch (Exception ex) {
                getLogger().warning("Unable to read PDF code: \"" + pdf417RawText + "\" !!");
            }
        }

        if (response == null) {
            String mrzRawText = getMRZCodeFromImage(documentBack);
            if (mrzRawText == null) {
                mrzRawText = getMRZCodeFromImage(documentFront);
            }

            if (mrzRawText != null) {
                try {
                    Map<String, Object> documentInformation = MRZUtils.parseCode(mrzRawText);
                    if (documentInformation != null && !documentInformation.isEmpty()){
                        response = Data.object()
                                .set(TYPE_PROPERTY_NAME, MRZ_TYPE)
                                .set(RAW_PROPERTY_NAME, mrzRawText)
                                .set(INFORMATION_PROPERTY_NAME, documentInformation);
                    }
                } catch(Exception ex) {
                    getLogger().warning("Unable to read MRZ code: \"" + mrzRawText + "\" !!");
                }
            }
        }

        if (response == null) {
            throw new ResponseException("Document data could not be read");
        }
        return response;
    }

    @Post("scan_barcode_data")
    public DataObject scanBarcode (@Body byte[] imageBytes) throws Exception {
        DataObject response = null;
        String pdf417RawText = getPDF417CodeFromImage(imageBytes);
        if (pdf417RawText != null) {
            try {
                Map<String, Object> documentInformation = PDF417Utils.parseCode(pdf417RawText);
                if (documentInformation != null && !documentInformation.isEmpty()){
                    response = Data.object()
                            .set(RAW_PROPERTY_NAME, pdf417RawText)
                            .set(INFORMATION_PROPERTY_NAME, documentInformation);
                }
            } catch (Exception ex) {}
        }
        if (response == null) {
            throw new ResponseException("Barcode data could not be read");
        }
        return response;
    }

    @Post("scan_mrz_data")
    public DataObject scanMRZ (@Body byte[] imageBytes) throws Exception {
        DataObject response = null;
        String mrzRawText = getMRZCodeFromImage(imageBytes);
        if (mrzRawText != null) {
            try {
                Map<String, Object> documentInformation = MRZUtils.parseCode(mrzRawText);
                if (documentInformation != null && !documentInformation.isEmpty()){
                    response = Data.object()
                            .set(RAW_PROPERTY_NAME, mrzRawText)
                            .set(INFORMATION_PROPERTY_NAME, documentInformation);
                }
            } catch (Exception ex) {}
        }
        if (response == null) {
            throw new ResponseException("MRZ data could not be read");
        }
        return response;
    }

    private String getMRZCodeFromImage (byte[] imageBytes) {
        String mrzCode = null;
        if (imageBytes.length > 0) {
            Mat mrzMat = detectMrz(OpenCVUtils.getImage(imageBytes));
            if (mrzMat != null) {
                mrzCode = MRZUtils.readCode(OpenCVUtils.getBufferedImage(mrzMat));
            }
        }
        return mrzCode;
    }

    private String getPDF417CodeFromImage(byte[] imageBytes) {
        String pdf417Code = null;
        if (imageBytes.length > 0) {
            Mat image = OpenCVUtils.getImage(imageBytes);
            List<Mat> barcodeImages = detectPDF417(image);
            for (Mat barcodeImage : barcodeImages) {
                pdf417Code = PDF417Utils.readCode(OpenCVUtils.getBufferedImage(barcodeImage));
                if (pdf417Code != null) {
                    break;
                }
            }
        }
        return pdf417Code;
    }

    private List<Mat> detectPDF417(Mat src){
        List<Mat> barcodeImageCandidates = new ArrayList<>();
        Mat grayScaleImage = OpenCVUtils.grayScale(src);
        Mat resizedImage = OpenCVUtils.resize(grayScaleImage, 800, 800, 0, 0);
        Mat image = resizedImage.clone();
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
            Mat translationMatrix2D = new Mat(2, 3, CV_64F);
            Size originalImageSize = src.size();
            Size resizedImageSize = resizedImage.size();
            double xMultiplier = originalImageSize.width / resizedImageSize.width;
            double yMultiplier = originalImageSize.height / resizedImageSize.height;
            for (RotatedRect rect : rotatedRects) {
                double rectWidth = Math.max(rect.size.width, rect.size.height) * xMultiplier * 1.2;
                double rectHeight = Math.min(rect.size.width, rect.size.height) * yMultiplier * 1.1;
                Size holderSize = new Size(rectWidth, rectWidth);
                translationMatrix2D.put(0, 0, 1);
                translationMatrix2D.put(0, 1, 0);
                translationMatrix2D.put(0, 2, (holderSize.width / 2) - rect.center.x * xMultiplier);
                translationMatrix2D.put(1, 0, 0);
                translationMatrix2D.put(1, 1, 1);
                translationMatrix2D.put(1, 2, (holderSize.height / 2) - rect.center.y * yMultiplier);
                Imgproc.warpAffine(src, transformedImg, translationMatrix2D, holderSize, Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT);
                Mat rotatedMatrix2D = Imgproc.getRotationMatrix2D(new Point(holderSize.width/2, holderSize.height/2), rect.size.width > rect.size.height ? 180 + rect.angle : 90 + rect.angle, 1.0);
                Imgproc.warpAffine(transformedImg, transformedImg, rotatedMatrix2D, holderSize, Imgproc.INTER_CUBIC, Core.BORDER_CONSTANT);
                barcodeImageCandidates.add(transformedImg.submat(new Rect(0,(int)((holderSize.height / 2) - (rectHeight / 2)), (int)rectWidth, (int)rectHeight)));
            }
        }
        return barcodeImageCandidates;
    }

    private Mat detectMrz(Mat src){
        Mat img = OpenCVUtils.grayScale(src);
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
        if (rotatedRect.size.width > (rotatedRect.size.height * 3.8)) {
            if (Math.abs(rotatedRect.angle) > 3) {
                Mat rotatedMatrix2D = Imgproc.getRotationMatrix2D(rotatedRect.center, rotatedRect.angle, 1.0);
                Mat rotatedImg = new Mat();
                Imgproc.warpAffine(resizedImg, rotatedImg, rotatedMatrix2D, resizedImg.size(), Imgproc.INTER_CUBIC, Core.BORDER_REPLICATE);
                Rect rect = new Rect((int)rotatedRect.center.x - ((int)rotatedRect.size.width / 2), (int)rotatedRect.center.y - ((int)rotatedRect.size.height / 2), (int)rotatedRect.size.width, (int)rotatedRect.size.height);
                mrzMat = rotatedImg.submat(rect);
            } else {
                Rect rect = Imgproc.boundingRect(contour);
                mrzMat = resizedImg.submat(rect);
            }
        } else if (rotatedRect.size.height > (rotatedRect.size.width * 3.8)) {
            double rotationAngle = (rotatedRect.angle < 0 ? 90 : -90) + rotatedRect.angle;
            Mat rotatedMatrix2D = Imgproc.getRotationMatrix2D(rotatedRect.center, rotationAngle, 1.0);
            Mat rotatedImg = new Mat();
            Imgproc.warpAffine(resizedImg, rotatedImg, rotatedMatrix2D, resizedImg.size(), Imgproc.INTER_CUBIC, Core.BORDER_REPLICATE);
            Rect rect = new Rect((int)rotatedRect.center.x - ((int)rotatedRect.size.height / 2), (int)rotatedRect.center.y - ((int)rotatedRect.size.width / 2), (int)rotatedRect.size.height, (int)rotatedRect.size.width);
            mrzMat = rotatedImg.submat(rect);
        }

        if (mrzMat != null && !mrzMat.empty()) {
            Imgproc.adaptiveThreshold(mrzMat, mrzMat, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 17, 5);
            Imgproc.erode(mrzMat, mrzMat, new Mat(), new Point(-1, -1), 1);
            Imgproc.dilate(mrzMat, mrzMat, new Mat(), new Point(-1, -1), 1);
        }
        return mrzMat;
    }
}
