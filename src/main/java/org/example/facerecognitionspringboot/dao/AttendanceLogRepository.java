package org.example.facerecognitionspringboot.dao;

import org.example.facerecognitionspringboot.entity.AttendanceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

public interface AttendanceLogRepository extends JpaRepository<AttendanceLog, Integer> {

    AttendanceLog findFirstByEmpIdAndPunchTimeBetween(
            Integer empId, LocalDateTime startOfDay, LocalDateTime endOfDay);

    @Query(value = "SELECT COUNT(DISTINCT emp_id) FROM attendance_log "
            + "WHERE DATE(punch_time) = CURDATE()", nativeQuery = true)
    long countTodayPunches();

    List<AttendanceLog> findByEmpIdOrderByPunchTimeDesc(Integer empId);

    @Query(value = "SELECT * FROM attendance_log "
            + "WHERE DATE(punch_time) = CURDATE() AND late_minutes > 0", nativeQuery = true)
    List<AttendanceLog> findTodayLateLogs();

    @Query(value = "SELECT DISTINCT emp_id FROM attendance_log "
            + "WHERE DATE(punch_time) = CURDATE()", nativeQuery = true)
    List<Integer> findTodayPunchedEmpIds();

    @Query(value = "SELECT COUNT(DISTINCT emp_id) FROM attendance_log "
            + "WHERE DATE(punch_time) = ?1", nativeQuery = true)
    long countPunchesByDate(LocalDate date);

    @Query(value = "SELECT COUNT(DISTINCT emp_id) FROM attendance_log "
            + "WHERE DATE(punch_time) = ?1 AND late_minutes > 0", nativeQuery = true)
    long countLateByDate(LocalDate date);

    @Query(value = "SELECT * FROM attendance_log WHERE DATE(punch_time) = ?1",
            nativeQuery = true)
    List<AttendanceLog> findByPunchDate(LocalDate date);

    @Query(value = "SELECT * FROM attendance_log "
            + "WHERE DATE(punch_time) = ?1 AND late_minutes > 0", nativeQuery = true)
    List<AttendanceLog> findLateByDate(LocalDate date);

    // 新增：事务性批量删除（级联删除员工时使用）
    @Modifying
    @Query("DELETE FROM AttendanceLog a WHERE a.empId = ?1")
    void deleteByEmpId(Integer empId);
}
