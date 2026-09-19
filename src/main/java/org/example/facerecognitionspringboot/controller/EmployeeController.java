package org.example.facerecognitionspringboot.controller;

import org.example.facerecognitionspringboot.dao.AttendanceLogRepository;
import org.example.facerecognitionspringboot.entity.AttendanceLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 员工端控制器
 * 路径前缀：/api/employee
 * 负责：员工查看自己的考勤记录等
 */
@RestController
@RequestMapping("/api/employee")
public class EmployeeController {

    @Autowired
    private AttendanceLogRepository attendanceLogRepository;

    /**
     * 获取某员工的全部打卡流水（按时间倒序）
     * @param empId 员工ID（从路径中获取）
     * @return 该员工的考勤日志列表
     */
    @GetMapping("/attendance/{empId}")
    public List<AttendanceLog> getMyAttendance(@PathVariable Integer empId) {
        // 调用数据访问层，查询该员工的所有打卡记录，按打卡时间降序排列
        return attendanceLogRepository.findByEmpIdOrderByPunchTimeDesc(empId);
    }
}