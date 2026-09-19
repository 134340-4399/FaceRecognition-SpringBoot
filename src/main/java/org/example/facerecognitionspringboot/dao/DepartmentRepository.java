    package org.example.facerecognitionspringboot.dao;

    import org.example.facerecognitionspringboot.entity.Department;
    import org.springframework.data.jpa.repository.JpaRepository;
    import org.springframework.stereotype.Repository;

    /**
     * <h2>部门数据访问层</h2>
     * <p>用于操作 department 表，除了继承自 JpaRepository 的基本方法外，提供了一个重名检查方法。</p>
     */
    @Repository
    public interface DepartmentRepository extends JpaRepository<Department, Integer> {

        /**
         * 根据部门名称检查是否已存在同名部门。
         * 方法名遵循 Spring Data 规范：existsByName，自动生成 SQL 查询。
         *
         * @param name 部门名称
         * @return true 表示已存在，false 表示可用
         */
        boolean existsByName(String name);
    }