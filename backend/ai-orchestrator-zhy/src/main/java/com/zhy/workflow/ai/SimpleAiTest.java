package com.zhy.workflow.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class SimpleAiTest implements CommandLineRunner {

    private final ChatClient chatClient;

    // 注入 Builder，并 build 出 ChatClient
    public SimpleAiTest(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public void run(String... args) {
        System.out.println("ChatClient 注入成功: " + (chatClient != null));
        try {
            String response = chatClient.prompt()
                    .user("用中文写一句励志短句")
                    .call()
                    .content();
            System.out.println("AI 回复: " + response);
        } catch (Exception e) {
            System.err.println("调用失败: " + e.getMessage());
            System.out.println("模拟回复: 配置 API Key 后即可获得真实回答。");
        }
    }
}