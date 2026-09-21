// TODO (Serves provinces and sorting centers (place-name source of truth).)
package co.wethinkcode.logisticsconnect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
// javalin is a lightweight java & kotlin web framework
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.javalin.Javalin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

public class HubServiceApp {

    // in-memory lookup of hub data by hub ID, built once at startup from ingestion-service's data
    static Map<String, JsonNode> hubsByid = new HashMap<>();

    public static void main(String[] args) throws Exception {
        // fetches the cleaned hub list from ingestion-service as a raw JSON string
        String body = fetchHubsJson("http://localhost:7050/hubs");

        // parses that JSON string into a tree, then indexes it by hubId for fast lookups
        hubsByid = indexByHubId(new ObjectMapper().readTree(body));

        // builds the Javalin app using the now-populated map, then starts listening on port 7051
        createApp(hubsByid).start(7051);
    }

    // makes the actual HTTP GET call to ingestion-service and returns the raw response body
    static String fetchHubsJson(String url) throws Exception {
        // creates a reusable HTTP client for making the request
        HttpClient client = HttpClient.newHttpClient();
        // builds a simple GET request targeting the given URL
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
        // sends the request and waits for the response, reading the body as a String
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        // returns just the response body - the caller is responsible for parsing it
        return resp.body();
    }

    // converts the JSON array of hubs into a map keyed by hubId, so a single hub
    // can be looked up directly instead of scanning the whole array every request
    static Map<String, JsonNode> indexByHubId(JsonNode hubsArray) {
        Map<String, JsonNode> map = new HashMap<>();
        // iterates over every hub object in the JSON array
        for (JsonNode hub : hubsArray) {
            // uses the hub's own "hubId" field as the map key, storing the whole hub object as the value
            map.put(hub.get("hubId").asText(), hub);
        }
        return map;
    }

    // builds (but does not start) the Javalin app - takes the hub map as a parameter
    // so tests can inject fake data instead of relying on a real network fetch
    static Javalin createApp(Map<String, JsonNode> hubsByid) {
        Javalin app = Javalin.create();

        // health check endpoint - confirms the service is up
        app.get("/health", ctx -> ctx.result("OK"));

        // serves a single hub's details by ID, as required by the transit-service contract
        app.get("/hubs/{hubId}", ctx -> {
            // looks up the requested hub in the pre-built map
            JsonNode hub = hubsByid.get(ctx.pathParam("hubId"));
            // if no hub was found under that ID, respond with 404 and stop
            if (hub == null) { ctx.status(404); return; }
            // otherwise serialise the found hub back to the caller as JSON
            ObjectNode mutableNode = hub.deepCopy();

            mutableNode.remove("hubId");
            mutableNode.remove("active");

            ctx.json(mutableNode);
        });

        return app;
    }
}