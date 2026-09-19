package org.example.facerecognitionspringboot.controller;

import org.example.facerecognitionspringboot.dao.AttendanceLogRepository;
import org.example.facerecognitionspringboot.dao.EmployeeRepository;
import org.example.facerecognitionspringboot.dao.LeaveRecordRepository;
import org.example.facerecognitionspringboot.dao.SystemConfigRepository;
import org.example.facerecognitionspringboot.entity.AttendanceLog;
import org.example.facerecognitionspringboot.entity.Employee;
import org.example.facerecognitionspringboot.entity.LeaveRecord;
import org.example.facerecognitionspringboot.entity.SystemConfig;
import org.example.facerecognitionspringboot.service.FaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired private LeaveRecordRepository leaveRecordRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private AttendanceLogRepository attendanceLogRepository;
    @Autowired private SystemConfigRepository systemConfigRepository;
    @Autowired private FaceService faceService;

    @GetMapping("/employees")
    public List<Employee> getAllEmployees() {
        List<Employee> list = employeeRepository.findAll();
        for (Employee emp : list) {
            emp.setFaceFeature(null);
            emp.setPassword(null);
        }
        return list;
    }

    @PostMapping("/role/update")
    public Map<String, Object> updateRole(@RequestParam("id") Integer id,
                                          @RequestParam("role") String role) {
        Map<String, Object> result = new HashMap<>();
        Employee emp = employeeRepository.findById(id).orElse(null);
        if (emp != null) {
            emp.setRole(role);
            employeeRepository.save(emp);
            result.put("success", true);
        } else {
            result.put("success", false);
            result.put("message", "员工不存在！");
        }
        return result;
    }

    @GetMapping("/attendance/{empId}")
    public List<AttendanceLog> getEmployeeAttendance(@PathVariable Integer empId) {
        return attendanceLogRepository.findByEmpIdOrderByPunchTimeDesc(empId);
    }

    @GetMapping("/config/checkin-time")
    public String getCheckInTime() {
        return systemConfigRepository.findById("CHECK_IN_TIME")
                .map(SystemConfig::getConfigValue).orElse("09:00");
    }

    @PostMapping("/config/checkin-time")
    public Map<String, Object> setCheckInTime(@RequestParam("time") String time) {
        SystemConfig config = new SystemConfig();
        config.setConfigKey("CHECK_IN_TIME");
        config.setConfigValue(time);
        systemConfigRepository.save(config);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return result;
    }

    @GetMapping("/config/checkout-time")
    public String getCheckOutTime() {
        return systemConfigRepository.findById("CHECK_OUT_TIME")
                .map(SystemConfig::getConfigValue).orElse("18:00");
    }

    @PostMapping("/config/checkout-time")
    public Map<String, Object> setCheckOutTime(@RequestParam("time") String time) {
        SystemConfig config = new SystemConfig();
        config.setConfigKey("CHECK_OUT_TIME");
        config.setConfigValue(time);
        systemConfigRepository.save(config);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return result;
    }

    /**
     * 删除员工 —— 事务保护（考勤、请假、员工记录要么全删，要么全不删）
     */
    @PostMapping("/employee/delete")
    public Map<String, Object> deleteEmployee(@RequestParam("id") Integer id) {
        Map<String, Object> result = new HashMap<>();
        try {
            faceService.deleteEmployeeCascade(id);
            result.put("success", true);
            result.put("message", "员工及其关联记录已成功删除！");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "删除失败：" + e.getMessage());
        }
        return result;
    }

    @PostMapping("/leave/add")
    public Map<String, Object> addLeave(
            @RequestParam("empId") Integer empId,
            @RequestParam("empName") String empName,
            @RequestParam("startTime") String startTimeStr,
            @RequestParam("endTime") String endTimeStr,
            @RequestParam("leaveType") String leaveType) {

        Map<String, Object> result = new HashMap<>();
        DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime start = LocalDateTime.parse(startTimeStr, df);
        LocalDateTime end = LocalDateTime.parse(endTimeStr, df);

        if (start.isAfter(end) || start.isEqual(end)) {
            result.put("success", false);
            result.put("message", "结束时间必须晚于开始时间！");
            return result;
        }

        if (leaveRecordRepository.countOverlappingLeaves(empId, start, end) > 0) {
            result.put("success", false);
            result.put("message", "该员工在该时间段内已有请假记录！");
            return result;
        }

        LeaveRecord leave = new LeaveRecord();
        leave.setEmpId(empId);
        leave.setEmpName(empName);
        leave.setStartTime(start);
        leave.setEndTime(end);
        leave.setLeaveType(leaveType);
        leave.setReason("管理员手动后台登记");
        leave.setStatus("已同意");
        leave.setApplyTime(LocalDateTime.now());
        leaveRecordRepository.save(leave);

        result.put("success", true);
        result.put("message", "请假单已生效！");
        return result;
    }
}
