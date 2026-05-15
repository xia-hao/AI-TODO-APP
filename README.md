# Todo App

Vue 3 + Spring Boot + Python AI Service + MySQL 全栈团队协作待办应用，以**项目看板**为核心组织工作。

## 目录

- [功能概览](#功能概览)
- [快速启动](#快速启动)
- [使用说明](#使用说明)
- [技术架构](#技术架构)
- [数据库设计](#数据库设计)
- [API 接口](#api-接口)
- [权限模型](#权限模型)
- [关键设计决策](#关键设计决策)

---

## 功能概览

### 项目看板
- 创建项目（个人/团队），支持颜色和图标
- 看板视图：项目内分区列展示待办卡片
- 拖拽排序：列内排序 + 跨列移动
- 批量操作：选择多项后批量完成/删除/移动
- 按状态/分类/关键词/标签/日期范围过滤

### 子任务
- 待办项下拆分子任务
- 支持指定处理人和截止日期
- 拖拽排序

### 标签系统
- 创建项目标签（自定义颜色），
- 标签关联待办，按标签筛选

### 团队协作
- 创建团队 / 通过邀请码加入
- 团队项目共享（成员均可创建和查看）
- 成员管理（查看、移除、角色调整）
- 角色权限控制（OWNER / ADMIN / MEMBER）
- 评论待办（支持嵌套回复）
- 附件上传/下载

### 提醒与通知
- 设置待办到期提醒
- 定时任务每分钟扫描待发送提醒
- 站内通知 + WebSocket 实时推送
- 支持邮件配置（需启用 SMTP）

### 看板（Dashboard）
- 任务概览：总数/完成/活跃/逾期（个人项目按创建者统计，团队项目按分配人统计）
- 项目排名：各项目完成率排行（横向柱状图）
- 人员统计：分配人任务分布（饼图）
- 标签热力图：标签使用频率统计
- 近 7 天完成趋势图
- 即将到期任务列表

### 日历视图
- FullCalendar 集成，月/周/日视图切换
- 按截止日期展示待办，绿色=已完成、灰色=普通、红色=逾期

### 回收站
- 软删除：待办移入回收站，30 天后自动清理
- 支持恢复和彻底删除

### 操作日志
- 自动记录项目内创建/更新/删除/完成/移动操作
- 项目内按时间线展示

### 全局搜索
- 顶部搜索框，Ctrl+K 聚焦
- 搜索待办内容，结果含项目名

### 数据导入
- 支持 CSV / JSON 格式批量导入待办
- 三步向导：上传 → 预览 → 导入结果

### 认证
- 用户注册 / 登录
- JWT 双 token（access 15min + refresh 7天）
- 自动续签，无感刷新

### 快捷操作
- Ctrl+K 全局唤出快速添加弹窗

### AI 智能助手
- 内嵌 AI 对话面板，支持会话管理和消息历史
- 自然语言操作待办（查询、创建、修改、删除）
- 支持流式输出（SSE）和工具调用（Function Calling）
- 对接 DeepSeek / OpenAI 等 LLM，可切换 provider
- 对话通过后端代理转发，支持多轮工具交互

### 国际化 & 深色模式
- 中英文切换（vue-i18n）
- 深色/浅色主题切换（@vueuse/useDark）

---

## 快速启动

### 前置条件

| 依赖 | 版本 |
|------|------|
| JDK | 1.8 |
| Node.js | 20+ |
| MySQL | 8.0（或 Docker） |
| Maven | 3.6+ |
| Python | 3.10+ |
| LLM API Key | DeepSeek / OpenAI 或其他兼容 provider |

### 初始化数据库

使用 `init.sql` 建表（完整 schema，已整合所有迁移）：

```bash
mysql -u root -p < backend/src/main/resources/db/init.sql
```

### 本地开发

**1. 启动 MySQL**

```bash
docker run -d --name todo-mysql \
  -e MYSQL_ROOT_PASSWORD=root123 \
  -e MYSQL_DATABASE=todo_app \
  -e MYSQL_USER=todo_user \
  -e MYSQL_PASSWORD=todo_pass \
  -p 3306:3306 \
  mysql:8.0 --character-set-server=utf8mb4
```

**2. 启动后端**

```bash
cd backend
mvn spring-boot:run
# 运行在 http://localhost:8080
```

**3. 启动 AI 服务**（可选，用于 AI 对话功能）

```bash
cd ai-service
pip install -r requirements.txt
python -m app.main
# 运行在 http://localhost:8000
```

需设置环境变量：`LLM_API_KEY`、`INTERNAL_API_KEY`（与后端 `app.ai.internal-api-key` 一致）。

**4. 启动前端**

```bash
cd frontend
npm install
npm run dev
# 运行在 http://localhost:5173
```

### Docker Compose 一键启动（含 AI 服务）

```bash
docker-compose up -d
# 访问 http://localhost
```

需先配置 `.env` 文件（参考各服务的 `.env.example`），至少包含数据库密码和 LLM API Key。

---

## 使用说明

### 注册 / 登录

1. 访问 `http://localhost:5173`，点击"注册"
2. 填写用户名、邮箱、密码完成注册，自动跳转主页
3. 已有账号直接登录

### 仪表盘

登录后默认进入仪表盘，显示：
- 任务统计概览（总数/完成/活跃/逾期）
- 近 7 天完成趋势折线图
- 即将到期任务列表

### 项目看板（核心工作区）

**创建项目**
1. 左侧项目面板点击"新建项目"
2. 填写名称、选择颜色和图标
3. 可创建个人项目或团队项目（需先加入团队）

**看板操作**
- 每个项目包含多个**分区列**，待办以卡片形式分布在列中
- 点击列标题拖拽可重新排序列顺序
- **待办卡片**：点击复选框切换完成，双击打开编辑，拖拽排序/跨列移动
- 点击分区标题旁的编辑/删除按钮可重命名或删除分区
- 底部"添加分区"按钮创建新列

**批量操作**
- 勾选待办卡片（可多选），顶部显示批量操作栏
- 支持批量完成、批量删除、批量移动到其他分区

**标签管理**
- 项目内可创建标签（名称+颜色）
- 待办详情中可添加/移除标签
- 筛选栏可按标签组合筛选

### 子任务、评论、附件

- 点击待办卡片进入详情抽屉
- 子任务列表：勾选完成、拖拽排序
- 评论：支持嵌套回复（点击"回复"按钮）
- 附件：支持上传和下载

### 提醒

- 在待办详情中设置提醒时间
- 后台每分钟扫描到期的提醒

### 回收站

- 左侧面板"回收站"入口，查看已删除待办
- 可恢复或彻底删除
- 系统每日凌晨 3 点自动清理 30 天前的已删除待办

### 日历视图

- 左侧面板"日历"入口，切换到日历视图
- 支持月/周/日视图切换
- 截止日期当天显示待办卡片，颜色区分状态

### 批量导入

- 在看板顶部点击"导入"按钮
- 三步向导：选择项目和文件 → 预览数据 → 执行导入
- 支持 CSV、JSON 格式（可下载模板）

### 全局搜索

- 顶部搜索框，输入关键词实时搜索（300ms 防抖）
- 键盘导航：↑↓ 选择，Enter 跳转，Esc 关闭
- Ctrl+K 快捷键聚焦搜索框

### 团队功能

**创建团队**
1. 左侧面板点击"创建团队"
2. 填写团队名称和描述
3. 创建后自动成为 OWNER，获得 8 位邀请码

**加入团队**
1. 左侧面板点击"加入团队"
2. 输入邀请码加入，默认角色为 MEMBER

**团队管理**
- 团队详情页管理成员、项目、标签
- 创建/查看团队项目
- OWNER 可管理成员角色和邀请码

---

## 技术架构

### 技术栈总览

| 层 | 技术 |
|----|------|
| 前端 | Vue 3 + TypeScript + Pinia + Element Plus + Axios + ECharts |
| 后端 | Spring Boot 2.7.18 + MyBatis-Plus + Spring Security 5.7 |
| AI 服务 | Python + FastAPI + OpenAI SDK（兼容 DeepSeek / 任意 LLM） |
| 数据库 | MySQL 8.0 |
| 认证 | JWT（JJWT 0.12） |
| 实时通信 | STOMP over SockJS（WebSocket） |
| 构建 | Vite（前端）/ Maven（后端）/ pip（AI 服务） |
| 部署 | Docker + Docker Compose + Nginx |

### 前端架构

```
frontend/src/
├── api/                    # Axios 请求模块
│   ├── http.ts             # 实例、Bearer 注入、401 自动 refresh
│   ├── ai.ts               # AI 对话 API（会话列表、消息收发、重命名）
│   ├── sections.ts / subtasks.ts / comments.ts
│   ├── attachments.ts / tags.ts / notifications.ts / dashboard.ts
│   ├── recycleBin.ts / activities.ts / calendar.ts
├── stores/                 # Pinia 状态管理
│   ├── auth.ts             # token / 用户信息
│   ├── todos.ts            # 待办 CRUD / 过滤 / 排序 / 跨分区移动（含导出）
│   ├── teams.ts            # 团队列表、当前团队
│   ├── projects.ts         # 项目列表、当前项目
│   ├── notifications.ts    # 通知列表/未读数
│   └── ai.ts               # AI 对话状态（会话列表、消息记录、流式接收）
├── views/
│   ├── LoginView.vue / RegisterView.vue
│   ├── DashboardView.vue   # 首页（仪表盘：概览+趋势+项目排名+人员统计+标签热力图）
│   ├── ProjectDetailView.vue # 项目看板（核心工作区，含数据导入）
│   ├── CalendarView.vue    # FullCalendar 月/周/日视图
│   ├── RecycleBinView.vue  # 回收站：浏览/恢复/彻底删除
│   └── TeamView.vue        # 团队详情
├── components/
│   ├── layout/AppLayout.vue   # el-aside + el-header + el-main（含搜索框、主题/语言切换）
│   ├── project/ProjectPanel.vue   # 侧边栏项目列表
│   ├── team/TeamPanel.vue         # 侧边栏团队列表
│   ├── todo/            # TodoList, TodoItem, TodoForm, TodoFilters, TodoDetailDrawer, SubtaskList
│   ├── comment/         # CommentList, CommentItem
│   ├── attachment/      # AttachmentList
│   ├── tag/             # TagManager
│   ├── ai/              # AiChatPanel, ChatInput, ChatView, MessageBubble, ConversationList, WelcomeScreen, CodeBlock
│   ├── notification/    # NotificationPanel
│   ├── dashboard/       # StatsCards, ProjectRanking, AssigneeStats, TagHeatmap（ECharts）
│   ├── search/          # SearchBox（全局搜索，Ctrl+K 快捷键）
│   ├── activity/        # ActivityTimeline（操作日志时间线）
│   ├── import/          # ImportDialog（三步导入向导：上传→预览→结果）
│   └── common/          # QuickAddDialog（Ctrl+K 快速添加）
├── i18n/                 # vue-i18n 中英文包
│   ├── index.ts
│   └── locales/zh-CN.js, en-US.js
├── composables/          # useTheme（深色模式切换）
├── router/index.ts      # 路由：/dashboard、/projects/:projectId、/team/:id、/calendar、/recycle-bin
├── services/websocket.ts # STOMP over SockJS
└── types/index.ts        # 全局 TS 类型定义
```

### 后端架构

```
backend/src/main/java/com/todo/
├── controller/     # Auth, Todo, Team, Project, Section, Subtask, Tag, Comment, Attachment, Reminder, Notification, Dashboard, ActivityLog, Calendar, Ai, AiMessage
├── service/        # 对应业务逻辑 + 权限校验（含 AiService 调用 AI 服务、ActivityLogService 异步日志）
├── mapper/         # MyBatis-Plus Mapper（继承 BaseMapper）
├── entity/         # 数据库实体（BaseEntity 含 @TableLogic 软删除）
├── scheduler/      # CleanupScheduler（每日 3:00 清理 30 天前回收站待办）
├── dto/request/ + dto/response/（ApiResponse 统一包装）
├── security/       # JwtTokenProvider + JwtAuthFilter
├── exception/      # AppException + GlobalExceptionHandler
└── config/         # SecurityConfig, WebSocketConfig, ReminderScheduler, CleanupScheduler, MybatisPlusMetaHandler
```

关键机制：
- `TodoService.getAndAssertAccess()` 统一处理项目/团队 todo 的访问控制
- `AppException` 工厂方法生成标准异常，`GlobalExceptionHandler` 统一转为 HTTP 响应
- `WebSocketConfig` 在握手阶段通过 query param `token` 校验 JWT
- `ReminderScheduler` 每分钟扫描到期待发送的提醒
- 后端 `AiService` 作为代理，将前端对话请求转发给 `ai-service`

### AI 服务架构

独立的 Python 微服务，通过 OpenAI 兼容 SDK 对接 LLM（默认 DeepSeek），提供 AI 对话功能。

```
ai-service/
├── app/
│   ├── main.py              # FastAPI 入口 + CORS 配置
│   ├── config.py            # 环境配置（provider / api_key / model）
│   ├── middleware.py         # INTERNAL_API_KEY 认证
│   ├── routes/chat.py       # 会话管理 + 消息流式/非流式响应
│   ├── session/manager.py   # 会话模型管理（消息构建、token 追踪）
│   ├── llm/
│   │   ├── base.py          # LLM 抽象基类
│   │   └── openai_compat.py # OpenAI SDK 适配器（可切换任意兼容 provider）
│   └── tools/
│       ├── registry.py      # 工具注册表（工具发现 + schema 生成）
│       ├── todo_tools.py    # 待办领域工具集（增删改查 / 项目 / 标签 / 评论）
│       └── context.py       # 注入参考上下文（项目名等）
├── Dockerfile
└── requirements.txt          # FastAPI, httpx, openai, lxml, beautifulsoup4
```

**对话流程：**

1. 前端 → 后端 `/api/ai/**` → ai-service `/chat/send`（由后端 `AiService` 代理转发）
2. ai-service 构建消息列表，注入系统提示词和可用工具定义
3. LLM 流式回复，支持工具调用（查询待办、创建任务等）
4. 新增消息自动同步回后端数据库

**关键机制：**
- 支持流式（SSE）和非流式两种回复模式
- `INTERNAL_API_KEY` 鉴权，防止 ai-service 端口直接暴露
- 系统提示词动态注入项目上下文，模型可理解看板结构和分区关系
- 工具调用结果自动注入下一轮对话，实现多轮工具交互

---

## 数据库设计

完整建表脚本：`backend/src/main/resources/db/init.sql`

所有业务表均继承 BaseEntity 字段（按序排列）：
`create_by`(创建人) → `create_time`(创建时间) → `update_by`(更新人) → `update_time`(更新时间) → `deleted`(逻辑删除)

### users

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 自增主键 |
| create_by | BIGINT | 创建人 |
| create_time | DATETIME | 创建时间 |
| update_by | BIGINT | 更新人 |
| update_time | DATETIME | 更新时间 |
| deleted | TINYINT(1) | 逻辑删除标记 |
| username | VARCHAR(50) UNIQUE | 用户名 |
| email | VARCHAR(100) UNIQUE | 邮箱 |
| password | VARCHAR(255) | BCrypt 加密密码 |
| display_name | VARCHAR(100) | 显示名称 |

### teams / team_members

| 字段 | 类型 | 说明 |
|------|------|------|
| teams.id | BIGINT PK | 自增主键 |
| teams.name | VARCHAR(100) | 团队名称 |
| teams.invite_code | VARCHAR(20) UNIQUE | 8 位邀请码 |
| teams.owner_id | BIGINT FK | 创建者 |
| team_members.role | ENUM | OWNER / ADMIN / MEMBER |

联合唯一键：`(team_id, user_id)`

### projects / sections

| 字段 | 类型 | 说明 |
|------|------|------|
| projects.name | VARCHAR(100) | 项目名称 |
| projects.color | VARCHAR(7) | 颜色 |
| projects.icon | VARCHAR(50) | 图标 |
| projects.is_archived | BOOLEAN | 是否归档 |
| projects.team_id | BIGINT FK | 所属团队（NULL=个人项目） |
| sections.project_id | BIGINT FK | 所属项目 |

### todos

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 自增主键 |
| text | VARCHAR(500) | 待办内容 |
| completed | BOOLEAN | 是否完成 |
| category | ENUM | 工作 / 生活 / 学习 / 其他 |
| priority | ENUM | high / medium / low |
| due_date | DATE | 截止日期 |
| sort_order | INT | 排序权重 |
| owner_id | BIGINT FK | 创建者 |
| team_id | BIGINT FK | 所属团队（可空） |
| assignee_id | BIGINT FK | 处理人 |
| project_id | BIGINT FK | 所属项目（可空） |
| section_id | BIGINT FK | 所属分区（可空） |

### subtasks / tags / comments / attachments / reminders / notifications

各表详见 `init.sql`。关键关联：
- `todo_tags` — todo 和 tag 多对多
- `comments.parent_id` — 支持嵌套回复
- `reminders` — 定时提醒（`remind_at` + `is_sent`）
- `notifications` — 站内通知（`is_read` 标记）

---

## API 接口

所有接口返回统一格式：

```json
{"code": 200, "message": "ok", "data": {}}
```

除 `/api/auth/**` 外，需携带 `Authorization: Bearer <access_token>`。

### 认证 `/api/auth`

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/auth/register` | 注册 | 否 |
| POST | `/api/auth/login` | 登录 | 否 |
| POST | `/api/auth/refresh` | 刷新 token（httpOnly cookie） | 否 |
| GET | `/api/auth/me` | 获取当前用户 | 是 |

### 待办 `/api/todos`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/todos/by-project?projectId=&sectionId=&tagIds=&dateFrom=&dateTo=` | **按项目查询**（核心接口） |
| POST | `/api/todos` | 创建待办（支持 projectId/sectionId） |
| PUT | `/api/todos/{id}` | 更新待办 |
| DELETE | `/api/todos/{id}` | 删除待办 |
| PATCH | `/api/todos/{id}/complete` | 切换完成状态 |
| PUT | `/api/todos/reorder` | 批量更新排序 |
| PATCH | `/api/todos/{id}/move-section` | 移动待办到其他分区 |
| GET | `/api/todos/{id}` | 获取单个待办详情 |
| GET | `/api/todos/search?q=&projectId=` | 全局搜索待办（LIMIT 20） |
| POST | `/api/todos/import` | 批量导入待办（CSV/JSON） |
| GET | `/api/todos/deleted` | 回收站列表 |
| POST | `/api/todos/{id}/restore` | 恢复已删除待办 |
| DELETE | `/api/todos/{id}/permanent` | 彻底删除待办 |
| GET | `/api/todos/calendar?start=&end=` | 日历事件数据 |

### 项目 `/api/projects`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/projects` | 可访问的项目列表 |
| GET | `/api/projects/{id}` | 项目详情 |
| POST | `/api/projects` | 创建项目 |
| PUT | `/api/projects/{id}` | 更新项目 |
| DELETE | `/api/projects/{id}` | 删除项目 |
| GET | `/api/projects/by-team?teamId=` | 按团队查询项目 |

### 分区 `/api/projects/{projectId}/sections`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `` | 分区列表 |
| POST | `` | 创建分区 |
| PUT | `/{sectionId}` | 重命名 |
| DELETE | `/{sectionId}` | 删除分区 |
| PUT | `/reorder` | 拖拽排序 |

### 看板 `/api/dashboard`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/dashboard/overview` | 任务概览（总数/完成/活跃/逾期） |
| GET | `/api/dashboard/trend?days=7` | 完成趋势 |
| GET | `/api/dashboard/upcoming?limit=10` | 即将到期任务 |
| GET | `/api/dashboard/projects` | 项目完成率排名 |
| GET | `/api/dashboard/assignees` | 分配人任务分布 |
| GET | `/api/dashboard/tags` | 标签使用频率 |

### 操作日志 `/api/projects/{projectId}/activities`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/projects/{projectId}/activities?limit=50` | 项目操作日志时间线 |

### AI 对话 `/api/ai`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/ai/chat` | 发送消息（SSE 流式响应），需 `sessionId` + `conversationId` |
| POST | `/api/ai/session` | 创建临时会话 ID |
| GET | `/api/ai/conversations` | 获取会话列表 |
| POST | `/api/ai/conversations` | 创建新会话 |
| DELETE | `/api/ai/conversations/{id}` | 删除会话 |
| PUT | `/api/ai/conversations/{id}/rename` | 重命名会话（请求体 `{"title":"..."}`） |
| GET | `/api/ai/conversations/{id}/messages` | 获取会话消息历史 |
| GET | `/api/ai/messages?sessionId=` | 按 sessionId 查询消息 |

**对话流程**：前端 SSE 连接 → 后端 `/api/ai/chat`（代理转发）→ ai-service `/chat` → LLM 流式回复 → SSE 事件推送回前端。

ai-service 内部还提供 `/generate-title` 端点，自动根据首条消息生成对话标题。

### 其余模块

子任务、标签、评论、附件、提醒、通知的 API 详见后端 Controller 文件注解。

### 团队 `/api/teams`

| 方法 | 路径 | 说明 | 最低权限 |
|------|------|------|----------|
| GET | `/api/teams` | 我的团队列表 | MEMBER |
| POST | `/api/teams` | 创建团队 | — |
| GET | `/api/teams/{id}` | 详情（含成员列表） | MEMBER |
| PUT | `/api/teams/{id}` | 更新团队信息 | OWNER |
| DELETE | `/api/teams/{id}` | 解散团队 | OWNER |
| POST | `/api/teams/join` | 通过邀请码加入 | — |
| DELETE | `/api/teams/{id}/members/{userId}` | 移除成员 | ADMIN |
| PUT | `/api/teams/{id}/members/{userId}/role` | 修改成员角色 | OWNER |
| GET | `/api/teams/{id}/invite-code` | 重新生成邀请码 | OWNER |

---

## 权限模型

### 角色层级

```
OWNER > ADMIN > MEMBER
```

每个团队只有一个 OWNER（创建者），OWNER 不可自降级。

| 操作 | MEMBER | ADMIN | OWNER |
|------|--------|-------|-------|
| 创建/查看团队项目 | ✓ | ✓ | ✓ |
| 删除自己的待办 | ✓ | ✓ | ✓ |
| 删除他人的待办 | ✗ | ✓ | ✓ |
| 移除 MEMBER | ✗ | ✓ | ✓ |
| 移除 ADMIN | ✗ | ✗ | ✓ |
| 设置/取消 ADMIN | ✗ | ✗ | ✓ |
| 更新/删除团队 | ✗ | ✗ | ✓ |
| 重新生成邀请码 | ✗ | ✗ | ✓ |

---

## 关键设计决策

### JWT 双 token 策略

- **access token**：15 分钟，存于前端内存 + localStorage
- **refresh token**：7 天，httpOnly cookie（path: `/api/auth/refresh`）
- 前端 401 自动 refresh，队列防竞态

### 项目看板拖拽

- `ProjectDetailView.vue` 使用 SortableJS 双实例
- 列内拖拽：`group: 'board'`，`forceFallback: true`
- 跨列移动：`onEnd` 中更新本地 state + 异步调用 `moveSection` + `reorder`
- 分区列排序：单独的 Sortable 实例，`PUT /reorder` 传递有序 ID 列表

### 项目访问控制

- `TodoService.getAndAssertAccess()` 统一校验
- 个人项目：仅 owner 可操作
- 团队项目：需是团队成员，删除他人需 ADMIN+
- 个人项目统计 `owner_id = userId`，团队项目统计 `assignee_id = userId`
- 分配人字段仅团队项目可用
- 团队邀请码 8 位 `SecureRandom`，排除 0/O/1/I

### 数据一致性

- MyBatis-Plus `updateById` 跳过 null；置 null 需 `LambdaUpdateWrapper.set()`
- `TodoService.update()` 已改用 `LambdaUpdateWrapper` 统一处理
