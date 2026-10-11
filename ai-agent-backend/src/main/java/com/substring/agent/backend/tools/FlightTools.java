
package com.substring.agent.backend.tools;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class FlightTools {

    private final RestClient restClient;
    private final String apiKey;

    public FlightTools(@Value("${serpapi.api-key}") String apiKey) {
        this.apiKey = apiKey;
        this.restClient = RestClient.builder()
                .baseUrl("https://serpapi.com")
                .build();
    }

    @Tool(
        name = "searchFlights",
        description = """
            Search Google Flights for one-way flights.
            Accept departure and destination as city names or three-letter
            airport codes, such as Pune or PNQ and Delhi or DEL.
            The departure date must be in YYYY-MM-DD format.
            Return actual API results sorted from cheapest to most expensive.
            Never invent prices, schedules, or availability.
            """
    )
    public String searchFlights(String source, String destination, String date) {

        String departureCode = airportCode(source);
        String arrivalCode = airportCode(destination);

        if (departureCode == null || arrivalCode == null) {
            return "Unsupported city or airport. Try Pune, Delhi, Mumbai, "
                    + "Bengaluru, Hyderabad, Chennai, Kolkata, Goa, Ahmedabad, "
                    + "Jaipur, Kochi, Lucknow, Nagpur, Patna, Bhubaneswar, "
                    + "Guwahati, Chandigarh, Srinagar, Varanasi or Indore. "
                    + "Three-letter airport codes are also supported.";
        }

        if (date == null || date.isBlank()) {
            return "Please provide a departure date in YYYY-MM-DD format.";
        }

        try {
            LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            return "Invalid departure date. Use YYYY-MM-DD, for example 2026-10-20.";
        }

        if (departureCode.equals(arrivalCode)) {
            return "Departure and destination airports must be different.";
        }

        try {
            Map<?, ?> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search.json")
                            .queryParam("engine", "google_flights")
                            .queryParam("departure_id", departureCode)
                            .queryParam("arrival_id", arrivalCode)
                            .queryParam("outbound_date", date)
                            .queryParam("type", "2")
                            .queryParam("currency", "INR")
                            .queryParam("gl", "in")
                            .queryParam("hl", "en")
                            .queryParam("api_key", apiKey)
                            .build())
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                return "Flight search returned an empty response. Please try again.";
            }

            Object error = response.get("error");
            if (error != null) {
                return "Flight search failed: " + error;
            }

            List<Map<?, ?>> flights = new ArrayList<>();
            collectFlights(flights, response.get("best_flights"));
            collectFlights(flights, response.get("other_flights"));

            flights.sort(Comparator.comparingDouble(FlightTools::getPriceAsDouble));

            if (flights.isEmpty()) {
                return "No flight results were returned for "
                        + source + " to " + destination + " on " + date
                        + ". Try another date or route.";
            }

            StringBuilder result = new StringBuilder();
            result.append("Flight search results: ")
                    .append(source).append(" (").append(departureCode).append(")")
                    .append(" to ")
                    .append(destination).append(" (").append(arrivalCode).append(")")
                    .append(" on ").append(date)
                    .append("\nSorted by price, cheapest first.\n");

            int displayed = 0;

            for (Map<?, ?> flight : flights) {
                if (displayed >= 6) {
                    break;
                }

                List<?> legs = asList(flight.get("flights"));

                if (legs.isEmpty()) {
                    continue;
                }

                Object price = flight.get("price");
                Object duration = flight.get("total_duration");

                result.append("\n").append(displayed + 1).append(". ")
                        .append(firstAirline(legs))
                        .append("\nPrice: INR ")
                        .append(valueOrDefault(price, "Unavailable"))
                        .append("\nTotal duration: ")
                        .append(formatDuration(duration))
                        .append("\nStops: ")
                        .append(getStopCount(flight))
                        .append("\n");

                for (int i = 0; i < legs.size(); i++) {
                    Object legObject = legs.get(i);

                    if (!(legObject instanceof Map<?, ?> leg)) {
                        continue;
                    }

                    result.append("Leg ").append(i + 1).append(": ")
                            .append(valueOrDefault(
                                    leg.get("airline"), "Unknown airline"))
                            .append(" | Flight: ")
                            .append(valueOrDefault(
                                    leg.get("flight_number"), "Unavailable"))
                            .append("\nDeparture: ")
                            .append(airportDetails(leg.get("departure_airport")))
                            .append("\nArrival: ")
                            .append(airportDetails(leg.get("arrival_airport")))
                            .append("\n");
                }

                Object bookingToken = flight.get("booking_token");
                if (bookingToken != null) {
                    result.append("Google Flights booking token: available")
                            .append("\n");
                }

                displayed++;
            }

            Object searchMetadata = response.get("search_metadata");
            if (searchMetadata instanceof Map<?, ?> metadata) {
                Object googleFlightsUrl = metadata.get("google_flights_url");

                if (googleFlightsUrl != null) {
                    result.append("\nGoogle Flights: ")
                            .append(googleFlightsUrl)
                            .append("\n");
                }
            }

            result.append("\nThese are search results, not confirmed bookings. ")
                    .append("Prices, schedules and availability may change.");

            return result.toString();

        } catch (Exception e) {
            // Avoid returning credentials or internal exception details.
            return "Unable to retrieve flight results right now. "
                    + "Please try again later.";
        }
    }

    private String airportCode(String cityOrCode) {
        if (cityOrCode == null || cityOrCode.isBlank()) {
            return null;
        }

        String normalized = cityOrCode.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");

        return switch (normalized) {
            case "pune", "pnq" -> "PNQ";
            case "delhi", "new delhi", "del" -> "DEL";
            case "mumbai", "bombay", "bom" -> "BOM";
            case "bengaluru", "bangalore", "blr" -> "BLR";
            case "hyderabad", "hyd" -> "HYD";
            case "chennai", "madras", "maa" -> "MAA";
            case "kolkata", "calcutta", "ccu" -> "CCU";
            case "goa", "dabolim", "goi" -> "GOI";
            case "ahmedabad", "amd" -> "AMD";
            case "jaipur", "jai" -> "JAI";
            case "kochi", "cochin", "cok" -> "COK";
            case "lucknow", "lko" -> "LKO";
            case "nagpur", "nag" -> "NAG";
            case "patna", "pat" -> "PAT";
            case "bhubaneswar", "bbi" -> "BBI";
            case "guwahati", "gau" -> "GAU";
            case "chandigarh", "ixc" -> "IXC";
            case "srinagar", "sxr" -> "SXR";
            case "varanasi", "vns" -> "VNS";
            case "indore", "idr" -> "IDR";
            default -> {
                if (normalized.matches("[a-z]{3}")) {
                    yield normalized.toUpperCase(Locale.ROOT);
                }
                yield null;
            }
        };
    }

    private void collectFlights(
            List<Map<?, ?>> destination,
            Object flightsObject) {

        for (Object item : asList(flightsObject)) {
            if (item instanceof Map<?, ?> flight) {
                destination.add(flight);
            }
        }
    }

    private static List<?> asList(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private static double getPriceAsDouble(Map<?, ?> flight) {
        Object price = flight.get("price");

        if (price instanceof Number number) {
            return number.doubleValue();
        }

        if (price != null) {
            try {
                return Double.parseDouble(price.toString());
            } catch (NumberFormatException ignored) {
                // Sort flights with missing or invalid prices last.
            }
        }

        return Double.MAX_VALUE;
    }

    private static String firstAirline(List<?> legs) {
        if (!legs.isEmpty() && legs.get(0) instanceof Map<?, ?> leg) {
            return valueOrDefault(leg.get("airline"), "Unknown airline");
        }
        return "Unknown airline";
    }

    private static String airportDetails(Object value) {
        if (value instanceof Map<?, ?> airport) {
            String name = valueOrDefault(airport.get("name"), "Unknown airport");
            String time = valueOrDefault(airport.get("time"), "Time unavailable");
            String id = valueOrDefault(airport.get("id"), "");

            return name
                    + (id.isBlank() ? "" : " (" + id + ")")
                    + " — " + time;
        }

        return valueOrDefault(value, "Unavailable");
    }

    private static String valueOrDefault(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }

    private static String formatDuration(Object value) {
        if (value instanceof Number number) {
            int minutes = number.intValue();
            return (minutes / 60) + "h " + (minutes % 60) + "m";
        }

        return valueOrDefault(value, "Unavailable");
    }

    private static String getStopCount(Map<?, ?> flight) {
        List<?> legs = asList(flight.get("flights"));

        if (legs.isEmpty()) {
            return "Unavailable";
        }

        int stops = Math.max(0, legs.size() - 1);
        return stops == 0 ? "Nonstop" : stops + " stop(s)";
    }
}
