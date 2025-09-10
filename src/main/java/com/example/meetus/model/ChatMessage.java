package com.example.meetus.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    private String username;
    private String message;
    private Long timestamp;
    private String roomId;
} 