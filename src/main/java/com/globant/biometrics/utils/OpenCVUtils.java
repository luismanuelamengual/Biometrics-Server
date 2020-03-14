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
