package com.substring.agent.backend.controller;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.substring.agent.backend.service.ChatService;

import lombok.RequiredArgsConstructor;

@RestController 
@RequestMapping("/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatService chatService;

    /**
     * Accepts POST requests with JSON payload: { "query": "..." } or { "message": "..." }
     * Returns JSON: { "response": "..." }
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> chatJson(@RequestBody Map<String, String> request) {
        String query = request.getOrDefault("query", request.get("message"));
        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Query cannot be empty"));
        }
        String response = chatService.chat(query.trim());
        return ResponseEntity.ok(Map.of("response", response != null ? response : ""));
    }

    /**
     * Accepts POST requests with plain text payload
     */
    @PostMapping(consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> chatText(@RequestBody String query) {
        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Query cannot be empty");
        }
        return ResponseEntity.ok(chatService.chat(query.trim()));
    }

    /**
     * Supports GET requests with ?query=... param, or legacy raw body
     */
    @GetMapping
    public ResponseEntity<String> chat(
            @RequestParam(value = "query", required = false) String queryParam,
            @RequestBody(required = false) String bodyQuery) {
        String query = (queryParam != null && !queryParam.trim().isEmpty()) ? queryParam : bodyQuery;
        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Query parameter or body required");
        }
        return ResponseEntity.ok(chatService.chat(query.trim()));
    }
}
