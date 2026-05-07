# 后端 API 测试指南

## 项目概述

本项目已成功实现认证与授权模块，包含以下功能：

### 前端功能
- ✅ 登录页面（用户名/密码、手机验证码）
- ✅ 注册页面（用户名、密码、手机号、验证码）
- ✅ 密码重置页面（手机验证码重置）
- ✅ 深海霓虹主题设计
- ✅ 路由守卫和权限控制
- ✅ Dashboard 页面

### 后端功能（已存在）
- ✅ 用户注册 API (`POST /api/auth/register`)
- ✅ 用户登录 API (`POST /api/auth/login`)
- ✅ 数据库连接配置
- ✅ JWT 认证配置
- ✅ Spring Security 配置
- ✅ User 实体和 Repository
- ✅ AuthService 服务层

## 测试步骤

### 1. 确保后端服务运行

首先启动后端服务：

```bash
cd D:/zhy_code/ai-workflow/backend/ai-orchestrator-zhy

# 使用 Maven 启动
./mvnw spring-boot:run

# 或使用 IDE 运行 AiOrchestratorZhyApplication
```

### 2. 确保数据库运行

需要安装并启动 MySQL 服务。

**选项 A：安装并启动 MySQL**

```bash
# Windows: 下载并安装 MySQL
# 1. 下载 MySQL 安装包
# 2. 运行安装程序
# 3. 设置 root 密码为 root123
# 4. 启动 MySQL 服务

# 或使用 Docker
docker run --name mysql-workflow -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=root123 \
  -e MYSQL_DATABASE=workflow_db \
  -d mysql:8.0
```

**选项 B：使用已有的 MySQL**

如果已经有 MySQL 安装：

```bash
# 启动 MySQL 服务
net start MySQL

# 验证连接
mysql -u root -proot123 -e "SELECT VERSION();"

# 创建数据库
mysql -u root -proot123 -e "CREATE DATABASE IF NOT EXISTS workflow_db;"

# 验证数据库
mysql -u root -proot123 workflow_db -e "SHOW TABLES;"
```

### 3. 运行 API 测试脚本

```bash
cd D:/zhy_code/ai-workflow/backend/ai-orchestrator-zhy

# 运行测试脚本
bash test-api.sh
```

### 4. 使用 curl 手动测试

#### 4.1 注册新用户

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser001",
    "password": "Test123456",
    "confirmPassword": "Test123456",
    "email": "testuser001@example.com",
    "phone": "13800138000",
    "code": "123456"
  }'
```

**预期响应：**
```json
{
  "success": true,
  "message": "Registration successful",
  "username": "testuser001"
}
```

#### 4.2 用户登录

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser001",
    "password": "Test123456",
    "rememberMe": false
  }'
```

**预期响应：**
```json
{
  "success": true,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "username": "testuser001",
    "expiresIn": 86399000
  }
}
```

#### 4.3 获取用户信息

```bash
curl -X GET http://localhost:8080/api/auth/info \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

**预期响应：**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "username": "testuser001",
    "email": "testuser001@example.com",
    "phone": "13800138000",
    "enabled": true,
    "createdAt": "2026-04-29T..."
  }
}
```

#### 4.4 登出

```bash
curl -X POST http://localhost:8080/api/auth/logout \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

**预期响应：**
```json
{
  "success": true
}
```

## 前端集成

### 1. 配置后端 API 地址

编辑前端环境变量文件：

```bash
# D:/zhy_code/ai-workflow/frontend/.env.local

VITE_API_BASE_URL=http://localhost:8080/api
VITE_APP_TITLE=智能协作平台
```

### 2. 启动前端开发服务器

```bash
cd D:/zhy_code/ai-workflow/frontend

# 安装依赖（如果尚未安装）
npm install

