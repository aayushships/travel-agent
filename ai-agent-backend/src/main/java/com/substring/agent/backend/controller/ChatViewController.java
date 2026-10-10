package com.substring.agent.backend.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ChatViewController {

    @GetMapping({"/", "/ui", "/chat-ui"})
    public String chatView(Model model) {
        model.addAttribute("appName", "Aura Concierge");
        model.addAttribute("appTagline", "Your personal travel architect & order assistant");
        model.addAttribute("modelName", "Gemini 3.8 Flash");
        model.addAttribute("systemStatus", "Online & Ready");
        model.addAttribute("suggestedPrompts", List.of(
            "What is the status of order #1042?",
            "Can you cancel order #1043 for me?",
            "Design a 4-day autumn getaway itinerary in Kyoto",
            "Recommend hidden gem destinations in Southern Europe"
        ));
        return "chat";
    }
}
