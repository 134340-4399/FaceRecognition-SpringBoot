package org.example.facerecognitionspringboot.controller;

import org.bytedeco.javacpp.indexer.DoubleRawIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.example.facerecognitionspringboot.entity.AttendanceLog;
import org.example.facerecognitionspringboot.utils.FaceFeatureExtractor;
import org.example.facerecognitionspringboot.entity.Employee;
import org.example.facerecognitionspringboot.entity.Department;
import org.example.facerecognitionspringboot.service.FaceService;
import org.example.facerecognitionspringboot.dao.SystemConfigRepository;
import org.example.facerecognitionspringboot.dao.DepartmentRepository;
import org.example.facerecognitionspringboot.dao.EmployeeRepository;
import org.example.facerecognitionspringboot.entity.SystemConfig;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.example.facerecognitionspringboot.utils.YoloFaceDetector;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.annotation.PostConstruct;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class FaceController {

    @Autowired private FaceService faceService;
    @Autowired private SystemConfigRepository systemConfigRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private EmployeeRepository employeeRepository;

    private YoloFaceDetector yoloDetector;
    private FaceFeatureExtractor featureExtractor;
    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @PostConstruct
    public void init() {
        yoloDetector = new YoloFaceDetector();
        featureExtractor = new FaceFeatureExtractor();
        System.out.println("YOLO人脸检测 + ArcFace 特征提取引擎启动完毕");
    }

    // ========== 活体检测（拉普拉斯方差）==========
    private double calculateLivenessScore(Mat img) {
        Mat gray = new Mat();
        opencv_imgproc.cvtColor(img, gray, opencv_imgproc.COLOR_BGR2GRAY);
        Mat laplacian = new Mat();
        opencv_imgproc.Laplacian(gray, laplacian, opencv_core.CV_64F);
        Mat mean = new Mat();
        Mat stdDev = new Mat();
        opencv_core.meanStdDev(laplacian, mean, stdDev);
        DoubleRawIndexer stdDevIndexer = stdDev.createIndexer();
        double stdDevValue = stdDevIndexer.get(0, 0);
        stdDevIndexer.close();
        double variance = Math.pow(stdDevValue, 2);
        gray.release(); laplacian.release(); mean.release(); stdDev.release();
        return variance;
    }

    // ========== 裁剪 + 直方图均衡化 ==========
    private Mat cropAndEqualize(Mat matImage, Rect faceRect) {
        Mat cropped = new Mat(matImage, faceRect).clone();
        Mat gray = new Mat();
        opencv_imgproc.cvtColor(cropped, gray, opencv_imgproc.COLOR_BGR2GRAY);
        opencv_imgproc.equalizeHist(gray, gray);
        opencv_imgproc.cvtColor(gray, cropped, opencv_imgproc.COLOR_GRAY2BGR);
        gray.release();
        return cropped;
    }

    // ========== L2 归一化取均值融合 ==========
    private float[] l2NormalizeAndAverage(List<float[]> features) {
        int dim = features.get(0).length;
        int count = features.size();

        // 第一步：对每个向量做 L2 归一化
        float[][] normalized = new float[count][dim];
        for (int i = 0; i < count; i++) {
            float[] v = features.get(i);
            float norm = 0.0f;
            for (float f : v) norm += f * f;
            norm = (float) Math.sqrt(norm);
            for (int j = 0; j < dim; j++) {
                normalized[i][j] = v[j] / norm;
            }
        }

        // 第二步：逐元素取均值
        float[] avg = new float[dim];
        for (int j = 0; j < dim; j++) {
            for (int i = 0; i < count; i++) {
                avg[j] += normalized[i][j];
            }
            avg[j] /= count;
        }

        // 第三步：对均值向量再做 L2 归一化
        float norm = 0.0f;
        for (float f : avg) norm += f * f;
        norm = (float) Math.sqrt(norm);
        if (norm > 0) {
            for (int j = 0; j < dim; j++) avg[j] /= norm;
        }
        return avg;
    }

    // ========== 人脸检测 + 打卡 ==========
    @PostMapping("/detect")
    public Map<String, Object> detectFace(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        Mat matImage = null;
        try {
            matImage = opencv_imgcodecs.imdecode(
                    new Mat(file.getBytes()), opencv_imgcodecs.IMREAD_COLOR);

            double livenessScore = calculateLivenessScore(matImage);
            if (livenessScore < 60.0) {
                matImage.release();
                result.put("success", false);
                result.put("message", "活体核验失败：检测到屏幕翻拍或图像过糊");
                return result;
            }

            RectVector faces = yoloDetector.detect(matImage);
            if (faces.size() == 0) {
                matImage.release(); faces.close();
                result.put("success", false);
                result.put("message", "等待员工进入画面...");
                return result;
            }
            if (faces.size() > 1) {
                matImage.release(); faces.close();
                result.put("success", false);
                result.put("message", "画面中检测到多个人脸，请保持单人识别");
                return result;
            }

            Mat croppedFace = cropAndEqualize(matImage, faces.get(0));
            float[] features = featureExtractor.extract(croppedFace);
            croppedFace.release();

            Employee emp = faceService.findMatchedEmployee(features);
            matImage.release(); faces.close();

            if (emp == null) {
                result.put("success", false);
                result.put("message", "识别失败，请调整角度或重新录入人脸");
            } else {
                result.put("success", true);
                result.put("name", emp.getName());
                result.put("message", "打卡成功");
            }
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            if (matImage != null) matImage.release();
            result.put("success", false);
            result.put("message", "服务器忙：" + e.getMessage());
            return result;
        }
    }

    /**
     * 三连拍注册：先过滤低质量帧（模糊/侧脸），再对合格帧做 L2 归一化取均值融合
     */
    @PostMapping("/register")
    public String register(
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            @RequestParam("name") String name,
            @RequestParam("deptId") Integer deptId,
            @RequestParam("role") String role,
            @RequestParam("files") MultipartFile[] files) {
        try {
            if (files == null || files.length < 3)
                return "❌ 请严格按照要求拍摄3张不同角度的照片！";

            // 收集所有合格帧的特征
            List<float[]> validFeatures = new ArrayList<>();
            List<String> rejectReasons = new ArrayList<>();

            for (int i = 0; i < files.length; i++) {
                MultipartFile file = files[i];
                if (file.isEmpty()) {
                    rejectReasons.add("第" + (i + 1) + "张照片为空");
                    continue;
                }

                Mat matImage = opencv_imgcodecs.imdecode(
                        new Mat(file.getBytes()), opencv_imgcodecs.IMREAD_COLOR);

                // 检查1：清晰度
                double liveness = calculateLivenessScore(matImage);
                if (liveness < 60) {
                    matImage.release();
                    rejectReasons.add("第" + (i + 1) + "张模糊或疑似翻拍(liveness="
                            + String.format("%.1f", liveness) + ")");
                    continue;
                }

                // 人脸检测
                RectVector faces = yoloDetector.detect(matImage);
                if (faces.size() == 0) {
                    matImage.release(); faces.close();
                    rejectReasons.add("第" + (i + 1) + "张未检测到人脸");
                    continue;
                }
                if (faces.size() > 1) {
                    matImage.release(); faces.close();
                    rejectReasons.add("第" + (i + 1) + "张检测到多张人脸");
                    continue;
                }

                // 人脸尺寸校验
                Rect faceRect = faces.get(0);
                if (faceRect.width() < matImage.cols() / 4
                        || faceRect.height() < matImage.rows() / 4) {
                    matImage.release(); faces.close();
                    rejectReasons.add("第" + (i + 1) + "张人脸太小");
                    continue;
                }

                // 检查2：正脸程度（宽高比）
                float aspect = (float) faceRect.width() / faceRect.height();
                System.out.printf("[注册] 帧%d aspect=%.2f liveness=%.1f%n",
                        i + 1, aspect, liveness);

                if (aspect < 0.7f || aspect > 1.3f) {
                    matImage.release(); faces.close();
                    rejectReasons.add("第" + (i + 1) + "张人脸角度过大(aspect="
                            + String.format("%.2f", aspect) + ")");
                    continue;
                }

                // 通过全部检查，提取特征
                Mat equalizedFace = cropAndEqualize(matImage, faceRect);
                validFeatures.add(featureExtractor.extract(equalizedFace));
                equalizedFace.release();
                matImage.release(); faces.close();
            }

            // 至少需要 1 张合格帧
            if (validFeatures.isEmpty()) {
                return "❌ 三张照片均不合格，无法注册！原因："
                        + String.join("；", rejectReasons);
            }

            // 打印筛选结果
            System.out.printf("[注册] 3张照片中%d张合格，%d张被淘汰%n",
                    validFeatures.size(), rejectReasons.size());
            for (String reason : rejectReasons) {
                System.out.println("  ↳ " + reason);
            }

            // L2 归一化取均值融合
            float[] fused = l2NormalizeAndAverage(validFeatures);

            // 注册
            String securePassword = passwordEncoder.encode(password);
            boolean success = faceService.registerFace(
                    username, securePassword, name, deptId, role, fused);

            if (success) {
                String msg = "✅ 注册成功（" + validFeatures.size() + "帧融合）";
                if (!rejectReasons.isEmpty()) {
                    msg += "，" + rejectReasons.size() + "帧因质量不合格被跳过";
                }
                return msg;
            } else {
                return "❌ 注册失败，用户名已存在";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "注册异常: " + e.getMessage();
        }
    }

    // ========== 管理接口 ==========
    @GetMapping("/admin/config/face-threshold")
    public String getFaceThreshold() {
        return systemConfigRepository.findById("FACE_THRESHOLD")
                .map(SystemConfig::getConfigValue).orElse("0.60");
    }

    @PostMapping("/admin/config/face-threshold")
    public String updateFaceThreshold(@RequestParam("value") String value) {
        SystemConfig config = systemConfigRepository.findById("FACE_THRESHOLD")
                .orElse(new SystemConfig());
        config.setConfigKey("FACE_THRESHOLD");
        config.setConfigValue(value);
        systemConfigRepository.save(config);
        return "✅ 阈值更新成功";
    }

    @GetMapping("/admin/departments")
    public List<Department> getDepartments() {
        return departmentRepository.findAll();
    }

    @PostMapping("/admin/department/add")
    public String addDepartment(@RequestParam("name") String name) {
        if (departmentRepository.existsByName(name)) return "❌ 部门名称已存在";
        Department d = new Department();
        d.setName(name);
        departmentRepository.save(d);
        return "✅ 新部门创建成功";
    }

    @PostMapping("/admin/department/delete")
    public String deleteDepartment(@RequestParam("id") Integer id) {
        if (faceService.hasEmployeesInDept(id)) return "❌ 删除失败：部门下仍有员工！";
        departmentRepository.deleteById(id);
        return "✅ 部门已注销";
    }

    @PostMapping("/admin/employee/department")
    public String updateEmployeeDepartment(@RequestParam("id") Integer empId,
                                           @RequestParam("deptId") Integer deptId) {
        return faceService.updateEmployeeDepartment(empId, deptId)
                ? "✅ 调岗成功" : "❌ 操作失败";
    }

    @GetMapping("/admin/attendance/all")
    public List<AttendanceLog> getAllAttendances() {
        return faceService.getAllAttendances();
    }
}
