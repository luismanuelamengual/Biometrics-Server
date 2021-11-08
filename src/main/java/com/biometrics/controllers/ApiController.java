package com.biometrics.controllers;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.impl.NullClaim;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.biometrics.Authentication;
import com.biometrics.exceptions.ResponseException;
import com.biometrics.resources.LivenessResource;
import com.biometrics.utils.LivenessUtils;
import com.biometrics.utils.MRZUtils;
import com.biometrics.utils.OpenCVUtils;
import com.biometrics.utils.PDF417Utils;
import org.neogroup.warp.controllers.ControllerComponent;
import org.neogroup.warp.controllers.routing.Before;
import org.neogroup.warp.controllers.routing.Body;
import org.neogroup.warp.controllers.routing.Param;
import org.neogroup.warp.controllers.routing.Post;
import org.neogroup.warp.data.Data;
import org.neogroup.warp.data.DataObject;
import org.neogroup.warp.http.Header;
import org.neogroup.warp.http.Request;
import org.neogroup.warp.http.Response;
import org.neogroup.warp.http.StatusCode;
import org.neogroup.warp.resources.Resources;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.objdetect.CascadeClassifier;

import java.util.Map;

@ControllerComponent("api")
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
    private static final String STATUS_PROPERTY_NAME = "status";
    private static final String CLIENT_ID_PARAMETER_NAME = "client";
    private static final String IP_PARAMETER_NAME = "ip";
    private static final String TIMESTAMP_PARAMETER_NAME = "timestamp";

    private static final char IP_SEPARATOR = ',';
    private static final String X_FORWARDED_FOR_HEADER_NAME = "X-Forwarded-For";
    private static final String AUTHORIZATION_BEARER = "Bearer";

    private final CascadeClassifier faceClassfier;

    public ApiController() {
        faceClassfier = OpenCVUtils.getClassfierFromResource("cascades/face.xml");
    }

    @Before("*")
    public void checkSession(Request request, Response response) {
        String authorizationHeader = request.getHeader(Header.AUTHORIZATION);
        if (authorizationHeader == null) {
            response.setStatus(StatusCode.UNAUTHORIZED);
            throw new ResponseException("Missing authorization header");
        }
        String[] authorizationTokens = authorizationHeader.split(" ");
        if (!authorizationTokens[0].equals(AUTHORIZATION_BEARER)) {
            response.setStatus(StatusCode.UNAUTHORIZED);
            throw new ResponseException("Authorization header is expecting a JWT token");
        }
        if (authorizationTokens.length < 2) {
            response.setStatus(StatusCode.UNAUTHORIZED);
            throw new ResponseException("Invalid authorization header");
        }
        String token = authorizationTokens[1];
        try {
            DecodedJWT verifiedToken = Authentication.decodeToken(token);
            String ip = getClientIp(request);
            request.set(CLIENT_ID_PARAMETER_NAME, verifiedToken.getClaim(Authentication.CLIENT_ID_CLAIM_NAME).asInt());
            request.set(IP_PARAMETER_NAME, ip);
            request.set(TIMESTAMP_PARAMETER_NAME, System.currentTimeMillis());

            Claim allowedIpsClaim = verifiedToken.getClaim(Authentication.ALLOWED_IPS_CLAIM_NAME);
            if (!(allowedIpsClaim instanceof NullClaim)) {
                String[] allowedIps = allowedIpsClaim.asArray(String.class);
                boolean allowed = false;
                for (String allowedIp : allowedIps) {
                    if (allowedIp.equals(ip)) {
                        allowed = true;
                        break;
                    }
                }
                if (!allowed) {
                    throw new ResponseException("Ip \"" + ip + "\" is not allowed !!");
                }
            }
        } catch (JWTVerificationException verificationException) {
            response.setStatus(StatusCode.UNAUTHORIZED);
            throw new ResponseException("Invalid authorization token");
        }
    }

    @Post("verify_liveness")
    public DataObject verifyLiveness(@Param("picture") byte[] imageBytes, @Param("zoomedPicture") byte[] zoomedImageBytes) {

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
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return response;
    }

    @Post("scan_document_data")
    public DataObject scanDocument(@Param("documentFront") byte[] documentFront, @Param("documentBack") byte[] documentBack) {

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

    private String getClientIp(Request request) {
        String clientIp = request.getHeader(X_FORWARDED_FOR_HEADER_NAME);
        if (clientIp == null || clientIp.isEmpty()) {
            clientIp = request.getRemoteAddress();
        }
        int index = clientIp.indexOf(IP_SEPARATOR);
        if (index >= 0) {
            clientIp = clientIp.substring(0, index);
        }
        return clientIp;
    }
}
