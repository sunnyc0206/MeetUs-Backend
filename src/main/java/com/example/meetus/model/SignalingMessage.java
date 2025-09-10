package com.example.meetus.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignalingMessage {
    private String type; // offer, answer, ice-candidate, video-offer, video-answer, video-ice-candidate
    private String from;
    private String to;
    private Object offer;
    private Object answer;
    private Object candidate;
    private String username;
} 