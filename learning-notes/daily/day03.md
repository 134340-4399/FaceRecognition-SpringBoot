# Day 03 学习笔记

> 日期：2026年4月5日  
> 学习阶段：第7-9天（关键代码深度解析）  
> 标签：#毕设/人脸识别 #ClaudeCode/learning #学习日志

## 今日学习目标
- [x] 深入分析YOLOv8人脸检测算法的Java实现
- [x] 理解ArcFace特征提取的具体实现
- [x] 掌握余弦相似度比对算法
- [x] 分析多特征融合的数学原理
- [x] 理解JWT和RSA安全算法的实现细节
- [x] 生成核心代码解析文档

## 学习内容记录

### 1. YOLOv8人脸检测深入分析

**核心文件**：`YoloFaceDetector.java`

#### 1.1 ONNX Runtime模型加载
```java
// 动态获取CPU核心数优化推理性能
int threads = Runtime.getRuntime().availableProcessors();
options.setIntraOpNumThreads(threads);
```
- 使用ONNX Runtime而非OpenCV DNN，因为YOLO需要高性能推理
- 动态线程配置充分利用多核CPU

#### 1.2 图像预处理算法细节
**关键步骤**：
1. **保持长宽比缩放**：长边缩放到640，短边按比例缩放
2. **对称灰色填充**：用(114,114,114)填充到640×640
3. **BGR→RGB转换**：YOLO模型要求RGB输入
4. **归一化**：像素值从[0,255]归一化到[0,1]
5. **格式转换**：HWC → NCHW（批次,通道,高,宽）

**数学计算**：
```java
float scale = Math.min(640.0f / height, 640.0f / width);
float padX = (640 - width * scale) / 2.0f;
float padY = (640 - height * scale) / 2.0f;
```

#### 1.3 YOLO输出解析
**输出格式**：`[1, 300, 6]`（1张图，最多300个检测框，6个值）
- 每个框：`[cx, cy, width, height, confidence, class_id]`
- 本项目只检测人脸，所以`class_id`始终为0

**坐标转换公式**：
```java
// 中心点转左上角
float x1 = cx - width / 2.0f;
float y1 = cy - height / 2.0f;

// 去除填充，缩放回原图
x1 = (x1 - padX) / scale;
y1 = (y1 - padY) / scale;
float realWidth = width / scale;
float realHeight = height / scale;
```

### 2. ArcFace特征提取深入分析

**核心文件**：`FaceFeatureExtractor.java`

#### 2.1 OpenCV DNN模型加载
- 使用OpenCV DNN而非ONNX Runtime，简化特征提取流程
- 模型路径：`src/main/resources/model/arcface.onnx`

#### 2.2 自动预处理（blobFromImage）
```java
Mat blob = opencv_dnn.blobFromImage(
    faceImage,
    1.0 / 127.5,           // 缩放：/127.5 → 范围[-1,1]
    new Size(112, 112),    // 输入尺寸：112×112
    new Scalar(127.5, 127.5, 127.5, 0.0), // 减去均值127.5
    true,                  // BGR转RGB
    false,                 // 不裁剪
    CV_32F                 // 32位浮点
);
```
- **一句话完成所有预处理**：OpenCV的`blobFromImage`太强大了

#### 2.3 512维特征向量提取
```java
float[] features = new float[512];
FloatIndexer indexer = flatOutput.createIndexer();
for (int i = 0; i < 512; i++) {
    features[i] = indexer.get(0, i);
}
```
- ArcFace输出512维单位向量（L2归一化）
- 存储在超球面上，适合余弦相似度计算

### 3. 人脸识别业务逻辑深入分析

**核心文件**：`FaceService.java`

#### 3.1 余弦相似度算法实现
```java
private float calculateCosineSimilarity(float[] v1, float[] v2) {
    float dot = 0, nA = 0, nB = 0;
    for (int i = 0; i < v1.length; i++) {
        dot += v1[i] * v2[i];  // 点积
        nA += v1[i] * v1[i];   // ||v1||²
        nB += v2[i] * v2[i];   // ||v2||²
    }
    return dot / (float) (Math.sqrt(nA) * Math.sqrt(nB));
}
```
**数学原理**：
```
cosθ = (v1·v2) / (||v1|| × ||v2||)
```
- 特征向量已L2归一化，所以`||v|| = 1`，实际就是点积
- 相似度范围：[-1, 1]，1表示完全相同

