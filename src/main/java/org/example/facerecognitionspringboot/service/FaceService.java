package org.example.facerecognitionspringboot.service;

import org.example.facerecognitionspringboot.dao.*;
import org.example.facerecognitionspringboot.entity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FaceService {

    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private AttendanceLogRepository attendanceLogRepository;
    @Autowired private SystemConfigRepository systemConfigRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private LeaveRecordRepository leaveRecordRepository;

    private final ConcurrentHashMap<Integer, Object> empLocks = new ConcurrentHashMap<>();

    // ==================== 注册 ====================

    /**
     * 注册人脸特征（用户名重复则拒绝，不再静默覆盖）
     */
    public boolean registerFace(String username, String encodedPassword, String name,
                                Integer deptId, String role, float[] features) {
        Employee existing = employeeRepository.findByUsername(username);
        if (existing != null) return false;    // 用户名重复直接拒绝，不覆盖

        Employee emp = new Employee();
        emp.setUsername(username);
        emp.setPassword(encodedPassword);
        emp.setName(name);
        emp.setRole(role);
        if (deptId != null) {
            emp.setDept(departmentRepository.findById(deptId).orElse(null));
        }
        emp.setFaceFeature(floatArrayToString(features));
        employeeRepository.save(emp);
        return true;
    }

    // ==================== 识别 + 打卡 ====================

    /**
     * 分级多模板匹配（内部含打卡副作用）
     *
     * 判定逻辑：
     *   >= SAFE_THRESHOLD (0.75)  → 直接通过
     *   < ABSOLUTE_MIN   (0.55)  → 直接拒绝
     *   其他                       → 需要与次高分差距 > 0.1 才通过
     */
    public Employee findMatchedEmployee(float[] currentFeatures) {
        String thresholdStr = systemConfigRepository.findById("FACE_THRESHOLD")
                .map(SystemConfig::getConfigValue).orElse("0.60");
        float threshold = Float.parseFloat(thresholdStr);
        final float SAFE_THRESHOLD = 0.75f;
        final float ABSOLUTE_MIN = 0.55f;

        List<Employee> allEmployees = employeeRepository.findByFaceFeatureIsNotNull();
        List<Map.Entry<Employee, Float>> candidates = new ArrayList<>();

        for (Employee emp : allEmployees) {
            try {
                List<float[]> templates = parseTemplates(emp.getFaceFeature());
                float best = 0;
                for (float[] t : templates) {
                    float sim = cosineSimilarity(currentFeatures, t);
                    if (sim > best) best = sim;
                }
                if (best >= threshold) {
                    candidates.add(new AbstractMap.SimpleEntry<>(emp, best));
                }
            } catch (Exception e) {
                System.err.println("员工 " + emp.getId() + " 特征数据异常，已跳过");
            }
        }

        if (candidates.isEmpty()) return null;
        candidates.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));

        Employee topEmp = candidates.get(0).getKey();
        float topScore = candidates.get(0).getValue();
        float secondScore = candidates.size() > 1 ? candidates.get(1).getValue() : 0.0f;

        System.out.printf("最高分: %.3f (%s), 次高分: %.3f%n",
                topScore, topEmp.getName(), secondScore);

        // 分级判定
        if (topScore >= SAFE_THRESHOLD) {
            recordPunch(topEmp, topScore, currentFeatures);
            return topEmp;
        }
        if (topScore < ABSOLUTE_MIN) return null;
        if (topScore - secondScore > 0.1f) {
            recordPunch(topEmp, topScore, currentFeatures);
            return topEmp;
        }

        System.out.println("拒绝识别：最高分与次高分差距不足");
        return null;
    }

    /**
     * 打卡记录 + 动态模板更新（员工级锁保护）
     */
    private void recordPunch(Employee topEmp, float topScore, float[] currentFeatures) {
        Object lock = empLocks.computeIfAbsent(topEmp.getId(), k -> new Object());
        synchronized (lock) {
            doRecordPunch(topEmp, topScore);
            if (topScore > 0.80f) {
                updateTemplates(topEmp, currentFeatures); // 高置信度时扩充模板
            }
        }
    }

    /**
     * 执行上下班打卡判定
     */
    private void doRecordPunch(Employee topEmp, float topScore) {
        String checkInTimeStr = systemConfigRepository.findById("CHECK_IN_TIME")
                .map(SystemConfig::getConfigValue).orElse("09:00");
        String checkOutTimeStr = systemConfigRepository.findById("CHECK_OUT_TIME")
                .map(SystemConfig::getConfigValue).orElse("18:00");
        LocalTime targetIn = LocalTime.parse(checkInTimeStr);
        LocalTime targetOut = LocalTime.parse(checkOutTimeStr);
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();

        AttendanceLog todayLog = attendanceLogRepository.findFirstByEmpIdAndPunchTimeBetween(
                topEmp.getId(), today.atStartOfDay(), today.plusDays(1).atStartOfDay());

        if (todayLog == null) {
            // 首次打卡 → 上班签到
            AttendanceLog log = new AttendanceLog();
            log.setEmpId(topEmp.getId());
            log.setEmpName(topEmp.getName());
            log.setPunchTime(now);
            log.setSimilarity(topScore);
            long lateMins = Duration.between(targetIn, now.toLocalTime()).toMinutes();
            log.setStatus(lateMins > 0 ? "迟到" : "正常");
            log.setLateMinutes(lateMins > 0 ? (int) lateMins : 0);
            attendanceLogRepository.save(log);
        } else {
            // 防抖：距上次操作不到 3 分钟则忽略
            LocalDateTime lastAction = todayLog.getCheckOutTime() != null
                    ? todayLog.getCheckOutTime()
                    : todayLog.getPunchTime();
            if (Duration.between(lastAction, now).toMinutes() < 3) return;

            // 后续打卡 → 下班签退
            todayLog.setCheckOutTime(now);
            todayLog.setSimilarity(topScore);
            long earlyMins = Duration.between(now.toLocalTime(), targetOut).toMinutes();
            todayLog.setEarlyMinutes(earlyMins > 0 ? (int) earlyMins : 0);

            String lateStatus = (todayLog.getLateMinutes() != null
                    && todayLog.getLateMinutes() > 0) ? "迟到" : "";
            String earlyStatus = earlyMins > 0 ? "早退" : "";
            todayLog.setStatus(
                    lateStatus.isEmpty() && earlyStatus.isEmpty()
                            ? "正常"
                            : (lateStatus + " " + earlyStatus).trim().replace(" ", " & "));
            attendanceLogRepository.save(todayLog);
        }
    }

    /**
     * 动态模板更新：最多保留 3 个，淘汰与新特征最相似的（保持多样性）
     */
    private void updateTemplates(Employee emp, float[] newFeatures) {
        List<float[]> templates = parseTemplates(emp.getFaceFeature());
        templates.add(newFeatures.clone());

        while (templates.size() > 3) {
            int mostSimilarIdx = 0;
            float maxSim = -1;
            for (int i = 0; i < templates.size() - 1; i++) {
                float sim = cosineSimilarity(newFeatures, templates.get(i));
                if (sim > maxSim) {
                    maxSim = sim;
                    mostSimilarIdx = i;
                }
            }
            templates.remove(mostSimilarIdx);
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < templates.size(); i++) {
            sb.append(floatArrayToString(templates.get(i)));
            if (i < templates.size() - 1) sb.append(";;;");
        }
        emp.setFaceFeature(sb.toString());
        employeeRepository.save(emp);
    }

    // ==================== 特征融合 ====================

    /**
     * 质量加权平均融合
     */
    public float[] weightedAverageFusion(List<float[]> features, List<Float> weights) {
        int dim = features.get(0).length;
        double[] sum = new double[dim];
        double totalWeight = 0.0;
        for (int i = 0; i < features.size(); i++) {
            float[] f = features.get(i);
            float w = weights.get(i);
            for (int j = 0; j < dim; j++) sum[j] += f[j] * w;
            totalWeight += w;
        }
        float[] fused = new float[dim];
        float norm = 0.0f;
        for (int j = 0; j < dim; j++) {
            fused[j] = (float) (sum[j] / totalWeight);
            norm += fused[j] * fused[j];
        }
        norm = (float) Math.sqrt(norm);
        for (int j = 0; j < dim; j++) fused[j] /= norm;
        return fused;
    }

    // ==================== 事务性级联删除 ====================

    /**
     * 级联删除员工及所有关联考勤/请假记录（@Transactional 保证原子性）
     */
    @Transactional
    public void deleteEmployeeCascade(Integer empId) {
        attendanceLogRepository.deleteByEmpId(empId);
        leaveRecordRepository.deleteByEmpId(empId);
        employeeRepository.deleteById(empId);
    }

    // ==================== 管理辅助方法 ====================

    public boolean updateEmployeeDepartment(Integer empId, Integer deptId) {
        Employee emp = employeeRepository.findById(empId).orElse(null);
        if (emp == null) return false;
        Department dept = departmentRepository.findById(deptId).orElse(null);
        if (dept == null) return false;
        emp.setDept(dept);
        employeeRepository.save(emp);
        return true;
    }

    public boolean hasEmployeesInDept(Integer deptId) {
        return employeeRepository.existsByDeptId(deptId);
    }

    public List<AttendanceLog> getAllAttendances() {
        return attendanceLogRepository.findAll(Sort.by(Sort.Direction.DESC, "punchTime"));
    }

    // ==================== 工具方法 ====================

    private float cosineSimilarity(float[] a, float[] b) {
        float dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / (float) (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private List<float[]> parseTemplates(String featureStr) {
        List<float[]> list = new ArrayList<>();
        if (featureStr == null || featureStr.isEmpty()) return list;
        if (featureStr.contains(";;;")) {
            for (String part : featureStr.split(";;;")) {
                list.add(stringToFloatArray(part));
            }
        } else {
            list.add(stringToFloatArray(featureStr));
        }
        return list;
    }

    private String floatArrayToString(float[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            sb.append(arr[i]);
            if (i < arr.length - 1) sb.append(",");
        }
        return sb.toString();
    }

    private float[] stringToFloatArray(String s) {
        String[] p = s.split(",");
        float[] a = new float[p.length];
        for (int i = 0; i < p.length; i++) a[i] = Float.parseFloat(p[i]);
        return a;
    }
}
