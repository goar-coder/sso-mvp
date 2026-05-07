package com.empresa.spi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class D1ApiClient {

    private final String baseUrl;
    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public D1ApiClient(String baseUrl, String apiKey) {
        this.baseUrl  = baseUrl;
        this.apiKey   = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.mapper = new ObjectMapper();
    }

    public D1UserData verify(String username, String password) {
        String body = String.format(
            "{\"username\":\"%s\",\"password\":\"%s\"}",
            username.replace("\"", ""),
            password.replace("\"", "")
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/internal/auth/verify/"))
                .header("Content-Type", "application/json")
                .header("X-Internal-Api-Key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(5))
                .build();

        return executeAndParse(request);
    }

    public D1UserData findByUsername(String username) {
        System.err.println("DEBUG-D1: username: " + username);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/internal/auth/user/?username=" + username))
                .header("X-Internal-Api-Key", apiKey)
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();

        return executeAndParse(request);
    }

    private D1UserData executeAndParse(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString()
            );

            JsonNode root = mapper.readTree(response.body());

            // verify endpoint
            if (root.has("valid")) {
                if (!root.get("valid").asBoolean()) return null;
                return parseUser(root.get("user"));
            }

            // get-user endpoint
            if (root.has("found")) {
                if (!root.get("found").asBoolean()) return null;
                return parseUser(root.get("user"));
            }

            return null;

        } catch (IOException | InterruptedException e) {
            return null;
        }
    }

    private D1UserData parseUser(JsonNode userNode) {
        if (userNode == null) return null;

        D1UserData data = new D1UserData();
        data.setId(userNode.path("id").asText());
        data.setUsername(userNode.path("username").asText());
        data.setEmail(userNode.path("email").asText());
        data.setFirstName(userNode.path("first_name").asText());
        data.setLastName(userNode.path("last_name").asText());
        data.setActive(userNode.path("is_active").asBoolean(true));

        List<String> roles = new ArrayList<>();
        JsonNode rolesNode = userNode.path("app_roles");
        if (rolesNode.isArray()) {
            rolesNode.forEach(r -> roles.add(r.asText()));
        }
        data.setAppRoles(roles);

        return data;
    }
}
