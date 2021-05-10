package com.biometrics.utils;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.*;

import static org.opencv.core.CvType.CV_8U;

public class LivenessUtils {

    public static double analyseMoirePatternDisturbances(Mat image) {
        // Convertir la imagen a escala de grises
        Mat grayImage = new Mat();
        OpenCVUtils.grayScale(image, grayImage);

        // Escalo la imagen a 400x400 para homogenizar las imagenes a una medida estandar
        OpenCVUtils.resize(grayImage, grayImage, 400, 400, 400, 400);

        // Aplico una ventana de hanning para evitar problemas en los bordes de la imagen
        OpenCVUtils.hanningWindow(grayImage, grayImage);

        // Generación de espectros de magnitud
        Mat magnitudeSpectrum = OpenCVUtils.getMagnitudeSpectrum(grayImage);

        // Obtener todos los valores que hay en cada franja de distancia
        int rows = grayImage.rows();
        int cols = grayImage.cols();
        int midCols = (int)(cols / 2.0);
        int midRows = (int)(rows / 2.0);
        Map<Integer, List<Double>> pixelValuesByDistance = new HashMap<>();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < midCols; col++) {
                int pixelDistance = (int)Math.sqrt(Math.pow(midCols - col, 2) + Math.pow(midRows - row, 2));
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

        // Escalo la imagen a 400x400 para homogenizar las imagenes a una medida estandar
        OpenCVUtils.resize(grayImage, grayImage, 400, 400, 400, 400);

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
            int rows = grayImage.rows();
            int cols = grayImage.cols();
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
