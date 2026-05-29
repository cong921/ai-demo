# Boss 直聘聊天浏览器自动化方案 - 实现计划

## 需求理解

用户希望实现一个**全自动化的浏览器机器人**，能够：
1. 打开 Boss 直聘网页版聊天界面
2. **自动监听** HR 发来的新消息
3. **自动识别**消息内容（离职原因、到岗时间等常见问题）
4. **调用后端 AI 接口**生成合适的回复
5. **自动将回复填入**聊天输入框并发送

## 技术约束

当前环境存在以下限制：
- `agent-browser` CLI 工具因沙箱限制无法创建 socket 目录，无法直接在本机运行浏览器自动化
- 但 `agent-browser` 的 Node.js 库（`agent-browser` npm 包）可以在代码中调用
- 用户本地已安装 Chrome 浏览器

## 推荐方案：Java + Playwright 浏览器自动化

### 为什么选择 Playwright？

| 对比项 | Playwright | agent-browser CLI | Selenium |
|--------|-----------|-------------------|----------|
| 安装复杂度 | Maven 依赖即可 | 需要 CLI + Chrome | 需要 WebDriver |
| 沙箱兼容性 | ✅ 纯 Java 代码，无沙箱问题 | ❌ 受沙箱限制 | ✅ |
| API 设计 | 现代、简洁 | 命令行驱动 | 较老旧 |
| 稳定性 | 高（自动等待、重试） | 中 | 中 |
| 与 Spring Boot 集成 | 完美 | 需调用子进程 | 良好 |

Playwright 是微软开源的浏览器自动化工具，支持 Java、Python、Node.js 等多种语言，API 设计现代，稳定性极高。

## 架构设计

```
┌─────────────────────────────────────────────────────────────────┐
│                        Spring Boot 后端                          │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐      │
│  │ BrowserBot   │───▶│ AutoReply    │───▶│ Playwright   │      │
│  │ Service      │    │ Service      │    │ Browser      │      │
│  │ (调度中心)    │    │ (生成回复)    │    │ (操控浏览器)  │      │
│  └──────────────┘    └──────────────┘    └──────────────┘      │
│         │                   │                   │               │
│         │                   │                   │               │
│         ▼                   ▼                   ▼               │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐      │
│  │ 定时任务      │    │ KnowledgeBase│    │ Chrome       │      │
│  │ (轮询检查)    │    │ Service      │    │ Browser      │      │
│  └──────────────┘    └──────────────┘    └──────────────┘      │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ HTTP
                              ▼
                    ┌─────────────────┐
                    │  Boss 直聘网页   │
                    │  zhipin.com     │
                    └─────────────────┘
```

## 实现步骤

### Step 1: 添加 Playwright 依赖

**修改文件**：`pom.xml`

```xml
<dependency>
    <groupId>com.microsoft.playwright</groupId>
    <artifactId>playwright</artifactId>
    <version>1.49.0</version>
</dependency>
```

### Step 2: 创建浏览器自动化服务

**新增文件**：`src/main/java/com/example/aidemo/service/BrowserBotService.java`

