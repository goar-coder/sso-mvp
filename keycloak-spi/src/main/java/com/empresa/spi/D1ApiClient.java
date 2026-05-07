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
        System.err.println("DEBUG-D1: [D1_API_CLIENT] verify() invocado para usuario: " + username);
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

        System.err.println("DEBUG-D1: [D1_API_CLIENT] Enviando solicitud a: " + baseUrl + "/api/internal/auth/verify/");
        return executeAndParse(request);
    }

    public D1UserData findByUsername(String username) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/internal/auth/user/?username=" + username))
                .header("X-Internal-Api-Key", apiKey)
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();

        return executeAndParse(request);
    }

    public D1UserData findById(String id) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/internal/auth/user/?id=" + id))
                .header("X-Internal-Api-Key", apiKey)
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();

        return executeAndParse(request);
    }

    public D1UserData verifyToken(String token) {
        String body = String.format(
            "{\"token\":\"%s\"}",
            token.replace("\"", "")
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/internal/auth/verify-token/"))
                .header("Content-Type", "application/json")
                .header("X-Internal-Api-Key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(5))
                .build();

        return executeAndParse(request);
    }

    private D1UserData executeAndParse(HttpRequest request) {
        try {
            System.err.println("DEBUG-D1: [D1_API_CLIENT] Enviando solicitud HTTP...");
            HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString()
            );

            System.err.println("DEBUG-D1: [D1_API_CLIENT] Respuesta HTTP - Status: " + response.statusCode());
            System.err.println("DEBUG-D1: [D1_API_CLIENT] Respuesta Body: " + response.body());

            JsonNode root = mapper.readTree(response.body());

            // verify endpoint
            if (root.has("valid")) {
                boolean isValid = root.get("valid").asBoolean();
                System.err.println("DEBUG-D1: [D1_API_CLIENT] Respuesta 'verify' - valid: " + isValid);
                if (!isValid) {
                    System.err.println("DEBUG-D1: [D1_API_CLIENT] Validación fallida (valid=false)");
                    return null;
                }
                System.err.println("DEBUG-D1: [D1_API_CLIENT] Validación exitosa, parseando usuario");
                return parseUser(root.get("user"));
            }

            // get-user endpoint
            if (root.has("found")) {
                boolean isFound = root.get("found").asBoolean();
                System.err.println("DEBUG-D1: [D1_API_CLIENT] Respuesta 'get-user' - found: " + isFound);
                if (!isFound) {
                    System.err.println("DEBUG-D1: [D1_API_CLIENT] Usuario no encontrado (found=false)");
                    return null;
                }
                System.err.println("DEBUG-D1: [D1_API_CLIENT] Usuario encontrado, parseando");
                return parseUser(root.get("user"));
            }

            System.err.println("DEBUG-D1: [D1_API_CLIENT] Respuesta no tiene campos 'valid' ni 'found', retornando null");
            return null;

        } catch (IOException | InterruptedException e) {
            System.err.println("DEBUG-D1: [D1_API_CLIENT] Excepción al enviar solicitud: " + e.getMessage());
            e.printStackTrace();
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
