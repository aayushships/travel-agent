package com.substring.agent.backend.tools;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.tool.annotation.Tool;

import com.substring.agent.backend.model.Flight;

public class FlightTools {

    private final List<Flight> flights = List.of(
            new Flight("IndiGo", "New Delhi", "Goa", "2024-07-01", "3000"),
            new Flight("Air India", "New Delhi", "Goa", "2024-07-02", "2300"),
            new Flight("SpiceJet", "New Delhi", "Goa", "2024-07-03", "5050"),
            new Flight("IndiGo", "New Delhi", "Mumbai", "2024-07-04", "3000"),
            new Flight("Air India", "New Delhi", "Mumbai", "2024-07-05", "2300"),
            new Flight("SpiceJet", "New Delhi", "Bangalore", "2024-07-06", "5050"));

    @Tool(name = "searchFlights", description = "Search for flights based on source, destination, and date.returns a list of matching flights with their airline and price.")
    public String searchFlights(String source, String destination, String date) {

        List<Flight> filteredFlights = flights.stream()
                .filter(f -> f.getSource().equalsIgnoreCase(source) && f.getDestination().equals(destination)
                        && f.getDate().equals(date))
                .toList();

        if (filteredFlights.isEmpty()) {
            return "No flights found for the given criteria.";
        }

        return filteredFlights.stream()
                .map(f -> "Airline: " + f.getAirline() + ", Price: " + f.getPrice())
                .collect(Collectors.joining(","));

        
    }

}
