
package com.substring.agent.backend.tools;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HotelTools {

    private final RestClient restClient;

    @Value("${serpapi.api-key}")
    private String apiKey;

    public HotelTools() {
        this.restClient = RestClient.builder()
                .baseUrl("https://serpapi.com")
                .build();
    }

    @Tool(description = """
            Search Google Hotels for accommodation in a city.
            Requires city, check-in date, check-out date, and number
            of adults. Dates must use YYYY-MM-DD format.
            Returns hotel names, prices, ratings, locations,
            and booking links when available.
            """)
    public String searchHotels(
            String city,
            String checkIn,
            String checkOut,
            int adults) {

        try {
            // Validate city
            if (city == null || city.isBlank()) {
                return "Please provide a destination city.";
            }

            // Validate dates
            LocalDate in = LocalDate.parse(checkIn);
            LocalDate out = LocalDate.parse(checkOut);

            if (!out.isAfter(in)) {
                return "Check-out date must be after check-in date.";
            }

            // Validate number of adults
            if (adults < 1 || adults > 20) {
                return "Please provide between 1 and 20 adults.";
            }

            // Call SerpApi Google Hotels
            Map<?, ?> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search.json")
                            .queryParam("engine", "google_hotels")
                            .queryParam("q", city.trim())
                            .queryParam("check_in_date", checkIn)
                            .queryParam("check_out_date", checkOut)
                            .queryParam("adults", adults)
                            .queryParam("currency", "INR")
                            .queryParam("gl", "in")
                            .queryParam("hl", "en")
                            .queryParam("api_key", apiKey)
                            .build())
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                return "The hotel search returned no response.";
            }

            Object error = response.get("error");

            if (error != null) {
                return "Hotel search failed: " + error;
            }

            Object propertiesObject = response.get("properties");

            if (!(propertiesObject instanceof List<?> properties)
                    || properties.isEmpty()) {
                return "No hotels were returned for " + city
                        + ". Try another destination or dates.";
            }

            // Collect hotel properties
            List<Map<?, ?>> hotels = new ArrayList<>();

            for (Object item : properties) {
                if (item instanceof Map<?, ?> hotel) {
                    hotels.add(hotel);
                }
            }

            if (hotels.isEmpty()) {
                return "No usable hotel results were returned for "
                        + city + ".";
            }

            // Sort by nightly price, cheapest first
            hotels.sort(Comparator.comparingDouble(
                    HotelTools::getPrice));

            StringBuilder result = new StringBuilder();

            result.append("Hotel search results for ")
                    .append(city)
                    .append("\nCheck-in: ").append(checkIn)
                    .append("\nCheck-out: ").append(checkOut)
                    .append("\nAdults: ").append(adults)
                    .append("\n\n");

            // Display at most eight hotels
            int count = Math.min(hotels.size(), 8);

            for (int i = 0; i < count; i++) {
                Map<?, ?> hotel = hotels.get(i);

                result.append(i + 1)
                        .append(". ")
                        .append(value(
                                hotel.get("name"),
                                "Unknown hotel"))
                        .append("\n");

                // Nightly price
                Object rateObject = hotel.get("rate_per_night");

                if (rateObject instanceof Map<?, ?> rate) {
                    Object lowest = rate.get("lowest");

                    if (lowest != null) {
                        result.append("Price per night: INR ")
                                .append(formatPrice(lowest))
                                .append("\n");
                    } else {
                        result.append(
                                "Price per night: Unavailable\n");
                    }
                } else {
                    result.append(
                            "Price per night: Unavailable\n");
                }

                // Rating
                result.append("Rating: ")
                        .append(value(
                                hotel.get("overall_rating"),
                                "Unavailable"))
                        .append("\n");

                // Location or description
                result.append("Location: ")
                        .append(value(
                                hotel.get("description"),
                                "Not provided"))
                        .append("\n");

                // Booking link, when available
                Object link = hotel.get("link");

                if (link != null
                        && !link.toString().isBlank()) {
                    result.append("Booking link: ")
                            .append(link)
                            .append("\n");
                } else {
                    result.append(
                            "Booking link: Not provided\n");
                }

                result.append("\n");
            }

            result.append(
                    "Prices and availability may change. "
                    + "Verify the final price, taxes, and booking "
                    + "conditions with the provider before booking. "
                    + "These results are not confirmed bookings.");

            return result.toString();

        } catch (DateTimeParseException e) {
            return "Invalid date format. Please use YYYY-MM-DD, "
                    + "for example 2026-10-20.";

        } catch (Exception e) {
            return "Unable to retrieve hotel results right now. "
                    + "Check your API configuration, dates, "
                    + "and network connection.";
        }
    }

    /**
     * Extracts the nightly price for sorting.
     * Missing or invalid prices are placed last.
     */
    private static double getPrice(Map<?, ?> hotel) {
        Object rateObject = hotel.get("rate_per_night");

        if (rateObject instanceof Map<?, ?> rate) {
            Object lowest = rate.get("lowest");

            if (lowest instanceof Number number) {
                return number.doubleValue();
            }

            if (lowest instanceof String text) {
                String cleaned = text.replaceAll("[^0-9.]", "");

                if (!cleaned.isBlank()) {
                    try {
                        return Double.parseDouble(cleaned);
                    } catch (NumberFormatException ignored) {
                        // Invalid price goes last.
                    }
                }
            }
        }

        return Double.MAX_VALUE;
    }

    /**
     * Formats a price without carrying over a broken currency symbol.
     */
    private static String formatPrice(Object price) {
        if (price instanceof Number number) {
            return String.format(
                    java.util.Locale.US,
                    "%,.2f",
                    number.doubleValue());
        }

        String text = price.toString().trim();

        // Remove an existing currency symbol or prefix.
        String cleaned = text.replaceAll("[^0-9,.]", "");

        if (cleaned.isBlank()) {
            return text;
        }

        // Preserve the API's displayed amount and separators.
        return cleaned;
    }

    private static String value(
            Object value,
            String fallback) {

        if (value == null || value.toString().isBlank()) {
            return fallback;
        }

        return value.toString();
    }
}
