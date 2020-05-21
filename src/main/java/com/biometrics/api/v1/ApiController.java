package com.biometrics.api.v1;

import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.AmazonRekognitionClientBuilder;
import com.amazonaws.services.rekognition.model.CompareFacesMatch;
import com.amazonaws.services.rekognition.model.CompareFacesRequest;
import com.amazonaws.services.rekognition.model.CompareFacesResult;
import com.biometrics.utils.MRZParser;
import com.biometrics.utils.OpenCVUtils;
import com.biometrics.utils.PDF417Parser;
import com.dynamsoft.barcode.BarcodeReader;
import com.dynamsoft.barcode.TextResult;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.util.LoadLibs;
import org.neogroup.warp.controllers.ControllerComponent;
import org.neogroup.warp.controllers.routing.Parameter;
import org.neogroup.warp.controllers.routing.Post;
import org.neogroup.warp.data.Data;
import org.neogroup.warp.data.DataObject;
import org.opencv.core.Point;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ControllerComponent("v1")
public class ApiController {

    private static final int FACE_MATCH_SUCCESS_STATUS_CODE = 0;
    private static final int FACE_WITH_INCORRECT_GESTURE_STATUS_CODE = 1;
    private static final int FACE_NOT_FOUND_STATUS_CODE = -1;
    private static final int FACE_NOT_CENTERED_STATUS_CODE = -2;
    private static final int FACE_TOO_CLOSE_STATUS_CODE = -3;
    private static final int FACE_TOO_FAR_AWAY_STATUS_CODE = -4;

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

    private CascadeClassifier faceClassfier;
    private CascadeClassifier profileFaceClassifier;
    private CascadeClassifier eyeClassifier;
    private CascadeClassifier eyePairClassifier;
    private Tesseract tesseract;
    private AmazonRekognition rekognitionClient;

    public ApiController() {
        faceClassfier = OpenCVUtils.getClassfierFromResource("cascades/face.xml");
        profileFaceClassifier = OpenCVUtils.getClassfierFromResource("cascades/profile-face.xml");
        eyeClassifier = OpenCVUtils.getClassfierFromResource("cascades/eye.xml");
        eyePairClassifier = OpenCVUtils.getClassfierFromResource("cascades/eye-pair.xml");
        rekognitionClient = AmazonRekognitionClientBuilder.defaultClient();
        tesseract = new Tesseract();
        tesseract.setDatapath(LoadLibs.extractTessResources("tessdata").getAbsolutePath());
        tesseract.setLanguage("spa");
    }