#### 3.2 动态阈值机制
```java
String thresholdStr = systemConfigRepository.findById("FACE_THRESHOLD")
        .map(SystemConfig::getConfigValue).orElse("0.60");
float dynamicThreshold = Float.parseFloat(thresholdStr);
```
- 阈值存储在数据库，可动态调整（默认0.60）
- 超过阈值且相似度最高的员工被选中

#### 3.3 考勤状态计算算法
```java
// 迟到计算
long lateMins = Duration.between(targetInTime, now.toLocalTime()).toMinutes();
log.setStatus(lateMins > 0 ? "迟到" : "正常");

// 早退计算  
long earlyMins = Duration.between(now.toLocalTime(), targetOutTime).toMinutes();
todayLog.setEarlyMinutes(earlyMins > 0 ? (int) earlyMins : 0);

// 状态组合
String lateStatus = (todayLog.getLateMinutes() > 0) ? "迟到" : "";
String earlyStatus = earlyMins > 0 ? "早退" : "";
todayLog.setStatus(lateStatus.isEmpty() && earlyStatus.isEmpty() ? "正常" 
        : (lateStatus + " " + earlyStatus).trim().replace(" ", " & "));
```
**业务规则**：
- 可识别"迟到"、"早退"、"迟到 & 早退"、"正常"
- 3分钟内防重复打卡：`Duration.between(lastActionTime, now).toMinutes() < 3`

### 4. 多特征融合算法分析

**核心文件**：`FeatureUtils.java`

#### 4.1 L2归一化融合原理
```java
public static float[] fuseFeaturesL2(float[][] featureList) {
    // 1. 每个向量L2归一化：v_norm = v / ||v||
    // 2. 累加所有归一化向量：sum = Σ v_norm_i
    // 3. 结果二次归一化：result = sum / ||sum||
}
```

**数学意义**：
1. 减少单张图片的噪声影响
2. 提取多人脸特征的平均表征
3. 保持特征向量的单位长度

### 5. 安全算法实现细节

#### 5.1 JWT令牌生成（JwtUtils.java）
```java
public static String generateToken(Integer empId, String role) {
    return JWT.create()
            .withClaim("empId", empId)   // 自定义声明
            .withClaim("role", role)
            .withExpiresAt(expireDate)   // 7天有效期
            .sign(Algorithm.HMAC256(SECRET_KEY)); // HMAC-SHA256签名
}
```
- 密钥：硬编码在代码中（生产环境应使用配置）
- 包含信息：员工ID和角色
- 验证：`JWT.require(Algorithm.HMAC256(SECRET_KEY)).build().verify(token)`

#### 5.2 RSA加密解密（RsaUtils.java）
```java
public static Map<String, Object> genKeyPair() throws Exception {
    KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
    keyPairGen.initialize(1024); // 1024位密钥
    KeyPair keyPair = keyPairGen.generateKeyPair();
}
```
**安全流程**：
1. 服务端生成1024位RSA密钥对
2. 公钥传给前端，前端加密密码
3. 服务端用私钥解密，得到明文密码
4. 用BCrypt验证密码哈希

### 6. 数据结构设计

#### 6.1 特征向量存储
```java
@Column(columnDefinition = "TEXT")
private String faceFeature; // "0.123,0.456,0.789,...,0.234"
```
- 512个浮点数用逗号分隔存储在TEXT字段
- 解析：`stringToFloatArray()`方法

#### 6.2 线程安全设计
```java
private final ConcurrentHashMap<Integer, Object> empLocks = new ConcurrentHashMap<>();

Object lock = empLocks.computeIfAbsent(empId, k -> new Object());
synchronized (lock) {
    // 考勤记录操作
}
```
- 防止同一员工并发打卡导致数据错误
- 每个员工有自己的锁对象

## 今日学习复盘

