
package com.substring.agent.backend.tools;

import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class WeatherTools {

    private final RestClient weatherClient;
    private final String apiKey;

    public WeatherTools(
            @Value("${weatherapi.api-key}") String apiKey) {

        this.apiKey = apiKey;

        this.weatherClient = RestClient.builder()
                .baseUrl("https://api.weatherapi.com/v1")
                .build();
    }

    @Tool(description = """
            Get current weather for a location, such as Goa, India,
            Pune, India, or Mumbai, India.
            Pass the complete location including country when provided.
            Returns temperature, feels-like temperature, humidity,
            wind speed, conditions, and observation time.
            Use this tool for current weather questions.
            Preserve the returned location and weather values.
            Report temperatures using the word Celsius.
            Never invent weather observations.
            """)
    public String getCurrentWeather(String city) {

        if (city == null || city.isBlank()) {
            return "Please provide a city name.";
        }

        if (apiKey == null || apiKey.isBlank()) {
            return "WeatherAPI key is missing. Configure WEATHERAPI_KEY.";
        }

        try {
            Map<?, ?> response = weatherClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/current.json")
                            .queryParam("key", apiKey)
                            .queryParam("q", city.trim())
                            .build())
                    .retrieve()
                    .body(Map.class);

            if (response == null
                    || !(response.get("location")
                    instanceof Map<?, ?> location)
                    || !(response.get("current")
                    instanceof Map<?, ?> current)) {
                return "Weather data is unavailable for " + city + ".";
            }

            String locationName = value(location.get("name"));
            String region = value(location.get("region"));
            String country = value(location.get("country"));

            String condition = "Unavailable";

            if (current.get("condition") instanceof Map<?, ?> conditionData) {
                condition = value(conditionData.get("text"));
            }

            StringBuilder result = new StringBuilder();

            result.append("Current weather for ")
                    .append(locationName);

            if (!region.equals("Unavailable") && !region.isBlank()) {
                result.append(", ").append(region);
            }

            if (!country.equals("Unavailable") && !country.isBlank()) {
                result.append(", ").append(country);
            }

            result.append("\nTemperature: ")
                    .append(value(current.get("temp_c")))
                    .append(" Celsius");

            result.append("\nFeels like: ")
                    .append(value(current.get("feelslike_c")))
                    .append(" Celsius");

            result.append("\nHumidity: ")
                    .append(value(current.get("humidity")))
                    .append("%");

            result.append("\nWind speed: ")
                    .append(value(current.get("wind_kph")))
                    .append(" km/h");

            result.append("\nConditions: ").append(condition);

            result.append("\nObservation time: ")
                    .append(value(current.get("last_updated")));

            result.append("\nSource: WeatherAPI.com");

            return result.toString();

        } catch (Exception e) {
            return "Unable to retrieve current weather for " + city
                    + ". Please check the location, API key, "
                    + "network connection, and API quota.";
        }
    }

    private static String value(Object value) {
        return value == null ? "Unavailable" : value.toString();
    }

    
    @Tool(description = """
            Get a any number of day weather forecast for a city.
            Accepts locations such as Goa, India or Pune, India.
            Returns daily forecast dates, maximum and minimum
            temperatures in Celsius, average temperatures,
            and weather conditions.
            Use this tool when users ask about upcoming weather
            or weather during the next three days.
            Preserve the returned locations and numeric values.
            Do not invent forecast data.
            """)
    public String getWeatherForecast(String city) {

        if (city == null || city.isBlank()) {
            return "Please provide a city name.";
        }

        if (apiKey == null || apiKey.isBlank()) {
            return "WeatherAPI key is missing. Configure WEATHERAPI_KEY.";
        }

        try {
            Map<?, ?> response = weatherClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/forecast.json")
                            .queryParam("key", apiKey)
                            .queryParam("q", city.trim())
                            .queryParam("days", 3)
                            .build())
                    .retrieve()
                    .body(Map.class);

            if (response == null
                    || !(response.get("location")
                    instanceof Map<?, ?> location)
                    || !(response.get("forecast")
                    instanceof Map<?, ?> forecast)
                    || !(forecast.get("forecastday")
                    instanceof List<?> forecastDays)) {
                return "Forecast data is unavailable for " + city + ".";
            }

            StringBuilder result = new StringBuilder();

            result.append("3-day weather forecast for ")
                    .append(value(location.get("name")));

            String country = value(location.get("country"));
            if (!country.equals("Unavailable")) {
                result.append(", ").append(country);
            }

            for (Object item : forecastDays) {
                if (!(item instanceof Map<?, ?> day)) {
                    continue;
                }

                Map<?, ?> dayData =
                        day.get("day") instanceof Map<?, ?> d ? d : Map.of();

                String condition = "Unavailable";
                if (dayData.get("condition") instanceof Map<?, ?> c) {
                    condition = value(c.get("text"));
                }

                result.append("\n\nDate: ")
                        .append(value(day.get("date")));

                result.append("\nMaximum temperature: ")
                        .append(value(dayData.get("maxtemp_c")))
                        .append(" Celsius");

                result.append("\nMinimum temperature: ")
                        .append(value(dayData.get("mintemp_c")))
                        .append(" Celsius");

                result.append("\nAverage temperature: ")
                        .append(value(dayData.get("avgtemp_c")))
                        .append(" Celsius");

                result.append("\nAverage humidity: ")
                        .append(value(dayData.get("avghumidity")))
                        .append("%");

                result.append("\nMaximum wind speed: ")
                        .append(value(dayData.get("maxwind_kph")))
                        .append(" km/h");

                result.append("\nConditions: ")
                        .append(condition);

                result.append("\nChance of rain: ")
                        .append(value(dayData.get("daily_chance_of_rain")))
                        .append("%");
            }

            result.append("\n\nSource: WeatherAPI.com");
            return result.toString();

        } catch (Exception e) {
            return "Unable to retrieve the 3-day forecast for " + city
                    + ". Please check the location, API key, "
                    + "network connection, and API quota.";
        }
    }

}