```java
package com.example.aidemo.service;

import com.example.aidemo.dto.ReplySuggestion;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
public class BrowserBotService {

    private final AutoReplyService autoReplyService;
    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;
    private volatile boolean running = false;
    private final Set<String> processedMessages = ConcurrentHashMap.newKeySet();
    private ScheduledExecutorService scheduler;

    public BrowserBotService(AutoReplyService autoReplyService) {
        this.autoReplyService = autoReplyService;
    }

    /**
     * 启动浏览器并登录 Boss 直聘
     */
    public void start(String chromeExecutablePath) {
        if (running) {
            log.warn("浏览器机器人已在运行中");
            return;
        }

        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(false)  // 显示浏览器窗口，方便用户扫码登录
                .setExecutablePath(Paths.get(chromeExecutablePath)));

        context = browser.newContext();
        page = context.newPage();

        // 打开 Boss 直聘聊天页面
        page.navigate("https://www.zhipin.com/web/geek/chat");
        log.info("已打开 Boss 直聘聊天页面，请扫码登录...");

        running = true;

        // 启动轮询任务，每 3 秒检查一次新消息
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::checkNewMessages, 10, 3, TimeUnit.SECONDS);
    }

    /**
     * 检查新消息并自动回复
     */
    private void checkNewMessages() {
        if (!running || page == null) return;

        try {
            // Boss 直聘聊天消息选择器（需要根据实际页面结构调整）
            // 这里使用示例选择器，实际需要根据页面 DOM 结构确定
            List<ElementHandle> messages = page.querySelectorAll(".message-item");

            for (ElementHandle msg : messages) {
                // 只处理对方发来的消息
                String senderClass = msg.getAttribute("class");
                if (senderClass != null && senderClass.contains("other")) {
                    String messageText = msg.innerText().trim();
                    String messageId = msg.getAttribute("data-id");

                    // 避免重复处理同一条消息
                    if (messageId != null && !processedMessages.contains(messageId)) {
                        processedMessages.add(messageId);
                        handleNewMessage(messageText);
                    }
                }
            }
        } catch (Exception e) {
            log.error("检查新消息时出错", e);
        }
    }

    /**
     * 处理新消息：调用 AI 生成回复并发送
     */
    private void handleNewMessage(String hrMessage) {
        log.info("收到 HR 消息: {}", hrMessage);

        // 调用 AI 生成回复
        ReplySuggestion suggestion = autoReplyService.suggestReply(hrMessage);

        if (suggestion.isMatched()) {
            String reply = suggestion.getSuggestedReply();
            log.info("AI 生成回复: {}", reply);

            // 将回复填入输入框并发送
            sendReply(reply);
        } else {
            log.info("未匹配到已知问题类型，跳过回复: {}", hrMessage);
        }
    }

    /**
     * 在聊天界面发送回复
     */
    private void sendReply(String reply) {
        try {
            // 1. 找到输入框并填入回复
            // Boss 直聘输入框选择器（需要根据实际页面调整）
            ElementHandle inputBox = page.waitForSelector(".chat-input textarea, .input-box textarea, [contenteditable='true']",
                    new Page.WaitForSelectorOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(5000));

            if (inputBox != null) {
                inputBox.fill(reply);
                log.info("已填入回复到输入框");

                // 2. 点击发送按钮
                ElementHandle sendButton = page.querySelector(".send-btn, .btn-send, button[type='submit']");
                if (sendButton != null) {
                    sendButton.click();
                    log.info("已发送回复");
                } else {
                    // 如果没有发送按钮，尝试按 Enter 键
                    inputBox.press("Enter");
                    log.info("已按 Enter 发送回复");
                }
            }
        } catch (Exception e) {
            log.error("发送回复失败", e);
        }
    }

    /**
     * 停止浏览器机器人
     */
    public void stop() {
        running = false;
        if (scheduler != null) {
            scheduler.shutdown();
        }
        if (context != null) {
            context.close();
        }
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
        log.info("浏览器机器人已停止");
    }

    public boolean isRunning() {
        return running;
    }
}
```

### Step 3: 创建浏览器机器人控制接口

**新增文件**：`src/main/java/com/example/aidemo/controller/BrowserBotController.java`

```java
package com.example.aidemo.controller;

import com.example.aidemo.service.BrowserBotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/bot")
@RequiredArgsConstructor
public class BrowserBotController {

    private final BrowserBotService browserBotService;

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startBot() {
        // 默认使用系统 Chrome 路径
        String chromePath = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
        browserBotService.start(chromePath);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "浏览器机器人已启动，请扫码登录 Boss 直聘"
        ));
    }

    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopBot() {
        browserBotService.stop();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "浏览器机器人已停止"
        ));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "running", browserBotService.isRunning()
        ));
    }
}
```

### Step 4: 配置文件更新

**修改文件**：`src/main/resources/application.yml`

