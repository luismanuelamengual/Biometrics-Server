package com.globant.biometrics.api.v1;

import com.dynamsoft.barcode.BarcodeReader;
import com.dynamsoft.barcode.TextResult;
import com.globant.biometrics.utils.AmazonUtils;
import com.globant.biometrics.utils.OpenCVUtils;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.util.LoadLibs;
import org.neogroup.warp.controllers.ControllerComponent;
import org.neogroup.warp.controllers.routing.Parameter;
import org.neogroup.warp.controllers.routing.Post;
import org.neogroup.warp.data.Data;
import org.neogroup.warp.data.DataObject;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.objdetect.CascadeClassifier;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

@ControllerComponent("v1")
public class ApiController {

    private static final int FACE_MATCH_SUCCESS_STATUS_CODE = 0;
    private static final int FACE_WITH_INCORRECT_GESTURE_STATUS_CODE = 1;
    private static final int FACE_NOT_FOUND_STATUS_CODE = -1;
    private static final int FACE_NOT_CENTERED_STATUS_CODE = -2;
    private static final int FACE_TOO_CLOSE_STATUS_CODE = -3;
    private static final int FACE_TOO_FAR_AWAY_STATUS_CODE = -4;

    private static final String FRONTAL_FACE_INSTRUCTION = "frontal_face";
    private static final String LEFT_PROFILE_FACE_INSTRUCTION = "left_profile_face";
    private static final String RIGHT_PROFILE_FACE_INSTRUCTION = "right_profile_face";

    private CascadeClassifier faceClassfier;
    private CascadeClassifier profileFaceClassifier;
    private CascadeClassifier eyeClassifier;
    private CascadeClassifier eyePairClassifier;
    private Tesseract tesseract;

    public ApiController() {
        faceClassfier = OpenCVUtils.getClassfierFromResource("cascades/face.xml");
        profileFaceClassifier = OpenCVUtils.getClassfierFromResource("cascades/profile-face.xml");
        eyeClassifier = OpenCVUtils.getClassfierFromResource("cascades/eye.xml");
        eyePairClassifier = OpenCVUtils.getClassfierFromResource("cascades/eye-pair.xml");
        tesseract = new Tesseract();
        tesseract.setDatapath(LoadLibs.extractTessResources("tessdata").getAbsolutePath());
        tesseract.setLanguage("spa");
    }

