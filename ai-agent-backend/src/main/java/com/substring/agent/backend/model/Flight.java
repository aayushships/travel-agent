package com.substring.agent.backend.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter 
@Setter 
@AllArgsConstructor 
public class Flight {
    private String airline;
    private String source;
    private String destination;
    private String date;
    private String price;    
}
