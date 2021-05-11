package com.biometrics.utils;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.util.*;

import static org.opencv.core.CvType.CV_8U;
import static org.opencv.imgproc.Imgproc.GC_INIT_WITH_RECT;

public class LivenessUtils {

    public static Mat getForegroundImage(Mat image) {
        return getForegroundImage(image, new Mat());
    }

    public static Mat getForegroundImage(Mat image, Mat foregroundImageMask) {
        int imageWidth = image.width();
        int imageHeight = image.height();
        int x = (int)(imageWidth * 0.05);
        int y = (int)(imageHeight * 0.05);
        int width = imageWidth - (x * 2);
        int height = imageHeight - (y * 2);
        Rect rect = new Rect(x, y, width, height);
        return getForegroundImage(image, foregroundImageMask, rect);
    }

    public static Mat getForegroundImage(Mat image, Mat foregroundImageMask, Rect foregroundRect) {
        Mat bgModel = new Mat();
        Mat fgModel = new Mat();
        Mat source = new Mat(1, 1, CvType.CV_8U, new Scalar(3));
        Imgproc.grabCut(image, foregroundImageMask, foregroundRect, bgModel, fgModel,5, GC_INIT_WITH_RECT);
        Core.compare(foregroundImageMask, source, foregroundImageMask, Core.CMP_EQ);
        Mat foreground = new Mat(image.size(), CvType.CV_8UC3, new Scalar(255, 255, 255));
        image.copyTo(foreground, foregroundImageMask);
        return foreground;
    }

