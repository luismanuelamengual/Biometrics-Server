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
import org.opencv.features2d.Features2d;
import org.opencv.features2d.FlannBasedMatcher;
import org.opencv.features2d.SIFT;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.util.*;

@ControllerComponent("v1")
public class ApiController {

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

    @Post("check_liveness_3d")
    public DataObject checkLiveness3d(@Parameter("picture") byte[] imageBytes, @Parameter("zoomedPicture") byte[] zoomedImageBytes) {
        int livenessStatusCode = 0;
        int[] histSize = { 50, 60 };
        float[] ranges = { 0, 180, 0, 256 };
        int[] channels = { 0, 1 };
        Mat image = OpenCVUtils.getImage(imageBytes);
        Mat zoomedImage = OpenCVUtils.getImage(zoomedImageBytes);
        Rect faceRect = OpenCVUtils.detectBiggestFeatureRect(image, faceClassfier);
        Rect zoomedFaceRect = OpenCVUtils.detectBiggestFeatureRect(zoomedImage, faceClassfier);
        if (faceRect == null || zoomedFaceRect == null) {
            livenessStatusCode = 1;
        }
        if (livenessStatusCode == 0) {
            if (faceRect.area() >= zoomedFaceRect.area()) {
                livenessStatusCode = 2;
            }
        }
        if (livenessStatusCode == 0) {
            Mat imageHist = new Mat();
            Imgproc.calcHist(Arrays.asList(image), new MatOfInt(channels), new Mat(), imageHist, new MatOfInt(histSize), new MatOfFloat(ranges), false);
            Mat zoomedImageHist = new Mat();
            Imgproc.calcHist(Arrays.asList(zoomedImage), new MatOfInt(channels), new Mat(), zoomedImageHist, new MatOfInt(histSize), new MatOfFloat(ranges), false);
            double histSimilarity = Imgproc.compareHist(imageHist, zoomedImageHist, Imgproc.HISTCMP_CORREL);
            if (histSimilarity < 0.75) {
                livenessStatusCode = 3;
            }
        }
        if (livenessStatusCode == 0) {
            SIFT sift = SIFT.create();
            MatOfKeyPoint keypoints1 = new MatOfKeyPoint();
            MatOfKeyPoint keypoints2 = new MatOfKeyPoint();
            Mat descriptors1 = new Mat();
            Mat descriptors2 = new Mat();
            sift.detectAndCompute(image, new Mat(), keypoints1, descriptors1);
            sift.detectAndCompute(zoomedImage, new Mat(), keypoints2, descriptors2);
            FlannBasedMatcher matcher = new FlannBasedMatcher();
            List<MatOfDMatch> matches = new ArrayList();
            matcher.knnMatch(descriptors1, descriptors2, matches,  2);
            LinkedList<DMatch> bestMatchesList = new LinkedList<>();
            matches.forEach(match -> {
                DMatch[] dmatcharray = match.toArray();
                DMatch m1 = dmatcharray[0];
                DMatch m2 = dmatcharray[1];
                if (m1.distance <= m2.distance * 0.7) {
                    bestMatchesList.addLast(m1);
                }
            });
            if (bestMatchesList.size() < 15) {
                livenessStatusCode = 4;
            } else if (bestMatchesList.size() >= 50) {
                livenessStatusCode = 5;
            }

            /*System.out.println (bestMatchesList.size());
            Mat matchImage = new Mat();
            MatOfDMatch bestMatches = new MatOfDMatch();
            bestMatches.fromList(bestMatchesList);
            Features2d.drawMatches(image, keypoints1, zoomedImage, keypoints2, bestMatches, matchImage);
            OpenCVUtils.display(matchImage);*/
        }

        DataObject response = Data.object();
        if (livenessStatusCode == 0) {
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
}
