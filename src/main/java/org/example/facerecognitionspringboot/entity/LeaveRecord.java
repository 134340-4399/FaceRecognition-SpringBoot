package org.example.facerecognitionspringboot.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "leave_record")
@Data
public class LeaveRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer empId;
    private String empName;

    // 🌟 核心升级：改用开始和结束时间点
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private String leaveType;
    private String reason;
    private String status;       // 待审批 / 已同意 / 已驳回
    private LocalDateTime applyTime;
}