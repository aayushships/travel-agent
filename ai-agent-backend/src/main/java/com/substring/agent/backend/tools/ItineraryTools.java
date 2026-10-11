
package com.substring.agent.backend.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class ItineraryTools {

    @Tool(description = """
            Create a day-by-day travel itinerary for a destination.
            Inputs are destination, number of days, budget in INR,
            and travel interests. Return a practical plan with
            morning, afternoon, evening, estimated costs, and
            useful travel tips. Clearly label prices as estimates.
            """)
    public String planItinerary(
            String destination,
            int days,
            double budgetInr,
            String interests) {

        if (destination == null || destination.isBlank()) {
            return "Please provide a destination.";
        }

        if (days < 1 || days > 30) {
            return "Please choose between 1 and 30 days.";
        }

        if (budgetInr <= 0) {
            return "Please provide a budget greater than zero INR.";
        }

        if (interests == null || interests.isBlank()) {
            interests = "general sightseeing, local food, and culture";
        }

        return """
                Create a practical %d-day itinerary for %s.

                Total trip budget: INR %.2f.
                Traveller interests: %s.

                Requirements:
                1. Organize the itinerary by day.
                2. Include morning, afternoon, and evening activities.
                3. Estimate costs in INR and show the estimated total.
                4. Consider meals and local transport.
                5. Keep the plan within the stated budget where feasible.
                6. Do not invent confirmed opening hours, ticket prices,
                   reservations, or live availability.
                7. Clearly label all costs as estimates.
                8. Suggest economical alternatives when appropriate.
                """.formatted(destination, days, budgetInr, interests);
    }
}
