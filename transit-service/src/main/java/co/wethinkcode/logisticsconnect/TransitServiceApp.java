// TODO (Calculates estimated arrival windows based on hub and delay stage.)
// Add domain endpoints for transit-service here.
// MQ TODO: subscribes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.logisticsconnect.mq.MqConfig)

package co.wethinkcode.logisticsconnect;

// javalin is a lightweight java & kotlin web framework
import io.javalin.Javalin;

public class TransitServiceApp {

    public static void main(String[] args) {
        // builds the Javalin app and starts listening on port 7053
        createApp().start(7053);
    }

    // calculates an ETA in minutes from a base travel time plus a per-delay-stage penalty
    // pure arithmetic - no network, no server, easy to unit test in isolation
    static int calculateEta(int baseMinutes, int stage) {
        // each delay stage adds 30 minutes on top of the base travel time
        return baseMinutes + (stage * 30);
    }

    // builds (but does not start) the Javalin app
    static Javalin createApp() {
        Javalin app = Javalin.create();

        // health check endpoint - confirms the service is up
        app.get("/health", ctx -> ctx.result("OK"));

        // TODO: /eta/{hubId} goes here - will call hub-service for location data
        // and delay-stage-service for the current stage, then combine them via calculateEta

        return app;
    }
}
