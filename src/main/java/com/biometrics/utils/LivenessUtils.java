package com.biometrics.utils;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.util.*;

import static org.opencv.core.CvType.CV_8U;
import static org.opencv.imgproc.Imgproc.GC_INIT_WITH_RECT;

public class LivenessUtils {

    private static int[] NORMALIZED_BINARY_PATTERN_OFFSETS;

    static {
        int offset = 0;
        NORMALIZED_BINARY_PATTERN_OFFSETS = new int[256];
        for (int number = 0; number <= 255; number++) {
            boolean lastValueActive = false;
            int valueTransitions = 0;
            for (int i = 7; i >= 0; i--) {
                boolean valueActive = (number & 1 << i) != 0;
                if (i < 7 && valueActive != lastValueActive) {
                    valueTransitions++;
                }
                lastValueActive = valueActive;
            }
            boolean isNormal = valueTransitions <= 2;
            NORMALIZED_BINARY_PATTERN_OFFSETS[number] = isNormal ? offset++ :  -1;
        }
    }

    public static double analyseImageQuality(Mat image) {
        Mat hsvImage = new Mat();
        Imgproc.cvtColor(image, hsvImage, Imgproc.COLOR_BGR2HSV);
        double[] saturationValues = OpenCVUtils.getHistogram(hsvImage, 1, 256);
        double[] valueValues = OpenCVUtils.getHistogram(hsvImage, 2, 256);
        int activeValuesCounter = 0;
        for (int i = 0; i < 256; i++) {
            if (valueValues[i] > 0) {
                activeValuesCounter++;
            }
        }
        double valueQuality = activeValuesCounter / 256.0;


        double maxSaturationValue = Arrays.stream(saturationValues).max().getAsDouble();
        double maxSaturationThreshold = maxSaturationValue * 0.4;
        int normalSaturationCounter = 0;
        for (int i = 0; i < 256; i++) {
            if (saturationValues[i] < maxSaturationThreshold) {
                normalSaturationCounter++;
            }
        }
        double saturationQuality = normalSaturationCounter / 256.0;
        return valueQuality * saturationQuality;
    }

    public static double analyseImageMoirePatternDisturbances(Mat image) {
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

    public static double analyseImageMoirePatternDisturbancesOnBandWidths(Mat image) {
        return analyseImageMoirePatternDisturbancesOnBandWidths(image, 2, 9, 0.1, 2.1, 0.2);
    }

    public static double analyseImageMoirePatternDisturbancesOnBandWidths(Mat image, double k, int kernelSize, double sigmaLow, double sigmaHigh, double sigmaDelta) {
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

    public static boolean analyseNormalizedImagesBlurriness(Mat image, Mat image2) {
        double blurriness1 = OpenCVUtils.getBlurriness(image);
        double blurriness2 = OpenCVUtils.getBlurriness(image2);
        double blurrinessCoeficient = blurriness2 / blurriness1;
        return blurrinessCoeficient >= 1.0 && blurrinessCoeficient <= 3.0;
    }

    private static Mat getForegroundImage(Mat image) {
        return getForegroundImage(image, new Mat());
    }

    private static Mat getForegroundImage(Mat image, Mat foregroundImageMask) {
        int imageWidth = image.width();
        int imageHeight = image.height();
        int x = (int)(imageWidth * 0.05);
        int y = (int)(imageHeight * 0.05);
        int width = imageWidth - (x * 2);
        int height = imageHeight - (y * 2);
        Rect rect = new Rect(x, y, width, height);
        return getForegroundImage(image, foregroundImageMask, rect);
    }

    private static Mat getForegroundImage(Mat image, Mat foregroundImageMask, Rect foregroundRect) {
        Mat bgModel = new Mat();
        Mat fgModel = new Mat();
        Mat source = new Mat(1, 1, CvType.CV_8U, new Scalar(3));
        Imgproc.grabCut(image, foregroundImageMask, foregroundRect, bgModel, fgModel,5, GC_INIT_WITH_RECT);
        Core.compare(foregroundImageMask, source, foregroundImageMask, Core.CMP_EQ);
        Mat foreground = new Mat(image.size(), CvType.CV_8UC3, new Scalar(255, 255, 255));
        image.copyTo(foreground, foregroundImageMask);
        return foreground;
    }

    private static double[] getHOGDescriptor(Mat image, int angleAgrupationSize, Size regionSize) {
        int rows = image.rows();
        int cols = image.cols();
        if (regionSize == null) {
            regionSize = new Size(cols, rows);
        }
        if (angleAgrupationSize < 1) {
            angleAgrupationSize = 1;
        }
        int regionElementsSize = (int)(180.0 / (double)angleAgrupationSize);
        int regionsCountX = (int)Math.ceil((double)cols / regionSize.width);
        int regionsCountY = (int)Math.ceil((double)rows / regionSize.height);
        int regionsCount = regionsCountX * regionsCountY;
        int descriptorSize = regionsCount * regionElementsSize;
        double[] descriptorData = new double[descriptorSize];

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                double xDifference = 0;
                double yDifference = 0;
                if (col > 0 && col < (cols - 1)) {
                    xDifference = image.get(row, col + 1)[0] - image.get(row, col - 1)[0];
                }
                if (row > 0 && row < (rows - 1)) {
                    yDifference = image.get(row + 1, col)[0] - image.get(row - 1, col)[0];
                }
                double pixelMagnitude = Math.sqrt(Math.pow(xDifference, 2) + Math.pow(yDifference, 2));
                int pixelAngle = (int)Math.abs(Math.toDegrees(Math.atan(yDifference / xDifference)));

                int subRegionX = (int)Math.floor((double)col / regionSize.width);
                int subRegionY = (int)Math.floor((double)row / regionSize.height);
                for (int descriptorOffset = 0, angle = 0; angle <= 180; angle += angleAgrupationSize, descriptorOffset++) {
                    if (angle >= pixelAngle) {
                        if (descriptorOffset > 0) {
                            double descriptorMultiplier = (angle - pixelAngle) / (double)angleAgrupationSize;
                            if (descriptorMultiplier > 0) {
                                int descriptorIndex = (((subRegionY * regionsCountX) + subRegionX) * regionElementsSize) + descriptorOffset - 1;
                                descriptorData[descriptorIndex] += descriptorMultiplier * pixelMagnitude;
                            }
                        }
                        double descriptorMultiplier = (descriptorOffset == 0)? 1 : ((pixelAngle - (angle - angleAgrupationSize)) / (double)angleAgrupationSize);
                        int descriptorIndex = (((subRegionY * regionsCountX) + subRegionX) * regionElementsSize);
                        descriptorData[descriptorIndex] += descriptorMultiplier * pixelMagnitude;
                        break;
                    }
                }
            }
        }
        return descriptorData;
    }

