package com.biometrics;

import com.biometrics.utils.OpenCVUtils;
import org.apache.commons.io.FileUtils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.File;

import static org.opencv.core.CvType.CV_8U;

public class TestMain {

    public static void main(String[] args) throws Exception {
        OpenCVUtils.initializeLibrary();
        String livenessFolder = "src/test/resources/liveness/fake/test18/";
        byte[] imageBytes = FileUtils.readFileToByteArray(new File(livenessFolder + "image.jpeg"));
        processImage(imageBytes);
    }

    public static void processImage(byte[] bytes) {
        Mat image = OpenCVUtils.getImage(bytes);
        OpenCVUtils.display(image, "Original Image");
        int rows = image.rows();
        int cols = image.cols();
        double[] pixelValues;
        double blueIntensity, greenIntensity, redIntensity, s1, s2, intensity, saturation, maxIntensity = 0, maxSaturation = 0;
        Mat intensityImage = Mat.zeros(image.size(), CV_8U);
        Mat saturationImage = Mat.zeros(image.size(), CV_8U);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                pixelValues = image.get(row, col);
                blueIntensity = pixelValues[0];
                greenIntensity = pixelValues[1];
                redIntensity = pixelValues[2];
                s1 = blueIntensity + redIntensity;
                s2 = 2.0 * greenIntensity;
                intensity = (blueIntensity + greenIntensity + redIntensity) / 3.0;
                if (s1 >= s2) {
                    saturation = 1.5 * (redIntensity - intensity);
                } else {
                    saturation = 1.5 * (intensity - blueIntensity);
                }
                if (intensity > maxIntensity) {
                    maxIntensity = intensity;
                }
                if (saturation > maxSaturation) {
                    maxSaturation = saturation;
                }
                intensityImage.put(row, col, intensity);
                saturationImage.put(row, col, saturation);
            }
        }
        double intensityThreshold = maxIntensity * 0.5;
        double saturationThreshold = maxSaturation * 0.333;
        Mat specularImage = Mat.zeros(image.size(), CV_8U);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                intensity = intensityImage.get(row, col)[0];
                saturation = saturationImage.get(row, col)[0];
                if (intensity >= intensityThreshold && saturation <= saturationThreshold) {
                    specularImage.put(row, col, 255);
                }
            }
        }
        OpenCVUtils.display(intensityImage, "Intensity Image");
        OpenCVUtils.display(saturationImage, "Saturation Image");
        OpenCVUtils.display(specularImage, "Specular Image");
    }

    public static void processImage2(byte[] bytes) {
        Mat image = OpenCVUtils.getImage(bytes);
        OpenCVUtils.display(image, "Original Image");
        Mat hsvImage = new Mat();
        Imgproc.cvtColor(image, hsvImage, Imgproc.COLOR_BGR2HSV);
        int rows = image.rows();
        int cols = image.cols();
        double[] pixelValues;
        double intensity, saturation;
        Mat specularImage = Mat.zeros(image.size(), CV_8U);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                pixelValues = hsvImage.get(row, col);
                intensity = pixelValues[2];
                saturation = pixelValues[1];

                if (intensity > 150 && saturation < 20) {
                    specularImage.put(row, col, 255);
                }
            }
        }
        OpenCVUtils.display(specularImage, "Specular Image");
    }
}
