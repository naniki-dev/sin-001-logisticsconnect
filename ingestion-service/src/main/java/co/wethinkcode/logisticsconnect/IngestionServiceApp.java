package co.wethinkcode.logisticsconnect;

// javalin is a lightweight java & kotlin web framework
import io.javalin.Javalin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class IngestionServiceApp {

    static List<Hubs> hubs = new ArrayList<>();

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7050);

        app.get("/health", ctx -> ctx.result("OK"));

        // TODO: read and clean src/main/resources/hubs-global.csv (hubs, sorting centers, regional districts data —
        // trim whitespace, fix casing, normalize dates/booleans) and expose the
        // cleaned records here for the other services to consume.

        // province lookup to clean province column
        Map<String, String> provinceLookup = new HashMap<>();
        provinceLookup.put("gauteng", "Gauteng");
        provinceLookup.put("western cape", "Western Cape");
        provinceLookup.put("eastern cape", "Eastern Cape");
        provinceLookup.put("kwazulu-natal", "KwaZulu-Natal");
        provinceLookup.put("kwa-zulu natal", "KwaZulu-Natal");
        provinceLookup.put("kwazulu natal", "KwaZulu-Natal");
        provinceLookup.put("free state", "Free State");
        provinceLookup.put("limpopo", "Limpopo");
        provinceLookup.put("mpumalanga", "Mpumalanga");
        provinceLookup.put("north west", "North West");
        provinceLookup.put("northern cape", "Northern Cape");

        // boolean lookup to clean active column
        Map<String, Boolean> activeLookup = new HashMap<>();
        activeLookup.put("y", true);
        activeLookup.put("yes", true);
        activeLookup.put("true", true);
        activeLookup.put("1", true);
        activeLookup.put("n", false);
        activeLookup.put("no", false);
        activeLookup.put("false", false);
        activeLookup.put("0", false);


        // creates reader and handles errors
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        IngestionServiceApp.class.getClassLoader().getResourceAsStream("hubs-global.csv"),
                        StandardCharsets.UTF_8))) {

            // looping - read lines
            String line;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {

                // skipping header row
                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                String[] fields = line.split(",", -1);
                // -1 does not ignore trailing empty fields

                // clean the values
                String cleanHubId = fields[0].trim().toUpperCase();

                String province = fields[1].trim().toLowerCase();
                String cleanProvince = provinceLookup.getOrDefault(province, "Unknown");

                String cleanSortingCenter = titleCase(fields[2].trim().replaceAll("\\s+", " "));

                String activeKey = fields[3].trim().toLowerCase();
                Boolean cleanActive = activeLookup.get(activeKey);

                Hubs hub = new Hubs(cleanHubId, cleanProvince, cleanSortingCenter, cleanActive);
                hubs.add(hub);
            }


        } catch (IOException e) {
            e.printStackTrace();
        }

        // exposes the cleaned records - Javalin's ctx.json() automatically
        // serialises List<Hubs> to a JSON array
        app.get("/hubs", ctx -> ctx.json(hubs));

    }

    // helper method for title casing the input
    private static String titleCase(String input) {
        String[] words = input.split(" ");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            String capitalized = word.substring(0, 1).toUpperCase()
                    + word.substring(1).toLowerCase();
            result.append(capitalized).append(" ");
        }

        return result.toString().trim();
    }
}
