# AI Demo - Boss 直聘智能回复助手

基于 Spring Boot 3.x + Spring AI 的 Boss 直聘智能回复应用。输入 HR 消息，AI 根据你的个人信息知识库自动生成回复；知识库没有的内容，大模型也能直接作答。

---

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17 | 编程语言 |
| Spring Boot | 3.4.5 | 应用框架 |
| Spring AI | 1.0.7 | AI 能力集成框架 |
| spring-ai-openai | 1.0.7 | 通过 OpenAI 兼容模式调用大模型 |
| Lombok | - | 减少样板代码 |

---

## 整体架构

```
┌──────────────┐     HTTP      ┌─────────────────────────────────────┐
│              │  ──────────►  │                                     │
│   前端页面    │               │         Spring Boot 后端             │
│  index.html  │               │                                     │
│              │  ◄──────────  │  ┌─────────────────────────────┐    │
└──────────────┘     JSON      │  │    AutoReplyController      │    │
                              │  │    POST /api/auto-reply/     │    │
                              │  │         suggest              │    │
                              │  └──────────┬──────────────────┘    │
                              │             │                       │
                              │             ▼                       │
                              │  ┌─────────────────────────────┐    │
                              │  │    AutoReplyService         │    │
                              │  │                             │    │
                              │  │  1. 知识库检索               │    │
                              │  │  2. 构建 Prompt             │    │
                              │  │  3. 调用大模型               │    │
                              │  └──┬──────────────────┬───────┘    │
                              │     │                  │            │
                              │     ▼                  ▼            │
                              │ ┌──────────┐   ┌──────────────┐    │
                              │ │知识库     │   │ 大模型 API    │    │
                              │ │JSON 文件  │   │ (阿里云百炼)  │    │
                              │ └──────────┘   └──────────────┘    │
                              └─────────────────────────────────────┘
```

### 核心流程

```
HR 消息输入
    │
    ▼
KnowledgeBaseService.retrieveRelevantInfo()
    │
    ├─ 匹配到知识库条目（可能多条）
    │   → 将匹配到的所有个人信息注入 system prompt
    │   → AI 基于个人信息生成回复
    │
    └─ 未匹配到知识库条目
        → 使用通用求职顾问提示词
        → AI 用大模型能力直接作答
```

---

## 项目结构

```
ai-demo/
├── pom.xml                                      # Maven 依赖配置
├── src/main/
│   ├── java/com/example/aidemo/
│   │   ├── AiDemoApplication.java               # 启动类
│   │   ├── config/
│   │   │   └── ChatMemoryConfig.java            # 上下文记忆配置（内存存储）
│   │   ├── controller/
│   │   │   ├── AutoReplyController.java         # 智能回复接口
│   │   │   └── ChatController.java              # 通用聊天接口
│   │   ├── dto/
│   │   │   ├── AutoReplyRequest.java            # 智能回复请求参数
│   │   │   ├── ReplySuggestion.java             # 智能回复响应结果
│   │   │   ├── ChatRequest.java                 # 聊天请求参数
│   │   │   └── ChatResponse.java                # 聊天响应结果
│   │   └── service/
│   │       ├── AutoReplyService.java            # 智能回复核心逻辑
│   │       ├── KnowledgeBaseService.java        # 知识库加载与检索
│   │       └── ChatService.java                 # 通用聊天服务
│   └── resources/
│       ├── application.yml                      # 应用配置
│       ├── knowledge/
│       │   └── profile.json                     # 个人信息知识库（JSON 格式）
│       └── static/
│           └── index.html                       # 前端页面
```

---

## 各模块说明

### 1. 知识库（KnowledgeBaseService）

知识库使用 JSON 文件存储个人信息，支持嵌套结构：

```json
{
  "离职时间": "3天",
  "离职原因": "公司需要长期外地出差",
  "学历": {
    "统招本科": "是",
    "专业": "信息系统",
    "学位证": "有"
  },
  "薪资": {
    "当前": "18K",
    "期望": "18K-25K"
  }
}
```

