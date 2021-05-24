package com.biometrics.utils;

import org.opencv.core.Point;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.opencv.core.CvType.*;

public final class OpenCVUtils {

    private static boolean initialized = false;

    public static void initializeLibrary() {
        if (!initialized) {
            try {
                nu.pattern.OpenCV.loadShared();
            } catch (Throwable ex) {
                nu.pattern.OpenCV.loadLocally();
            }
            initialized = true;
        }
    }

    public static Mat getImage(byte[] imageBytes) {
        return OpenCVUtils.getImage(imageBytes, Imgcodecs.IMREAD_UNCHANGED);
    }

    public static Mat getImage(byte[] imageBytes, int flags) {
        return Imgcodecs.imdecode(new MatOfByte(imageBytes), flags);
    }

    public static void subImage(Mat image, Mat destinationImage, RotatedRect rect) {
        Size destinationSize = rect.size;
        Point rotationAnchorPoint = rect.center;
        double translationX = (destinationSize.width / 2.0) - rotationAnchorPoint.x;
        double translationY = (destinationSize.height / 2.0) - rotationAnchorPoint.y;
        rotateAndTranslate(image, destinationImage, rotationAnchorPoint, rect.angle, translationX, translationY, destinationSize);
    }

    public static void subImage(Mat image, Mat destinationImage, Rect rect) {
        destinationImage = image.submat(rect);
    }

    public static byte[] getImageBytes(Mat image) {
        return getImageBytes(image, ".jpg");
    }

    public static byte[] getImageBytes(Mat image, String extension) {
        MatOfByte bytesMat = new MatOfByte();
        Imgcodecs.imencode(extension, image, bytesMat);
        return bytesMat.toArray();
    }

    public static Scalar getScalarFromColor (Color color) {
        return new Scalar(color.getBlue(), color.getGreen(), color.getRed());
    }

