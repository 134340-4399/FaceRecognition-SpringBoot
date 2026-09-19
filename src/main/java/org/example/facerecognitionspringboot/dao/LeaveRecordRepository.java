package org.example.facerecognitionspringboot.dao;

import org.example.facerecognitionspringboot.entity.LeaveRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;

public interface LeaveRecordRepository extends JpaRepository<LeaveRecord, Integer> {

    @Query("SELECT COUNT(l) FROM LeaveRecord l "
            + "WHERE l.empId = ?1 AND l.status != '已驳回' "
            + "AND l.startTime < ?3 AND l.endTime > ?2")
    long countOverlappingLeaves(Integer empId, LocalDateTime startTime, LocalDateTime endTime);

    List<LeaveRecord> findByEmpIdOrderByApplyTimeDesc(Integer empId);

    List<LeaveRecord> findByStatusOrderByApplyTimeDesc(String status);

    @Query(value = "SELECT * FROM leave_record "
            + "WHERE status = '已同意' "
            + "AND DATE(start_time) <= CURDATE() AND DATE(end_time) >= CURDATE()",
            nativeQuery = true)
    List<LeaveRecord> findTodayApprovedLeaves();

    // 新增：事务性批量删除（级联删除员工时使用）
    @Modifying
    @Query("DELETE FROM LeaveRecord l WHERE l.empId = ?1")
    void deleteByEmpId(Integer empId);
}
