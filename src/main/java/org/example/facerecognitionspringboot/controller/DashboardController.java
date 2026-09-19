package org.example.facerecognitionspringboot.controller;

import org.example.facerecognitionspringboot.dao.AttendanceLogRepository;
import org.example.facerecognitionspringboot.dao.EmployeeRepository;
import org.example.facerecognitionspringboot.dao.LeaveRecordRepository;
import org.example.facerecognitionspringboot.entity.AttendanceLog;
import org.example.facerecognitionspringboot.entity.Employee;
import org.example.facerecognitionspringboot.entity.LeaveRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 仪表盘（大屏）控制器
 * 路径前缀：/api/dashboard
 * 提供：今日统计总览、近7天趋势、异常名单（迟到/缺勤/请假等）
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private AttendanceLogRepository attendanceLogRepository;
    @Autowired private LeaveRecordRepository leaveRecordRepository;

    /**
     * 获取大屏基础统计数据（概览数字 + 近7天趋势）
     * @return 包含总人数、今日打卡、迟到、缺勤、请假人数及7天趋势的Map
     */
    @GetMapping("/statistics")
    public Map<String, Object> getStatistics() {
        Map<String, Object> result = new HashMap<>();

        // ----- 1. 总人数 -----
        long totalEmployees = employeeRepository.count();

        // ----- 2. 今日打卡人数（严格按人头去重）-----
        // 查询今天所有有打卡记录的员工ID列表，然后去重
        List<Integer> punchedEmpIds = attendanceLogRepository.findTodayPunchedEmpIds()
                .stream()
                .distinct()                      // 去重
                .collect(Collectors.toList());
        long todayPunches = punchedEmpIds.size();

        // ----- 3. 今日迟到人数（去重）-----
        List<AttendanceLog> lateLogs = attendanceLogRepository.findTodayLateLogs();
        long lateCount = lateLogs.stream()
                .map(AttendanceLog::getEmpId)    // 提取员工ID
                .distinct()                      // 去重
                .count();                        // 统计个数

        // ----- 4. 今日合法请假人数（去重）-----
        List<LeaveRecord> todayLeaves = leaveRecordRepository.findTodayApprovedLeaves();
        long leaveCount = todayLeaves.stream()
                .map(LeaveRecord::getEmpId)
                .distinct()
                .count();

        // ----- 5. 缺勤人数计算 -----
        // 缺勤 = 总人数 - 今日已打卡人数 - 今日请假人数
        long absentCount = totalEmployees - todayPunches - leaveCount;
        if (absentCount < 0) absentCount = 0; // 兜底防止负数

        // 将数据放入返回Map
        result.put("totalEmployees", totalEmployees);
        result.put("todayPunches", todayPunches);
        result.put("lateCount", lateCount);
        result.put("absentCount", absentCount);
        result.put("leaveCount", leaveCount);

        // ----- 6. 近7天趋势图数据 -----
        String[] trendDates = new String[7];   // X轴日期标签
        int[] normalData = new int[7];         // 每日打卡人数（去重）
        int[] lateData = new int[7];           // 每日迟到人数（去重）

        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");

        // 循环过去7天（从6天前到今日）
        for (int i = 0; i < 7; i++) {
            // 计算目标日期：i=0对应6天前，i=6对应今天
            LocalDate targetDate = today.minusDays(6 - i);

            // X轴标签：今天显示"今日"，其他显示 "03-15" 格式
            trendDates[i] = (i == 6) ? "今日" : targetDate.format(formatter);

            // 查询当日所有打卡记录，提取员工ID并去重，统计人数
            List<AttendanceLog> dayLogs = attendanceLogRepository.findByPunchDate(targetDate);
            Set<Integer> empIds = dayLogs.stream()
                    .map(AttendanceLog::getEmpId)
                    .collect(Collectors.toSet());
            normalData[i] = empIds.size();

            // 查询当日所有迟到记录，提取员工ID并去重
            List<AttendanceLog> dayLateLogs = attendanceLogRepository.findLateByDate(targetDate);
            Set<Integer> lateEmpIds = dayLateLogs.stream()
                    .map(AttendanceLog::getEmpId)
                    .collect(Collectors.toSet());
            lateData[i] = lateEmpIds.size();
        }

        result.put("trendDates", trendDates);
        result.put("normalData", normalData);
        result.put("lateData", lateData);

        return result;
    }

    /**
     * 获取大屏异常详情穿透数据（各类名单）
     * @return 包含全体员工、已打卡、迟到、缺勤、请假名单的Map
     */
    @GetMapping("/abnormal-details")
    public Map<String, Object> getAbnormalDetails() {
        Map<String, Object> result = new HashMap<>();

        // 0. 获取全体名单（脱敏）
        List<Employee> allEmps = employeeRepository.findAll();
        allEmps.forEach(e -> {
            e.setFaceFeature(null);   // 隐藏人脸特征
            e.setPassword(null);      // 隐藏密码
        });

        // 1. 获取今日已打卡员工ID列表（去重）
        List<Integer> punchedIds = attendanceLogRepository.findTodayPunchedEmpIds()
                .stream()
                .distinct()
                .collect(Collectors.toList());

        // 已打卡名单：从全部员工中筛选出 ID 在 punchedIds 中的人
        List<Employee> punchedList = allEmps.stream()
                .filter(e -> punchedIds.contains(e.getId()))
                .collect(Collectors.toList());

        // 2. 迟到名单去重（同一个员工多条迟到只保留最早的一条打卡记录）
        List<AttendanceLog> distinctLateList = attendanceLogRepository.findTodayLateLogs()
                .stream()
                .collect(Collectors.toMap(
                        AttendanceLog::getEmpId,           // key：员工ID
                        log -> log,                        // value：打卡记录本身
                        (existing, replacement) ->         // 冲突时：保留打卡时间更早的那条
                                existing.getPunchTime().isBefore(replacement.getPunchTime()) ? existing : replacement
                ))
                .values().stream()
                .collect(Collectors.toList());

        // 3. 请假名单去重（同一个员工当天只保留一条请假记录）
        List<LeaveRecord> leaveList = leaveRecordRepository.findTodayApprovedLeaves()
                .stream()
                .collect(Collectors.toMap(
                        LeaveRecord::getEmpId,
                        record -> record,
                        (existing, replacement) -> existing   // 冲突保留第一条
                ))
                .values().stream()
                .collect(Collectors.toList());

        // 提取请假员工的ID列表
        List<Integer> leaveIds = leaveList.stream()
                .map(LeaveRecord::getEmpId)
                .collect(Collectors.toList());

        // 4. 缺勤名单：不在已打卡名单且不在请假名单中的员工
        List<Employee> absentList = allEmps.stream()
                .filter(e -> !punchedIds.contains(e.getId()) && !leaveIds.contains(e.getId()))
                .collect(Collectors.toList());

        // 组装返回数据
        result.put("allList", allEmps);
        result.put("punchedList", punchedList);
        result.put("lateList", distinctLateList);
        result.put("absentList", absentList);
        result.put("leaveList", leaveList);

        return result;
    }
}