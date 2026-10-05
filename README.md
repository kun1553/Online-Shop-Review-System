# Online-Shop-Review-System(正在开发)

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

## 跑起来需要什么

| 依赖 | 说明 |
| --- | --- |
| JDK 17+ | Spring Boot 2.7.18 要求 |
| Maven | 或用 IDEA 自带的 |
| MySQL 8 | 建库并导入 `db/hmdp.sql` |
| Redis | 默认端口 6379 |
| Nginx（可选） | 只用于托管前端页面；不需要页面可用 Postman / Apifox 直接调接口 |

### 1. 数据库

`db/hmdp.sql` 里没有 `CREATE DATABASE` 也没有 `USE`，需要自己先建库并选中：

```sql
CREATE DATABASE ikun DEFAULT CHARACTER SET utf8mb4;
USE ikun;
-- 然后执行 hm-dianping/src/main/resources/db/hmdp.sql
```

库名要和配置里的 `MYSQL_DATABASE` 一致（默认 `ikun`）。用 Navicat 的「运行 SQL 文件」时记得手动指定目标库。

### 2. Redis

默认连 `6379`。注意 `REDIS_HOST` 的默认值是 `192.168.234.128`（原作者的虚拟机地址），**在自己机器上跑要改成 `127.0.0.1`**，否则启动时会连接超时。

### 3. 配置密码

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
| `HMDP_UPLOAD_DIR` | 图片上传目录 | 见「说明」 |

两种填写方式，任选其一：

**方式一（推荐）**：把 `application-local.yaml.example` 复制为同目录下的 `application-local.yaml`，填入真实密码。
该文件已被 `.gitignore` 忽略，且 `application.yaml` 中的 `spring.profiles.include: local` 会自动加载它。

**方式二**：在 IDEA 的 Run/Debug Configurations -> Environment variables 中填写，例如
`MYSQL_PASSWORD=你的数据库密码;REDIS_PASSWORD=你的Redis密码`

### 4. 前端页面（可选）

前端是课程配套的静态页面，在另一个仓库：[gitee.com/huyi612/hmdp-web](https://gitee.com/huyi612/hmdp-web)

```bash
git clone https://gitee.com/huyi612/hmdp-web.git
```

把文件放进 Nginx 的 `html/hmdp` 目录（例如 `D:/nginx-1.18.0/html/hmdp/`），并让 Nginx 监听 8080、把 `/api` 反向代理到后端的 8081：

```nginx
server {
    listen       8080;
    server_name  localhost;

    # 前端页面
    location / {
        root   html/hmdp;
        index  index.html index.htm;
    }

    # 接口反向代理：/api/user/login -> /user/login
    location /api {
        rewrite /api(/.*) $1 break;
        proxy_pass http://127.0.0.1:8081;
        proxy_pass_request_headers on;
        proxy_http_version 1.1;
    }
}
```

前端 `js/common.js` 里的 `commonURL` 就是 `/api`，靠上面这段 rewrite 去掉前缀转发给后端。

启动 Nginx 后访问 <http://localhost:8080>。

### 5. 启动后端

```bash
cd hm-dianping
mvn spring-boot:run
```

或直接在 IDEA 中运行 `com.hmdp.HmDianPingApplication`，服务端口 `8081`。

### 6. 登录

项目没有对接短信服务商。调用 `POST /user/code?phone=13800138000` 之后，**6 位验证码会打印在后端控制台/日志里**（`UserServiceImpl.sendCode`），拿它去调 `POST /user/login` 即可。

## 说明

- **图片上传目录必须指向 Nginx 站点 `html/hmdp/imgs`**，通过 `hmdp.upload-dir` 配置（默认 `D:/mongdb/nginx/nginx-1.18.0/html/hmdp/imgs/`）。它是课程作者机器上的路径，请改成你自己的。配错了的表现是：图片上传接口返回成功，但浏览器访问 `/imgs/...` 404 —— 因为前端上传后拼的地址是 `/imgs` + 接口返回的路径。
- 前端页面不在本仓库中（它是课程方的独立仓库，见第 4 步），本仓库只包含后端代码。
- 代码里没有配置 CORS，前端依赖 Nginx 代理保持同源。若用其它端口直接打开页面，接口会被浏览器跨域拦截。
- `db/hmdp.sql` 为课程配套测试数据（含约 1000 条测试用户），仅供学习使用。
