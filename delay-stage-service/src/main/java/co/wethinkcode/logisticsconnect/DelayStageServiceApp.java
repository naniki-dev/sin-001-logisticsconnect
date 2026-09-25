package co.wethinkcode.logisticsconnect;

import co.wethinkcode.logisticsconnect.mq.MqConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class DelayStageServiceApp {

    static Map<String, Integer> stages = new HashMap<>();

    public static void main(String[] args) throws JMSException {
        // sets up the JMS connection/session/producer used to publish stage changes
        ConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
        Connection connection = factory.createConnection();
        connection.start();
        Session session = session(connection);
        Topic topic = session.createTopic(MqConfig.TOPIC);
        MessageProducer producer = session.createProducer(topic);

        createApp(stages, session, producer).start(7052);
    }

    static Session session(Connection connection) throws JMSException {
        return connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
    }

    static Javalin createApp(Map<String, Integer> stages, Session session, MessageProducer producer) {
        Javalin app = Javalin.create();

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/delay-stage/{hubId}", ctx -> {
            String hubId = ctx.pathParam("hubId");
            ctx.json(Map.of("hubId", hubId, "stage", stages.getOrDefault(hubId, 0)));
        });

        app.post("/delay-stage/{hubId}", ctx -> {
            String hubId = ctx.pathParam("hubId");
            int stage = ((Number) ctx.bodyAsClass(Map.class).get("stage")).intValue();
            stages.put(hubId, stage);

            // publishes the stage change onto package-status-topic for any subscribers
            try {
                ObjectMapper mapper = new ObjectMapper();
                String json = mapper.writeValueAsString(Map.of(
                        "hubId", hubId,
                        "stage", stage,
                        "timestamp", Instant.now().toString()
                ));
                TextMessage message = session.createTextMessage(json);
                producer.send(message);
            } catch (Exception e) {
                // publish failures shouldn't break the REST response - the stage
                // is already saved locally; log so it's visible during a demo
                System.err.println("Failed to publish stage change: " + e.getMessage());
            }

            ctx.status(200).json(Map.of("hubId", hubId, "stage", stage));
        });

        return app;
    }
}