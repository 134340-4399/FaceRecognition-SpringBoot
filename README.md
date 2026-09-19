# 智能考勤系统（FaceRecognition-SpringBoot）

基于人脸识别的智能考勤系统，支持员工注册、刷脸打卡、考勤统计与请假管理。

## 技术栈

- **后端**：Java 17 + Spring Boot 3.2 + Spring Data JPA + MySQL
- **人脸检测**：YOLO（ONNX Runtime 推理）
- **人脸特征提取**：ArcFace（OpenCV DNN 推理，512 维特征向量）
- **认证**：JWT + RSA 前端加密 + BCrypt 密码哈希
- **前端**：Vue 3（见同级目录 `Face-Web`）

## 核心流程

```
图片 → YOLO 人脸检测 → 活体检测(拉普拉斯方差) → 裁剪+直方图均衡化
     → ArcFace 特征提取(512维 + L2归一化) → 余弦相似度匹配 → 打卡/注册
```

### 关键设计

- **三连拍注册**：三张不同角度照片 → 过滤低质量帧（模糊/侧脸/人脸太小）→ 对合格帧做 L2 归一化取均值融合。
- **分级匹配阈值**：≥ 0.75 直接通过，< 0.55 直接拒绝，区间内需与次高分拉开 0.1 以上才通过。
- **动态模板更新**：高置信度打卡时扩充特征模板（最多 3 个，淘汰最相似者保持多样性）。
- **上下班判定**：按配置的上下班时间自动判迟到/早退，3 分钟防抖。

## ⚠️ 模型文件未提交

人脸检测与特征提取依赖两个 ONNX 模型，**体积约 244MB，未提交到仓库**：

| 文件 | 用途 | 生成方式 |
|---|---|---|
| `src/main/resources/model/best.onnx` | YOLO 人脸检测 | 自行训练 YOLO 后导出 ONNX |
| `src/main/resources/model/arcface.onnx` | ArcFace 特征提取 | 从 ArcFace 预训练权重转换 |

训练后导出 ONNX 放入上述路径即可运行。模型转换参考项目根目录 `convert_model.py`。

## 快速开始

### 1. 准备数据库

```sql
CREATE DATABASE face_attendance DEFAULT CHARSET utf8mb4;
```

### 2. 配置私密信息

复制以下内容到 `src/main/resources/application-secret.properties`（已被 `.gitignore` 排除）：

```properties
DB_PASSWORD=你的数据库密码
JWT_SECRET=你的JWT密钥
```

### 3. 放置模型

按上表把两个 `.onnx` 放入 `src/main/resources/model/`。

### 4. 启动

```bash
mvn spring-boot:run
```

默认端口 8080，前端 `Face-Web` 通过反向代理对接。

## 主要接口

| 接口 | 说明 |
|---|---|
| `POST /api/detect` | 刷脸打卡（活体检测 + 识别） |
| `POST /api/register` | 三连拍注册 |
| `POST /api/auth/login` | 登录（RSA 加密密码） |
| `GET /api/auth/public-key` | 获取 RSA 公钥 |
| `GET /api/admin/attendance/all` | 考勤记录 |
| `POST /api/admin/config/face-threshold` | 调整识别阈值 |
