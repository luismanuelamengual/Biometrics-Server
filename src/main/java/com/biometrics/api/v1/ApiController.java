package com.biometrics.api.v1;

import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.AmazonRekognitionClientBuilder;
import com.amazonaws.services.rekognition.model.CompareFacesMatch;
import com.amazonaws.services.rekognition.model.CompareFacesRequest;
import com.amazonaws.services.rekognition.model.CompareFacesResult;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.biometrics.utils.MRZParser;
import com.biometrics.utils.OpenCVUtils;
import com.biometrics.utils.PDF417Parser;
import com.dynamsoft.barcode.BarcodeReader;
import com.dynamsoft.barcode.TextResult;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.util.LoadLibs;
import org.neogroup.warp.controllers.ControllerComponent;
import org.neogroup.warp.controllers.routing.Body;
import org.neogroup.warp.controllers.routing.HeaderParameter;
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
import java.util.List;
import java.util.*;

import static org.neogroup.warp.Warp.getProperty;

@ControllerComponent("v1")
public class ApiController {

    private static final String LIVENESS_SESSION_SECRET_KEY_PROPERTY_NAME = "liveness_session_secret_key";
    private static final int LIVENESS_STATUS_RUNNING = 0;
    private static final int LIVENESS_STATUS_COMPLETED = 1;
    private static final int LIVENESS_STATUS_TIMEOUT = 2;
    private static final int LIVENESS_STATUS_ERROR = 2;
    private static final String LIVENESS_INSTRUCTIONS_CLAIM_NAME = "instructions";
    private static final String LIVENESS_TIMEOUT_TIMESTAMP_CLAIM_NAME = "timeoutTimestamp";
    private static final String LIVENESS_INSTRUCTION_CLAIM_NAME = "instructionIndex";

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
    private static final String[] ALL_INSTRUCTIONS = { FRONTAL_FACE_INSTRUCTION, LEFT_PROFILE_FACE_INSTRUCTION, RIGHT_PROFILE_FACE_INSTRUCTION };

    private static final String MATCH_PROPERTY_NAME = "match";
    private static final String STATUS_PROPERTY_NAME = "status";
    private static final String SIMILARITY_PROPERTY_NAME = "similarity";
    private static final String TYPE_PROPERTY_NAME = "type";
    private static final String RAW_PROPERTY_NAME = "raw";
    private static final String INFORMATION_PROPERTY_NAME = "information";
    private static final String SESSION_PROPERTY_NAME = "session";
    private static final String INSTRUCTION_PROPERTY_NAME = "instruction";
    private static final String MESSAGE_PROPERTY_NAME = "message";

    private CascadeClassifier faceClassfier;
    private CascadeClassifier profileFaceClassifier;
    private CascadeClassifier eyeClassifier;
    private CascadeClassifier eyePairClassifier;
    private Tesseract tesseract;
    private AmazonRekognition rekognitionClient;
    private Algorithm livenessJwtAlgorithm;
    private JWTVerifier livenessJwtVerifier;

    public ApiController() {
        livenessJwtAlgorithm = Algorithm.HMAC256(getProperty(LIVENESS_SESSION_SECRET_KEY_PROPERTY_NAME));
        livenessJwtVerifier = JWT.require(livenessJwtAlgorithm).build();
        faceClassfier = OpenCVUtils.getClassfierFromResource("cascades/face.xml");
        profileFaceClassifier = OpenCVUtils.getClassfierFromResource("cascades/profile-face.xml");
        eyeClassifier = OpenCVUtils.getClassfierFromResource("cascades/eye.xml");
        eyePairClassifier = OpenCVUtils.getClassfierFromResource("cascades/eye-pair.xml");
        rekognitionClient = AmazonRekognitionClientBuilder.defaultClient();
        tesseract = new Tesseract();
        tesseract.setDatapath(LoadLibs.extractTessResources("tessdata").getAbsolutePath());
        tesseract.setLanguage("spa");
    }

