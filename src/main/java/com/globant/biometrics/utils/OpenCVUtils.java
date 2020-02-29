package com.globant.biometrics.utils;

import com.globant.biometrics.models.BoundingBox;
import com.globant.biometrics.models.Face;
import com.globant.biometrics.models.FaceProfile;
import org.opencv.core.Point;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public final class OpenCVUtils {

    private static final String FACE_CLASSIFIER = "face";
    private static final String PROFILE_FACE_CLASSIFIER = "profile-face";
    private static final String EYE_CLASSIFIER = "eye";
    private static final String EYE_PAIR_CLASSIFIER = "eye-pair";
    private static final String LEFT_EAR_CLASSIFIER = "left-ear";
    private static final String RIGHT_EYE_CLASSIFIER = "right-ear";
    private static final String NOSE_CLASSIFIER = "nose";
    private static final String MOUTH_CLASSIFIER = "mouth";

    private static final String CASCADES_FOLDER_NAME = "cascades";

    private static final String[] ALL_CLASSIFIERS = {
            FACE_CLASSIFIER,
            PROFILE_FACE_CLASSIFIER,
            EYE_CLASSIFIER,
            EYE_PAIR_CLASSIFIER,
            LEFT_EAR_CLASSIFIER,
            RIGHT_EYE_CLASSIFIER,
            NOSE_CLASSIFIER,
            MOUTH_CLASSIFIER
    };

    private static Map<String, CascadeClassifier> classifiers = new HashMap<>();

    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        File cascadesTempDirectory = new File(CASCADES_FOLDER_NAME);
        cascadesTempDirectory.mkdir();

        for (String classifier : ALL_CLASSIFIERS) {
            InputStream inputStream = null;
            OutputStream outputStream = null;
            try {
                inputStream = OpenCVUtils.class.getClassLoader().getResourceAsStream("cascades" + File.separator + classifier + ".xml");
                File cascadeFile = new File(cascadesTempDirectory, classifier + ".xml");
                outputStream = new FileOutputStream(cascadeFile);
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                classifiers.put(classifier, new CascadeClassifier(cascadeFile.getAbsolutePath()));
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
            finally {
                try { inputStream.close(); } catch (Exception ex) {}
                try { outputStream.close(); } catch (Exception ex) {}
            }
        }
        File[] files = cascadesTempDirectory.listFiles();
        if (files != null) {
            for(File file: files) {
                file.delete();
            }
        }
        cascadesTempDirectory.delete();
    }

    public static Face detectFace (byte[] imageBytes) throws Exception {
        return detectFace(getImageMat(imageBytes));
    }

    private static Mat getImageMat(byte[] imageBytes) throws Exception {
        return Imgcodecs.imdecode(new MatOfByte(imageBytes), Imgcodecs.IMREAD_UNCHANGED);
    }

    private static byte[] getImageBytes(Mat image) {
        MatOfByte bytesMat = new MatOfByte();
        Imgcodecs.imencode(".jpg", image, bytesMat);
        return bytesMat.toArray();
    }

    private static Mat flipImageMat(Mat image) {
        Mat flippedImage = new Mat();
        Core.flip(image, flippedImage, 1);
        return flippedImage;
    }

    private static Rect[] detectFeatures (Mat image, String classifierName) {
        MatOfRect features = new MatOfRect();
        classifiers.get(classifierName).detectMultiScale(image, features);
        return features.toArray();
    }

    private static Rect detectBiggestFeature(Mat image, String classifierName) {
        Rect[] features = detectFeatures(image, classifierName);
        Rect biggestFeature = null;
        double biggestFeatureArea = 0.0;
        for (Rect feature : features) {
            if (biggestFeature == null || feature.area() > biggestFeatureArea) {
                biggestFeature = feature;
                biggestFeatureArea = biggestFeature.area();
            }
        }
        return biggestFeature;
    }

    private static void drawFeatures (Mat image, Rect[] features, Color color) {
        for (Rect feature : features) {
            drawFeature(image, feature, color);
        }
    }

    private static void drawFeature (Mat image, Rect feature, Color color) {
        Imgproc.rectangle(image, new Point(feature.x, feature.y), new Point(feature.x + feature.width, feature.y + feature.height), new Scalar(color.getBlue(), color.getGreen(), color.getRed()),3);
    }

    private static boolean featureContains(Rect feature, Rect innerFeature) {
        return feature.contains(innerFeature.tl()) && feature.contains(innerFeature.br());
    }

    private static boolean featureContainsAny(Rect feature, Rect[] innerFeatures) {
        boolean contains = false;
        for (Rect innerFeature : innerFeatures) {
            if (featureContains(feature, innerFeature)) {
                contains = true;
                break;
            }
        }
        return contains;
    }

    private static Face detectFace (Mat image) {
        Face face = null;
        Rect faceRect = detectBiggestFeature(image, FACE_CLASSIFIER);
        Rect eyePairRect = detectBiggestFeature(image, EYE_PAIR_CLASSIFIER);

        if (faceRect != null && eyePairRect != null && featureContains(faceRect, eyePairRect)) {
            face = new Face(createBoundingBoxForRect(faceRect, image), FaceProfile.FRONT);
        } else {
            Rect[] eyeRects = detectFeatures(image, EYE_CLASSIFIER);
            Rect rightFaceRect = detectBiggestFeature(image, PROFILE_FACE_CLASSIFIER);
            if (rightFaceRect != null && eyePairRect == null && featureContainsAny(rightFaceRect, eyeRects)) {
                face = new Face(createBoundingBoxForRect(rightFaceRect, image), FaceProfile.RIGHT);
            } else {
                Rect leftFaceRect = detectBiggestFeature(flipImageMat(image), PROFILE_FACE_CLASSIFIER);
                if (leftFaceRect != null) {
                    leftFaceRect.x = image.width() - leftFaceRect.x - leftFaceRect.width;
                }
                if (leftFaceRect != null && eyePairRect == null && featureContainsAny(leftFaceRect, eyeRects)) {
                    face = new Face(createBoundingBoxForRect(leftFaceRect, image), FaceProfile.LEFT);
                } else {
                    if (faceRect != null) {
                        face = new Face(createBoundingBoxForRect(faceRect, image), FaceProfile.UNDEFINED);
                    } else if (rightFaceRect != null) {
                        face = new Face(createBoundingBoxForRect(rightFaceRect, image), FaceProfile.UNDEFINED);
                    } else if (leftFaceRect != null) {
                        face = new Face(createBoundingBoxForRect(leftFaceRect, image), FaceProfile.UNDEFINED);
                    }
                }
            }
        }
        return face;
    }

    private static BoundingBox createBoundingBoxForRect(Rect rect, Mat image) {
        return new BoundingBox(image.width() - rect.x - rect.width, rect.y, rect.width, rect.height);
    }
}