    @Post("check_liveness_instruction")
    public DataObject checkLivenessInstruction(@Parameter("instruction") String instruction, @Parameter("selfie") byte[] selfie) throws Exception {

        if (!instruction.equals(FRONTAL_FACE_INSTRUCTION) && !instruction.equals(LEFT_PROFILE_FACE_INSTRUCTION) && !instruction.equals(RIGHT_PROFILE_FACE_INSTRUCTION)) {
            throw new Exception("Unrecognized liveness instruction");
        }

        Mat image = OpenCVUtils.getMat(selfie);
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
                Rect leftFaceRect = OpenCVUtils.detectBiggestFeatureRect(OpenCVUtils.flipMat(image), profileFaceClassifier);
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
            int imageWidth = image.width();
            int imageHeight = image.height();
            int imageMiddleX = imageWidth / 2;
            int imageMiddleY = imageHeight / 2;
            int faceMiddleX = faceRect.x + (faceRect.width / 2);
            int faceMiddleY = faceRect.y + (faceRect.height / 2);
            if (faceInstruction == LEFT_PROFILE_FACE_INSTRUCTION) {
                faceMiddleX -= (faceRect.width * 10.0) / 100.0;
            } else if (faceInstruction == RIGHT_PROFILE_FACE_INSTRUCTION) {
                faceMiddleX += (faceRect.width * 10.0) / 100.0;
            }
            int xDifferential = Math.abs(imageMiddleX - faceMiddleX);
            int yDifferential = Math.abs(imageMiddleY - faceMiddleY);
            double faceAspectRatio = 0.5;
            double imageAspectRatio = (double)imageWidth / (double)imageHeight;
            double xDifferentialLimit = 0.0;
            double yDifferentialLimit = 0.0;
            if (imageAspectRatio > faceAspectRatio) {
                xDifferentialLimit = imageHeight / 4.0;
                yDifferentialLimit = imageHeight / 4.0;
            } else {
                xDifferentialLimit = imageWidth / 4.0;
                yDifferentialLimit = imageWidth / 4.0;
            }

            if (xDifferential > xDifferentialLimit || yDifferential > yDifferentialLimit) {
                status = FACE_NOT_CENTERED_STATUS_CODE;
            } else {
                switch (instruction) {
                    case FRONTAL_FACE_INSTRUCTION:
                    case LEFT_PROFILE_FACE_INSTRUCTION:
                    case RIGHT_PROFILE_FACE_INSTRUCTION:
                        if (instruction.equals(faceInstruction)) {
                            status = FACE_MATCH_SUCCESS_STATUS_CODE;
                        } else {
                            status = FACE_WITH_INCORRECT_GESTURE_STATUS_CODE;
                        }
                        break;
                    default:
                        status = FACE_WITH_INCORRECT_GESTURE_STATUS_CODE;
                }
            }
        }

