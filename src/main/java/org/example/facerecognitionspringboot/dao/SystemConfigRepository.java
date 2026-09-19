package org.example.facerecognitionspringboot.dao;

import org.example.facerecognitionspringboot.entity.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * <h2>系统配置数据访问层</h2>
 * <p>用于读取和修改 system_config 表中的键值对配置，如上下班时间、人脸识别阈值等。</p>
 */
public interface SystemConfigRepository extends JpaRepository<SystemConfig, String> {

    /**
     * 根据配置键查找配置项。
     * 例如 configKey = "CHECK_IN_TIME" 可获取上班时间。
     *
     * @param configKey 配置项的键名
     * @return 包装了 SystemConfig 的 Optional，避免空指针
     */
    Optional<SystemConfig> findByConfigKey(String configKey);
}