package com.globant.biometrics.utils;

import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.AmazonRekognitionClientBuilder;
import com.amazonaws.services.rekognition.model.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class AmazonUtils {

    private static final AmazonRekognition rekognitionClient;

    static {
        rekognitionClient = AmazonRekognitionClientBuilder.defaultClient();
    }

    public static List<String> getImageLabels(byte[] imageBytes) {
        return getImageLabels(imageBytes, 10);
    }

    public static List<String> getImageLabels(byte[] imageBytes, int maxLabels) {
        DetectLabelsRequest request = new DetectLabelsRequest().withImage(getImageFromBytes(imageBytes)).withMaxLabels(maxLabels).withMinConfidence(75F);
        DetectLabelsResult result = rekognitionClient.detectLabels(request);
        List<Label> amazonLabels = result.getLabels();
        List<String> labels = null;
        if (amazonLabels != null && !amazonLabels.isEmpty()) {
            labels = new ArrayList<>();
            for (Label amazonLabel : amazonLabels) {
                labels.add(amazonLabel.getName());
            }
        }
        return labels;
    }

    public static List<String> getImageTexts(byte[] imageBytes) {
        Image image = getImageFromBytes(imageBytes);
        DetectTextRequest request = new DetectTextRequest().withImage(image);
        DetectTextResult result = rekognitionClient.detectText(request);
        List<TextDetection> textDetections = result.getTextDetections();
        List<String> texts = null;
        if (textDetections != null && !textDetections.isEmpty()) {
            texts = new ArrayList<>();
            for (TextDetection text: textDetections) {
                texts.add(text.getDetectedText());
            }
        }
        return texts;
    }

    public static float compareFacesInImages (byte[] image1Bytes, byte[] image2Bytes) {
        Image image1 = getImageFromBytes(image1Bytes);
        Image image2 = getImageFromBytes(image2Bytes);
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

    public static boolean isImageOfType(byte[] imageBytes, String type) {
        List<String> labels = getImageLabels(imageBytes);
        return labels != null && labels.contains(type);
    }

    public static Image getImageFromBytes(byte[] imageBytes) {
        return new Image().withBytes(ByteBuffer.wrap(imageBytes));
    }
}
