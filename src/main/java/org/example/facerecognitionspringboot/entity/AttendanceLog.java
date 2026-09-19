    package org.example.facerecognitionspringboot.entity;

    import jakarta.persistence.*;
    import lombok.Data;
    import java.time.LocalDateTime;

    @Entity
    @Table(name = "attendance_log")
    @Data
    public class AttendanceLog {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Integer id;

        private Integer empId;      // 员工ID
        private String empName;     // 员工姓名

        // 🌅 上班打卡与迟到
        private LocalDateTime punchTime; // 上班打卡时间
        private Integer lateMinutes;     // 迟到了多少分钟

        // 🌃 下班打卡与早退
        private LocalDateTime checkOutTime; // 下班打卡时间
        private Integer earlyMinutes;       // 早退了多少分钟

        // 🤖 综合状态与AI判定
        private String status;      // 状态：正常/迟到/早退/迟到&早退
        private Float similarity;   // 刷脸相似度
    }