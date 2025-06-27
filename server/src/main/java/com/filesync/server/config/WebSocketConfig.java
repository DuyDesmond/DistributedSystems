package com.filesync.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket configuration for real-time file synchronization
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable simple broker for destinations prefixed with "/topic"
        config.enableSimpleBroker("/topic", "/queue");
        
        // Route messages prefixed with "/app" to @MessageMapping methods
        config.setApplicationDestinationPrefixes("/app");
        
        // Route messages to specific users
        config.setUserDestinationPrefix("/user");
    }
    
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Register STOMP endpoint that clients can connect to
        registry.addEndpoint("/ws/sync")
                .setAllowedOriginPatterns("*")
                .withSockJS();
        
        // Also add a WebSocket endpoint without SockJS for native WebSocket clients
        registry.addEndpoint("/ws/sync")
                .setAllowedOriginPatterns("*");
    }
}
