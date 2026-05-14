# GoodShare (好物分享平台)

GoodShare 是一个基于微服务架构的好物分享与价格比价平台。本项目集成了社交分享、商品比价、个性化推荐和 AI 助手等功能，旨在为用户提供一个发现好物、交流心得和获取最优价格的社区。

## 🛠 技术栈 (Tech Stack)

### 前端 (Frontend)
- **核心框架**: Vue 3 + Composition API
- **构建工具**: Vite 5
- **UI 组件库**: Element Plus + @element-plus/icons-vue
- **状态管理**: Vue 3 pinia
- **路由管理**: Vue Router 4
- **数据可视化**: ECharts 5
- **富文本编辑器**: VueQuill
- **图片裁剪**: vue-cropper
- **网络请求**: Axios
- **实时通信**: SockJS + StompJS (WebSocket)
- **样式**: Sass

### 后端 (Backend)
- **核心框架**: Spring Boot 3.2.x (JDK 21)
- **微服务架构**: Spring Cloud 2023.0.x + Spring Cloud Alibaba 2023.0.x
- **网关**: Spring Cloud Gateway
- **ORM 框架**: MyBatis Plus
- **数据库**: MySQL 8.x
- **缓存**: Redis
- **搜索引擎**: Elasticsearch 8.x
- **安全认证**: Spring Security + JWT
- **AI 集成**: DeepSeek API (支持 SSE 流式对话)
- **爬虫服务**: Jsoup + Selenium (独立微服务)
- **推荐算法**: Python FastAPI + Scikit-learn + Pandas (UserCF / ItemCF / ContentBase 协同过滤)

### 基础设施 (Infrastructure)
- **容器化**: Docker & Docker Compose
- **服务发现**: Nacos v2.3.2
- **反向代理**: Nginx (前端部署 & API 代理)

## ✨ 主要功能 (Key Features)

1.  **用户系统**: 注册、登录、个人资料管理、头像裁剪上传、关注/粉丝系统。
2.  **内容社区**:
    - 发布图文帖子（支持富文本）。
    - 评论、回复（二级评论）、点赞、收藏。
    - 标签系统与热门标签。
3.  **全网比价 (Crawler)**:
    - 集成爬虫服务，支持慢慢买等平台的商品价格查询。
    - 历史价格曲线展示。
4.  **智能推荐 (Recommendation)**:
    - 基于用户的协同过滤 (UserCF)、基于物品的协同过滤 (ItemCF)、基于内容的推荐 (ContentBase)。
    - 根据用户交互历史（点赞、收藏、评论）推荐相似用户喜欢的帖子。
    - 支持周期性自动重训练模型。
5.  **全文搜索 (Search)**:
    - 基于 Elasticsearch 的高性能帖子搜索。
    - 支持关键词高亮与多条件过滤。
6.  **AI 助手 (DeepSeek)**:
    - 集成 DeepSeek 大模型。
    - 支持流式对话 (SSE)，提供好物咨询与文案辅助。
7.  **估价系统 (Appraisal)**:
    - 二手商品估价求助。
    - 社区投票估价。
8.  **实时通知**:
    - WebSocket 实现的消息推送（点赞、评论、关注通知）。

## 🚀 快速开始 (Getting Started)

### 前置要求

| 依赖 | 版本 | 说明 |
|------|------|------|
| JDK | 21+ | 后端微服务编译运行 |
| Node.js | 18+ | 前端开发与构建 |
| Docker & Docker Compose | - | 容器化部署 |
| Python | 3.10+ | 仅推荐服务 (可选，Docker 模式无需本地安装) |
| MySQL | 8.x | 数据库 (需本地启动) |
| Maven | 3.8+ | 后端项目构建 |

### 方式一：Docker 一键部署 (推荐)

```bash
# 1. 确保本地 MySQL 已启动，goodshare 数据库已创建
# 2. 构建后端 JAR 包
mvn clean package -DskipTests

# 3. 启动全部服务（基础设施 + 微服务 + 前端）
docker-compose -f docker-compose-prod.yml up -d --build

# 4. 查看日志
docker-compose -f docker-compose-prod.yml logs -f
```



启动完成后访问: **http://localhost:8088**

### 方式二：本地开发模式

