package com.example.meetus.service;

import com.example.meetus.model.Room;
import com.example.meetus.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RoomService {
    
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<String, RoomPassword> roomPasswords = new ConcurrentHashMap<>();
    private final Map<String, UserSession> userSessions = new ConcurrentHashMap<>();
    
    private static final Duration ROOM_PASSWORD_EXPIRY = Duration.ofMinutes(30);
    
    // Inner class for storing room passwords with expiry
    private static class RoomPassword {
        String password;
        Instant createdAt;
        
        RoomPassword(String password) {
            this.password = password;
            this.createdAt = Instant.now();
        }
        
        boolean isExpired() {
            return Duration.between(createdAt, Instant.now()).compareTo(ROOM_PASSWORD_EXPIRY) > 0;
        }
    }
    
    // Inner class for storing user session data
    private static class UserSession {
        String userId;
        String username;
        String roomId;
        
        UserSession(String userId, String username, String roomId) {
            this.userId = userId;
            this.username = username;
            this.roomId = roomId;
        }
    }
    
//    public synchronized Room joinRoom(String roomId, String sessionId, String username, String password) throws Exception {
//        // Validate input
//        if (roomId == null || roomId.trim().isEmpty()) {
//            throw new IllegalArgumentException("Room ID is required");
//        }
//        if (username == null || username.trim().isEmpty()) {
//            throw new IllegalArgumentException("Username is required");
//        }
//
//        // Check if user is already in a room
//        String currentRoom = userSessions.get(sessionId).roomId;
//        if (currentRoom != null && !currentRoom.equals(roomId)) {
//            // Leave the current room first
//            leaveRoom(sessionId);
//        }
//
//        Room room = rooms.get(roomId);
//
//        if (room == null) {
//            // Create new room
//            log.info("Creating new room {} with password: {}", roomId, password != null ? "yes" : "no");
//            room = new Room(roomId);
//            room.setCreatedBy(sessionId);
//            if (password != null && !password.trim().isEmpty()) {
//                room.setPassword(password);
//            }
//            rooms.put(roomId, room);
//
//            // Store password for persistence
//            if (password != null && !password.trim().isEmpty()) {
//                roomPasswords.put(roomId, new RoomPassword(password));
//            }
//        } else {
//            // Check password for existing room
//            String storedPassword = room.getPassword();
//            if (storedPassword != null && !storedPassword.equals(password)) {
//                throw new Exception("Invalid password");
//            }
//
//            // Check if username is already taken in the room
//            boolean usernameTaken = room.getUsers().stream()
//                .anyMatch(u -> u.getUsername().equalsIgnoreCase(username) && !u.getId().equals(sessionId));
//
//            if (usernameTaken) {
//                throw new Exception("Username '" + username + "' is already taken in this room");
//            }
//
//            // Check room capacity (optional - set a max limit)
//            if (room.getUsers().size() >= 10) {
//                throw new Exception("Room is full (max 10 users)");
//            }
//        }
//
//        // Add user to room
//        User user = new User(sessionId, username);
//        room.addUser(user);
//        userSessions.put(sessionId, new UserSession(sessionId, username, roomId));
//
//        log.info("User {} ({}) joined room {}", username, sessionId, roomId);
//
//        return room;
//    }
public synchronized Room joinRoom(String roomId, String sessionId, String username, String password) throws Exception {
    // Validate input
    if (roomId == null || roomId.trim().isEmpty()) {
        throw new IllegalArgumentException("Room ID is required");
    }
    if (username == null || username.trim().isEmpty()) {
        throw new IllegalArgumentException("Username is required");
    }

    // Check if user is already in a room
    // FIX: Get the session object first and check for null.
    UserSession existingSession = userSessions.get(sessionId);

    // Now, perform the null check
    if (existingSession != null) {
        String currentRoom = existingSession.roomId;
        if (currentRoom != null && !currentRoom.equals(roomId)) {
            // Leave the current room first
            leaveRoom(sessionId);
        }
    }

    Room room = rooms.get(roomId);

    if (room == null) {
        // Create new room
        log.info("Creating new room {} with password: {}", roomId, password != null ? "yes" : "no");
        room = new Room(roomId);
        room.setCreatedBy(sessionId);
        if (password != null && !password.trim().isEmpty()) {
            room.setPassword(password);
        }
        rooms.put(roomId, room);

        // Store password for persistence
        if (password != null && !password.trim().isEmpty()) {
            roomPasswords.put(roomId, new RoomPassword(password));
        }
    } else {
        // Check password for existing room
        String storedPassword = room.getPassword();
        if (storedPassword != null && !storedPassword.equals(password)) {
            throw new Exception("Invalid password");
        }

        // Check if username is already taken in the room
        boolean usernameTaken = room.getUsers().stream()
                .anyMatch(u -> u.getUsername().equalsIgnoreCase(username) && !u.getId().equals(sessionId));

        if (usernameTaken) {
            throw new Exception("Username '" + username + "' is already taken in this room");
        }

        // Check room capacity (optional - set a max limit)
        if (room.getUsers().size() >= 10) {
            throw new Exception("Room is full (max 10 users)");
        }
    }

    // Add user to room
    User user = new User(sessionId, username);
    room.addUser(user);
    userSessions.put(sessionId, new UserSession(sessionId, username, roomId));

    log.info("User {} ({}) joined room {}", username, sessionId, roomId);

    return room;
}
    
    public synchronized void leaveRoom(String sessionId) {
        UserSession session = userSessions.get(sessionId);
        if (session == null) {
            return;
        }
        
        String roomId = session.roomId;
        Room room = rooms.get(roomId);
        if (room != null) {
            room.removeUser(sessionId);
            
            // If room is empty, remove it
            if (room.getUsers().isEmpty()) {
                rooms.remove(roomId);
                roomPasswords.remove(roomId);
                log.info("Room {} is now empty and removed from active rooms", roomId);
            }
            
            log.info("User {} left room {}", sessionId, roomId);
        }
        userSessions.remove(sessionId);
    }
    
    public Room getRoom(String roomId) {
        return rooms.get(roomId);
    }
    
    public List<Room> getAllRooms() {
        return new ArrayList<>(rooms.values());
    }
    
    public Map<String, Object> getRoomInfo(String roomId) {
        Room room = rooms.get(roomId);
        RoomPassword password = roomPasswords.get(roomId);
        
        Map<String, Object> info = new HashMap<>();
        info.put("roomId", roomId);
        info.put("userCount", room != null ? room.getUsers().size() : 0);
        info.put("hasPassword", (room != null && room.hasPassword()) || 
                                (password != null && password.password != null && !password.isExpired()));
        return info;
    }
    
    public List<Map<String, Object>> getRoomList() {
        return rooms.keySet().stream()
                .map(this::getRoomInfo)
                .collect(Collectors.toList());
    }
    
    public UserSession getUserSession(String userId) {
        return userSessions.get(userId);
    }
    
    public String getUserRoom(String userId) {
        UserSession session = userSessions.get(userId);
        return session != null ? session.roomId : null;
    }
    
    public String getUsername(String userId) {
        UserSession session = userSessions.get(userId);
        return session != null ? session.username : null;
    }
    
    public synchronized void deleteRoom(String roomId, String sessionId) throws Exception {
        Room room = rooms.get(roomId);
        if (room == null) {
            throw new Exception("Room not found");
        }
        
        // Only creator can delete the room
        if (!room.getCreatedBy().equals(sessionId)) {
            throw new Exception("Only the room creator can delete the room");
        }
        
        // Remove all users from room tracking
        for (User user : room.getUsers()) {
            userSessions.remove(user.getId());
        }
        
        // Remove room
        rooms.remove(roomId);
        roomPasswords.remove(roomId);
        
        log.info("Room {} deleted by {}", roomId, sessionId);
    }
    
    // Clean up expired room passwords periodically
    @Scheduled(fixedDelay = 300000) // Every 5 minutes
    public void cleanupExpiredPasswords() {
        roomPasswords.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().isExpired();
            if (expired) {
                log.info("Removing expired password for room {}", entry.getKey());
            }
            return expired;
        });
    }
} 