    public static double analyseMoirePatternDisturbances(Mat image) {
        // Convertir la imagen a escala de grises
        Mat grayImage = new Mat();
        OpenCVUtils.grayScale(image, grayImage);

        // Aplico una ventana de hanning para evitar problemas en los bordes de la imagen
        OpenCVUtils.hanningWindow(grayImage, grayImage);

        // Generación de espectros de magnitud
        Mat magnitudeSpectrum = OpenCVUtils.getMagnitudeSpectrum(grayImage);

        // Obtener todos los valores que hay en cada franja de distancia
        int rows = magnitudeSpectrum.rows();
        int cols = magnitudeSpectrum.cols();
        int midCols = (int)(cols / 2.0);
        int midRows = (int)(rows / 2.0);
        Map<Integer, List<Double>> pixelValuesByDistance = new HashMap<>();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < midCols; col++) {
                int pixelDistance = (int) Math.sqrt(Math.pow(midCols - col, 2) + Math.pow(midRows - row, 2));
                double pixelValue = magnitudeSpectrum.get(row, col)[0];
                List<Double> pixelValuesAtDistance = pixelValuesByDistance.computeIfAbsent(pixelDistance, k -> new ArrayList<>());
                pixelValuesAtDistance.add(pixelValue);
            }
        }

        // Obtener la media y la desviación estandar de cada franja de distancia
        Map<Integer, Double> pixelsAverageByDistance = new HashMap<>();
        Map<Integer, Double> pixelsStandardDeviationByDistance = new HashMap<>();
        for (int distance : pixelValuesByDistance.keySet()) {
            List<Double> distanceValues = pixelValuesByDistance.get(distance);
            Collections.sort(distanceValues);
            double distanceValuesSum = distanceValues.stream().mapToDouble(Double::doubleValue).sum();
            int distanceValuesCount = distanceValues.size();
            double distanceValuesAverage = distanceValuesSum / distanceValuesCount;
            double distanceValuesDeviationSum = 0;
            for (double distanceValue : distanceValues) {
                distanceValuesDeviationSum += Math.pow(distanceValue - distanceValuesAverage, 2);
            }
            double distanceValuesStandardDeviation = Math.sqrt(distanceValuesDeviationSum / (double)distanceValuesCount);
            pixelsAverageByDistance.put(distance, distanceValuesAverage);
            pixelsStandardDeviationByDistance.put(distance, distanceValuesStandardDeviation);
        }

        // Pintado de pixels del spectro de alta frecuencia
        Mat highFrequencySpectrum = Mat.zeros(magnitudeSpectrum.size(), CV_8U);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < midCols; col++) {
                int pixelDistance = (int) Math.sqrt(Math.pow(midCols - col, 2) + Math.pow(midRows - row, 2));
                double pixelValue = magnitudeSpectrum.get(row, col)[0];
                double distanceAverage = pixelsAverageByDistance.get(pixelDistance);
                double distanceStandardDeviation = pixelsStandardDeviationByDistance.get(pixelDistance);
                double testPixelValue = distanceAverage;
                testPixelValue += distanceStandardDeviation * 1.0;
                if (pixelValue > testPixelValue) {
                    highFrequencySpectrum.put(row, col, 255);
                    highFrequencySpectrum.put(rows - row - 1, cols - col - 1, 255);
                }
            }
        }

        // Erosionar el espectro de alta frecuencia para eliminar pixels aislados (ruido)
        Imgproc.erode(highFrequencySpectrum, highFrequencySpectrum, new Mat(), new Point(-1, -1), 1);

        // Calcular el porcentaje de pixels encendidos en el espectro de alta frecuencia
        int totalPixels = cols * rows;
        int activatedPixels = Core.countNonZero(highFrequencySpectrum);
        return activatedPixels * 100.0 / totalPixels;
    }

    public static double analyseMoirePatternDisturbancesOnBandWidths(Mat image) {
        return analyseMoirePatternDisturbancesOnBandWidths(image, 2, 9, 0.1, 2.1, 0.2);
    }

    public static double analyseMoirePatternDisturbancesOnBandWidths(Mat image, double k, int kernelSize, double sigmaLow, double sigmaHigh, double sigmaDelta) {
        // Convertir la imagen a escala de grises
        Mat grayImage = new Mat();
        OpenCVUtils.grayScale(image, grayImage);

        // Iterar en diferentes bandas de frequencia para encontrar picos
        Mat dog1 = new Mat();
        Mat dog2 = new Mat();
        Mat dog = new Mat();
        Mat magnitudeSpectrum = new Mat();
        Size kernel = new Size(kernelSize,kernelSize);
        double disturbancesPercentage = 0;
        for (double sigma = sigmaLow; sigma <= sigmaHigh; sigma += sigmaDelta) {
            // Obtener una banda de frecuencia haciendo un diferencial de Gauss
            Imgproc.GaussianBlur(grayImage, dog1, kernel, sigma, sigma);
            Imgproc.GaussianBlur(grayImage, dog2, kernel, k * sigma, k * sigma);
            Core.subtract(dog2, dog1, dog);

            // Aplicar una ventana de hanning para evitar problemas en los bordes de la imagen
            OpenCVUtils.hanningWindow(dog, dog);

            // Obtener el espectro de magnitud de la banda de frecuencia
            magnitudeSpectrum = OpenCVUtils.getMagnitudeSpectrum(dog);

            // Obtener todos los valores que hay en cada franja de distancia
            int rows = magnitudeSpectrum.rows();
            int cols = magnitudeSpectrum.cols();
            int midCols = (int)(cols / 2.0);
            int midRows = (int)(rows / 2.0);
            Map<Integer, List<Double>> pixelValuesByDistance = new HashMap<>();
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < midCols; col++) {
                    int pixelDistance = (int)Math.sqrt(Math.pow(midCols - col, 2) + Math.pow(midRows - row, 2));
                    double pixelValue = magnitudeSpectrum.get(row, col)[0];
                    List<Double> pixelValuesAtDistance = pixelValuesByDistance.computeIfAbsent(pixelDistance, list -> new ArrayList<>());
                    pixelValuesAtDistance.add(pixelValue);
                }
            }

            // Obtener la media y la desviación estandar de cada franja de distancia
            Map<Integer, Double> pixelsAverageByDistance = new HashMap<>();
            Map<Integer, Double> pixelsStandardDeviationByDistance = new HashMap<>();
            for (int distance : pixelValuesByDistance.keySet()) {
                List<Double> distanceValues = pixelValuesByDistance.get(distance);
                Collections.sort(distanceValues);
                double distanceValuesSum = distanceValues.stream().mapToDouble(Double::doubleValue).sum();
                int distanceValuesCount = distanceValues.size();
                double distanceValuesAverage = distanceValuesSum / distanceValuesCount;
                double distanceValuesDeviationSum = 0;
                for (double distanceValue : distanceValues) {
                    distanceValuesDeviationSum += Math.pow(distanceValue - distanceValuesAverage, 2);
                }
                double distanceValuesStandardDeviation = Math.sqrt(distanceValuesDeviationSum / (double)distanceValuesCount);
                pixelsAverageByDistance.put(distance, distanceValuesAverage);
                pixelsStandardDeviationByDistance.put(distance, distanceValuesStandardDeviation);
            }

            // Pintado de pixels del spectro de alta frecuencia
            Mat highFrequencySpectrum = Mat.zeros(magnitudeSpectrum.size(), CV_8U);
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < midCols; col++) {
                    int pixelDistance = (int) Math.sqrt(Math.pow(midCols - col, 2) + Math.pow(midRows - row, 2));
                    double pixelValue = magnitudeSpectrum.get(row, col)[0];
                    double distanceAverage = pixelsAverageByDistance.get(pixelDistance);
                    double distanceStandardDeviation = pixelsStandardDeviationByDistance.get(pixelDistance);
                    double testPixelValue = distanceAverage;
                    testPixelValue += distanceStandardDeviation * 1.0;
                    if (pixelValue > testPixelValue) {
                        highFrequencySpectrum.put(row, col, 255);
                        highFrequencySpectrum.put(rows - row - 1, cols - col - 1, 255);
                    }
                }
            }

            // Erosionar el espectro de alta frecuencia para eliminar pixels aislados (ruido)
            Imgproc.erode(highFrequencySpectrum, highFrequencySpectrum, new Mat(), new Point(-1, -1), 1);

            // Calcular el porcentaje de pixels encendidos en el espectro de alta frecuencia
            int totalPixels = cols * rows;
            int activatedPixels = Core.countNonZero(highFrequencySpectrum);
            double highFrequencyPercentage = activatedPixels * 100.0 / totalPixels;
            if (highFrequencyPercentage > disturbancesPercentage) {
                disturbancesPercentage = highFrequencyPercentage;
            }
        }

        return disturbancesPercentage;
    }
}
