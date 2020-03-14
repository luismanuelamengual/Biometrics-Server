package com.globant.biometrics.utils;

import org.opencv.core.Point;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public final class OpenCVUtils {

    public static Mat getImageMat(byte[] imageBytes) throws Exception {
        return Imgcodecs.imdecode(new MatOfByte(imageBytes), Imgcodecs.IMREAD_UNCHANGED);
    }

    public static byte[] getImageBytes(Mat image) {
        return getImageBytes(image, ".jpg");
    }

    public static byte[] getImageBytes(Mat image, String extension) {
        MatOfByte bytesMat = new MatOfByte();
        Imgcodecs.imencode(extension, image, bytesMat);
        return bytesMat.toArray();
    }

    public static Image getBufferedImage(Mat image){
        int type = BufferedImage.TYPE_BYTE_GRAY;
        if ( image.channels() > 1 ) {
            Mat m2 = new Mat();
            Imgproc.cvtColor(image,m2,Imgproc.COLOR_BGR2RGB);
            type = BufferedImage.TYPE_3BYTE_BGR;
            image = m2;
        }
        byte [] b = new byte[image.channels()*image.cols()*image.rows()];
        image.get(0,0,b);
        BufferedImage bufferedImage = new BufferedImage(image.cols(),image.rows(), type);
        bufferedImage.getRaster().setDataElements(0, 0, image.cols(),image.rows(), b);
        return bufferedImage;
    }

    public static Mat flipImageMat(Mat image) {
        Mat flippedImage = new Mat();
        Core.flip(image, flippedImage, 1);
        return flippedImage;
    }

    public static Mat resizeImageMat(Mat image, int width, int height){
        Size imgDim = image.size();
        Size dim = null;
        double r = 1;
        if(width <= 0 && height <= 0) {
            return image;
        }
        if (height == 0) {
            r =  width/imgDim.width;
            dim = new Size(width, (int)(image.height() * r));
        } else if(width == 0) {
            r = height/imgDim.height;
            dim = new Size((int)(image.width() * r), height);
        }
        else if (width > 0 && height > 0) {
            dim = new Size(width, height);
        }
        Mat resized = new Mat();
        Imgproc.resize(image, resized, dim, 0, 0, Imgproc.INTER_AREA);
        return resized;
    }

    public static Rect[] detectFeatures (Mat image, CascadeClassifier classifier) {
        MatOfRect features = new MatOfRect();
        classifier.detectMultiScale(image, features);
        return features.toArray();
    }

    public static Rect detectBiggestFeature(Mat image, CascadeClassifier classifier) {
        Rect[] features = detectFeatures(image, classifier);
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

    public static void drawFeatures (Mat image, Rect[] features, Color color) {
        for (Rect feature : features) {
            drawFeature(image, feature, color);
        }
    }

    public static void drawFeature (Mat image, Rect feature, Color color) {
        Imgproc.rectangle(image, new Point(feature.x, feature.y), new Point(feature.x + feature.width, feature.y + feature.height), new Scalar(color.getBlue(), color.getGreen(), color.getRed()),3);
    }

    public static boolean featureContains(Rect feature, Rect innerFeature) {
        return feature.contains(innerFeature.tl()) && feature.contains(innerFeature.br());
    }

    public static boolean featureContainsAny(Rect feature, Rect[] innerFeatures) {
        boolean contains = false;
        for (Rect innerFeature : innerFeatures) {
            if (featureContains(feature, innerFeature)) {
                contains = true;
                break;
            }
        }
        return contains;
    }

    public static void displayImage(Mat image) {
        displayImage(image, "Image");
    }

    public static void displayImage(Mat image, String label) {
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

    public static Mat detectMRZ(Mat img) {
        Mat roi = null;
        Mat rectKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(13,5));
        Mat sqKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(21,21));

        if (img.width() > 800) {
            img = resizeImageMat(img, 800, img.height() * 800 / img.width());
        }
        if (img.height() > 600) {
            img = resizeImageMat(img, img.width() * 600 / img.height(), 600);
        }
        Mat gray = new Mat();
        Imgproc.cvtColor(img, gray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.GaussianBlur(gray, gray, new Size(3, 3), 0);
        Mat blackhat = new Mat();
        Imgproc.morphologyEx(gray, blackhat, Imgproc.MORPH_BLACKHAT, rectKernel);
        Mat gradX = new Mat();
        Imgproc.Sobel(blackhat, gradX, CvType.CV_32F, 1, 0, -1, 1, 0);
        Core.MinMaxLocResult minMaxVal = Core.minMaxLoc(gradX);
        gradX.convertTo(gradX,CvType.CV_8U,255.0/(minMaxVal.maxVal-minMaxVal.minVal),-255.0/minMaxVal.minVal);
        Imgproc.morphologyEx(gradX, gradX, Imgproc.MORPH_CLOSE, rectKernel);
        Mat thresh = new Mat();
        Imgproc.threshold(gradX, thresh, 0, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
        Imgproc.morphologyEx(thresh, thresh, Imgproc.MORPH_CLOSE, sqKernel);
        Imgproc.erode(thresh, thresh, new Mat(), new Point(-1,-1), 4);
        int pRows = (int)(img.rows() * 0.05);
        int pCols = (int)(img.cols() * 0.05);
        for (int i=0; i <= thresh.rows(); i++)
            for (int j=0; j<=pCols; j++)
                thresh.put(i, j, 0);
        for (int i=0; i <= thresh.rows(); i++)
            for (int j=img.cols()-pCols; j<=img.cols(); j++)
                thresh.put(i, j, 0);
        List<MatOfPoint> cnts = new ArrayList<>();
        Imgproc.findContours(thresh.clone(), cnts, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        for (MatOfPoint c : cnts) {
            Rect bRect = Imgproc.boundingRect(c);
            int x=bRect.x;
            int y=bRect.y;
            int w=bRect.width;
            int h=bRect.height;
            int grWidth = gray.width();
            float ar = (float)w / (float)h;
            float crWidth = (float)w / (float)grWidth;
            if (ar > 4 && crWidth > 0.75){
                int pX = (int)((x + w) * 0.03);
                int pY = (int)((y + h) * 0.03);
                x = x - pX;
                y = y - pY;
                w = w + (pX * 2);
                h = h + (pY * 2);
                roi = new Mat(img, new Rect(x, y, w, h));
                Imgproc.rectangle(img, new Point(x, y), new Point(x + w, y + h), new Scalar(0, 255, 0), 2);
                break;
            }
        }
        return roi;
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
