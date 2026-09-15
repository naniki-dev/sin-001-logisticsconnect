package co.wethinkcode.logisticsconnect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

// gets hubs from ingestion-service once at startup and caches them
// then gives them by ID
public class HubServiceApp {
    // in-memory cache mapping hubId -> hub JSON, shared across all requests
    static Map<String, JsonNode> hubsByid = new HashMap<>();

    public static void main(String[] args) throws Exception {
        // parses JSON strings into JsonNode objects
        ObjectMapper mapper = new ObjectMapper();
        // reusable HTTP client for calling the ingestion-service
        HttpClient client = HttpClient.newHttpClient();

        // builds a GET request to the ingestion-service's /hubs endpoint
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:7050/hubs")).GET().build();
        // sends the request synchronously and captures the response body as a String
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        // parses the response body into a JSON tree and iterates over each hub element
        for (JsonNode hub : mapper.readTree(response.body())) {
            // cache each hub in the map, keyed by its "hubId" field
            hubsByid.put(hub.get("hubId").asText(), hub);
        }

        // creates and starts the Javalin web server on port 7051
        Javalin app = Javalin.create().start(7051);
        // health check endpoint - confirms the service is up
        app.get("/health", ctx -> ctx.result("OK"));

        // TODO (Serves provinces and sorting centers (place-name source of truth).)
        // Add domain endpoints for hub-service here.
        // endpoint to fetch a single hub by its ID from the path parameter
        app.get("/hubs/{hubId}", ctx -> {
            // look up the requested hub in the cache using the path param
            JsonNode hub = hubsByid.get(ctx.pathParam("hubId"));
            // if not found, respond with 404 and no body
            if (hub == null) { ctx.status(404); return; }
            // otherwise return the hub as a JSON response
            ctx.json(hub);
        });

    }
}