### 1. 今天学会了什么？
✅ **YOLOv8完整实现**：从预处理、推理到后处理的每个细节  
✅ **ArcFace特征提取**：OpenCV DNN的自动预处理和512维向量提取  
✅ **余弦相似度算法**：数学原理和代码实现（点积和模长计算）  
✅ **多特征融合数学**：L2归一化的两次归一化过程  
✅ **JWT和RSA实现**：HMAC-SHA256签名和1024位RSA加密  
✅ **业务逻辑细节**：考勤状态计算、防重复打卡、动态阈值  
✅ **数据结构设计**：特征向量存储、线程安全锁机制  
✅ **性能优化技巧**：ONNX Runtime多线程、内存释放

### 2. 哪里还不清楚？
❓ **ONNX模型转换细节**：YOLOv8和ArcFace模型如何从PyTorch转换为ONNX  
❓ **活体检测算法**：Laplacian方差计算图像清晰度的具体原理  
❓ **特征向量维度**：为什么ArcFace输出512维？其他维度（256、1024）效果如何？  
❓ **相似度阈值调优**：0.60阈值是如何确定的？不同场景下的最佳阈值  
❓ **模型性能对比**：YOLOv8与其他人脸检测模型（MTCNN、RetinaFace）的对比  
❓ **大规模数据优化**：如果员工数超过1000，线性扫描比对是否效率太低？  
❓ **前端加密实现**：前端如何用JavaScript进行RSA加密？  
❓ **模型更新机制**：如何在不重启服务的情况下更新模型文件？

### 3. 明天想重点学什么？
🔍 **前端交互实现**：HTML+JavaScript如何调用摄像头和API  
🔍 **完整业务流程**：从用户打开网页到识别成功的完整数据流  
🔍 **数据库设计**：所有实体类的字段设计和关联关系  
🔍 **系统配置管理**：动态阈值、上下班时间等配置项的维护  
🔍 **异常处理机制**：网络错误、模型加载失败等异常情况的处理  
🔍 **测试和验证**：如何测试人脸识别准确率和系统稳定性  
🔍 **部署和运维**：如何将系统部署到生产环境

## 明日学习计划
根据15天学习计划，进入**第10-12天：数据流与业务流程**阶段：

1. **前端交互分析**（2小时）
   - 分析`index.html`的摄像头调用和Canvas截图
   - 理解Fetch API调用后端接口的细节
   - 查看实时显示和状态更新的逻辑

2. **完整业务流程追踪**（2小时）
   - 从摄像头→YOLO→ArcFace→比对的完整代码调用链
   - 异常处理和错误恢复机制
   - 数据在前后端的流转过程

3. **数据库和配置分析**（1小时）
   - 查看所有实体类的设计
   - 分析Repository的查询方法
   - 理解系统配置表的用途

## 代码实现亮点

### 1. 工程化设计
- **模块分离清晰**：检测、特征提取、业务逻辑分层明确
- **配置化设计**：阈值、时间等参数可动态配置
- **线程安全考虑**：并发打卡的锁机制

### 2. 性能优化
- **多线程推理**：ONNX Runtime自动利用多核CPU
- **内存管理**：及时释放OpenCV Mat对象
- **防重复处理**：1秒间隔的轮询检测

### 3. 安全设计
- **传输加密**：RSA保护密码传输
- **存储加密**：BCrypt哈希存储密码
- **身份验证**：JWT Token机制

### 4. 用户体验
- **实时反馈**：1秒检测间隔，响应迅速
- **状态提示**：颜色区分正常和警报状态
- **镜像显示**：摄像头画面水平翻转，符合直觉

## 学习建议
1. **动手实验**：尝试修改阈值参数，观察识别效果变化
2. **代码调试**：添加日志输出，跟踪特征向量的变化
3. **性能测试**：测试不同人数下的识别响应时间
4. **安全测试**：尝试破解JWT或RSA加密（学习目的）
5. **扩展思考**：如何支持戴口罩的人脸识别？

## 算法理解检查点
1. **YOLO输出为什么要进行中心点→左上角的转换？**
2. **为什么特征向量需要L2归一化？**
3. **余弦相似度为1表示什么？为0表示什么？**
4. **多特征融合为什么要进行两次归一化？**
5. **RSA公钥和私钥分别用于什么操作？**

---

> 上一篇：[Day 02](day02.md)  
> 下一篇：[Day 04](day04.md)  
> 项目笔记：[03-核心代码解析.md](../03-核心代码解析.md)  
> 学习进度：3/15天（还剩12天）