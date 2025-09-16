package com.example.meetus.config;

import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

@org.springframework.context.annotation.Configuration
public class SocketIOConfig {

    private static final Logger log = LoggerFactory.getLogger(SocketIOConfig.class);

    @Value("${socket.io.port:9092}")
    private int localPort;

    @Value("${socket.io.host:0.0.0.0}")
    private String host;

    @Value("${frontend.origin}")
    private String frontendOrigin;

    @Bean
    public SocketIOServer socketIOServer() {
        Configuration config = new Configuration();
        config.setHostname(host);

        //*** DYNAMIC PORT LOGIC FOR RENDER ***
        String envPort = System.getenv("PORT");
        int finalPort;

        if (envPort != null) {
            finalPort = Integer.parseInt(envPort);
            log.info("Environment variable PORT found: {}. Using it for Socket.IO server.", finalPort);
        } else {
            finalPort = localPort;
            log.info("Environment variable PORT not found. Using local port from properties: {}.", finalPort);
        }

        config.setPort(finalPort);

        // *** CORS CONFIGURATION FOR FRONTEND ***
        log.info("Configuring CORS to allow connections from origin: {}", frontendOrigin);
        config.setOrigin(frontendOrigin);

        // Standard ping settings
        config.setPingTimeout(60000);
        config.setPingInterval(25000);

        config.setAllowCustomRequests(true);

        return new SocketIOServer(config);
    }
}