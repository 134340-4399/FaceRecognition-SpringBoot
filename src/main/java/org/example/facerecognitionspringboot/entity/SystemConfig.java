package org.example.facerecognitionspringboot.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "system_config")
@Data
public class SystemConfig {
    @Id
    private String configKey;   // 设置项的名称 (比如：CHECK_IN_TIME)
    private String configValue; // 设置项的值 (比如：09:00)
}