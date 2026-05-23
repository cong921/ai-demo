# AI Demo - Spring AI 智能聊天应用

一个基于 Spring Boot 3.x + Spring AI 的智能聊天应用，支持 OpenAI 大模型对话、上下文记忆、MySQL 持久化存储。

---

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17 | 编程语言 |
| Spring Boot | 3.4.5 | 应用框架 |
| Spring AI | 1.0.7 | AI 能力集成框架 |
| MySQL | 8.x | 数据库，存储对话记忆和历史 |
| Spring Data JPA | - | ORM 框架，操作数据库 |
| Lombok | - | 减少样板代码 |

---

## 项目结构

```
ai-demo/
├── pom.xml                          # Maven 依赖配置
├── src/main/
│   ├── java/com/example/aidemo/
│   │   ├── AiDemoApplication.java   # 启动类，程序入口
│   │   ├── config/
│   │   │   └── ChatMemoryConfig.java    # 上下文记忆配置
│   │   ├── controller/
│   │   │   └── ChatController.java      # 接口层，接收 HTTP 请求
│   │   ├── dto/
│   │   │   ├── ChatRequest.java         # 请求参数
│   │   │   └── ChatResponse.java        # 响应结果
│   │   ├── entity/
│   │   │   └── ChatHistory.java         # 聊天记录实体，对应数据库表
│   │   ├── repository/
│   │   │   └── ChatHistoryRepository.java  # 数据库操作层
│   │   └── service/
│   │       └── ChatService.java         # 核心业务逻辑
│   └── resources/
│       ├── application.yml          # 应用配置文件
│       └── schema.sql               # 数据库建表脚本
```

---

## 架构说明

### 整体架构图

```
┌──────────┐     HTTP      ┌────────────────┐     调用      ┌──────────┐
│          │  ──────────►  │                │  ──────────►  │          │
│  前端/    │               │  Spring Boot   │               │  OpenAI  │
│  客户端   │  ◄──────────  │  后端服务      │  ◄──────────  │  API     │
│          │     JSON      │                │     AI回复     │          │
└──────────┘               └───────┬────────┘               └──────────┘
                                   │
                          读写对话数据 │
                                   ▼
                            ┌──────────────┐
                            │    MySQL     │
                            │   数据库     │
                            └──────────────┘
```

### 各层职责

| 层 | 类 | 职责 |
|----|-----|------|
| 接口层 | `ChatController` | 接收 HTTP 请求，参数校验，返回响应 |
| 业务层 | `ChatService` | 核心逻辑：保存消息、调用 AI、管理记忆 |
| 数据层 | `ChatHistoryRepository` | 操作 `chat_history` 表，存取完整聊天记录 |
| 记忆层 | Spring AI `ChatMemory` | 管理上下文记忆，让 AI "记住"之前的对话 |

### 上下文记忆原理

大模型本身是**无状态的**——每次调用就像失忆了一样，不记得之前聊过什么。为了让 AI 能理解上下文，我们需要在每次请求时把历史对话一起发给它。

Spring AI 通过 `PromptChatMemoryAdvisor` 自动完成这件事：

```
第 1 次对话：
  用户说: "你好，我是小明"
  AI 回复: "你好小明！"
  → 这轮对话被保存到 MySQL

第 2 次对话：
  用户说: "你还记得我叫什么吗？"
  Advisor 自动从 MySQL 取出历史，实际发给 AI 的内容：
    [历史] 用户: 你好，我是小明
    [历史] AI: 你好小明！
    [新消息] 用户: 你还记得我叫什么吗？
  AI 回复: "你叫小明！"  ← 因为看到了历史，所以能回答
```

### 双层存储设计

本项目使用两个表存储对话数据，各有分工：

| 表名 | 管理者 | 用途 | 特点 |
|------|--------|------|------|
| `SPRING_AI_CHAT_MEMORY` | Spring AI 自动管理 | 上下文记忆 | 滑动窗口，只保留最近 20 条，用于 AI 理解上下文 |
| `chat_history` | 我们的代码管理 | 完整历史记录 | 永久保存所有对话，用于前端展示和审计 |

简单理解：`SPRING_AI_CHAT_MEMORY` 是 AI 的"短期记忆"，`chat_history` 是"完整日记"。

---

## 快速开始

### 环境准备

