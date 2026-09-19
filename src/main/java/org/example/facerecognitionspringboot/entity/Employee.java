package org.example.facerecognitionspringboot.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 使用 Lombok @Data 自动生成 getter/setter/toString/equals/hashCode 等方法。</p>
 */
@Entity                     // 声明这是一个 JPA 实体类
@Table(name = "employee")   // 指定映射的数据库表名
@Data                       // Lombok 注解：自动生成所有字段的 getter/setter 及通用方法
public class Employee {

    /**
     * 主键 ID，自动递增。
     * 使用数据库自增策略，由 JPA 管理。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String username;
    private String password;

    private String name;

    @ManyToOne
    @JoinColumn(name = "dept_id")   // 外键列名
    private Department dept;
    private String role;

    /**
     * 人脸特征向量，存储为逗号分隔的字符串。
     * 例如： "0.12,-0.34,0.56,..." （共 512 个浮点数）
     * 使用 @Column(columnDefinition = "TEXT") 指定数据库列类型为 TEXT，防止字符串过长溢出。
     */
    @Column(columnDefinition = "TEXT")
    private String faceFeature;
}