```bash
# 1. 启动基础设施 (Nacos, Redis, Elasticsearch, 推荐服务)
docker-compose up -d

# 2. 启动本地 MySQL，确保 goodshare 数据库已创建

# 3. 启动后端微服务（分别在三个终端中运行）
cd gateway-service && mvn spring-boot:run       # 网关 :8080
cd goodshare-server && mvn spring-boot:run      # 核心服务 :8081
cd crawler-service && mvn spring-boot:run       # 爬虫服务 :8082

# 4. 启动前端
cd goodshare-web
npm install
npm run dev                                      # 开发服务器 :5180
```

> Windows 用户也可直接运行 `start_dev.bat` 一键启动。

开发模式下前端访问: **http://localhost:5180** (自动代理 API 请求到网关)

### 服务端口一览

| 服务 | 端口 | 说明 |
|------|------|------|
| **goodshare-web** (Nginx) | 8088 | 生产环境前端入口 |
| **goodshare-web** (Vite Dev) | 5180 | 开发环境前端 |
| **gateway-service** | 8080 | API 网关 |
| **goodshare-server** | 8081 | 核心后端服务 |
| **crawler-service** | 8082 | 爬虫微服务 |
| **recommendation-service** | 5000 | 推荐算法服务 |
| **Nacos** | 18848 | 服务注册与发现 |
| **Redis** | 6379 | 缓存 |
| **Elasticsearch** | 19200 | 搜索引擎 |
| **MySQL** | 3306 | 数据库 (本地) |

## 📂 项目结构 (Project Structure)

```
goodshare/
├── goodshare-server/           # 主后端服务 (用户、帖子、搜索、AI 等)
│   └── src/main/resources/
│       └── application.properties   # 核心配置 (数据库、JWT、DeepSeek 等)
├── goodshare-web/              # 前端 Vue 3 项目
│   ├── src/
│   │   ├── views/              # 页面组件
│   │   ├── components/         # 公共组件
│   │   ├── stores/             # 状态管理 (Auth, Home)
│   │   ├── router/             # 路由配置
│   │   └── utils/              # 工具函数 (Axios 封装等)
│   └── nginx.conf              # 生产环境 Nginx 配置
├── crawler-service/            # 爬虫微服务 (价格比价)
│   └── scripts/                # Python 爬虫脚本 (慢慢买、淘宝)
├── gateway-service/            # API 网关 (路由转发)
├── recommendation-service/     # 推荐算法微服务 (Python FastAPI)
│   ├── main.py                 # 服务入口
│   ├── recommender.py          # 推荐算法实现
│   ├── database.py             # 数据库连接
│   └── evaluate.py             # 离线评估
├── elasticsearch/              # Elasticsearch Docker 构建文件
├── uploads/                    # 文件上传目录
├── docker-compose.yml          # 开发环境基础设施编排
├── docker-compose-prod.yml     # 生产环境全量编排
├── start_dev.bat               # Windows 开发环境一键启动
└── start_docker.bat            # Windows Docker 一键部署
```

## ⚙️ 配置说明

### 核心配置 (goodshare-server)

配置文件位于 `goodshare-server/src/main/resources/application.properties`：

| 配置项 | 说明 |
|--------|------|
| `spring.datasource.*` | MySQL 数据库连接 |
| `spring.elasticsearch.uris` | Elasticsearch 地址 |
| `app.jwtSecret` | JWT 签名密钥 (生产环境务必修改) |
| `deepseek.api.key` | DeepSeek API Key (需替换为有效 Key) |
| `recommendation.service.url` | 推荐服务地址 |
| `app.proxy.host/port` | 网络代理配置 (可选) |

### 环境变量覆盖 (Docker)

Docker 模式下可通过环境变量覆盖配置，详见 `docker-compose-prod.yml`。

## ⚠️ 注意事项

- **DeepSeek API Key**: 请在 `application.properties` 中将 `deepseek.api.key` 替换为你的有效 API Key。
- **MySQL**: 需本地提前启动并创建 `goodshare` 数据库，默认账号 `root/123456`。
- **Elasticsearch**: 首次启动时系统会自动创建索引。
- **Nacos**: Docker 模式映射到端口 `18848`，控制台地址 `http://localhost:18848/nacos`。
- **文件上传**: 上传的文件存储在项目根目录 `uploads/` 文件夹中，Docker 模式已挂载该目录。
- **代理设置**: 如网络受限，可在配置文件或 Docker 环境变量中配置 HTTP 代理。




