package com.biometrics.api.v1;

import com.biometrics.ResponseException;
import com.biometrics.utils.*;
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

import static org.opencv.core.Core.NORM_MINMAX;

@ControllerComponent("v1")
public class ApiController {

    private static final int LIVENESS_OK_STATUS_CODE = 0;
    private static final int LIVENESS_FACE_NOT_FOUND_STATUS_CODE = 1;
    private static final int LIVENESS_FACE_NOT_ZOOMED_STATUS_CODE = 2;
    private static final int LIVENESS_BLURRINESS_CHECK_FAILED_STATUS_CODE = 3;
    private static final int LIVENESS_IMAGE_QUALITY_CHECK_FAILED_STATUS_CODE = 4;
    private static final int LIVENESS_IMAGE_BRIGHTNESS_CHECK_FAILED_STATUS_CODE = 5;
    private static final int LIVENESS_IMAGE_HISTOGRAM_CHECK_FAILED_STATUS_CODE = 6;
    private static final int LIVENESS_IMAGE_MOIRE_PATTERN_CHECK_FAILED_STATUS_CODE = 7;

    private static final int FACE_MATCH_STATUS_CODE = 1;
    private static final int FACE_FOUND_STATUS_CODE = 0;
    private static final int FACE_NOT_CENTERED_STATUS_CODE = -1;
    private static final int FACE_TOO_CLOSE_STATUS_CODE = -2;
    private static final int FACE_TOO_FAR_AWAY_STATUS_CODE = -3;
    private static final int FACE_TOO_BLURRY_CODE = -4;
    private static final int FACE_NOT_FOUND_STATUS_CODE = -99;

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
    private static final String REASON_PROPERTY_NAME = "reason";

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
    public DataObject checkLivenessInstruction(@Parameter("instruction") String instruction, @Parameter("selfie") byte[] selfie) {

        if (!instruction.equals(FRONTAL_FACE_INSTRUCTION) && !instruction.equals(LEFT_PROFILE_FACE_INSTRUCTION) && !instruction.equals(RIGHT_PROFILE_FACE_INSTRUCTION)) {
            throw new ResponseException("Unrecognized liveness instruction");
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
                    Mat face = image.submat(faceRect);
                    int faceSize = 250;
                    OpenCVUtils.resize(face, face, faceSize, faceSize, faceSize, faceSize);
                    double faceBlurriness = OpenCVUtils.getBlurriness(face);
                    if (faceBlurriness < 10) {
                        status = FACE_TOO_BLURRY_CODE;
                    } else {
                        if (instruction.equals(faceInstruction)) {
                            status = FACE_MATCH_STATUS_CODE;
                        } else {
                            status = FACE_FOUND_STATUS_CODE;
                        }
                    }
                }
            }
        }

        return Data.object()
                .set(MATCH_PROPERTY_NAME, status == FACE_MATCH_STATUS_CODE)
                .set(STATUS_PROPERTY_NAME, status);
    }

    @Post("check_liveness_images")
    public DataObject checkLivenessImages(Request request) {
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
            Core.normalize(image, image, 0, 1, NORM_MINMAX);
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

    @Post("check_liveness_3d")
    public DataObject checkLiveness3d(@Parameter("picture") byte[] imageBytes, @Parameter("zoomedPicture") byte[] zoomedImageBytes) {

        /*try {
            FileUtils.writeByteArrayToFile(new File("src/test/resources/liveness/liveness/image.jpeg"), imageBytes);
            FileUtils.writeByteArrayToFile(new File("src/test/resources/liveness/liveness/zoomedImage.jpeg"), zoomedImageBytes);
        } catch (Exception ex) {}*/

        int livenessStatusCode = LIVENESS_OK_STATUS_CODE;
        Mat image = OpenCVUtils.getImage(imageBytes);
        Mat zoomedImage = OpenCVUtils.getImage(zoomedImageBytes);

        // Validación de que existen rostros en las 2 imagenes
        Rect faceRect = OpenCVUtils.detectBiggestFeatureRect(image, faceClassfier);
        Rect zoomedFaceRect = OpenCVUtils.detectBiggestFeatureRect(zoomedImage, faceClassfier);
        if (faceRect == null || zoomedFaceRect == null) {
            livenessStatusCode = LIVENESS_FACE_NOT_FOUND_STATUS_CODE;
        }

        if (livenessStatusCode == LIVENESS_OK_STATUS_CODE) {
            // Obtencioń de los rostros en las imagenes
            Mat faceImage = image.submat(faceRect);
            Mat zoomedFaceImage = zoomedImage.submat(zoomedFaceRect);
            int imagesSize = 400;
            Mat normalizedFaceImage = new Mat();
            Mat normalizedZoomedFaceImage = new Mat();
            OpenCVUtils.resize(faceImage, normalizedFaceImage, imagesSize, imagesSize, imagesSize, imagesSize);
            OpenCVUtils.resize(zoomedFaceImage, normalizedZoomedFaceImage, imagesSize, imagesSize, imagesSize, imagesSize);

            // Validación de que el rostro con zoom sea efectivamente más grande que el otro
            double imageFaceArea = faceRect.area();
            double zoomedImageFaceArea = zoomedFaceRect.area();
            if (imageFaceArea >= (0.9 * zoomedImageFaceArea)) {
                livenessStatusCode = LIVENESS_FACE_NOT_ZOOMED_STATUS_CODE;
            }

            // Validación de blurriness de los rostros
            if (livenessStatusCode == LIVENESS_OK_STATUS_CODE) {
                if (!LivenessUtils.analyseNormalizedImagesBlurriness(normalizedFaceImage, normalizedZoomedFaceImage)) {
                    livenessStatusCode = LIVENESS_BLURRINESS_CHECK_FAILED_STATUS_CODE;
                }
            }

            // Validación de calidad de las imagenes
            if (livenessStatusCode == LIVENESS_OK_STATUS_CODE) {
                if (!LivenessUtils.analyseImageQuality(image) || !LivenessUtils.analyseImageQuality(zoomedImage)) {
                    livenessStatusCode = LIVENESS_IMAGE_QUALITY_CHECK_FAILED_STATUS_CODE;
                }
            }

            // Validación del grado de brillo de las imagenes
            if (livenessStatusCode == LIVENESS_OK_STATUS_CODE) {
                if (!LivenessUtils.analyseImageBrightness(image) || !LivenessUtils.analyseImageBrightness(zoomedImage)) {
                    livenessStatusCode = LIVENESS_IMAGE_BRIGHTNESS_CHECK_FAILED_STATUS_CODE;
                }
            }

            // Validación de comparación de histogramas
            if (livenessStatusCode == LIVENESS_OK_STATUS_CODE) {
                if (!LivenessUtils.analyseImageHistograms(image, zoomedImage)) {
                    livenessStatusCode = LIVENESS_IMAGE_HISTOGRAM_CHECK_FAILED_STATUS_CODE;
                }
            }

            // Validación de los patrones de Moire
            if (livenessStatusCode == LIVENESS_OK_STATUS_CODE) {
                if (!LivenessUtils.analyseImageMoirePatternDisturbances(faceImage) || !LivenessUtils.analyseImageMoirePatternDisturbances(zoomedFaceImage)) {
                    livenessStatusCode = LIVENESS_IMAGE_MOIRE_PATTERN_CHECK_FAILED_STATUS_CODE;
                }
            }
        }

        DataObject response = Data.object();
        if (livenessStatusCode == LIVENESS_OK_STATUS_CODE) {
            response.set(LIVENESS_PROPERTY_NAME, true);
        } else {
            response.set(LIVENESS_PROPERTY_NAME, false);
            response.set(REASON_PROPERTY_NAME, livenessStatusCode);
        }
        return response;
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
    public DataObject scanDocument(@Parameter("documentFront") byte[] documentFront, @Parameter("documentBack") byte[] documentBack) {

        DataObject response = null;
        String pdf417RawText = PDF417Utils.readCode(documentFront);
        if (pdf417RawText == null) {
            pdf417RawText = PDF417Utils.readCode(documentBack);
        }

        if (pdf417RawText != null) {
            Map<String, Object> documentInformation = PDF417Utils.parseCode(pdf417RawText);
            if (documentInformation != null && !documentInformation.isEmpty()){
                response = Data.object().set(TYPE_PROPERTY_NAME, PDF417_TYPE).set(RAW_PROPERTY_NAME, pdf417RawText).set(INFORMATION_PROPERTY_NAME, documentInformation);
            }
        }

        if (response == null) {
            String mrzRawText = MRZUtils.readCode(documentBack);
            if (mrzRawText == null) {
                mrzRawText = MRZUtils.readCode(documentFront);
            }

            if (mrzRawText != null) {
                Map<String, Object> documentInformation = MRZUtils.parseCode(mrzRawText);
                if (documentInformation != null && !documentInformation.isEmpty()){
                    response = Data.object().set(TYPE_PROPERTY_NAME, MRZ_TYPE).set(RAW_PROPERTY_NAME, mrzRawText).set(INFORMATION_PROPERTY_NAME, documentInformation);
                }
            }
        }

        if (response == null) {
            throw new ResponseException("Document data could not be read");
        }
        return response;
    }

    @Post("scan_barcode_data")
    public DataObject scanBarcode (@Body byte[] imageBytes) {
        DataObject response = null;
        String pdf417RawText = PDF417Utils.readCode(imageBytes);
        if (pdf417RawText != null) {
            Map<String, Object> documentInformation = PDF417Utils.parseCode(pdf417RawText);
            if (documentInformation != null && !documentInformation.isEmpty()){
                response = Data.object().set(RAW_PROPERTY_NAME, pdf417RawText).set(INFORMATION_PROPERTY_NAME, documentInformation);
            }
        }
        if (response == null) {
            throw new ResponseException("Barcode data could not be read");
        }
        return response;
    }

    @Post("scan_mrz_data")
    public DataObject scanMRZ (@Body byte[] imageBytes) {
        DataObject response = null;
        String mrzRawText = MRZUtils.readCode(imageBytes);
        if (mrzRawText != null) {
            Map<String, Object> documentInformation = MRZUtils.parseCode(mrzRawText);
            if (documentInformation != null && !documentInformation.isEmpty()){
                response = Data.object().set(RAW_PROPERTY_NAME, mrzRawText).set(INFORMATION_PROPERTY_NAME, documentInformation);
            }
        }
        if (response == null) {
            throw new ResponseException("MRZ data could not be read");
        }
        return response;
    }

    /*public static void main(String[] args) throws Exception {
        OpenCVUtils.initializeLibrary();
        String livenessFolder = "src/test/resources/liveness/liveness1/";
        byte[] imageBytes = FileUtils.readFileToByteArray(new File(livenessFolder + "image.jpeg"));
        byte[] zoomedImageBytes = FileUtils.readFileToByteArray(new File(livenessFolder + "zoomedImage.jpeg"));
        ApiController controller = new ApiController();
        System.out.println(controller.checkLiveness3d(imageBytes, zoomedImageBytes));
    }*/
}
