# Boss 直聘聊天智能回复功能 - 实现计划（RAG 知识库方案）

## 需求理解

用户每天在 Boss 直聘上和 HR/面试官聊天时，会遇到大量重复性问题，例如：
- 离职原因是什么？
- 什么时候能到岗？
- 学校和专业是什么？
- 期望薪资是多少？
- 目前在职还是离职？

这些问题的答案相对固定，用户希望有一个智能回复功能，能够：
1. **自动识别** HR 发来的消息属于哪类常见问题
2. **根据预设的个人信息**，自动生成合适的回复
3. **支持自定义** 个人信息的配置（离职原因、到岗时间、学校专业等）
4. **支持手动确认** 后再发送（或可选自动发送）

## 技术方案：RAG 知识库 + AI 生成回复

### 核心思路

**RAG（Retrieval-Augmented Generation，检索增强生成）**：
- 用户的个人信息（离职原因、到岗时间、学校专业等）作为**知识库文档**
- 收到 HR 消息后，先从知识库中**检索**最相关的个人信息片段
- 将检索到的信息 + HR 消息一起传给 AI，让 AI **生成**自然、专业的回复

**为什么用 RAG 而不是关键词匹配？**
- 能处理更灵活、更复杂的问法（如"你为啥离开上一家公司"也能匹配到"离职原因"）
- 回复语气更自然、更专业
- 可以扩展支持更多类型的问答，不仅限于预设问题

**为什么知识库存文件而不是数据库？**
- 用户要求不写到数据库
- 个人信息文档化更直观，方便手动编辑
- 支持 Markdown/JSON/YAML 等多种格式

### 架构图

```
┌─────────────┐      HR消息       ┌─────────────────┐
│             │ ───────────────► │                 │
│  Boss直聘   │                  │  AutoReplyService│
│  聊天界面   │ ◄─────────────── │                 │
│             │    AI生成回复     └────────┬────────┘
└─────────────┘                            │
                                           │
                              ┌────────────┴────────────┐
                              │                         │
                              ▼                         ▼
                    ┌─────────────────┐      ┌─────────────────┐
                    │  知识库检索      │      │  OpenAI API     │
                    │  (向量相似度)    │      │  (生成回复)      │
                    └────────┬────────┘      └─────────────────┘
                             │
                             ▼
                    ┌─────────────────┐
                    │  个人信息文档    │
                    │  (本地文件)      │
                    │  profile.md     │
                    └─────────────────┘
```

## 实现步骤

### Step 1: 创建个人信息知识库文档

**新增文件**：
- `src/main/resources/knowledge/profile-template.md` - 个人信息模板

**文档格式（Markdown）**：

```markdown
# 个人求职信息

## 基本信息
- 姓名：张三
- 工作年限：5年
- 目前状态：已离职

## 离职原因
我离职的主要原因是希望寻求更好的职业发展机会。上一家公司的技术栈比较老旧，
我希望能够接触更前沿的技术，同时也有更大的成长空间。

## 到岗时间
我目前处于离职状态，可以随时到岗。如果需要交接，一周内也可以接受。

## 教育背景
我毕业于某某大学，专业是计算机科学与技术，本科学历。

## 期望薪资
我的期望薪资是 20K-25K，具体可以根据公司福利和发展空间协商。

## 期望城市
我希望在上海或杭州工作。
```

用户只需修改这个 Markdown 文件，填写自己的真实信息即可。

### Step 2: 创建知识库加载与检索服务

**新增文件**：
- `src/main/java/com/example/aidemo/service/KnowledgeBaseService.java` - 知识库服务

**核心逻辑**：

```java
@Service
public class KnowledgeBaseService {
    
    private static final String PROFILE_PATH = "classpath:knowledge/profile.md";
    
    private String profileContent;
    
    @PostConstruct
    public void init() {
        // 启动时加载个人信息文档
        this.profileContent = loadProfile();
    }
    
    /**
     * 根据 HR 消息，从知识库中检索最相关的信息
     */
    public String retrieveRelevantInfo(String hrMessage) {
        // 简单实现：按段落分块，计算关键词匹配度
        // 进阶实现：使用向量嵌入（Embedding）计算语义相似度
        
        String[] sections = profileContent.split("## ");
        String bestMatch = "";
        int bestScore = 0;
        
        for (String section : sections) {
            int score = calculateRelevance(section, hrMessage);
            if (score > bestScore) {
                bestScore = score;
                bestMatch = section;
            }
        }
        
        return bestMatch;
    }
    
    private int calculateRelevance(String section, String hrMessage) {
        String lowerSection = section.toLowerCase();
        String lowerMsg = hrMessage.toLowerCase();
        
        // 关键词匹配打分
        String[][] keywordGroups = {
            {"离职", "离开", "为啥走", "为什么走", "辞职"},
            {"到岗", "入职", "什么时候", "多久", "时间"},
            {"学校", "专业", "学历", "毕业", "大学"},
            {"薪资", "工资", "期望", "多少钱", "待遇"},
            {"在职", "离职", "状态", "目前"}
        };
        
        int score = 0;
        for (String[] group : keywordGroups) {
            boolean sectionHas = Arrays.stream(group).anyMatch(lowerSection::contains);
            boolean msgHas = Arrays.stream(group).anyMatch(lowerMsg::contains);
            if (sectionHas && msgHas) {
                score += 10;
            }
        }
        
        return score;
    }
    
    private String loadProfile() {
        // 从 classpath 加载 profile.md
        // 如果文件不存在，返回默认模板
    }
    
    /**
     * 重新加载知识库（支持热更新）
     */
    public void reload() {
        this.profileContent = loadProfile();
    }
}
```

