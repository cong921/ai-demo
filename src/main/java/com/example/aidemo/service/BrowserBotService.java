package com.example.aidemo.service;

import com.example.aidemo.config.BrowserSelectorConfig;
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
    private final BrowserSelectorConfig selectorConfig;
    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;
    private volatile boolean running = false;
    private final Set<String> processedMessages = ConcurrentHashMap.newKeySet();
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> pollTask;

    public BrowserBotService(AutoReplyService autoReplyService, BrowserSelectorConfig selectorConfig) {
        this.autoReplyService = autoReplyService;
        this.selectorConfig = selectorConfig;
    }

    public void start(String chromeExecutablePath) {
        if (running) {
            log.warn("浏览器机器人已在运行中");
            return;
        }

        try {
            playwright = Playwright.create();
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(selectorConfig.isHeadless())
                    .setExecutablePath(Paths.get(chromeExecutablePath)));

            context = browser.newContext();
            page = context.newPage();

            page.navigate("https://www.zhipin.com/web/geek/chat");
            log.info("已打开 Boss 直聘聊天页面，请扫码登录...");

            running = true;

            scheduler = Executors.newSingleThreadScheduledExecutor();
            pollTask = scheduler.scheduleAtFixedRate(
                    this::checkNewMessages,
                    10,
                    selectorConfig.getPollIntervalSeconds(),
                    TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("启动浏览器机器人失败", e);
            cleanup();
            throw new RuntimeException("启动浏览器机器人失败", e);
        }
    }

    private void checkNewMessages() {
        if (!running || page == null || page.isClosed()) {
            return;
        }

        try {
            List<ElementHandle> messages = page.querySelectorAll(selectorConfig.getMessageItemSelector());

            for (ElementHandle msg : messages) {
                try {
                    String senderClass = msg.getAttribute("class");
                    if (senderClass != null && senderClass.contains(selectorConfig.getOtherMessageClass())) {
                        String messageText = msg.innerText().trim();
                        String messageId = msg.getAttribute("data-id");

                        if (messageId != null && !processedMessages.contains(messageId)) {
                            processedMessages.add(messageId);
                            handleNewMessage(messageText);
                        }
                    }
                } catch (Exception e) {
                    log.warn("处理单条消息时出错，跳过", e);
                }
            }
        } catch (PlaywrightException e) {
            if (e.getMessage().contains("Target page, context or browser has been closed")) {
                log.warn("浏览器页面已关闭，停止轮询");
                running = false;
            } else {
                log.error("检查新消息时出错", e);
            }
        } catch (Exception e) {
            log.error("检查新消息时出错", e);
        }
    }

    private void handleNewMessage(String hrMessage) {
        log.info("收到 HR 消息: {}", hrMessage);

        ReplySuggestion suggestion = autoReplyService.suggestReply(hrMessage);

        if (suggestion.isMatched()) {
            String reply = suggestion.getSuggestedReply();
            log.info("AI 生成回复: {}", reply);
            sendReply(reply);
        } else {
            log.info("未匹配到已知问题类型，跳过回复: {}", hrMessage);
        }
    }

    private void sendReply(String reply) {
        if (!running || page == null || page.isClosed()) {
            log.warn("浏览器已关闭，无法发送回复");
            return;
        }

        try {
            ElementHandle inputBox = page.waitForSelector(
                    selectorConfig.getInputBoxSelector(),
                    new Page.WaitForSelectorOptions()
                            .setState(WaitForSelectorState.VISIBLE)
                            .setTimeout(5000));

            if (inputBox != null) {
                inputBox.fill(reply);
                log.info("已填入回复到输入框");

                ElementHandle sendButton = page.querySelector(selectorConfig.getSendButtonSelector());
                if (sendButton != null) {
                    sendButton.click();
                    log.info("已发送回复");
                } else {
                    inputBox.press("Enter");
                    log.info("已按 Enter 发送回复");
                }
            }
        } catch (Exception e) {
            log.error("发送回复失败", e);
        }
    }

    public void stop() {
        running = false;
        if (pollTask != null) {
            pollTask.cancel(false);
        }
        if (scheduler != null) {
            scheduler.shutdown();
        }
        cleanup();
        log.info("浏览器机器人已停止");
    }

    private void cleanup() {
        if (context != null) {
            try { context.close(); } catch (Exception ignored) {}
            context = null;
        }
        if (browser != null) {
            try { browser.close(); } catch (Exception ignored) {}
            browser = null;
        }
        if (playwright != null) {
            try { playwright.close(); } catch (Exception ignored) {}
            playwright = null;
        }
        page = null;
    }

    public boolean isRunning() {
        return running;
    }
}