# 启动开发服务器
npm run dev
```

前端将运行在：http://localhost:3000

### 3. 测试前端功能

1. 访问 http://localhost:3000
2. 测试登录页面
3. 测试注册功能
4. 测试密码重置功能
5. 测试 Dashboard 页面（需要先登录）

## 数据库表结构

后端会自动创建 `users` 表，结构如下：

```sql
CREATE TABLE users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) UNIQUE NOT NULL,
  password VARCHAR(100) NOT NULL,
  email VARCHAR(100),
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
```

## 常见问题

### 1. 端口冲突

如果端口 8080 被占用，修改配置：

**后端：** 编辑 `application.yml`
```yaml
server:
  port: 8081  # 改为其他端口
```

**前端：** 编辑 `.env.local`
```bash
VITE_API_BASE_URL=http://localhost:8081/api
```

### 2. 数据库连接失败

- 检查 MySQL 服务是否运行
- 检查用户名密码是否正确（root/root123）
- 检查数据库是否创建（workflow_db）

### 3. JWT 令牌无效

- 登录后复制完整的 token
- 确保 header 格式正确：`Authorization: Bearer <token>`
- 检查 token 是否过期（默认 24 小时）

## 项目结构

### 前端文件结构
```
frontend/
├── src/
│   ├── api/
│   │   ├── auth.ts              # 认证 API
│   │   └── verify.ts            # 验证码 API
│   ├── components/
│   │   └── common/
│   │       └── PasswordInput.vue
│   ├── router/
│   │   └── index.ts             # 路由配置
│   ├── stores/
│   │   └── auth.ts              # 认证状态管理
│   ├── styles/
│   │   └── dark-neon.css        # 深海霓虹主题
│   ├── views/
│   │   ├── LoginView.vue        # 登录页面
│   │   ├── RegisterView.vue     # 注册页面
│   │   ├── ResetPasswordView.vue # 密码重置页面
│   │   └── DashboardView.vue    # Dashboard 页面
│   ├── types/
│   │   └── auth.ts              # 类型定义
│   ├── utils/
│   │   └── request.ts           # Axios 封装
│   ├── App.vue
│   └── main.ts
├── index.html
└── package.json
```

### 后端文件结构
```
backend/ai-orchestrator-zhy/
├── src/main/java/com/zhy/workflow/ai/
│   ├── controller/
│   │   └── auth/
│   │       └── AuthController.java   # 认证控制器
│   ├── service/
│   │   ├── auth/
│   │   │   └── AuthService.java     # 认证服务
│   │   └── user/
│   │       └── UserService.java     # 用户服务
│   ├── entity/
│   │   └── User.java                # 用户实体
│   ├── repository/
│   │   └── UserRepository.java      # 用户数据访问层
│   ├── security/
│   │   ├── SecurityConfig.java      # 安全配置
│   │   ├── JwtAuthenticationFilter.java
│   │   └── JwtUtil.java             # JWT 工具类
│   ├── dto/
│   │   ├── LoginRequest.java
│   │   ├── LoginResponse.java
│   │   └── RegisterRequest.java
│   └── AiOrchestratorZhyApplication.java
└── src/main/resources/
    └── application.yml               # 应用配置
```

## 下一步

1. ✅ 启动后端服务
2. ✅ 启动前端服务
3. ✅ 测试 API 集成
4. 📋 实现 RAG 功能
5. 📋 实现 AI 工作流编排
6. 📋 实现 RAG 知识问答

## 技术栈总结

### 前端
- **框架:** Vue 3 (Composition API)
- **构建工具:** Vite 5.x
- **UI 库:** Ant Design Vue 4.x
- **状态管理:** Pinia
- **路由:** Vue Router 4.x
- **HTTP 客户端:** Axios
- **设计风格:** 深海霓虹（Deep Sea Neon）

### 后端
- **框架:** Spring Boot 3.5.14
- **语言:** Java 21
- **数据库:** MySQL 8.0
- **AI 框架:** Spring AI 1.1.4
- **向量数据库:** Milvus 2.6.18
- **认证:** JWT + Spring Security
- **ORM:** Spring Data JPA

---

**创建时间:** 2026/04/29
**状态:** ✅ 认证与授权模块已完成
