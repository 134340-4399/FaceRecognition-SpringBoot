package org.example.facerecognitionspringboot.utils;

import ai.onnxruntime.*;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.*;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class YoloFaceDetector {

    private OrtEnvironment env;
    private OrtSession session;
    private final float CONFIDENCE_THRESHOLD = 0.5f;
    private final int INPUT_SIZE = 768;

    // 封装预处理结果的内部类
    private static class PreprocessResult {
        final float[][][][] inputTensor; // 模型输入张量
        final float scale;                // 本次预处理实际使用的缩放比例
        final float padX;                 // 本次预处理实际使用的X方向填充量
        final float padY;                 // 本次预处理实际使用的Y方向填充量

        public PreprocessResult(float[][][][] inputTensor, float scale, float padX, float padY) {
            this.inputTensor = inputTensor;
            this.scale = scale;
            this.padX = padX;
            this.padY = padY;
        }
    }

    // 初始化 ONNX Runtime 环境，加载预训练的 YOLO 人脸检测模型。
    public YoloFaceDetector() {
        try {
            ClassPathResource resource = new ClassPathResource("model/best.onnx");
            try (InputStream modelStream = resource.getInputStream()) {
                byte[] modelBytes = modelStream.readAllBytes();

                // 初始化ONNX Runtime环境和会话
                env = OrtEnvironment.getEnvironment();
                OrtSession.SessionOptions options = new OrtSession.SessionOptions();
                options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
                //动态获取 CPU 核心数
                int threads = Runtime.getRuntime().availableProcessors();
                System.out.println("CPU 核心数: " + threads);
                options.setIntraOpNumThreads(threads);
                // 把模型加载进内存，创建会话
                session = env.createSession(modelBytes, options);

                System.out.println("YOLO26m ONNX Runtime 引擎加载成功");
            }
        } catch (IOException e) {
            System.err.println("找不到模型文件，请确认 resources/model/best.onnx 存在");
            e.printStackTrace();
        } catch (OrtException e) {
            e.printStackTrace();
        }
    }

    /**
     * 检测人脸，返回人脸矩形框
     */
    public RectVector detect(Mat frame) throws OrtException {
        if (session == null) {
            throw new RuntimeException("YOLO 模型未加载");
        }
        int originalWidth = frame.cols();
        int originalHeight = frame.rows();

        // 1. 图像预处理：得到输入张量和本次的缩放、填充参数
        PreprocessResult preResult = preprocess(frame);

        // 2. 创建 ONNX Runtime 输入张量，把 Java 数组变成 ONNX 的 Tensor
        OnnxTensor tensor = OnnxTensor.createTensor(env, preResult.inputTensor);

        // 3. 推理
        OrtSession.Result result = session.run(Collections.singletonMap("images", tensor));

        // 4. 获取输出 (shape: [1, 300, 6])
        float[][][] output = (float[][][]) result.get(0).getValue();

        // 5. 后处理，将检测框映射回原图，直接使用预处理计算好的参数
        return postprocess(output, originalWidth, originalHeight,
                preResult.scale, preResult.padX, preResult.padY);
    }

    /**
     * 图像预处理：缩放（保持宽高比，对称填充黑边），转为 NCHW float 数组，值域 [0,1]
     */
    private PreprocessResult preprocess(Mat src) {
        int h = src.rows();
        int w = src.cols();

        // 计算缩放比例（长边缩放到640）
        float scale = Math.min(INPUT_SIZE * 1.0f / h, INPUT_SIZE * 1.0f / w);
        int newW = Math.round(w * scale);
        int newH = Math.round(h * scale);

        // 计算对称填充量
        float padX = (INPUT_SIZE - newW) / 2.0f;
        float padY = (INPUT_SIZE - newH) / 2.0f;
        int leftPad = Math.round(padX);
        int topPad = Math.round(padY);

        // 缩放图像
        Mat resized = new Mat();
        opencv_imgproc.resize(src, resized, new Size(newW, newH));

        // 创建填充画布 640x640，颜色为灰色 (114,114,114)
        Mat canvas = new Mat(INPUT_SIZE, INPUT_SIZE, src.type(), new Scalar(114, 114, 114, 0));
        // 将缩放后的图像对称放置到画布中央
        Rect roi = new Rect(leftPad, topPad, newW, newH);
        resized.copyTo(canvas.apply(roi));

        // 转为 RGB (OpenCV 默认 BGR)
        Mat rgb = new Mat();
        opencv_imgproc.cvtColor(canvas, rgb, opencv_imgproc.COLOR_BGR2RGB);

        // 归一化与数据类型转换
        rgb.convertTo(rgb, opencv_core.CV_32FC3, 1.0 / 255.0, 0.0);

        // 获取数据指针 (FloatBuffer)
        FloatBuffer buffer = rgb.createBuffer();
        float[] data = new float[INPUT_SIZE * INPUT_SIZE * 3];
        buffer.get(data); // 此时 data 是 HWC 顺序 [height, width, channel]

        // 转换为 NCHW 格式 [1, 3, 640, 640]
        float[][][][] nchw = new float[1][3][INPUT_SIZE][INPUT_SIZE];
        for (int y = 0; y < INPUT_SIZE; y++) {
            for (int x = 0; x < INPUT_SIZE; x++) {
                for (int c = 0; c < 3; c++) {
                    // HWC 索引: data[y * INPUT_SIZE * 3 + x * 3 + c]
                    nchw[0][c][y][x] = data[y * INPUT_SIZE * 3 + x * 3 + c];
                }
            }
        }

        // 释放临时 Mat
        resized.release();
        canvas.release();
        rgb.release();

        // 返回张量和本次使用的缩放、填充参数（保留浮点精度）
        return new PreprocessResult(nchw, scale, padX, padY);
    }

    /**
     * 后处理：自适应解析 YOLO 输出，过滤，返回最终框
     * @param output 模型输出 [1, 300, 6]
     * @param origW 原始图像宽度
     * @param origH 原始图像高度
     * @param scale 预处理使用的缩放比例
     * @param padX 预处理使用的X方向填充量
     * @param padY 预处理使用的Y方向填充量
     */
    private RectVector postprocess(float[][][] output, int origW, int origH,
                                   float scale, float padX, float padY) {
        float[][] outMatrix = output[0];
        boolean isTransposed = outMatrix.length < outMatrix[0].length;
        int rows = isTransposed ? outMatrix[0].length : outMatrix.length;

        List<Rect> boxList = new ArrayList<>();
        List<Float> confList = new ArrayList<>();

        for (int i = 0; i < rows; i++) {
            float cx = isTransposed ? outMatrix[0][i] : outMatrix[i][0];
            float cy = isTransposed ? outMatrix[1][i] : outMatrix[i][1];
            float w  = isTransposed ? outMatrix[2][i] : outMatrix[i][2];
            float h  = isTransposed ? outMatrix[3][i] : outMatrix[i][3];
            float conf = isTransposed ? outMatrix[4][i] : outMatrix[i][4];

            if (conf < CONFIDENCE_THRESHOLD) continue;

            // 模型输出的是640画布上的绝对坐标，直接使用
            float x1 = cx - w / 2.0f;   // 左上角x
            float y1 = cy - h / 2.0f;   // 左上角y

            // 减去填充，缩放到原图尺寸
            x1 = (x1 - padX) / scale;
            y1 = (y1 - padY) / scale;
            float realW = w / scale;
            float realH = h / scale;

            // 边界裁剪
            int left = Math.max(0, Math.round(x1));
            int top = Math.max(0, Math.round(y1));
            int right = Math.min(origW, Math.round(x1 + realW));
            int bottom = Math.min(origH, Math.round(y1 + realH));

            if (right > left && bottom > top) {
                boxList.add(new Rect(left, top, right - left, bottom - top));
                confList.add(conf);
            }
        }

        RectVector finalBoxes = new RectVector();
        for (Rect rect : boxList) {
            finalBoxes.push_back(rect);
        }
        return finalBoxes;
    }
}