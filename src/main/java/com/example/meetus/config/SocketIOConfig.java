package com.example.meetus.config;

import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOServer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.CommandLineRunner;

@Configuration
public class SocketIOConfig {

    // Use the main server port
    @Value("${server.port:8080}")
    private int port;

    @Value("${socket.io.host:0.0.0.0}")
    private String host;

    @Bean(destroyMethod = "stop") // Add destroyMethod for clean shutdown
    public SocketIOServer socketIOServer() {
        Configuration config = new Configuration();
        config.setHostname(host);
        config.setPort(port); // Crucially, set the port to the main server port

        // Configure CORS
        // For production, set to your specific frontend URL
        config.setOrigin("https://meet-us-three.vercel.app");
        
        config.setPingTimeout(60000);
        config.setPingInterval(25000);

        return new SocketIOServer(config);
    }
    
    // Start the server automatically
    @Bean
    public CommandLineRunner startSocketIOServer(SocketIOServer server) {
        return args -> {
            server.start();
        };
    }
}