    private static double[] getLBPDescriptor(Mat image, int pointsCount, int radius, Size regionSize, boolean onlyUniformPatterns, int valueAgrupationSize, boolean useVariance) {
        double degreesDelta = (Math.PI * 2) / pointsCount;
        int rows = image.rows();
        int cols = image.cols();
        if (regionSize == null) {
            regionSize = new Size(cols, rows);
        }
        int regionElementsSize = onlyUniformPatterns ? 58 : (int)Math.ceil(256.0 / valueAgrupationSize);
        int regionsCountX = (int)Math.ceil((double)cols / regionSize.width);
        int regionsCountY = (int)Math.ceil((double)rows / regionSize.height);
        int regionsCount = regionsCountX * regionsCountY;
        int descriptorSize = regionsCount * regionElementsSize;
        double valueMultiplier = 256 / (Math.pow(2, pointsCount));
        double[] descriptorData = new double[descriptorSize];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                double centerValue = image.get(row, col)[0];
                double[] neighborValues = new double[pointsCount];
                for (int index = 0; index < pointsCount; index++) {
                    double radians = index * degreesDelta;
                    int offsetCol = (int) Math.round(radius * Math.cos(radians) + col);
                    int offsetRow = (int) Math.round(radius * Math.sin(radians) + row);
                    neighborValues[index] = (offsetRow >= 0 && offsetCol >= 0 && offsetRow < rows && offsetCol < cols)? image.get(offsetRow, offsetCol)[0] : centerValue;
                }
                int newCenterValue = 0;
                for (int index = 0; index < pointsCount; index++) {
                    if (neighborValues[index] >= centerValue) {
                        newCenterValue += Math.pow(2, index);
                    }
                }
                newCenterValue *= valueMultiplier;
                int normalizedBinaryOffset = NORMALIZED_BINARY_PATTERN_OFFSETS[newCenterValue];
                if (!onlyUniformPatterns || normalizedBinaryOffset >= 0) {
                    int subRegionX = (int)Math.floor((double)col / regionSize.width);
                    int subRegionY = (int)Math.floor((double)row / regionSize.height);
                    int descriptorIndex = (((subRegionY * regionsCountX) + subRegionX) * regionElementsSize) + (onlyUniformPatterns ? normalizedBinaryOffset : (int)Math.floor(newCenterValue / (double)valueAgrupationSize));

                    if (useVariance) {
                        double neighborValuesAverage = Arrays.stream(neighborValues).average().getAsDouble();
                        double neighborValuesVarianceSum = 0;
                        for (int index = 0; index < pointsCount; index++) {
                            neighborValuesVarianceSum += Math.pow(neighborValues[index] - neighborValuesAverage, 2);
                        }
                        double neighborValuesVariance = neighborValuesVarianceSum / pointsCount;
                        descriptorData[descriptorIndex] += neighborValuesVariance;
                    } else {
                        descriptorData[descriptorIndex]++;
                    }
                }
            }
        }
        return descriptorData;
    }

    private static Mat getLBP(Mat image) {
        return getLBP(image, 8, 1, false);
    }

    private static Mat getLBP(Mat image, int pointsCount, int radius, boolean onlyUniformPatters) {
        Mat lbp = Mat.zeros(image.size(), CV_8U);
        double degreesDelta = (Math.PI * 2) / pointsCount;
        int rows = image.rows();
        int cols = image.cols();
        double valueMultiplier = 256 / (Math.pow(2, pointsCount));
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                double centerValue = image.get(row, col)[0];
                double[] neighborValues = new double[pointsCount];
                for (int index = 0; index < pointsCount; index++) {
                    double radians = index * degreesDelta;
                    int offsetCol = (int) Math.round(radius * Math.cos(radians) + col);
                    int offsetRow = (int) Math.round(radius * Math.sin(radians) + row);
                    neighborValues[index] = (offsetRow >= 0 && offsetCol >= 0 && offsetRow < rows && offsetCol < cols)? image.get(offsetRow, offsetCol)[0] : centerValue;
                }
                double newCenterValue = 0;
                boolean lastValueActive = false;
                int valueTransitions = 0;
                for (int index = 0; index < pointsCount; index++) {
                    double neighborValue = neighborValues[index];
                    boolean valueActive = neighborValue >= centerValue;
                    if (valueActive) {
                        newCenterValue += Math.pow(2, index);
                    }
                    if (index > 0 && valueActive != lastValueActive) {
                        valueTransitions++;
                    }
                    lastValueActive = valueActive;
                }
                newCenterValue *= valueMultiplier;
                boolean isUniformPattern = valueTransitions <= 2;
                if (!onlyUniformPatters || isUniformPattern) {
                    lbp.put(row, col, newCenterValue);
                }
            }
        }
        return lbp;
    }

    private static double[] getLBPVHistogram(Mat image, int pointsCount, int radius, boolean onlyUniformPatters, int valueAgrupationSize) {
        int histogramSize = (int)Math.ceil(256.0 / valueAgrupationSize);
        double[] histogram = new double[histogramSize];
        double degreesDelta = (Math.PI * 2) / pointsCount;
        int rows = image.rows();
        int cols = image.cols();
        double valueMultiplier = 256 / (Math.pow(2, pointsCount));
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                double centerValue = image.get(row, col)[0];
                double[] neighborValues = new double[pointsCount];
                for (int index = 0; index < pointsCount; index++) {
                    double radians = index * degreesDelta;
                    int offsetCol = (int) Math.round(radius * Math.cos(radians) + col);
                    int offsetRow = (int) Math.round(radius * Math.sin(radians) + row);
                    neighborValues[index] = (offsetRow >= 0 && offsetCol >= 0 && offsetRow < rows && offsetCol < cols)? image.get(offsetRow, offsetCol)[0] : centerValue;
                }
                double newCenterValue = 0;
                boolean lastValueActive = false;
                int valueTransitions = 0;
                for (int index = 0; index < pointsCount; index++) {
                    double neighborValue = neighborValues[index];
                    boolean valueActive = neighborValue > centerValue;
                    if (valueActive) {
                        newCenterValue += Math.pow(2, index);
                    }
                    if (index > 0 && valueActive != lastValueActive) {
                        valueTransitions++;
                    }
                    lastValueActive = valueActive;
                }
                newCenterValue *= valueMultiplier;
                boolean isUniformPattern = valueTransitions <= 2;
                if (!onlyUniformPatters || isUniformPattern) {
                    double neighborValuesAverage = Arrays.stream(neighborValues).average().getAsDouble();
                    double neighborValuesVarianceSum = 0;
                    for (int index = 0; index < pointsCount; index++) {
                        double neighborValue = neighborValues[index];
                        neighborValuesVarianceSum += Math.pow(neighborValue - neighborValuesAverage, 2);
                    }
                    double neighborValuesVariance = Math.sqrt(neighborValuesVarianceSum / pointsCount);
                    int histogramIndex = (int)Math.floor(newCenterValue / valueAgrupationSize);
                    histogram[histogramIndex] += neighborValuesVariance;
                }
            }
        }
        return histogram;
    }
}