- JDK 17+
- Maven 3.8+
- MySQL 8.x
- OpenAI API Key（或兼容的 API 中转服务）

### 第 1 步：初始化数据库

连接 MySQL，执行建表脚本：

```bash
mysql -u root -p < src/main/resources/schema.sql
```

这会创建 `ai_demo` 数据库和 `chat_history` 表。

> `SPRING_AI_CHAT_MEMORY` 表会在应用启动时由 Spring AI 自动创建，无需手动建表。

### 第 2 步：配置应用

编辑 `src/main/resources/application.yml`，修改以下配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ai_demo?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: 你的MySQL用户名
    password: 你的MySQL密码

  ai:
    openai:
      api-key: 你的OpenAI-API-Key
      base-url: https://api.openai.com    # 如果用中转服务，改成中转地址
```

> 也可以通过环境变量设置，不需要改配置文件：
> ```bash
> export OPENAI_API_KEY=sk-xxxxx
> export OPENAI_BASE_URL=https://your-proxy.com
> ```

### 第 3 步：启动应用

```bash
mvn spring-boot:run
```

看到以下日志说明启动成功：

```
Started AiDemoApplication in x.xx seconds
```

---

## API 接口文档

### 1. 发送聊天消息

**请求**

```
POST /api/chat
Content-Type: application/json
```

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| conversationId | String | 否 | 会话 ID，为空时自动生成新会话 |
| message | String | 是 | 用户消息内容 |

**示例：开始新对话**

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "你好，请介绍一下你自己"}'
```

响应：

```json
{
  "conversationId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "message": "你好！我是一个 AI 助手..."
}
```

**示例：继续对话（保持上下文）**

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "message": "你还记得我刚才说了什么吗？"
  }'
```

> 传入上一次返回的 `conversationId`，AI 就能记住之前的对话内容。

### 2. 查询聊天历史

```
GET /api/chat/history/{conversationId}
```

```bash
curl http://localhost:8080/api/chat/history/a1b2c3d4-e5f6-7890-abcd-ef1234567890
```

响应：

```json
[
  {
    "id": 1,
    "conversationId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "role": "user",
    "content": "你好，请介绍一下你自己",
    "createdAt": "2026-05-23T10:30:00"
  },
  {
    "id": 2,
    "conversationId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "role": "assistant",
    "content": "你好！我是一个 AI 助手...",
    "createdAt": "2026-05-23T10:30:02"
  }
]
```

### 3. 清除聊天历史

```
DELETE /api/chat/history/{conversationId}
```

```bash
curl -X DELETE http://localhost:8080/api/chat/history/a1b2c3d4-e5f6-7890-abcd-ef1234567890
```

> 会同时清除 `chat_history` 和 `SPRING_AI_CHAT_MEMORY` 中的记录。

---

## 数据库表结构

### chat_history 表（完整聊天记录）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键，自增 |
| conversation_id | VARCHAR(36) | 会话 ID，同一轮对话共享 |
| role | VARCHAR(20) | 消息角色：`user`（用户）或 `assistant`（AI） |
| content | TEXT | 消息内容 |
| created_at | TIMESTAMP | 创建时间，自动填充 |

### SPRING_AI_CHAT_MEMORY 表（AI 上下文记忆）

由 Spring AI 自动创建和管理，存储 AI 需要的上下文消息，保留最近 20 条。

---

## 常见问题

### Q: 如何使用国内的 OpenAI 中转服务？

修改 `application.yml` 中的 `base-url` 为你的中转地址：

```yaml
spring:
  ai:
    openai:
      base-url: https://your-proxy-url.com
```

### Q: 如何更换 AI 模型？

修改 `application.yml` 中的 `model`：

```yaml
spring:
  ai:
    openai:
      chat:
        options:
          model: gpt-4o        # 更强大的模型
          # model: gpt-4o-mini # 性价比更高的模型（默认）
```

### Q: 上下文记忆能保留多少条？

默认保留最近 20 条消息，可在 `ChatMemoryConfig.java` 中修改 `maxMessages` 的值。

### Q: conversationId 是什么？

它是一个 UUID，用来标识一次完整的对话会话。同一个 `conversationId` 下的消息共享上下文记忆。前端应该在第一次对话时保存返回的 `conversationId`，后续对话传入同一个 ID 即可保持上下文。
