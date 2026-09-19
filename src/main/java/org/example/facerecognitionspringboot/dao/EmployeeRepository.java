package org.example.facerecognitionspringboot.dao;

import org.example.facerecognitionspringboot.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface EmployeeRepository extends JpaRepository<Employee, Integer> {

    Employee findByUsername(String username);

    List<Employee> findByFaceFeatureIsNotNull();

    // 新增：检查某部门下是否有员工（删除部门前校验用）
    @Query("SELECT COUNT(e) > 0 FROM Employee e WHERE e.dept.id = ?1")
    boolean existsByDeptId(Integer deptId);
}
