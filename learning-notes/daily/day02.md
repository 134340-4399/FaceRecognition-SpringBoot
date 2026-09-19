# Day 02 学习笔记

> 日期：2026年4月5日  
> 学习阶段：第4-6天（核心模块逐层拆解 - 控制器层）  
> 标签：#毕设/人脸识别 #ClaudeCode/learning #学习日志

## 今日学习目标
- [x] 分析所有Controller的API设计和功能
- [x] 理解JWT认证拦截器机制
- [x] 掌握Web配置和安全策略
- [x] 绘制API调用流程图
- [x] 生成控制器层分析文档

## 学习内容记录

### 1. 控制器层整体分析
今天深入分析了项目的6个主要Controller：

**控制器分类**：
1. **AuthController** (`/api/auth`) - 用户认证
2. **FaceController** (`/api`) - 人脸识别核心功能
3. **AdminController** (`/api/admin`) - 管理员功能
4. **DashboardController** (`/api/dashboard`) - 数据统计看板
5. **EmployeeController** (`/api/employee`) - 员工个人功能
6. **LeaveController** (`/api/leave`) - 请假管理

### 2. 安全认证机制详解
**JWT拦截器** (`JwtInterceptor.java`)：
- 拦截所有`/api/**`请求
- 验证`Authorization: Bearer <token>`头
- 验证失败返回401状态码
- 放行OPTIONS预检请求（CORS）

**Web配置** (`WebConfig.java`)：
```java
// 配置拦截规则
registry.addInterceptor(new JwtInterceptor())
    .addPathPatterns("/api/**")
    .excludePathPatterns("/api/auth/login", "/api/auth/face-login", "/api/auth/public-key");
```

### 3. 关键API接口分析

#### 认证模块 (AuthController)
- `GET /api/auth/public-key`：获取RSA公钥（前端加密用）
- `POST /api/auth/login`：用户登录（RSA解密 + BCrypt验证）

**安全特点**：
1. 密码RSA加密传输
2. 数据库存储BCrypt哈希
3. 登录成功后生成JWT Token

#### 人脸识别模块 (FaceController)
核心功能：
- `POST /api/detect`：人脸检测打卡（含活体检测）
- `POST /api/register`：员工注册（多照片特征融合）
- 部门管理、阈值配置等管理功能

**技术亮点**：
- **活体检测**：Laplacian方差计算图像清晰度
- **多特征融合**：3张照片特征L2归一化平均
- **模型初始化**：`@PostConstruct`加载YOLO和ArcFace

#### 数据统计模块 (DashboardController)
- `GET /api/dashboard/statistics`：考勤统计数据（含7天趋势）
- `GET /api/dashboard/abnormal-details`：详细人员分类名单

**数据处理**：
- 数据去重：同一员工多次打卡只算一次
- 准确计算：缺勤人数 = 总人数 - 打卡人数 - 请假人数

### 4. API设计模式总结
- **路由设计**：RESTful风格，按功能模块划分
- **参数传递**：`@RequestParam`、`@PathVariable`、`MultipartFile`
- **响应格式**：统一`{success, message, data}`结构
- **跨域支持**：`@CrossOrigin(origins = "*")`

### 5. 代码质量评估
**优点**：
- 分层清晰，Controller职责单一
- 安全考虑全面（RSA+BCrypt+JWT）
- 中文注释详细，易于理解

**改进建议**：
- 缺少参数验证注解
- 错误消息硬编码
- 响应格式可以进一步标准化

## 今日学习复盘

### 1. 今天学会了什么？
✅ **Spring MVC控制器设计**：掌握了6个Controller的职责划分和API设计  
✅ **JWT认证流程**：理解了Token生成、验证、拦截的完整流程  
✅ **安全机制实现**：RSA传输加密 + BCrypt存储加密 + JWT认证的三重保护  
✅ **人脸识别API设计**：活体检测、特征提取、特征比对的接口设计  
✅ **数据统计逻辑**：考勤统计的数据去重和准确计算方法  
✅ **代码组织结构**：拦截器、配置类、控制器的协作关系  
✅ **API文档编写**：学会了用表格形式整理API接口文档

### 2. 哪里还不清楚？
❓ **Service层具体实现**：Controller调用的`FaceService`等具体业务逻辑  
❓ **人脸识别算法细节**：`YoloFaceDetector`和`FaceFeatureExtractor`的内部实现  
❓ **数据库查询优化**：Repository层的方法定义和查询性能  
❓ **前端调用方式**：前端如何调用这些API，参数如何组织  
❓ **异常处理机制**：全局异常处理和错误码设计  
❓ **配置管理细节**：`SystemConfig`的配置项和使用方式  
❓ **多线程处理**：人脸识别是否涉及并发处理

### 3. 明天想重点学什么？
🔍 **服务层分析**：深入研究`FaceService`的业务逻辑实现  
🔍 **工具类深入**：分析人脸识别核心工具类的算法实现  
🔍 **数据访问层**：查看Repository接口的定义和查询方法  
🔍 **实体类设计**：分析JPA实体类的字段设计和关联关系  
🔍 **配置文件详解**：理解所有配置项的作用和使用场景  
🔍 **启动流程分析**：查看应用启动时的初始化过程

## 明日学习计划
根据15天学习计划，继续**第4-6天：核心模块逐层拆解**阶段：

1. **服务层分析**（2小时）
   - 分析`FaceService`的业务逻辑
   - 理解人脸识别特征比对的实现
   - 查看服务层的异常处理

2. **工具类研究**（2小时）
   - 深入研究`YoloFaceDetector`人脸检测
   - 分析`FaceFeatureExtractor`特征提取
   - 查看`FeatureUtils`特征处理工具

3. **数据访问层分析**（1小时）
   - 查看Repository接口定义
   - 理解JPA查询方法命名规则
   - 分析实体类关联关系

## 遇到的问题和思考
1. **代码质量较高**：项目结构清晰，安全考虑周全
2. **中文注释友好**：详细的中文注释降低了理解难度
3. **技术栈实用**：选择了成熟稳定的技术组合
4. **业务逻辑完整**：覆盖了考勤系统的核心功能
5. **扩展性良好**：模块化设计便于功能扩展

## 学习建议
1. **由浅入深**：从Controller到Service再到工具类，逐步深入
2. **理论与实践结合**：在理解代码的同时，思考实际应用场景
3. **对比学习**：对比不同Controller的设计模式，找出共性
4. **问题驱动**：带着问题阅读代码，提高学习效率

## 代码理解检查点
1. **JWT Token如何包含用户信息？**
2. **人脸特征向量如何存储和比对？**
3. **多照片特征融合的具体算法是什么？**
4. **活体检测的阈值如何确定？**
5. **数据去重的实现方式有哪些？**

---

> 上一篇：[Day 01](day01.md)  
> 下一篇：[Day 03](day03.md)  
> 项目笔记：[02-控制器层分析.md](../02-控制器层分析.md)  
> 学习进度：2/15天（还剩13天）