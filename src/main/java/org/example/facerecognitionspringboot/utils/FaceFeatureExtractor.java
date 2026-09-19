package org.example.facerecognitionspringboot.utils;

import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.opencv.global.opencv_dnn;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;
import org.bytedeco.opencv.opencv_dnn.Net;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.InputStream;

public class FaceFeatureExtractor {
    private Net net;

    public FaceFeatureExtractor() {
        try {
            ClassPathResource resource = new ClassPathResource("model/arcface.onnx");
            try (InputStream modelStream = resource.getInputStream()) {
                byte[] modelBytes = modelStream.readAllBytes();
                File tempFile = File.createTempFile("arcface", ".onnx");
                tempFile.deleteOnExit();
                java.nio.file.Files.write(tempFile.toPath(), modelBytes);
                this.net = opencv_dnn.readNetFromONNX(tempFile.getAbsolutePath());
                System.out.println("ArcFace ONNX 模型加载成功");
            }
        } catch (Exception e) {
            System.err.println("❌ ArcFace 模型加载失败，人脸识别将不可用！");
            e.printStackTrace();
        }
    }

    public float[] extract(Mat faceImage) {
        if (net == null || net.empty()) {
            throw new RuntimeException("ArcFace 模型未加载！");
        }

        // 预处理：缩放至112x112，值域归一化
        Mat blob = opencv_dnn.blobFromImage(
                faceImage,
                1.0 / 127.5,
                new Size(112, 112),
                new Scalar(127.5, 127.5, 127.5, 0.0),
                true,
                false,
                org.bytedeco.opencv.global.opencv_core.CV_32F
        );

        net.setInput(blob);
        Mat output = net.forward();
        Mat flatOutput = output.reshape(1, 1);

        float[] features = new float[512];
        FloatIndexer indexer = flatOutput.createIndexer();
        for (int i = 0; i < 512; i++) {
            features[i] = indexer.get(0, i);
        }

        indexer.release();
        flatOutput.release();
        blob.release();
        output.release();

        // L2 归一化，让特征落在单位超球面上
        float norm = 0.0f;
        for (float f : features) norm += f * f;
        norm = (float) Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < features.length; i++) {
                features[i] /= norm;
            }
        }
        return features;
    }
}