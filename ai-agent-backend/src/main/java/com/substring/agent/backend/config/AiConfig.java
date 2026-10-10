package com.substring.agent.backend.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.substring.agent.backend.tools.OrderTools;

import lombok.RequiredArgsConstructor;

@Configuration 
@RequiredArgsConstructor 
public class AiConfig {

    private final OrderTools orderTools;

    @Bean 
    public ChatClient chatClient(ChatClient.Builder builder ) {
        return builder
        .defaultSystem("You're a helpful assistant that provides information about orders and their statuses.")
        .defaultTools(orderTools)
        .build();
    }
}