    @Post("start_liveness_session")
    public DataObject startLivenessSession(@Parameter(value="maxInstructions", required=false) int maxInstructions, @Parameter(value="sessionTimeout", required=false) long sessionTimeout) {

        if (maxInstructions < 2) {
            maxInstructions = 3;
        }
        if (sessionTimeout == 0) {
            sessionTimeout = 60000L;
        }

        Random rand = new Random();
        Integer[] instructions = new Integer[maxInstructions];
        int currentInstructionIndex = Arrays.binarySearch(ALL_INSTRUCTIONS, FRONTAL_FACE_INSTRUCTION);
        instructions[0] = currentInstructionIndex;
        for (int instructionSlot = 1; instructionSlot < maxInstructions; instructionSlot++) {
            List<Integer> possibleInstructions = new ArrayList<>();
            for (int instructionIndex = 0; instructionIndex < ALL_INSTRUCTIONS.length; instructionIndex++) {
                if (instructionIndex != currentInstructionIndex) {
                    possibleInstructions.add(instructionIndex);
                }
            }
            int nextInstructionIndex = rand.nextInt(possibleInstructions.size());
            currentInstructionIndex = possibleInstructions.get(nextInstructionIndex);
            instructions[instructionSlot] = currentInstructionIndex;
        }

        long timeoutTimestamp = System.currentTimeMillis() + sessionTimeout;
        String session = createLivenessSession(instructions, timeoutTimestamp, 0);
        return Data.object()
            .set(SESSION_PROPERTY_NAME, session)
            .set(STATUS_PROPERTY_NAME, LIVENESS_STATUS_RUNNING)
            .set(INSTRUCTION_PROPERTY_NAME, ALL_INSTRUCTIONS[instructions[0]]);
    }

    @Post("post_liveness_image")
    public DataObject postLivenessImage(@HeaderParameter("Liveness-Session-Id") String session, @Body byte[] image) {
        DataObject response = Data.object();
        try {
            DecodedJWT decodedJWT = livenessJwtVerifier.verify(session);
            long expirationTimestamp = decodedJWT.getClaim(LIVENESS_TIMEOUT_TIMESTAMP_CLAIM_NAME).asLong();
            if (System.currentTimeMillis() > expirationTimestamp) {
                response.set(SESSION_PROPERTY_NAME, session);
                response.set(STATUS_PROPERTY_NAME, LIVENESS_STATUS_TIMEOUT);
            } else {
                Integer[] instructions = decodedJWT.getClaim(LIVENESS_INSTRUCTIONS_CLAIM_NAME).asArray(Integer.class);
                int instructionIndex = decodedJWT.getClaim(LIVENESS_INSTRUCTION_CLAIM_NAME).asInt();
                int lastInstructionIndex = instructionIndex;
                String instruction = ALL_INSTRUCTIONS[instructions[instructionIndex]];
                int status = checkLivenessImage(instruction, image);
                int livenessStatus = LIVENESS_STATUS_RUNNING;
                switch (status) {
                    case FACE_NOT_FOUND_STATUS_CODE:
                    case FACE_NOT_CENTERED_STATUS_CODE:
                    case FACE_TOO_CLOSE_STATUS_CODE:
                    case FACE_TOO_FAR_AWAY_STATUS_CODE:
                        instructionIndex = 0;
                        break;
                    case FACE_WITH_INCORRECT_GESTURE_STATUS_CODE:
                        break;
                    case FACE_MATCH_SUCCESS_STATUS_CODE:
                        if (instructionIndex >= instruction.length() - 1) {
                            livenessStatus = LIVENESS_STATUS_COMPLETED;
                        } else {
                            instructionIndex++;
                        }
                        break;
                }
                if (instructionIndex != lastInstructionIndex) {
                    session = createLivenessSession(instructions, expirationTimestamp, instructionIndex);
                }
                response.set(SESSION_PROPERTY_NAME, session);
                response.set(STATUS_PROPERTY_NAME, livenessStatus);
                response.set(INSTRUCTION_PROPERTY_NAME, ALL_INSTRUCTIONS[instructions[instructionIndex]]);
            }

        } catch (Exception e) {
            response.set(SESSION_PROPERTY_NAME, session);
            response.set(STATUS_PROPERTY_NAME, LIVENESS_STATUS_ERROR);
            response.set(MESSAGE_PROPERTY_NAME, e.getMessage());
        }
        return response;
    }

