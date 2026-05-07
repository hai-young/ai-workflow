#!/bin/bash

# Backend API Test Script with Docker MySQL
# 测试后端 API 是否正常工作

echo "=================================="
echo "Backend API Test Script"
echo "测试认证与授权 API (使用 Docker MySQL)"
echo "=================================="
echo ""

# 配置
BASE_URL="http://localhost:8080/api"
CONTAINER_NAME="ai-mysql"

# 颜色输出
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 测试计数器
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# 测试函数
test_api() {
    local test_name=$1
    local endpoint=$2
    local method=${3:-POST}
    local data=$4

    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    echo -n "测试 $TOTAL_TESTS: $test_name... "

    # 发送请求
    response=$(curl -s -w "\n%{http_code}" -X $method "$BASE_URL$endpoint" \
        -H "Content-Type: application/json" \
        -d "$data")

    # 分离响应和状态码
    body=$(echo "$response" | head -n -1)
    status=$(echo "$response" | tail -n 1)

    # 检查响应
    if [ $status -ge 200 ] && [ $status -lt 300 ]; then
        echo -e "${GREEN}通过${NC} (HTTP $status)"
        echo "响应: $body"
        PASSED_TESTS=$((PASSED_TESTS + 1))
        return 0
    else
        echo -e "${RED}失败${NC} (HTTP $status)"
        echo "响应: $body"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        return 1
    fi
}

# 测试 1: 测试 Docker MySQL 连接
echo "=================================="
echo "测试 1: Docker MySQL 连接"
echo "=================================="
echo ""

# 检查 MySQL 容器是否运行
if docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    echo -e "${GREEN}✓${NC} MySQL Docker 容器正在运行: $CONTAINER_NAME"

    # 测试连接
    if docker exec $CONTAINER_NAME mysql -uroot -proot123 -e "USE workflow_db; SHOW TABLES;" 2>/dev/null; then
        echo -e "${GREEN}✓${NC} MySQL 数据库连接正常"
        echo "已连接到: workflow_db"
        docker exec $CONTAINER_NAME mysql -uroot -proot123 workflow_db -e "SHOW TABLES;" 2>/dev/null
    else
        echo -e "${YELLOW}⚠${NC} 数据库可能未创建，尝试创建..."
        docker exec $CONTAINER_NAME mysql -uroot -proot123 -e "CREATE DATABASE IF NOT EXISTS workflow_db;"
        docker exec $CONTAINER_NAME mysql -uroot -proot123 workflow_db -e "SHOW TABLES;"
    fi
    echo ""
else
    echo -e "${RED}✗${NC} MySQL Docker 容器未运行: $CONTAINER_NAME"
    echo "请启动 MySQL 容器: docker start $CONTAINER_NAME"
    echo ""
    # 创建容器（如果不存在）
    if docker ps -a --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
        echo "容器存在但已停止，尝试启动..."
        docker start $CONTAINER_NAME
        sleep 3
        echo ""
    else
        echo "容器不存在，尝试创建..."
        docker run --name $CONTAINER_NAME -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root123 -e MYSQL_DATABASE=workflow_db -d mysql:8.0
        sleep 5
        echo ""
    fi
fi

# 测试 2: 测试注册接口
echo "=================================="
echo "测试 2: 用户注册 API"
echo "=================================="
echo ""

test_api \
    "用户注册" \
    "/register" \
    "POST" \
    '{
        "username": "testuser001",
        "password": "Test123456",
        "confirmPassword": "Test123456",
        "email": "testuser001@example.com",
        "phone": "13800138000",
        "code": "123456"
    }'

echo ""

# 测试 3: 测试重复注册
echo "=================================="
echo "测试 3: 重复用户注册 (应失败)"
echo "=================================="
echo ""

test_api \
    "重复用户注册" \
    "/register" \
    "POST" \
    '{
        "username": "testuser001",
        "password": "Test123456",
        "confirmPassword": "Test123456"
    }'

echo ""

# 测试 4: 测试登录接口（使用刚才注册的用户）
echo "=================================="
echo "测试 4: 用户登录 API"
echo "=================================="
echo ""

test_api \
    "用户登录" \
    "/login" \
    "POST" \
    '{
        "username": "testuser001",
        "password": "Test123456",
        "rememberMe": false
    }'

echo ""

# 测试 5: 测试错误的密码登录
echo "=================================="
echo "测试 5: 错误密码登录 (应失败)"
echo "=================================="
echo ""

test_api \
    "错误密码登录" \
    "/login" \
    "POST" \
    '{
        "username": "testuser001",
        "password": "WrongPassword",
        "rememberMe": false
    }'

echo ""

# 测试 6: 测试登出接口
echo "=================================="
echo "测试 6: 用户登出 API"
echo "=================================="
echo ""

test_api \
    "用户登出" \
    "/logout" \
    "POST" \
    '{}'

echo ""

# 测试 7: 测试获取用户信息
echo "=================================="
echo "测试 7: 获取用户信息 API"
echo "=================================="
echo ""

test_api \
    "获取用户信息" \
    "/info" \
    "GET" \
    '{}'

echo ""

# 测试 8: 检查数据库中的用户
echo "=================================="
echo "测试 8: 检查数据库中的用户数据"
echo "=================================="
echo ""

if docker exec $CONTAINER_NAME mysql -uroot -proot123 workflow_db -e "SELECT id, username, email, phone, enabled, created_at FROM users;" 2>/dev/null; then
    echo -e "${GREEN}✓${NC} 数据库查询成功"
else
    echo -e "${RED}✗${NC} 数据库查询失败"
fi

echo ""

# 测试 9: 尝试登录已存在的用户（使用正确的密码）
echo "=================================="
echo "测试 9: 登录已存在的用户"
echo "=================================="
echo ""

test_api \
    "重新登录" \
    "/login" \
    "POST" \
    '{
        "username": "testuser001",
        "password": "Test123456",
        "rememberMe": false
    }'

echo ""

# 测试 10: 测试不存在的用户登录
echo "=================================="
echo "测试 10: 不存在的用户登录 (应失败)"
echo "=================================="
echo ""

test_api \
    "不存在的用户登录" \
    "/login" \
    "POST" \
    '{
        "username": "nonexistentuser",
        "password": "Test123456",
        "rememberMe": false
    }'

echo ""

# 总结
echo "=================================="
echo "测试总结"
echo "=================================="
echo "总测试数: $TOTAL_TESTS"
echo -e "通过: ${GREEN}$PASSED_TESTS${NC}"
echo -e "失败: ${RED}$FAILED_TESTS${NC}"
echo ""

if [ $FAILED_TESTS -eq 0 ]; then
    echo -e "${GREEN}✓ 所有测试通过！${NC}"
    echo ""
    echo "=================================="
    echo "下一步：启动前端测试集成"
    echo "=================================="
    echo ""
    echo "1. 启动后端服务:"
    echo "   cd D:/zhy_code/ai-workflow/backend/ai-orchestrator-zhy"
    echo "   ./mvnw spring-boot:run"
    echo ""
    echo "2. 启动前端服务:"
    echo "   cd D:/zhy_code/ai-workflow/frontend"
    echo "   npm run dev"
    echo ""
    echo "3. 访问前端: http://localhost:3000"
    echo ""
    exit 0
else
    echo -e "${YELLOW}⚠ 有测试失败，请检查后端日志${NC}"
    echo ""
    echo "后端日志位置: backend/ai-orchestrator-zhy/logs/"
    exit 1
fi