    @Post("check_liveness_instruction")
    public DataObject checkLivenessInstruction(@Parameter("instruction") String instruction, @Parameter("selfie") byte[] selfie) throws Exception {

        if (!instruction.equals(FRONTAL_FACE_INSTRUCTION) && !instruction.equals(LEFT_PROFILE_FACE_INSTRUCTION) && !instruction.equals(RIGHT_PROFILE_FACE_INSTRUCTION)) {
            throw new Exception("Unrecognized liveness instruction");
        }

        Mat image = OpenCVUtils.getImageMat(selfie);
        Rect frontalFaceRect = OpenCVUtils.detectBiggestFeature(image, faceClassfier);
        Rect eyePairRect = OpenCVUtils.detectBiggestFeature(image, eyePairClassifier);
        Rect faceRect = null;
        String faceInstruction = null;

        if (frontalFaceRect != null && eyePairRect != null && OpenCVUtils.featureContains(frontalFaceRect, eyePairRect)) {
            faceRect = frontalFaceRect;
            faceInstruction = FRONTAL_FACE_INSTRUCTION;
        } else {
            Rect[] eyeRects = OpenCVUtils.detectFeatures(image, eyeClassifier);
            Rect rightFaceRect = OpenCVUtils.detectBiggestFeature(image, profileFaceClassifier);
            if (rightFaceRect != null && eyePairRect == null && OpenCVUtils.featureContainsAny(rightFaceRect, eyeRects)) {
                faceRect = rightFaceRect;
                faceInstruction = RIGHT_PROFILE_FACE_INSTRUCTION;
            } else {
                Rect leftFaceRect = OpenCVUtils.detectBiggestFeature(OpenCVUtils.flipImageMat(image), profileFaceClassifier);
                if (leftFaceRect != null) {
                    leftFaceRect.x = image.width() - leftFaceRect.x - leftFaceRect.width;
                }
                if (leftFaceRect != null && eyePairRect == null && OpenCVUtils.featureContainsAny(leftFaceRect, eyeRects)) {
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
            .set("match", status == FACE_MATCH_SUCCESS_STATUS_CODE)
            .set("status", status);
    }

    @Post("verify_identity")
    public DataObject verifyIdentity(@Parameter("selfie") byte[] selfie, @Parameter("documentFront") byte[] documentFront, @Parameter("documentBack") byte[] documentBack) {
        boolean match = false;
        float similarity = 0;
        similarity = AmazonUtils.compareFacesInImages (selfie, documentFront);
        if (similarity > 0) {
            match = true;
        }
        if (!match) {
            similarity = AmazonUtils.compareFacesInImages (selfie, documentBack);
            if (similarity > 0) {
                match = true;
            }
        }
        return Data.object()
            .set("match", match)
            .set("similarity", similarity);
    }

    @Post("scan_document_data")
    public DataObject scanDocument(@Parameter("documentFront") byte[] documentFront, @Parameter("documentBack") byte[] documentBack) throws Exception {

        DataObject response = null;
        String pdf417RawText = getPDF417CodeImage(documentFront);
        if (pdf417RawText == null) {
            pdf417RawText = getPDF417CodeImage(documentBack);
        }

        if (pdf417RawText != null) {
            response = Data.object()
                .set("type", "PDF417")
                .set("raw", pdf417RawText)
                .set("information", getDocumentDataFromPDF417Code(pdf417RawText));
        }

        if (response == null) {
            String mrzRawText = getMRZCodeFromImage(documentBack);
            if (mrzRawText == null) {
                mrzRawText = getMRZCodeFromImage(documentFront);
            }
            response = Data.object()
                .set("type", "MRZ")
                .set("raw", mrzRawText)
                .set("information", getDocumentDataFromMRZCode(mrzRawText));
        }

        if (response == null) {
            throw new RuntimeException("Document data could not be read");
        }
        return response;
    }

    private Map<String,String> getDocumentDataFromPDF417Code (String pdf317Code) {
        String[] dataTokens = pdf317Code.split("@");
        Map<String,String> documentData = new HashMap<>();
        documentData.put("firstName", dataTokens[2]);
        documentData.put("lastName", dataTokens[1]);
        documentData.put("documentNumber", dataTokens[4]);
        documentData.put("gender", dataTokens[3]);
        documentData.put("birthDate", dataTokens[6]); // TODO homogeneizar birthDate (timestamp?)
        documentData.put("nationalIdentificationNumber", dataTokens[0]);
        return documentData;
    }

    private Map<String,String> getDocumentDataFromMRZCode (String mrzCode) {
        Map<String,String> documentData = new HashMap<>();
        if (mrzCode.length() == 90) {
            String section1 = mrzCode.substring(0, 30);
            String section2 = mrzCode.substring(30, 60);
            String section3 = mrzCode.substring(60, 90);
            documentData.put("documentNumber", section1.substring(5, section1.indexOf("<")));
            documentData.put("birthDate", section2.substring(0, 6)); // TODO homogeneizar birthDate (timestamp?)
            documentData.put("gender", section2.substring(7, 8));
            String[] name = section3.split("<<");
            documentData.put("lastName", name[0].replace("<", " "));
            documentData.put("firstName", name[1].replace("<", " "));
        }
        return documentData;
    }

    private String getMRZCodeFromImage (byte[] image) throws Exception {
        String mrzCode = null;
        Mat mrzMat = OpenCVUtils.detectMRZ(OpenCVUtils.getImageMat(image));
        if (mrzMat != null) {
            Image mrzImage = OpenCVUtils.getBufferedImage(mrzMat);
            mrzCode = tesseract.doOCR((BufferedImage)mrzImage);
            if (mrzCode != null && !mrzCode.isEmpty()) {
                mrzCode = mrzCode.replaceAll("\n", "");
            }
        }
        return mrzCode;
    }

    public static String getPDF417CodeImage(byte[] imageBytes) throws Exception {
        String pdf417Code = null;
        BarcodeReader dbr = new BarcodeReader();
        TextResult[] result = dbr.decodeFileInMemory(imageBytes, "");
        if (result != null && result.length > 0) {
            TextResult barcodeData = result[0];
            int dataLimitIndex = barcodeData.barcodeText.indexOf("***");
            if (dataLimitIndex > 0) {
                pdf417Code = barcodeData.barcodeText.substring(0, dataLimitIndex);
            }
        }
        return pdf417Code;
    }
}