```yaml
app:
  auto-reply:
    enabled: true
    knowledge-base:
      profile-path: classpath:knowledge/profile.md
  browser-bot:
    chrome-path: /Applications/Google Chrome.app/Contents/MacOS/Google Chrome
    poll-interval-seconds: 3
    headless: false  # 是否无头模式（false 显示窗口，方便调试）
```

### Step 5: 消息选择器配置（关键！）

Boss 直聘的页面 DOM 结构会变化，需要根据实际情况调整选择器。建议：

1. **首次使用时手动调试**：启动浏览器后，用 Chrome DevTools 查看实际 DOM 结构
2. **将选择器提取到配置文件**：方便后续调整

**新增文件**：`src/main/java/com/example/aidemo/config/BrowserSelectorConfig.java`

```java
package com.example.aidemo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.browser-bot")
public class BrowserSelectorConfig {
    private String chromePath = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
    private int pollIntervalSeconds = 3;
    private boolean headless = false;

    // Boss 直聘页面选择器（需要根据实际页面调整）
    private String messageListSelector = ".message-list";
    private String messageItemSelector = ".message-item";
    private String otherMessageClass = "other";
    private String inputBoxSelector = ".chat-input textarea";
    private String sendButtonSelector = ".send-btn";
}
```

## 使用流程

```
1. 启动 Spring Boot 应用
       ↓
2. 调用 POST /api/bot/start
   - 打开 Chrome 浏览器
   - 导航到 Boss 直聘聊天页面
   - 显示窗口，用户扫码登录
       ↓
3. 用户扫码登录 Boss 直聘
       ↓
4. 机器人开始轮询（每 3 秒检查一次）
       ↓
5. 检测到 HR 新消息
   - 提取消息文本
   - 调用 AutoReplyService 生成回复
       ↓
6. 自动填入输入框并发送
       ↓
7. 用户可随时调用 POST /api/bot/stop 停止机器人
```

## 文件变更清单

### 新增文件
| 文件路径 | 说明 |
|---------|------|
| `pom.xml` | 添加 Playwright 依赖 |
| `src/main/java/com/example/aidemo/service/BrowserBotService.java` | 浏览器自动化核心服务 |
| `src/main/java/com/example/aidemo/controller/BrowserBotController.java` | 机器人控制接口 |
| `src/main/java/com/example/aidemo/config/BrowserSelectorConfig.java` | 页面选择器配置 |

### 修改文件
| 文件路径 | 修改内容 |
|---------|---------|
| `pom.xml` | 添加 `playwright` 依赖 |
| `src/main/resources/application.yml` | 新增 `app.browser-bot` 配置 |

## API 汇总

| 方法 | 接口 | 说明 |
|------|------|------|
| POST | `/api/bot/start` | 启动浏览器机器人 |
| POST | `/api/bot/stop` | 停止浏览器机器人 |
| GET | `/api/bot/status` | 查询机器人运行状态 |
| POST | `/api/auto-reply/suggest` | 手动测试 AI 回复生成 |
| POST | `/api/auto-reply/reload` | 重新加载知识库 |

## 注意事项

1. **选择器需要根据实际情况调整**：Boss 直聘页面结构可能变化，首次使用时需要通过 Chrome DevTools 确认正确的 CSS 选择器
2. **登录问题**：Boss 直聘可能需要扫码登录，首次启动时需要用户手动扫码
3. **频率限制**：不要过于频繁地轮询，建议 3-5 秒一次，避免被检测为机器人
4. **消息去重**：使用 `processedMessages` 集合避免重复回复同一条消息
5. **异常处理**：网络波动或页面刷新可能导致选择器失效，需要做好异常处理和重试

## 进阶扩展

1. **消息队列**：使用消息队列（如 Redis）存储待处理消息，提高可靠性
2. **智能等待**：使用 Playwright 的自动等待功能，而不是固定轮询间隔
3. **多账号支持**：支持同时管理多个 Boss 直聘账号
4. **回复确认**：发送前弹窗确认，让用户审核后再发送
5. **日志记录**：记录所有自动回复的操作日志，方便审计
