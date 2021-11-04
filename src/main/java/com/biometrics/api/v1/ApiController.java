package com.biometrics.api.v1;

import com.biometrics.ResponseException;
import com.biometrics.resources.LivenessResource;
import com.biometrics.utils.LivenessUtils;
import com.biometrics.utils.MRZUtils;
import com.biometrics.utils.OpenCVUtils;
import com.biometrics.utils.PDF417Utils;
import org.neogroup.warp.controllers.ControllerComponent;
import org.neogroup.warp.controllers.routing.Body;
import org.neogroup.warp.controllers.routing.Parameter;
import org.neogroup.warp.controllers.routing.Post;
import org.neogroup.warp.data.Data;
import org.neogroup.warp.data.DataObject;
import org.neogroup.warp.resources.Resources;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.objdetect.CascadeClassifier;

import java.util.Map;

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

    private static final String MRZ_TYPE = "MRZ";
    private static final String PDF417_TYPE = "PDF417";

    private static final String TYPE_PROPERTY_NAME = "type";
    private static final String RAW_PROPERTY_NAME = "raw";
    private static final String INFORMATION_PROPERTY_NAME = "information";
    private static final String LIVENESS_PROPERTY_NAME = "liveness";
    private static final String REASON_PROPERTY_NAME = "reason";

    private final CascadeClassifier faceClassfier;

    public ApiController() {
        faceClassfier = OpenCVUtils.getClassfierFromResource("cascades/face.xml");
    }

    @Post("check_liveness_3d")
    public DataObject checkLiveness3d(@Parameter("picture") byte[] imageBytes, @Parameter("zoomedPicture") byte[] zoomedImageBytes) {

        /*try {
            FileUtils.writeByteArrayToFile(new File("src/test/resources/liveness/test/image.jpeg"), imageBytes);
            FileUtils.writeByteArrayToFile(new File("src/test/resources/liveness/test/zoomedImage.jpeg"), zoomedImageBytes);
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
        boolean success = livenessStatusCode == LIVENESS_OK_STATUS_CODE;
        if (success) {
            response.set(LIVENESS_PROPERTY_NAME, true);
        } else {
            response.set(LIVENESS_PROPERTY_NAME, false);
            response.set(REASON_PROPERTY_NAME, livenessStatusCode);
        }

        try {
            Resources.get(LivenessResource.NAME)
                .set(LivenessResource.Fields.FACE_IMAGE, imageBytes)
                .set(LivenessResource.Fields.ZOOMED_FACE_IMAGE, zoomedImageBytes)
                .set(LivenessResource.Fields.SUCCESS, success)
                .set(LivenessResource.Fields.STATUS, livenessStatusCode)
                .insert();
        } catch (Exception ex) {}
        return response;
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
        String livenessFolder = "src/test/resources/liveness/real/test1/";
        byte[] imageBytes = FileUtils.readFileToByteArray(new File(livenessFolder + "image.jpeg"));
        byte[] zoomedImageBytes = FileUtils.readFileToByteArray(new File(livenessFolder + "zoomedImage.jpeg"));
        ApiController controller = new ApiController();
        System.out.println(controller.checkLiveness3d(imageBytes, zoomedImageBytes));
    }*/
}
