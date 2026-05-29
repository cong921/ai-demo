package com.example.aidemo.controller;

import com.example.aidemo.config.BrowserSelectorConfig;
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
    private final BrowserSelectorConfig selectorConfig;

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startBot() {
        browserBotService.start(selectorConfig.getChromePath());
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
