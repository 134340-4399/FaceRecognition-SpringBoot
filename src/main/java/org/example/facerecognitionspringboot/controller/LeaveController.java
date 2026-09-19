package org.example.facerecognitionspringboot.controller;

import org.example.facerecognitionspringboot.dao.LeaveRecordRepository;
import org.example.facerecognitionspringboot.entity.LeaveRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/leave")
public class LeaveController {

    @Autowired
    private LeaveRecordRepository leaveRecordRepository;

    @PostMapping("/apply")
    public Map<String, Object> applyLeave(
            @RequestParam("empId") Integer empId,
            @RequestParam("empName") String empName,
            @RequestParam("startTime") String startTimeStr,
            @RequestParam("endTime") String endTimeStr,
            @RequestParam("leaveType") String leaveType,
            @RequestParam("reason") String reason) {

        Map<String, Object> result = new HashMap<>();
        DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime start = LocalDateTime.parse(startTimeStr, df);
        LocalDateTime end = LocalDateTime.parse(endTimeStr, df);

        if (start.isAfter(end)) {
            result.put("success", false);
            result.put("message", "结束时间不能早于开始时间！");
            return result;
        }

        LeaveRecord leave = new LeaveRecord();
        leave.setEmpId(empId);
        leave.setEmpName(empName);
        leave.setStartTime(start);
        leave.setEndTime(end);
        leave.setLeaveType(leaveType);
        leave.setReason(reason);
        leave.setStatus("待审批");
        leave.setApplyTime(LocalDateTime.now());

        leaveRecordRepository.save(leave);
        result.put("success", true);
        return result;
    }

    @GetMapping("/my/{empId}")
    public List<LeaveRecord> getMyLeaves(@PathVariable Integer empId) {
        return leaveRecordRepository.findByEmpIdOrderByApplyTimeDesc(empId);
    }

    @GetMapping("/pending")
    public List<LeaveRecord> getPendingLeaves() {
        return leaveRecordRepository.findByStatusOrderByApplyTimeDesc("待审批");
    }

    @PostMapping("/audit")
    public Map<String, Object> auditLeave(@RequestParam("id") Integer id, @RequestParam("status") String status) {
        Map<String, Object> result = new HashMap<>();
        leaveRecordRepository.findById(id).ifPresent(l -> {
            l.setStatus(status);
            leaveRecordRepository.save(l);
        });
        result.put("success", true);
        return result;
    }
}