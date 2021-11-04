package com.biometrics.controllers;

import com.biometrics.resources.LivenessResource;
import com.biometrics.utils.LivenessUtils;
import com.biometrics.utils.OpenCVUtils;
import org.neogroup.warp.controllers.ControllerComponent;
import org.neogroup.warp.controllers.routing.Parameter;
import org.neogroup.warp.controllers.routing.Post;
import org.neogroup.warp.data.Data;
import org.neogroup.warp.data.DataObject;
import org.neogroup.warp.resources.Resources;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.objdetect.CascadeClassifier;

@ControllerComponent("api/liveness")
public class LivenessController {

    private static final int LIVENESS_OK_STATUS_CODE = 0;
    private static final int LIVENESS_FACE_NOT_FOUND_STATUS_CODE = 1;
    private static final int LIVENESS_FACE_NOT_ZOOMED_STATUS_CODE = 2;
    private static final int LIVENESS_BLURRINESS_CHECK_FAILED_STATUS_CODE = 3;
    private static final int LIVENESS_IMAGE_QUALITY_CHECK_FAILED_STATUS_CODE = 4;
    private static final int LIVENESS_IMAGE_BRIGHTNESS_CHECK_FAILED_STATUS_CODE = 5;
    private static final int LIVENESS_IMAGE_HISTOGRAM_CHECK_FAILED_STATUS_CODE = 6;
    private static final int LIVENESS_IMAGE_MOIRE_PATTERN_CHECK_FAILED_STATUS_CODE = 7;

    private static final String LIVENESS_PROPERTY_NAME = "liveness";
    private static final String STATUS_PROPERTY_NAME = "status";

    private final CascadeClassifier faceClassfier;

    public LivenessController() {
        faceClassfier = OpenCVUtils.getClassfierFromResource("cascades/face.xml");
    }

    @Post("verify_liveness")
    public DataObject verifyLiveness(@Parameter("picture") byte[] imageBytes, @Parameter("zoomedPicture") byte[] zoomedImageBytes) {

        int status = LIVENESS_OK_STATUS_CODE;
        Mat image = OpenCVUtils.getImage(imageBytes);
        Mat zoomedImage = OpenCVUtils.getImage(zoomedImageBytes);

        // Validación de que existen rostros en las 2 imagenes
        Rect faceRect = OpenCVUtils.detectBiggestFeatureRect(image, faceClassfier);
        Rect zoomedFaceRect = OpenCVUtils.detectBiggestFeatureRect(zoomedImage, faceClassfier);
        if (faceRect == null || zoomedFaceRect == null) {
            status = LIVENESS_FACE_NOT_FOUND_STATUS_CODE;
        }

        if (status == LIVENESS_OK_STATUS_CODE) {
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
                status = LIVENESS_FACE_NOT_ZOOMED_STATUS_CODE;
            }

            // Validación de blurriness de los rostros
            if (status == LIVENESS_OK_STATUS_CODE) {
                if (!LivenessUtils.analyseNormalizedImagesBlurriness(normalizedFaceImage, normalizedZoomedFaceImage)) {
                    status = LIVENESS_BLURRINESS_CHECK_FAILED_STATUS_CODE;
                }
            }

            // Validación de calidad de las imagenes
            if (status == LIVENESS_OK_STATUS_CODE) {
                if (!LivenessUtils.analyseImageQuality(image) || !LivenessUtils.analyseImageQuality(zoomedImage)) {
                    status = LIVENESS_IMAGE_QUALITY_CHECK_FAILED_STATUS_CODE;
                }
            }

            // Validación del grado de brillo de las imagenes
            if (status == LIVENESS_OK_STATUS_CODE) {
                if (!LivenessUtils.analyseImageBrightness(image) || !LivenessUtils.analyseImageBrightness(zoomedImage)) {
                    status = LIVENESS_IMAGE_BRIGHTNESS_CHECK_FAILED_STATUS_CODE;
                }
            }

            // Validación de comparación de histogramas
            if (status == LIVENESS_OK_STATUS_CODE) {
                if (!LivenessUtils.analyseImageHistograms(image, zoomedImage)) {
                    status = LIVENESS_IMAGE_HISTOGRAM_CHECK_FAILED_STATUS_CODE;
                }
            }

            // Validación de los patrones de Moire
            if (status == LIVENESS_OK_STATUS_CODE) {
                if (!LivenessUtils.analyseImageMoirePatternDisturbances(faceImage) || !LivenessUtils.analyseImageMoirePatternDisturbances(zoomedFaceImage)) {
                    status = LIVENESS_IMAGE_MOIRE_PATTERN_CHECK_FAILED_STATUS_CODE;
                }
            }
        }

        DataObject response = Data.object();
        boolean success = status == LIVENESS_OK_STATUS_CODE;
        if (success) {
            response.set(LIVENESS_PROPERTY_NAME, true);
        } else {
            response.set(LIVENESS_PROPERTY_NAME, false);
            response.set(STATUS_PROPERTY_NAME, status);
        }

        try {
            Resources.get(LivenessResource.NAME)
                    .set(LivenessResource.Fields.FACE_IMAGE, imageBytes)
                    .set(LivenessResource.Fields.ZOOMED_FACE_IMAGE, zoomedImageBytes)
                    .set(LivenessResource.Fields.SUCCESS, success)
                    .set(LivenessResource.Fields.STATUS, status)
                    .insert();
        } catch (Exception ex) {}
        return response;
    }
}
