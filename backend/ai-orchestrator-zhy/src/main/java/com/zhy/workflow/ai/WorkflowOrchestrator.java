package com.zhy.workflow.ai;

import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Flux;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;



import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class WorkflowOrchestrator {

    private final ChatClient chatClient;

    public WorkflowOrchestrator(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

   /* @PostConstruct
    public void runWorkflow() throws Exception {
        // 原有的工作流代码（假设您已经将核心逻辑提取到 executeWorkflow 方法中）
        executeWorkflow();
    }*/

    /**
     * 执行工作流，接收用户输入，返回 AI 回复
     */
    public String executeWorkflow(String userInput) throws Exception {
        System.out.println("\n========== LangGraph4j 工作流开始 ==========");
        System.out.println("用户输入: " + userInput);

        StateGraph<AgentState> graph = new StateGraph<>(AgentState::new);

        graph.addNode("ai_node", (AsyncNodeAction<AgentState>) state -> {
            System.out.println(">>> 执行 AI 节点...");
            String response = chatClient.prompt()
                    .user(userInput)  // 使用动态输入
                    .call()
                    .content();
            System.out.println(">>> AI 回复: " + response);
            Map<String, Object> updates = new HashMap<>();
            updates.put("ai_message", response);
            return CompletableFuture.completedFuture(updates);
        });

        graph.addEdge(START, "ai_node");
        graph.addEdge("ai_node", END);

        var compiled = graph.compile();
        AgentState finalState = compiled.invoke(Map.of()).orElseThrow();
        String result = (String) finalState.data().get("ai_message");
        System.out.println("工作流执行完成，最终状态: " + finalState.data());
        System.out.println("========== 工作流结束 ==========\n");
        return result;
    }

    /**
     * 流式输出 AI 响应（不经过工作流图，直接调用 ChatClient 的流式 API）
     */
    public Flux<String> streamAIResponse(String userInput) {
        return chatClient.prompt()
                .user(userInput)
                .stream()
                .content();
    }
}