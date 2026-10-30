# Backend（Spring Boot 3.5 / Java 21）

W1 阶段只提供健康检查，不加载数据库、Redis 配置，因此**不启动中间件也能运行**。

## 用 IDEA 打开

1. `File → Open`，选择本目录的 `pom.xml`，弹窗选 `Open as Project`。
2. `File → Project Structure → Project → SDK` 选择 JDK 21。
3. 右键 `CampusLostFoundApplication` → Run。

## 命令行

```powershell
cd G:\软件工程\campus-lost-found\backend
$s = "G:\软件工程\campus-lost-found\.local\maven-settings.xml"
mvn -gs $s -s $s spring-boot:run
```

> 本机全局 `conf\settings.xml` 目前无法解析，临时配置文件只在这台机器上有效（已被 `.gitignore` 排除）。修复全局配置后用原生的 `mvn spring-boot:run` 即可。

构建与测试：

```powershell
mvn clean test
mvn clean package
java -jar target\lostfound-backend-0.1.0-SNAPSHOT.jar
```

## 接口

```text
GET http://localhost:8081/api/v1/health

{
  "code": 0,
  "message": "ok",
  "data": {
    "status": "UP",
    "service": "campus-lost-found-backend",
    "version": "0.1.0",
    "timestamp": "2026-10-30T10:00:00Z"
  }
}
```

## 说明

- 统一响应结构定义在 `common/ApiResponse.java`，后续所有接口复用它。
- `SecurityConfig` 当前 `permitAll()`，仅为骨架启动；W9 接入 JWT 时替换。
- CORS 只放开 `localhost:5173`，前端开发期主要走 Vite 代理。

