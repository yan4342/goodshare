# GoodShare (好物分享平台)

GoodShare 是一个基于微服务架构的好物分享与价格比价平台。本项目集成了社交分享、商品比价、个性化推荐和 AI 助手等功能，旨在为用户提供一个发现好物、交流心得和获取最优价格的社区。

## 🛠 技术栈 (Tech Stack)

### 前端 (Frontend)
- **核心框架**: Vue 3
- **构建工具**: Vite
- **UI 组件库**: Element Plus
- **状态管理**: Pinia
- **路由管理**: Vue Router
- **数据可视化**: ECharts
- **富文本编辑器**: VueQuill
- **网络请求**: Axios
- **实时通信**: SockJS + StompJS (WebSocket)

### 后端 (Backend)
- **核心框架**: Spring Boot 3.x
- **微服务架构**: Spring Cloud Alibaba (Nacos Discovery)
- **网关**: Spring Cloud Gateway
- **ORM 框架**: MyBatis Plus
- **数据库**: MySQL 8.0
- **缓存 & 消息**: Redis
- **搜索引擎**: Elasticsearch 8.x
- **安全认证**: Spring Security + JWT
- **AI 集成**: DeepSeek API (支持流式对话)
- **爬虫服务**: Jsoup / Selenium (独立服务)
- **推荐算法**: Python (FastAPI + Scikit-learn + Pandas) - UserCF 协同过滤

### 基础设施 (Infrastructure)
- **容器化**: Docker & Docker Compose
- **服务发现**: Nacos
- **反向代理**: Nginx (前端部署)

## ✨ 主要功能 (Key Features)

1.  **用户系统**: 注册、登录、个人资料管理、关注/粉丝系统。
2.  **内容社区**:
    - 发布图文帖子（支持富文本）。
    - 评论、回复（二级评论）、点赞、收藏。
    - 标签系统与热门标签。
3.  **全网比价 (Crawler)**:
    - 集成爬虫服务，支持淘宝/慢慢买等平台的商品价格查询。
    - 历史价格曲线展示。
4.  **智能推荐 (Recommendation)**:
    - 基于用户的协同过滤算法 (User-Based CF)。
    - 根据用户交互历史（点赞、收藏、评论）推荐相似用户喜欢的帖子。
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
- JDK 17+
- Node.js 18+
- Docker & Docker Compose
- Python 3.10+ (仅开发推荐服务需要)

### 1. 启动基础设施
使用 Docker Compose 启动 MySQL, Redis, Nacos, Elasticsearch 等基础服务。

```bash
docker-compose up -d
```

### 2. 后端服务启动
请按顺序启动以下服务：
1.  **Nacos**: 确保 Docker 中已启动。
2.  **Goodshare Server**: 主后端服务 (`GoodshareApplication`).
3.  **Crawler Service**: 爬虫服务 (`CrawlerServiceApplication`).
4.  **Gateway Service**: 网关服务 (`GatewayApplication`).
5.  **Recommendation Service**: 推荐算法服务 (Python).

**启动推荐服务 (Python):**
```bash
cd recommendation-service
pip install -r requirements.txt
python main.py
```

### 3. 前端启动
```bash
cd goodshare-web
npm install
npm run dev
```

访问地址: `http://localhost:8088`

## 📂 项目结构 (Project Structure)

```
goodshare/
├── goodshare-server/       # 主后端服务 (User, Post, Search, AI, etc.)
├── goodshare-web/          # 前端 Vue3 项目
├── crawler-service/        # 爬虫微服务 (Price comparison)
├── gateway-service/        # API 网关
├── recommendation-service/ # 推荐算法微服务 (Python)
├── docker-compose.yml      # 基础设施编排
└── ...
```

## ⚠️ 注意事项
- **DeepSeek API Key**: 请在 `application.properties` 中配置有效的 API Key。
- **Elasticsearch**: 首次启动可能需要初始化索引，系统会自动处理或需手动触发。
- **Nacos**: 默认端口 8848，控制台账号密码默认 `nacos/nacos` (如开启鉴权)。



![](C:\Users\16019\Pictures\Screenshots\屏幕截图_23-3-2026_212856_localhost.jpeg)



![屏幕截图_23-3-2026_212640_localhost](C:\Users\16019\Pictures\Screenshots\屏幕截图_23-3-2026_212640_localhost.jpeg)



![屏幕截图_23-3-2026_21266_localhost](C:\Users\16019\Pictures\Screenshots\屏幕截图_23-3-2026_21266_localhost.jpeg)



![屏幕截图_23-3-2026_212452_localhost](C:\Users\16019\Pictures\Screenshots\屏幕截图_23-3-2026_212452_localhost.jpeg)
