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
import java.util.List;

import static org.opencv.core.CvType.CV_64F;
import static org.opencv.core.CvType.CV_8U;

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
        Point rotationAnchor = rect.center;
        double translationX = (destinationSize.width / 2.0) - rotationAnchor.x;
        double translationY = (destinationSize.height / 2.0) - rotationAnchor.y;
        Mat matrix = Imgproc.getRotationMatrix2D(rotationAnchor, rect.angle, 1.0);
        matrix.put(0, 2, matrix.get(0,2)[0] + translationX);
        matrix.put(1, 2, matrix.get(1,2)[0] + translationY);
        Imgproc.warpAffine(image, destinationImage, matrix, destinationSize, Imgproc.INTER_CUBIC, Core.BORDER_CONSTANT);
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

    public static void drawRects(Mat image, Rect[] features, Color color) {
        for (Rect feature : features) {
            drawRect(image, feature, color);
        }
    }

    public static void drawRect(Mat image, Rect feature, Color color) {
        Imgproc.rectangle(image, new Point(feature.x, feature.y), new Point(feature.x + feature.width, feature.y + feature.height), getScalarFromColor(color),3);
    }

    public static void drawContours(Mat image, List<MatOfPoint> contours, Color color, boolean fill) {
        Scalar colorScalar = getScalarFromColor(color);
        for (int i = 0; i < contours.size(); i++) {
            Imgproc.drawContours(image, contours, i, colorScalar, fill ? -1 : 1);
        }
    }

    public static void drawContour(Mat image, MatOfPoint contour, Color color, boolean fill) {
        List<MatOfPoint> contours = new ArrayList<>();
        contours.add(contour);
        drawContours(image, contours, color, fill);
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

    public static void translate(Mat image, Mat destinationImage, double translationX, double translationY, Size destinationSize) {
        Mat translationMatrix2D = new Mat(2, 3, CV_64F);
        translationMatrix2D.put(0, 0, 1);
        translationMatrix2D.put(0, 1, 0);
        translationMatrix2D.put(0, 2, translationX);
        translationMatrix2D.put(1, 0, 0);
        translationMatrix2D.put(1, 1, 1);
        translationMatrix2D.put(1, 2, translationY);
        Imgproc.warpAffine(image, destinationImage, translationMatrix2D, destinationSize, Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT);
    }

    public static void rotate (Mat image, Mat destinationImage, Point anchorPoint, double angle, Size destinationSize) {
        Mat rotatedMatrix2D = Imgproc.getRotationMatrix2D(anchorPoint, angle, 1.0);
        Imgproc.warpAffine(image, destinationImage, rotatedMatrix2D, destinationSize, Imgproc.INTER_CUBIC, Core.BORDER_CONSTANT);
    }

    public static Mat getLBP(Mat src) {
        Mat lbp = Mat.zeros(src.size(), CV_8U);
        int rows = src.rows();
        int cols = src.cols();
        int[] rowOffsets = {-1, 0, 1, 1, 1, 0, -1, -1};
        int[] colOffsets = {-1, -1, -1, 0, 1, 1, 1, 0};
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                double centerValue = src.get(row, col)[0];
                double newCenterValue = 0;
                for (int offset = 0; offset < 8; offset++) {
                    int offsetRow = row + rowOffsets[offset];
                    int offsetCol = col + colOffsets[offset];
                    if (offsetRow > 0 && offsetCol > 0 && offsetRow < rows && offsetCol < cols) {
                        double offsetValue = src.get(offsetRow, offsetCol)[0];
                        if (offsetValue >= centerValue) {
                            newCenterValue += Math.pow(2, offset);
                        }
                    }
                }
                lbp.put(row, col, newCenterValue);
            }
        }
        return lbp;
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
