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

    public BrowserBotService(AutoReplyService autoReplyService, BrowserSelectorConfig selectorConfig) {
        this.autoReplyService = autoReplyService;
        this.selectorConfig = selectorConfig;
    }

    public void start(String chromeExecutablePath) {
        if (running) {
            log.warn("浏览器机器人已在运行中");
            return;
        }

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
        scheduler.scheduleAtFixedRate(
                this::checkNewMessages,
                10,
                selectorConfig.getPollIntervalSeconds(),
                TimeUnit.SECONDS);
    }

    private void checkNewMessages() {
        if (!running || page == null) return;

        try {
            List<ElementHandle> messages = page.querySelectorAll(selectorConfig.getMessageItemSelector());

            for (ElementHandle msg : messages) {
                String senderClass = msg.getAttribute("class");
                if (senderClass != null && senderClass.contains(selectorConfig.getOtherMessageClass())) {
                    String messageText = msg.innerText().trim();
                    String messageId = msg.getAttribute("data-id");

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