    public String createLivenessSession(Integer[] instructions, long timeoutTimestamp, int instructionIndex) {
        return JWT.create()
            .withArrayClaim(LIVENESS_INSTRUCTIONS_CLAIM_NAME, instructions)
            .withClaim(LIVENESS_TIMEOUT_TIMESTAMP_CLAIM_NAME, timeoutTimestamp)
            .withClaim(LIVENESS_INSTRUCTION_CLAIM_NAME, instructionIndex)
            .sign(livenessJwtAlgorithm);
    }

    @Post("check_liveness_instruction")
    @Deprecated
    public DataObject checkLivenessInstruction(@Parameter("instruction") String instruction, @Parameter("selfie") byte[] selfie) throws Exception {
        if (!instruction.equals(FRONTAL_FACE_INSTRUCTION) && !instruction.equals(LEFT_PROFILE_FACE_INSTRUCTION) && !instruction.equals(RIGHT_PROFILE_FACE_INSTRUCTION)) {
            throw new Exception("Unrecognized liveness instruction");
        }
        int status = checkLivenessImage(instruction, selfie);
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
                if (documentInformation != null && !documentInformation.isEmpty()){
                    response = Data.object()
                            .set(TYPE_PROPERTY_NAME, PDF417_TYPE)
                            .set(RAW_PROPERTY_NAME, pdf417RawText)
                            .set(INFORMATION_PROPERTY_NAME, documentInformation);
                }
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
                    if (documentInformation != null && !documentInformation.isEmpty()){
                        response = Data.object()
                                .set(TYPE_PROPERTY_NAME, MRZ_TYPE)
                                .set(RAW_PROPERTY_NAME, mrzRawText)
                                .set(INFORMATION_PROPERTY_NAME, documentInformation);
                    }
                } catch(Exception ex) {}
            }
        }

        if (response == null) {
            throw new RuntimeException("Document data could not be read");
        }
        return response;
    }

    private int checkLivenessImage(String instruction, byte[] selfie) {
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
                double xRatio = faceRect.width / (double)imageWidth;
                double yRatio = faceRect.height / (double)imageHeight;
                double ratio = Math.max(xRatio, yRatio);
                if (ratio < 0.4) {
                    status = FACE_TOO_FAR_AWAY_STATUS_CODE;
                } else if (ratio > 0.7) {
                    status = FACE_TOO_CLOSE_STATUS_CODE;
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
        }
        return status;
    }

    private String getMRZCodeFromImage (byte[] imageBytes) throws Exception {
        String mrzCode = null;
        if (imageBytes.length > 0) {
            Mat mrzMat = detectMrz(OpenCVUtils.getMat(imageBytes));
            if (mrzMat != null) {
                Image mrzImage = OpenCVUtils.getBufferedImage(mrzMat);
                String mrzCodeText = tesseract.doOCR((BufferedImage)mrzImage);
                if (mrzCodeText != null && !mrzCodeText.isEmpty() && mrzCodeText.length() > 40 && mrzCodeText.indexOf("<<") > 0) {
                    mrzCodeText = mrzCodeText.replaceAll("\n", "");
                    mrzCodeText = mrzCodeText.replaceFirst("<0O<", "<0<");
                    mrzCodeText = mrzCodeText.replaceFirst("<O<", "<0<");
                    mrzCodeText = mrzCodeText.replaceFirst("<D<", "<0<");
                    mrzCodeText = mrzCodeText.replaceFirst("<B<", "<8<");
                    mrzCodeText = mrzCodeText.replaceFirst("<A<", "<4<");
                    if (mrzCodeText.startsWith("1D")) {
                        mrzCodeText = mrzCodeText.replaceFirst("1D", "ID");
                    }
                    mrzCode = mrzCodeText;
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
            mrzMat = OpenCVUtils.sharpenMat(mrzMat);
        }
        return mrzMat;
    }
}