### Step 3: 创建 AI 智能回复服务

**新增文件**：
- `src/main/java/com/example/aidemo/service/AutoReplyService.java` - AI 智能回复服务

**核心逻辑**：

```java
@Service
@RequiredArgsConstructor
public class AutoReplyService {
    
    private final ChatClient.Builder chatClientBuilder;
    private final KnowledgeBaseService knowledgeBaseService;
    
    /**
     * 根据 HR 消息生成智能回复
     */
    public ReplySuggestion suggestReply(String hrMessage) {
        // 1. 从知识库检索相关信息
        String relevantInfo = knowledgeBaseService.retrieveRelevantInfo(hrMessage);
        
        if (relevantInfo.isEmpty()) {
            return ReplySuggestion.builder()
                .matched(false)
                .suggestedReply("未找到相关信息，请检查知识库配置")
                .build();
        }
        
        // 2. 构建 RAG Prompt
        String systemPrompt = """
            你是一位专业的求职沟通顾问。请根据以下个人信息，回答 HR 的问题。
            要求：
            1. 语气自然、专业、礼貌
            2. 回答简洁，不要太长
            3. 不要编造个人信息中没有的内容
            4. 如果信息不足，可以委婉表示需要进一步沟通
            
            个人信息：
            %s
            """.formatted(relevantInfo);
        
        // 3. 调用 AI 生成回复
        String reply = chatClientBuilder.build()
            .prompt()
            .system(systemPrompt)
            .user(hrMessage)
            .call()
            .content();
        
        return ReplySuggestion.builder()
            .matched(true)
            .questionType(extractQuestionType(hrMessage))
            .suggestedReply(reply)
            .confidence(0.9)
            .build();
    }
    
    private String extractQuestionType(String hrMessage) {
        // 简单提取问题类型，用于展示
        if (hrMessage.contains("离职")) return "离职原因";
        if (hrMessage.contains("到岗") || hrMessage.contains("入职")) return "到岗时间";
        if (hrMessage.contains("学校") || hrMessage.contains("专业")) return "教育背景";
        if (hrMessage.contains("薪资") || hrMessage.contains("工资")) return "期望薪资";
        return "其他";
    }
}
```

### Step 4: 新增接口层

**新增文件**：
- `src/main/java/com/example/aidemo/dto/AutoReplyRequest.java` - 请求 DTO
- `src/main/java/com/example/aidemo/dto/ReplySuggestion.java` - 回复建议 DTO
- `src/main/java/com/example/aidemo/controller/AutoReplyController.java` - 智能回复接口

**接口设计**：

```
POST /api/auto-reply/suggest
请求体：
{
    "hrMessage": "请问你什么时候能到岗？"
}

响应体：
{
    "matched": true,
    "questionType": "到岗时间",
    "suggestedReply": "我目前处于离职状态，可以随时到岗。",
    "confidence": 0.9
}
```

```
POST /api/auto-reply/reload
说明：重新加载知识库文档（修改 profile.md 后调用）

响应体：
{
    "success": true,
    "message": "知识库已重新加载"
}
```

### Step 5: 配置文件

**修改文件**：
- `src/main/resources/application.yml` - 新增知识库配置

```yaml
app:
  auto-reply:
    enabled: true
    knowledge-base:
      profile-path: classpath:knowledge/profile.md  # 个人信息文档路径
      reload-on-startup: true                        # 启动时自动加载
```

## 文件变更清单

### 新增文件
| 文件路径 | 说明 |
|---------|------|
| `src/main/resources/knowledge/profile-template.md` | 个人信息知识库模板 |
| `src/main/java/com/example/aidemo/service/KnowledgeBaseService.java` | 知识库加载与检索服务 |
| `src/main/java/com/example/aidemo/service/AutoReplyService.java` | AI 智能回复服务 |
| `src/main/java/com/example/aidemo/dto/AutoReplyRequest.java` | 智能回复请求 DTO |
| `src/main/java/com/example/aidemo/dto/ReplySuggestion.java` | 回复建议 DTO |
| `src/main/java/com/example/aidemo/controller/AutoReplyController.java` | 智能回复接口 |

### 修改文件
| 文件路径 | 修改内容 |
|---------|---------|
| `src/main/resources/application.yml` | 新增 `app.auto-reply` 知识库配置 |

## 使用流程

```
1. 用户首次使用 → 复制 profile-template.md 为 profile.md，填写个人信息
                    ↓
2. 启动应用 → 自动加载知识库文档
                    ↓
3. 收到 HR 消息 → 调用 POST /api/auto-reply/suggest
                    ↓
4. 返回 AI 生成的回复建议 → 前端展示"一键回复"按钮
                    ↓
5. 用户点击确认 → 发送回复
                    ↓
6. 修改个人信息 → 更新 profile.md → 调用 POST /api/auto-reply/reload
```

## API 汇总

| 方法 | 接口 | 说明 |
|------|------|------|
| POST | `/api/auto-reply/suggest` | 根据 HR 消息生成 AI 回复建议 |
| POST | `/api/auto-reply/reload` | 重新加载知识库文档 |

## 进阶扩展（未来可选）

1. **向量检索**：使用 Embedding 模型将知识库文档向量化，支持语义相似度检索（而不仅是关键词匹配）
2. **多文档支持**：支持多个知识库文档（如不同公司的不同回答策略）
3. **历史对话学习**：根据用户的历史聊天记录，自动优化回复风格
