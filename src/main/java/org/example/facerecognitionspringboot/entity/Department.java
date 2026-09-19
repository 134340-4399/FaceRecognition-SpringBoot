package org.example.facerecognitionspringboot.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "department")
@Data
public class Department {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id; // 部门ID（主键）

    private String name; // 部门名称
}