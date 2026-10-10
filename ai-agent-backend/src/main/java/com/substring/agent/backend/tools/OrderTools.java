package com.substring.agent.backend.tools;

import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component 
public class OrderTools {

    private final Map<String, String> orders = Map.of(
        "1042","Shipped-arriving tomorrow",
        "1043","Shipped-arriving tomorrow",
        "1044","Shipped-arriving tomorrow",
        "1045","Shipped-arriving tomorrow"
    );


    @Tool(description = "Get the status of an order by its ID")
    public String getOrderStatus(String orderId) {
        return orders.getOrDefault(orderId, "Order not found");
    }

    @Tool(description = "Cancel an order by its order ID")
    public String cancelOrder(String orderId) {
        if (orders.containsKey(orderId)) {
            return "Order " + orderId + " has been canceled.";
        } else {
            return "Order not found";
        }
    }
    
}
