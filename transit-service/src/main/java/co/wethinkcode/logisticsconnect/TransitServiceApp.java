package co.wethinkcode.logisticsconnect;

import co.wethinkcode.logisticsconnect.mq.MqConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TransitServiceApp {

    static final String HUB_SERVICE_URL = "http://localhost:7051";

    // replaces the old synchronous call to delay-stage-service - kept up to date
    // by the MQ subscription set up in main(), so reads here are just a local lookup
    static final Map<String, Integer> latestStageByHub = new ConcurrentHashMap<>();

    static final Map<String, Integer> PROVINCE_BASE_MINUTES = provinceBaseMinutes();
    static final int DEFAULT_BASE_MINUTES = 90;

    static Map<String, Integer> provinceBaseMinutes() {
        Map<String, Integer> map = new HashMap<>();
        map.put("Gauteng", 45);
        map.put("Western Cape", 60);
        map.put("Eastern Cape", 75);
        map.put("KwaZulu-Natal", 60);
        map.put("Free State", 70);
        map.put("Limpopo", 80);
        map.put("Mpumalanga", 65);
        map.put("North West", 70);
        map.put("Northern Cape", 90);
        return map;
    }

    public static void main(String[] args) throws JMSException {
        subscribeToStageUpdates();
        createApp().start(7053);
    }

    // sets up a JMS subscriber on package-status-topic that keeps latestStageByHub
    // updated in the background - this is the "push" replacement for the old GET call
    static void subscribeToStageUpdates() throws JMSException {
        ConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
        Connection connection = factory.createConnection();
        connection.start();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Topic topic = session.createTopic(MqConfig.TOPIC);
        MessageConsumer consumer = session.createConsumer(topic);

        consumer.setMessageListener(message -> {
            try {
                String json = ((TextMessage) message).getText();
                JsonNode node = new ObjectMapper().readTree(json);
                latestStageByHub.put(node.get("hubId").asText(), node.get("stage").asInt());
            } catch (Exception e) {
                System.err.println("Failed to process stage update message: " + e.getMessage());
            }
        });
    }

    static int calculateEta(int baseMinutes, int stage) {
        return baseMinutes + (stage * 30);
    }

    static int baseMinutesForProvince(String province) {
        return PROVINCE_BASE_MINUTES.getOrDefault(province, DEFAULT_BASE_MINUTES);
    }

    static Javalin createApp() {
        Javalin app = Javalin.create();

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/eta/{hubId}", ctx -> {
            String hubId = ctx.pathParam("hubId");
            HttpClient client = HttpClient.newHttpClient();
            ObjectMapper mapper = new ObjectMapper();

            HttpRequest hubReq = HttpRequest.newBuilder(
                    URI.create(HUB_SERVICE_URL + "/hubs/" + hubId)).GET().build();
            HttpResponse<String> hubResp;
            try {
                hubResp = client.send(hubReq, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                ctx.status(502).json(Map.of("error", "hub-service unreachable"));
                return;
            }

            if (hubResp.statusCode() == 404) {
                ctx.status(404).json(Map.of("error", "hub not found: " + hubId));
                return;
            }

            JsonNode hub = mapper.readTree(hubResp.body());
            String province = hub.get("province").asText();
            int baseMinutes = baseMinutesForProvince(province);

            // reads from the local map kept fresh by the MQ subscriber, instead of
            // calling delay-stage-service directly - defaults to 0, same as before
            int stage = latestStageByHub.getOrDefault(hubId, 0);

            int eta = calculateEta(baseMinutes, stage);

            ctx.json(Map.of(
                    "hubId", hubId,
                    "province", province,
                    "stage", stage,
                    "etaMinutes", eta
            ));
        });

        return app;
    }
}