    public static BufferedImage getBufferedImage(Mat image) {
        BufferedImage bufferedImage = null;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(getImageBytes(image))) {
            bufferedImage = ImageIO.read(bais);
        } catch(Exception ex) {}
        return bufferedImage;
    }

    public static MatOfRect detectFeatures(Mat image, CascadeClassifier classifier) {
        MatOfRect features = new MatOfRect();
        classifier.detectMultiScale(image, features);
        return features;
    }

    public static Mat detectBiggestFeature(Mat image, CascadeClassifier classifier) {
        MatOfRect features = detectFeatures(image, classifier);
        Rect biggestFeature = null;
        double biggestFeatureArea = 0.0;
        for (Rect feature : features.toArray()) {
            if (biggestFeature == null || feature.area() > biggestFeatureArea) {
                biggestFeature = feature;
                biggestFeatureArea = biggestFeature.area();
            }
        }
        return biggestFeature != null ? image.submat(biggestFeature) : null;
    }

    public static Rect[] detectFeatureRects(Mat image, CascadeClassifier classifier) {
        return detectFeatures(image, classifier).toArray();
    }

    public static Rect detectBiggestFeatureRect(Mat image, CascadeClassifier classifier) {
        Rect[] features = detectFeatureRects(image, classifier);
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

    public static void drawRects(Mat image, Rect[] rects, Color color, int thickness) {
        for (Rect feature : rects) {
            drawRect(image, feature, color, thickness);
        }
    }

    public static void drawRect(Mat image, Rect rect, Color color, int thickness) {
        Imgproc.rectangle(image, new Point(rect.x, rect.y), new Point(rect.x + rect.width, rect.y + rect.height), getScalarFromColor(color), thickness);
    }

    public static void drawRect(Mat image, RotatedRect rect, Color color, int thickness) {
        Point[] points = new Point[4];
        rect.points(points);
        Scalar scalar = getScalarFromColor(color);
        for (int i = 0; i < 4; i++) {
            Imgproc.line(image, points[i], points[(i+1) % 4], scalar, thickness);
        }
    }

    public static void drawContours(Mat image, List<MatOfPoint> contours, Color color, int thickness) {
        Scalar colorScalar = getScalarFromColor(color);
        for (int i = 0; i < contours.size(); i++) {
            Imgproc.drawContours(image, contours, i, colorScalar, thickness);
        }
    }

    public static void drawContour(Mat image, MatOfPoint contour, Color color, int thickness) {
        List<MatOfPoint> contours = new ArrayList<>();
        contours.add(contour);
        drawContours(image, contours, color, thickness);
    }

    public static boolean containsRect(Rect feature, Rect innerFeature) {
        return feature.contains(innerFeature.tl()) && feature.contains(innerFeature.br());
    }

    public static boolean containerAnyRect(Rect feature, Rect[] innerFeatures) {
        boolean contains = false;
        for (Rect innerFeature : innerFeatures) {
            if (containsRect(feature, innerFeature)) {
                contains = true;
                break;
            }
        }
        return contains;
    }

    public static MatOfPoint getLargestContour(List<MatOfPoint> contours) {
        MatOfPoint largestContour = null;
        double largestContourPerimeter = 0;
        for (MatOfPoint contour : contours) {
            double contourPerimeter = Imgproc.arcLength(new MatOfPoint2f(contour.toArray()), true);
            if (largestContour == null || contourPerimeter > largestContourPerimeter) {
                largestContour = contour;
                largestContourPerimeter = contourPerimeter;
            }
        }
        return largestContour;
    }

    public static void display(Mat image) {
        display(image, "Image");
    }

    public static void display(Mat image, String label) {
        Image bufferedImage = getBufferedImage(image);
        ImageIcon icon = new ImageIcon(bufferedImage);
        JFrame frame = new JFrame(label);
        frame.setLayout(new FlowLayout());
        frame.setSize(bufferedImage.getWidth(null)+50, bufferedImage.getHeight(null)+50);
        JLabel lbl=new JLabel();
        lbl.setIcon(icon);
        frame.add(lbl);
        frame.setVisible(true);
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
    }

    public static void resize(Mat image, Mat destinationImage, int maxWidth, int maxHeight, int minWidth, int minHeight) {
        Size imageSize = image.size();
        double newImageWidth = imageSize.width;
        double newImageHeight = imageSize.height;

        if (maxWidth > 0 && newImageWidth > maxWidth) {
            double ratio = (maxWidth / newImageWidth);
            newImageWidth *= ratio;
            newImageHeight *= ratio;
        }

        if (maxHeight > 0 && newImageHeight > maxHeight) {
            double ratio = (maxHeight / newImageHeight);
            newImageWidth *= ratio;
            newImageHeight *= ratio;
        }

        if (minWidth > 0 && newImageWidth < minWidth) {
            double ratio = minWidth / newImageWidth;
            newImageWidth *= ratio;
            newImageHeight *= ratio;
        }

        if (minHeight > 0 && newImageHeight < minHeight) {
            double ratio = minHeight / newImageHeight;
            newImageWidth *= ratio;
            newImageHeight *= ratio;
        }

        Size newImageSize = new Size(newImageWidth, newImageHeight);
        Imgproc.resize(image, destinationImage, newImageSize);
    }

    public static void grayScale(Mat image, Mat destinationImage){
        Imgproc.cvtColor(image, destinationImage, Imgproc.COLOR_BGR2GRAY);
    }

    public static void blackAndWhite(Mat image, Mat destinationImage) {
        blackAndWhite(image, destinationImage, 127);
    }

    public static void blackAndWhite(Mat image, Mat destinationImage, double threshold) {
        grayScale(image, destinationImage);
        Imgproc.threshold(destinationImage, destinationImage, threshold, 255, Imgproc.THRESH_BINARY);
    }

    public static void hanningWindow(Mat image, Mat destinationImage) {
        int rows = image.rows();
        int cols = image.cols();
        Mat hanningWindow = new Mat();
        Imgproc.createHanningWindow(hanningWindow, new Size(cols, rows), CV_64F);
        int channels = image.channels();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                double hanningWindowMultiplier = hanningWindow.get(row, col)[0];
                double[] values = new double[channels];
                for (int channel = 0; channel < channels; channel++) {
                    values[channel] = hanningWindowMultiplier * image.get(row, col)[channel];
                }
                destinationImage.put(row, col, values);
            }
        }
    }

    public static void translate(Mat image, Mat destinationImage, double translationX, double translationY, Size destinationSize) {
        Mat matrix = new Mat(2, 3, CV_64F);
        matrix.put(0, 0, 1);
        matrix.put(0, 1, 0);
        matrix.put(0, 2, translationX);
        matrix.put(1, 0, 0);
        matrix.put(1, 1, 1);
        matrix.put(1, 2, translationY);
        Imgproc.warpAffine(image, destinationImage, matrix, destinationSize, Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT);
    }

    public static void rotate (Mat image, Mat destinationImage, Point rotationAnchorPoint, double rotationAngle, Size destinationSize) {
        Mat matrix = Imgproc.getRotationMatrix2D(rotationAnchorPoint, rotationAngle, 1.0);
        Imgproc.warpAffine(image, destinationImage, matrix, destinationSize, Imgproc.INTER_CUBIC, Core.BORDER_CONSTANT);
    }

    public static void rotateAndTranslate(Mat image, Mat destinationImage, Point rotationAnchorPoint, double rotationAngle, double translationX, double translationY, Size destinationSize) {
        Mat matrix = Imgproc.getRotationMatrix2D(rotationAnchorPoint, rotationAngle, 1.0);
        matrix.put(0, 2, matrix.get(0,2)[0] + translationX);
        matrix.put(1, 2, matrix.get(1,2)[0] + translationY);
        Imgproc.warpAffine(image, destinationImage, matrix, destinationSize, Imgproc.INTER_CUBIC, Core.BORDER_CONSTANT);
    }

    public static Mat getMagnitudeSpectrum(Mat image) {
        List<Mat> planes = new ArrayList<>();
        Mat complexImage = new Mat();
        Mat padded = new Mat();
        int addPixelRows = Core.getOptimalDFTSize(image.rows());
        int addPixelCols = Core.getOptimalDFTSize(image.cols());
        Core.copyMakeBorder(image, padded, 0, addPixelRows - image.rows(), 0, addPixelCols - image.cols(), Core.BORDER_CONSTANT, Scalar.all(0));
        padded.convertTo(padded, CvType.CV_32F);
        planes.add(padded);
        planes.add(Mat.zeros(padded.size(), CvType.CV_32F));
        Core.merge(planes, complexImage);
        Core.dft(complexImage, complexImage);

        List<Mat> newPlanes = new ArrayList<>();
        Mat mag = new Mat();
        Core.split(complexImage, newPlanes);
        Core.magnitude(newPlanes.get(0), newPlanes.get(1), mag);
        Core.add(Mat.ones(mag.size(), CvType.CV_32F), mag, mag);
        Core.log(mag, mag);

        mag = mag.submat(new Rect(0, 0, mag.cols() & -2, mag.rows() & -2));
        int cx = mag.cols() / 2;
        int cy = mag.rows() / 2;
        Mat q0 = new Mat(mag, new Rect(0, 0, cx, cy));
        Mat q1 = new Mat(mag, new Rect(cx, 0, cx, cy));
        Mat q2 = new Mat(mag, new Rect(0, cy, cx, cy));
        Mat q3 = new Mat(mag, new Rect(cx, cy, cx, cy));
        Mat tmp = new Mat();
        q0.copyTo(tmp);
        q3.copyTo(q0);
        tmp.copyTo(q3);
        q1.copyTo(tmp);
        q2.copyTo(q1);
        tmp.copyTo(q2);

        mag.convertTo(mag, CvType.CV_8UC1);
        Core.normalize(mag, mag, 0, 255, Core.NORM_MINMAX, CvType.CV_8UC1);
        return mag;
    }

    public static double[] getLBPHistogram(Mat src) {
        return getLBPHistogram(src, 8, 1, false);
    }

    public static double[] getLBPHistogram(Mat src, int pointsCount, int radius, boolean onlyUniformPatters) {
        Mat lbp = Mat.zeros(src.size(), CV_8U);
        double degreesDelta = (Math.PI * 2) / pointsCount;
        int rows = src.rows();
        int cols = src.cols();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                double centerValue = src.get(row, col)[0];
                double[] neighborValues = new double[pointsCount];
                for (int index = 0; index < pointsCount; index++) {
                    double radians = index * degreesDelta;
                    int offsetCol = (int) Math.round(radius * Math.cos(radians) + col);
                    int offsetRow = (int) Math.round(radius * Math.sin(radians) + row);
                    neighborValues[index] = (offsetRow > 0 && offsetCol > 0 && offsetRow < rows && offsetCol < cols)? src.get(offsetRow, offsetCol)[0] : centerValue;
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
                boolean isUniformPattern = valueTransitions <= 2;
                if (!onlyUniformPatters || isUniformPattern) {
                    lbp.put(row, col, newCenterValue);
                }
            }
        }
        return getHistogram(lbp);
    }

    public static double[] getLBPVHistogram(Mat image, int pointsCount, int radius, boolean onlyUniformPatters) {
        double[] histogram = new double[256];
        double degreesDelta = (Math.PI * 2) / pointsCount;
        int rows = image.rows();
        int cols = image.cols();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                double centerValue = image.get(row, col)[0];
                double[] neighborValues = new double[pointsCount];
                for (int index = 0; index < pointsCount; index++) {
                    double radians = index * degreesDelta;
                    int offsetCol = (int) Math.round(radius * Math.cos(radians) + col);
                    int offsetRow = (int) Math.round(radius * Math.sin(radians) + row);
                    neighborValues[index] = (offsetRow > 0 && offsetCol > 0 && offsetRow < rows && offsetCol < cols)? image.get(offsetRow, offsetCol)[0] : centerValue;
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
                boolean isUniformPattern = valueTransitions <= 2;
                if (!onlyUniformPatters || isUniformPattern) {
                    double neighborValuesAverage = Arrays.stream(neighborValues).average().getAsDouble();
                    double neighborValuesVarianceSum = 0;
                    for (int index = 0; index < pointsCount; index++) {
                        double neighborValue = neighborValues[index];
                        neighborValuesVarianceSum += Math.pow(neighborValue - neighborValuesAverage, 2);
                    }
                    double neighborValuesVariance = neighborValuesVarianceSum / pointsCount;
                    histogram[(int)newCenterValue] = neighborValuesVariance;
                }
            }
        }
        return histogram;
    }

    public static double[] getHistogram(Mat image) {
        return getHistogram(image, 0);
    }

    public static double[] getHistogram(Mat image, int channel) {
        int histSize = 256;
        double[] histogramValues = new double[histSize];
        Mat histogram = new Mat();
        Imgproc.calcHist(Arrays.asList(image), new MatOfInt(channel), new Mat(), histogram, new MatOfInt(histSize), new MatOfFloat(0, histSize - 1), false);
        for (int i = 0; i < histSize; i++) {
            histogramValues[i] = histogram.get(i, 0)[0];
        }
        return histogramValues;
    }

    public static void displayHistogram (double[] histogramValues) {
        displayHistogram (histogramValues, 600, 400, Color.RED);
    }

    public static void displayHistogram (double[] histogramValues, int histWidth, int histHeight, Color color) {
        Scalar scalar = getScalarFromColor(color);
        Mat histogramImage = Mat.zeros(histHeight, histWidth, CV_8UC3);
        double maxValue = 0;
        int histSize = histogramValues.length;
        for (int i = 0; i < histSize; i++) {
            if (histogramValues[i] > maxValue) {
                maxValue = histogramValues[i];
            }
        }
        for (int i = 0; i < histSize; i++) {
            double value = histogramValues[i] * histHeight / maxValue;
            Point point1 = new Point(i * (double)histWidth / (double)histSize, histHeight - 1);
            Point point2 = new Point(((i + 1) * (double)histWidth / (double)histSize) - 1, histHeight - value);
            Imgproc.rectangle(histogramImage, point1, point2, scalar, Imgproc.FILLED);
        }
        display(histogramImage);
    }

    public static double getBlurriness(Mat image) {
        Mat laplacian = new Mat();
        Imgproc.Laplacian(image, laplacian, CV_64F);
        MatOfDouble mu = new MatOfDouble();
        MatOfDouble sigma = new MatOfDouble();
        Core.meanStdDev(laplacian, mu, sigma);
        return Math.pow(sigma.get(0,0)[0], 2);
    }

    public static CascadeClassifier getClassfierFromResource(String resourceName) {
        CascadeClassifier classifier = null;
        InputStream inputStream = null;
        OutputStream outputStream = null;
        File cascadeFile = null;
        try {
            cascadeFile = new File("cascade_file.xml");
            inputStream = OpenCVUtils.class.getClassLoader().getResourceAsStream(resourceName);
            outputStream = new FileOutputStream(cascadeFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            classifier = new CascadeClassifier(cascadeFile.getAbsolutePath());
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        finally {
            try { inputStream.close(); } catch (Exception ex) {}
            try { outputStream.close(); } catch (Exception ex) {}
            try { cascadeFile.delete(); } catch (Exception ex) {}
        }
        return classifier;
    }
}