**检索逻辑：**

1. 启动时加载 JSON 文件，用 `flattenJson()` 将嵌套结构展平为 `Map<String, String>`（如 `学历.专业` → `信息系统`）
2. 接收 HR 消息后，对每条知识库条目计算关键词匹配分数
3. 返回**所有匹配的条目**（而非只取最佳一条），支持一条消息包含多个问题的场景
4. 匹配不到任何条目时返回空字符串，由大模型直接作答

**关键词匹配示例：**

HR 消息："离职原因？是否统招本科？期望薪资？"

匹配结果：
```
离职原因：公司需要长期外地出差
学历.统招本科：是
薪资.期望：18K-25K
```

### 2. 智能回复（AutoReplyService）

核心服务，根据知识库匹配结果决定 Prompt 策略：

| 场景 | system prompt | 效果 |
|------|--------------|------|
| 知识库有匹配 | 求职顾问 + 个人信息 | AI 基于你的信息回答 |
| 知识库无匹配 | 求职顾问（通用） | AI 用大模型能力直接作答 |

每条消息独立处理，不记录上下文。

### 3. 通用聊天（ChatService）

支持上下文记忆的通用 AI 聊天服务，使用 `InMemoryChatMemoryRepository` 存储对话历史（内存存储，重启即清空）。

### 4. 前端页面（index.html）

单页面应用，功能：
- 输入 HR 消息，获取 AI 回复
- 显示问题类型标签（蓝色=知识库已知，橙色=其他）
- 回车发送，Shift+回车换行

---

## 快速开始

### 环境准备

- JDK 17+
- Maven 3.8+
- 阿里云百炼 API Key（或其他 OpenAI 兼容的 API 服务）

### 第 1 步：配置应用

编辑 `src/main/resources/application.yml`：

```yaml
spring:
  ai:
    openai:
      api-key: 你的API Key
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      chat:
        options:
          model: qwen-plus          # 阿里云百炼支持的模型
          temperature: 0.7
```

也可以通过环境变量设置：

```bash
export OPENAI_API_KEY=sk-xxxxx
```

### 第 2 步：编辑个人信息

修改 `src/main/resources/knowledge/profile.json`，填入你自己的信息：

```json
{
  "离职原因": "你的离职原因",
  "到岗时间": "你的到岗时间",
  "学历": {
    "学校": "你的学校",
    "专业": "你的专业"
  }
}
```

### 第 3 步：启动应用

```bash
mvn spring-boot:run
```

打开浏览器访问 http://localhost:8080

---

## API 接口

### 智能回复

```
POST /api/auto-reply/suggest
Content-Type: application/json

{
  "hrMessage": "你好，请问你的离职原因是什么？"
}
```

响应：

```json
{
  "matched": true,
  "questionType": "离职原因",
  "suggestedReply": "主要是因为公司需要长期外地出差，不太适合我的情况。",
  "confidence": 0.9
}
```

### 重新加载知识库

```
POST /api/auto-reply/reload
```

修改 `profile.json` 后调用此接口，无需重启应用。

### 通用聊天

```
POST /api/chat
Content-Type: application/json

{
  "conversationId": "可选，传入可保持上下文",
  "message": "你好"
}
```

---

## 支持的大模型

本项目通过 OpenAI 兼容模式调用，支持所有兼容 OpenAI API 格式的服务：

| 服务商 | base-url | 模型示例 |
|--------|----------|----------|
| 阿里云百炼 | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `qwen-plus`、`qwen-max`、`kimi-k2.6` |
| Moonshot | `https://api.moonshot.cn/v1` | `moonshot-v1-8k` |
| OpenAI | `https://api.openai.com` | `gpt-4o-mini`、`gpt-4o` |

修改 `application.yml` 中的 `base-url` 和 `model` 即可切换。
