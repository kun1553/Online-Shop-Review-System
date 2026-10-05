# Online-Shop-Review-System

Comment project（电商点评项目）—— 这是一个基于 Java 开发的商品点评项目，内容包含 Java、MySQL、JavaWeb、Spring Boot、Redis。

Maven 模块名为 `hm-dianping`（黑马点评课程项目）。

## 技术栈

| 组件 | 版本 |
| --- | --- |
| JDK | 17 |
| Spring Boot | 2.7.18 |
| MyBatis-Plus | 3.5.3.1 |
| MySQL | 8.x（mysql-connector-java 8.0.33） |
| Redis | 6+（Lettuce 连接池） |
| Hutool | 5.7.17 |

## 目录结构

```
.
├── .gitignore
├── README.md
└── hm-dianping/                 # Maven 模块
    ├── pom.xml
    └── src/main
        ├── java/com/hmdp        # 启动类 / controller / service / mapper / entity / utils
        └── resources
            ├── application.yaml
            ├── application-local.yaml.example
            ├── db/hmdp.sql
            └── mapper/VoucherMapper.xml
```

## 本地运行

### 1. 准备中间件

- MySQL 8：新建数据库（默认库名 `ikun`），导入 `hm-dianping/src/main/resources/db/hmdp.sql`
- Redis：默认端口 `6379`

### 2. 配置敏感信息

`application.yaml` 中不包含任何真实密码，全部通过环境变量注入：

| 环境变量 | 说明 | 默认值 |
| --- | --- | --- |
| `MYSQL_HOST` | MySQL 地址 | `127.0.0.1` |
| `MYSQL_PORT` | MySQL 端口 | `3306` |
| `MYSQL_DATABASE` | 数据库名 | `ikun` |
| `MYSQL_USERNAME` | 数据库用户名 | `root` |
| `MYSQL_PASSWORD` | 数据库密码 | 空 |
| `REDIS_HOST` | Redis 地址 | `192.168.234.128` |
| `REDIS_PORT` | Redis 端口 | `6379` |
| `REDIS_PASSWORD` | Redis 密码 | 空 |

两种填写方式，任选其一：

**方式一（推荐）**：把 `application-local.yaml.example` 复制为同目录下的 `application-local.yaml`，填入真实密码。
该文件已被 `.gitignore` 忽略，且 `application.yaml` 中的 `spring.profiles.include: local` 会自动加载它。

**方式二**：在 IDEA 的 Run/Debug Configurations -> Environment variables 中填写，例如
`MYSQL_PASSWORD=你的数据库密码;REDIS_PASSWORD=你的Redis密码`

### 3. 启动

```bash
cd hm-dianping
mvn spring-boot:run
```

或直接在 IDEA 中运行 `com.hmdp.HmDianPingApplication`，服务端口 `8081`。

## 说明

- 图片上传目录默认是 `D:\lesson\nginx-1.18.0\html\hmdp\imgs\`，可用 JVM 参数 `-Dhmdp.upload-dir=你的路径` 覆盖。
- 前端静态资源（Nginx `html/hmdp`）不在本仓库中，本仓库只包含后端代码。
- `db/hmdp.sql` 为课程配套测试数据（含约 1000 条测试用户），仅供学习使用。