        return Data.object()
            .set(MATCH_PROPERTY_NAME, status == FACE_MATCH_SUCCESS_STATUS_CODE)
            .set(STATUS_PROPERTY_NAME, status);
    }

    @Post("verify_identity")
    public DataObject verifyIdentity(@Parameter("selfie") byte[] selfie, @Parameter("documentFront") byte[] documentFront, @Parameter("documentBack") byte[] documentBack) {
        Mat selfieMat = OpenCVUtils.detectBiggestFeature(OpenCVUtils.getMat(selfie), faceClassfier);
        if (selfieMat == null || selfieMat.empty()) {
            throw new RuntimeException ("No face found in selfie");
        }
        Mat documentFaceMat = OpenCVUtils.detectBiggestFeature(OpenCVUtils.getMat(documentFront), faceClassfier);
        if (documentFaceMat == null || documentFaceMat.empty()) {
            documentFaceMat = OpenCVUtils.detectBiggestFeature(OpenCVUtils.getMat(documentBack), faceClassfier);
        }
        if (documentFaceMat == null || documentFaceMat.empty()) {
            throw new RuntimeException ("No face found in document");
        }

        byte[] selfieBytes = OpenCVUtils.getImageBytes(selfieMat);
        byte[] documentSelfieBytes = OpenCVUtils.getImageBytes(documentFaceMat);
        boolean match = false;
        float similarity = compareFacesInImages (selfieBytes, documentSelfieBytes);
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
                Map<String, Object> documentInformation = PDF417Parser.parseCode(pdf417RawText);
                response = Data.object()
                    .set(TYPE_PROPERTY_NAME, PDF417_TYPE)
                    .set(RAW_PROPERTY_NAME, pdf417RawText)
                    .set(INFORMATION_PROPERTY_NAME, documentInformation);
            } catch (Exception ex) {}
        }

        if (response == null) {
            String mrzRawText = getMRZCodeFromImage(documentBack);
            if (mrzRawText == null) {
                mrzRawText = getMRZCodeFromImage(documentFront);
            }

            if (mrzRawText != null) {
                try {
                    Map<String, Object> documentInformation = MRZParser.parseCode(mrzRawText);
                    response = Data.object()
                        .set(TYPE_PROPERTY_NAME, MRZ_TYPE)
                        .set(RAW_PROPERTY_NAME, mrzRawText)
                        .set(INFORMATION_PROPERTY_NAME, documentInformation);
                } catch(Exception ex) {}
            }
        }

        if (response == null) {
            throw new RuntimeException("Document data could not be read");
        }
        return response;
    }

    private String getMRZCodeFromImage (byte[] imageBytes) throws Exception {
        String mrzCode = null;
        if (imageBytes.length > 0) {
            Mat mrzMat = detectMrz(OpenCVUtils.getMat(imageBytes));
            if (mrzMat != null) {
                Image mrzImage = OpenCVUtils.getBufferedImage(mrzMat);
                String mrzCodeTest = tesseract.doOCR((BufferedImage)mrzImage);
                if (mrzCodeTest != null && !mrzCodeTest.isEmpty() && mrzCodeTest.length() > 40 && mrzCodeTest.indexOf("<<") > 0) {
                    mrzCodeTest = mrzCodeTest.replaceAll("\n", "");
                    //BUG Tesseract: Sometimes reads <0O< instead of <0<
                    mrzCodeTest = mrzCodeTest.replaceAll("<0O<", "<0<");
                    mrzCode = mrzCodeTest;
                }
            }
        }
        return mrzCode;
    }

    private String getPDF417CodeFromImage(byte[] imageBytes) throws Exception {
        String pdf417Code = null;
        if (imageBytes.length > 0) {
            BarcodeReader dbr = new BarcodeReader();
            TextResult[] result = dbr.decodeFileInMemory(imageBytes, "");
            if (result != null && result.length > 0) {
                TextResult barcodeData = result[0];
                int dataLimitIndex = barcodeData.barcodeText.indexOf("***");
                if (dataLimitIndex > 0) {
                    pdf417Code = barcodeData.barcodeText.substring(0, dataLimitIndex);
                }
            }
        }
        return pdf417Code;
    }



    private float compareFacesInImages (byte[] image1Bytes, byte[] image2Bytes) {
        com.amazonaws.services.rekognition.model.Image image1 = new com.amazonaws.services.rekognition.model.Image().withBytes(ByteBuffer.wrap(image1Bytes));;
        com.amazonaws.services.rekognition.model.Image image2 = new com.amazonaws.services.rekognition.model.Image().withBytes(ByteBuffer.wrap(image2Bytes));;
        CompareFacesRequest request = new CompareFacesRequest().withSourceImage(image1).withTargetImage(image2).withSimilarityThreshold(70F);
        float similarity = 0;
        try {
            CompareFacesResult compareFacesResult = rekognitionClient.compareFaces(request);
            List<CompareFacesMatch> comparissonResults = compareFacesResult.getFaceMatches();
            if (comparissonResults != null) {
                for (CompareFacesMatch comparissonResult : comparissonResults) {
                    similarity = comparissonResult.getSimilarity();
                    break;
                }
            }
        } catch (Exception ex) {}
        return similarity;
    }

    public Mat detectMrz(Mat src){
        Mat img = src.clone();
        src.release();
        double ratio = img.height() / 800.0;
        int width = (int) (img.size().width / ratio);
        int height = (int) (img.size().height / ratio);
        Size newSize = new Size(width, height);
        Mat resizedImg = new Mat(newSize, CvType.CV_8UC4);
        Imgproc.resize(img, resizedImg, newSize);
        Mat gray = new Mat();
        Imgproc.cvtColor(resizedImg, gray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.medianBlur(gray, gray, 3);
        Mat morph = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(13, 5));
        Mat dilatedImg = new Mat();
        Imgproc.morphologyEx(gray, dilatedImg, Imgproc.MORPH_BLACKHAT, morph);
        gray.release();
        Mat gradX = new Mat();
        Imgproc.Sobel(dilatedImg, gradX, CvType.CV_32F, 1, 0);
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
        Rect rect = Imgproc.boundingRect(contour);
        Mat roi = resizedImg.submat(rect);
        double roiProportion = roi.width() > roi.height() ? (double)roi.width() / (double)roi.height() : (double)roi.height() / (double)roi.width();
        return (roiProportion >= 4)? roi : null;
    }
}
