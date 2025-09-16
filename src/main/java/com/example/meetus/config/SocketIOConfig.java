package com.example.meetus.config;

import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOServer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

@org.springframework.context.annotation.Configuration
public class SocketIOConfig {

    @Value("${socket.io.port:9092}")
    private int port;

    @Value("${socket.io.host:0.0.0.0}")
    private String host;

    @Bean
    public SocketIOServer socketIOServer() {
        Configuration config = new Configuration();
        config.setHostname(host);
        config.setPort(port);

        // Configure CORS
        config.setOrigin("*");

        // Configure ping settings
        config.setPingTimeout(60000);
        config.setPingInterval(25000);

        // Allow any origin for development
        config.setAllowCustomRequests(true);

        return new SocketIOServer(config);
    }
}