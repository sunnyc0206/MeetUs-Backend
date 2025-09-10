package com.example.meetus.socketio;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.ConnectListener;
import com.corundumstudio.socketio.listener.DataListener;
import com.corundumstudio.socketio.listener.DisconnectListener;
import com.example.meetus.model.*;
import com.example.meetus.service.RoomService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class SocketIOHandler implements CommandLineRunner {

    private final SocketIOServer server;
    private final RoomService roomService;

    @Autowired
    public SocketIOHandler(SocketIOServer server, RoomService roomService) {
        this.server = server;
        this.roomService = roomService;
    }

    @Override
    public void run(String... args) throws Exception {
        server.addConnectListener(onConnected());
        server.addDisconnectListener(onDisconnected());
        
        // Room management
        server.addEventListener("join-room", JoinRoomData.class, onJoinRoom());
        server.addEventListener("get-rooms", Void.class, onGetRooms());
        server.addEventListener("delete-room", String.class, onDeleteRoom());
        server.addEventListener("leave-room", JoinRoomData.class, onLeaveRoom());


        // WebRTC signaling
        server.addEventListener("offer", SignalingData.class, onOffer());
        server.addEventListener("answer", SignalingData.class, onAnswer());
        server.addEventListener("ice-candidate", SignalingData.class, onIceCandidate());
        
        // Video call signaling
        server.addEventListener("video-offer", SignalingData.class, onVideoOffer());
        server.addEventListener("video-answer", SignalingData.class, onVideoAnswer());
        server.addEventListener("video-ice-candidate", SignalingData.class, onVideoIceCandidate());
        
        // Chat and file transfer
        server.addEventListener("chat-message", ChatMessageData.class, onChatMessage());
        server.addEventListener("file-metadata", FileMetadataData.class, onFileMetadata());
        server.addEventListener("file-accepted", SignalingData.class, onFileAccepted());
        server.addEventListener("file-rejected", SignalingData.class, onFileRejected());
        server.addEventListener("end-call", Map.class, onEndCall());
        
        server.start();
        log.info("Socket.IO server started on port {}", server.getConfiguration().getPort());
    }

    private ConnectListener onConnected() {
        return client -> {
            String sessionId = client.getSessionId().toString();
            log.info("New client connected: {}", sessionId);
        };
    }

//    private DisconnectListener onDisconnected() {
//        return client -> {
//            String sessionId = client.getSessionId().toString();
//            log.info("Client disconnected: {}", sessionId);
//
//            String roomId = roomService.getUserRoom(sessionId);
//            String username = roomService.getUsername(sessionId);
//
//            if (roomId != null) {
//                Room room = roomService.getRoom(roomId);
//
//                // Remove user from room
//                roomService.leaveRoom(sessionId);
//
//                // Notify other users
//                if (room != null && !room.getUsers().isEmpty()) {
//                    Map<String, Object> userLeft = new HashMap<>();
//                    userLeft.put("id", sessionId);
//                    userLeft.put("username", username);
//
//                    for (User user : room.getUsers()) {
//                        SocketIOClient userClient = server.getClient(UUID.fromString(user.getId()));
//                        if (userClient != null) {
//                            userClient.sendEvent("user-left", userLeft);
//                            userClient.sendEvent("user-ended-call", userLeft);
//                        }
//                    }
//                }
//            }
//        };
//    }

    private DisconnectListener onDisconnected() {
        return client -> {
            String sessionId = client.getSessionId().toString();
            log.info("Client disconnected: {}", sessionId);

            String roomId = roomService.getUserRoom(sessionId);
            log.info("Client {} was in room: {}", sessionId, roomId);

            String username = roomService.getUsername(sessionId);
            log.info(username,"joined");

            if (roomId != null) {
                Room room = roomService.getRoom(roomId);

                if (room != null) {
                    // Log the state of the room before any modifications
                    log.info("Found room {} with {} users.", roomId, room.getUsers().size());

                    // Get a snapshot of the users BEFORE removing the disconnected client
                    List<User> usersInRoom = new ArrayList<>(room.getUsers());
                    log.info("Copied {} users for notification.", usersInRoom.size());

                    // Perform the room-leaving logic
                    roomService.leaveRoom(sessionId);
                    log.info("Client {} has been removed from room {}.", sessionId, roomId);

                    // Notify other users
                    if (!usersInRoom.isEmpty()) {
                        Map<String, Object> userLeft = new HashMap<>();
                        userLeft.put("id", sessionId);
                        userLeft.put("username", username);

                        for (User user : usersInRoom) {
                            // Ensure we're not sending to the disconnected user
                            if (!user.getId().equals(sessionId)) {
                                // Check if the client exists on the server
                                SocketIOClient userClient = server.getClient(UUID.fromString(user.getId()));
                                if (userClient != null) {
                                    log.info("Sending 'user-left' event to client: {}", user.getId());
                                    userClient.sendEvent("user-left", userLeft);
                                    userClient.sendEvent("user-ended-call", userLeft);
                                } else {
                                    log.warn("Client with UUID {} not found on server.", user.getId());
                                }
                            }
                        }
                    } else {
                        log.info("No users found in room {} to notify.", roomId);
                    }
                } else {
                    log.warn("Room with ID {} not found. Could not notify other users.", roomId);
                }
            } else {
                log.warn("Session ID {} has no associated room ID. No action taken.", sessionId);
            }
        };
    }

    private DataListener<JoinRoomData> onJoinRoom() {
        return (client, data, ackSender) -> {
            String sessionId = client.getSessionId().toString();
            String roomId = data.getRoomId();
            String username = data.getUsername();
            String password = data.getPassword();

            try {
                log.info("{} ({}) joining room: {}", username, sessionId, roomId);

                // Leave previous room if any
                roomService.leaveRoom(sessionId);

                // Join new room
                Room room = roomService.joinRoom(roomId, sessionId, username, password);

                // Send success response
                Map<String, Object> response = new HashMap<>();
                response.put("roomId", roomId);
                response.put("username", username);
                response.put("hasPassword", room.hasPassword());
                response.put("password", password);
                response.put("isCreator", room.getCreatedBy().equals(sessionId));

                client.sendEvent("join-success", response);

                // Send existing users
                client.sendEvent("existing-users", room.getUsers());

                // Notify other users
                Map<String, Object> userJoined = new HashMap<>();
                userJoined.put("id", sessionId);
                userJoined.put("username", username);

                for (User user : room.getUsers()) {
                    if (!user.getId().equals(sessionId)) {
                        SocketIOClient userClient = server.getClient(UUID.fromString(user.getId()));
                        if (userClient != null) {
                            userClient.sendEvent("user-joined", userJoined);
                        }
                    }
                }

            } catch (Exception e) {
                log.error("Error joining room: ", e);
                client.sendEvent("join-error", e.getMessage());
            }
        };
    }

    private DataListener<JoinRoomData> onLeaveRoom() {
        return (client, data, ackSender) -> {
            String sessionId = client.getSessionId().toString();
            String roomId = roomService.getUserRoom(sessionId);
            String username = roomService.getUsername(sessionId);

            if (roomId != null) {
                log.info("{} is leaving room: {}", username, roomId);

                Room room = roomService.getRoom(roomId);

                if (room != null) {
                    // Get a snapshot of the users BEFORE removal
                    List<User> usersInRoom = new ArrayList<>(room.getUsers());

                    // Remove the user from the room
                    roomService.leaveRoom(sessionId);
                    log.info("Client {} has been removed from room {}.", sessionId, roomId);

                    // Notify other users
                    if (!usersInRoom.isEmpty()) {
                        Map<String, Object> userLeft = new HashMap<>();
                        userLeft.put("id", sessionId);
                        userLeft.put("username", username);

                        for (User user : usersInRoom) {
                            if (!user.getId().equals(sessionId)) {
                                SocketIOClient userClient = server.getClient(UUID.fromString(user.getId()));
                                if (userClient != null) {
                                    userClient.sendEvent("user-left", userLeft);
                                    userClient.sendEvent("user-ended-call", userLeft); // You can choose to send both or just one
                                }
                            }
                        }
                    }
                } else {
                    log.warn("Room with ID {} not found. Could not handle leave-room event.", roomId);
                }
            } else {
                log.warn("Session ID {} has no associated room ID. No action taken for leave-room event.", sessionId);
            }
        };
    }

    private DataListener<Void> onGetRooms() {
        return (client, data, ackSender) -> {
            client.sendEvent("room-list", roomService.getRoomList());
        };
    }

    private DataListener<String> onDeleteRoom() {
        return (client, roomId, ackSender) -> {
            String sessionId = client.getSessionId().toString();
            
            try {
                Room room = roomService.getRoom(roomId);
                if (room != null) {
                    // Notify all users
                    Map<String, Object> notification = new HashMap<>();
                    notification.put("roomId", roomId);
                    notification.put("deletedBy", sessionId);
                    
                    for (User user : room.getUsers()) {
                        SocketIOClient userClient = server.getClient(UUID.fromString(user.getId()));
                        if (userClient != null) {
                            userClient.sendEvent("room-deleted", notification);
                        }
                    }
                    
                    roomService.deleteRoom(roomId, sessionId);
                }
            } catch (Exception e) {
                log.error("Error deleting room: ", e);
                client.sendEvent("delete-error", e.getMessage());
            }
        };
    }

    private DataListener<SignalingData> onOffer() {
        return (client, data, ackSender) -> {
            String sessionId = client.getSessionId().toString();
            String username = roomService.getUsername(sessionId);
            
            log.info("Offer from {} to {}", sessionId, data.getTo());
            
            SocketIOClient targetClient = server.getClient(UUID.fromString(data.getTo()));
            if (targetClient != null) {
                Map<String, Object> response = new HashMap<>();
                response.put("from", sessionId);
                response.put("offer", data.getOffer());
                response.put("username", username);
                targetClient.sendEvent("offer", response);
            }
        };
    }

    private DataListener<SignalingData> onAnswer() {
        return (client, data, ackSender) -> {
            String sessionId = client.getSessionId().toString();
            
            log.info("Answer from {} to {}", sessionId, data.getTo());
            
            SocketIOClient targetClient = server.getClient(UUID.fromString(data.getTo()));
            if (targetClient != null) {
                Map<String, Object> response = new HashMap<>();
                response.put("from", sessionId);
                response.put("answer", data.getAnswer());
                targetClient.sendEvent("answer", response);
            }
        };
    }

    private DataListener<SignalingData> onIceCandidate() {
        return (client, data, ackSender) -> {
            String sessionId = client.getSessionId().toString();
            
            log.info("ICE candidate from {} to {}", sessionId, data.getTo());
            
            SocketIOClient targetClient = server.getClient(UUID.fromString(data.getTo()));
            if (targetClient != null) {
                Map<String, Object> response = new HashMap<>();
                response.put("from", sessionId);
                response.put("candidate", data.getCandidate());
                targetClient.sendEvent("ice-candidate", response);
            }
        };
    }

    private DataListener<SignalingData> onVideoOffer() {
        return (client, data, ackSender) -> {
            String sessionId = client.getSessionId().toString();
            String username = roomService.getUsername(sessionId);
            
            log.info("Video offer from {} to {}", sessionId, data.getTo());
            
            SocketIOClient targetClient = server.getClient(UUID.fromString(data.getTo()));
            if (targetClient != null) {
                Map<String, Object> response = new HashMap<>();
                response.put("from", sessionId);
                response.put("offer", data.getOffer());
                response.put("username", username);
                targetClient.sendEvent("video-offer", response);
            }
        };
    }

    private DataListener<SignalingData> onVideoAnswer() {
        return (client, data, ackSender) -> {
            String sessionId = client.getSessionId().toString();
            
            log.info("Video answer from {} to {}", sessionId, data.getTo());
            
            SocketIOClient targetClient = server.getClient(UUID.fromString(data.getTo()));
            if (targetClient != null) {
                Map<String, Object> response = new HashMap<>();
                response.put("from", sessionId);
                response.put("answer", data.getAnswer());
                targetClient.sendEvent("video-answer", response);
            }
        };
    }

    private DataListener<SignalingData> onVideoIceCandidate() {
        return (client, data, ackSender) -> {
            String sessionId = client.getSessionId().toString();
            
            log.info("Video ICE candidate from {} to {}", sessionId, data.getTo());
            
            SocketIOClient targetClient = server.getClient(UUID.fromString(data.getTo()));
            if (targetClient != null) {
                Map<String, Object> response = new HashMap<>();
                response.put("from", sessionId);
                response.put("candidate", data.getCandidate());
                targetClient.sendEvent("video-ice-candidate", response);
            }
        };
    }

    private DataListener<ChatMessageData> onChatMessage() {
        return (client, data, ackSender) -> {
            String sessionId = client.getSessionId().toString();
            String roomId = roomService.getUserRoom(sessionId);
            String username = roomService.getUsername(sessionId);
            
            if (roomId != null) {
                log.info("Chat message from {}: {}", sessionId, data.getMessage());
                
                Room room = roomService.getRoom(roomId);
                if (room != null) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("username", username);
                    response.put("message", data.getMessage());
                    response.put("timestamp", data.getTimestamp());
                    
                    for (User user : room.getUsers()) {
                        if (!user.getId().equals(sessionId)) {
                            SocketIOClient userClient = server.getClient(UUID.fromString(user.getId()));
                            if (userClient != null) {
                                userClient.sendEvent("chat-message", response);
                            }
                        }
                    }
                }
            }
        };
    }

    private DataListener<FileMetadataData> onFileMetadata() {
        return (client, data, ackSender) -> {
            String sessionId = client.getSessionId().toString();
            String username = roomService.getUsername(sessionId);
            
            log.info("File metadata from {}: {}", sessionId, data.getFileName());
            
            SocketIOClient targetClient = server.getClient(UUID.fromString(data.getTo()));
            if (targetClient != null) {
                Map<String, Object> response = new HashMap<>();
                response.put("from", sessionId);
                response.put("fileName", data.getFileName());
                response.put("fileSize", data.getFileSize());
                response.put("fileType", data.getFileType());
                response.put("username", username);
                targetClient.sendEvent("file-metadata", response);
            }
        };
    }

    private DataListener<SignalingData> onFileAccepted() {
        return (client, data, ackSender) -> {
            String sessionId = client.getSessionId().toString();
            
            log.info("File accepted by {} for {}", sessionId, data.getTo());
            
            SocketIOClient targetClient = server.getClient(UUID.fromString(data.getTo()));
            if (targetClient != null) {
                Map<String, Object> response = new HashMap<>();
                response.put("from", sessionId);
                targetClient.sendEvent("file-accepted", response);
            }
        };
    }

    private DataListener<SignalingData> onFileRejected() {
        return (client, data, ackSender) -> {
            String sessionId = client.getSessionId().toString();
            
            log.info("File rejected by {} for {}", sessionId, data.getTo());
            
            SocketIOClient targetClient = server.getClient(UUID.fromString(data.getTo()));
            if (targetClient != null) {
                Map<String, Object> response = new HashMap<>();
                response.put("from", sessionId);
                targetClient.sendEvent("file-rejected", response);
            }
        };
    }

    private DataListener<Map> onEndCall() {
        return (client, data, ackSender) -> {
            String sessionId = client.getSessionId().toString();
            String roomId = roomService.getUserRoom(sessionId);
            String username = roomService.getUsername(sessionId);
            
            if (roomId != null) {
                log.info("{} ended the call", sessionId);
                
                Room room = roomService.getRoom(roomId);
                if (room != null) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("userId", sessionId);
                    response.put("username", username);
                    
                    for (User user : room.getUsers()) {
                        if (!user.getId().equals(sessionId)) {
                            SocketIOClient userClient = server.getClient(UUID.fromString(user.getId()));
                            if (userClient != null) {
                                userClient.sendEvent("user-ended-call", response);
                            }
                        }
                    }
                }
            }
        };
    }
    
    // Data classes for Socket.io events
    public static class JoinRoomData {
        private String roomId;
        private String username;
        private String password;
        
        // Getters and setters
        public String getRoomId() { return roomId; }
        public void setRoomId(String roomId) { this.roomId = roomId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
    
    public static class SignalingData {
        private String to;
        private Object offer;
        private Object answer;
        private Object candidate;
        
        // Getters and setters
        public String getTo() { return to; }
        public void setTo(String to) { this.to = to; }
        public Object getOffer() { return offer; }
        public void setOffer(Object offer) { this.offer = offer; }
        public Object getAnswer() { return answer; }
        public void setAnswer(Object answer) { this.answer = answer; }
        public Object getCandidate() { return candidate; }
        public void setCandidate(Object candidate) { this.candidate = candidate; }
    }
    
    public static class ChatMessageData {
        private String message;
        private Long timestamp;
        
        // Getters and setters
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public Long getTimestamp() { return timestamp; }
        public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
    }
    
    public static class FileMetadataData {
        private String to;
        private String fileName;
        private Long fileSize;
        private String fileType;
        
        // Getters and setters
        public String getTo() { return to; }
        public void setTo(String to) { this.to = to; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public Long getFileSize() { return fileSize; }
        public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
        public String getFileType() { return fileType; }
        public void setFileType(String fileType) { this.fileType = fileType; }
    }
} 