package co.wethinkcode.logisticsconnect;

// javalin is a lightweight java & kotlin web framework
import io.javalin.Javalin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class IngestionServiceApp {

    // list of hubs that gets served via the /hubs endpoint - final cleaned list
    static List<Hubs> hubs = new ArrayList<>();

    public static void main(String[] args) {
        // creates and starts the javalin web server on port 7050
        Javalin app = Javalin.create().start(7050);

        // health check endpoint - confirms the service is up
        app.get("/health", ctx -> ctx.result("OK"));

        // TODO: read and clean src/main/resources/hubs-global.csv (hubs, sorting centers, regional districts data —
        // trim whitespace, fix casing, normalize dates/booleans) and expose the
        // cleaned records here for the other services to consume.

        // province lookup to clean province column
        // maps every messy variant of province name to a clean variant
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
        // maps every raw text variant to a Boolean
        Map<String, Boolean> activeLookup = new HashMap<>();
        activeLookup.put("y", true);
        activeLookup.put("yes", true);
        activeLookup.put("true", true);
        activeLookup.put("1", true);
        activeLookup.put("n", false);
        activeLookup.put("no", false);
        activeLookup.put("false", false);
        activeLookup.put("0", false);

        // groups raw hub rows by province+sorting-center key so duplicates can be resolved later
        Map<String, List<Hubs>> groups = new HashMap<>();

        // creates reader and handles errors
        // opens the CSV file from the classpath resources and reads it as UTF-8 text
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        IngestionServiceApp.class.getClassLoader().getResourceAsStream("hubs-global.csv"),
                        StandardCharsets.UTF_8))) {

            // looping - read lines
            String line;
            // skips the CSV header row on the first iteration
            boolean isHeader = true;

            // reads the file one line at a time until there are no lines left
            while ((line = reader.readLine()) != null) {

                // skipping header row
                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                // splits the line into columns on commas
                String[] fields = line.split(",", -1);
                // -1 does not ignore trailing empty fields

                // clean the values
                // trims whitespace and uppercases the hub ID
                String cleanHubId = fields[0].trim().toUpperCase();

                // trims and lowercases the raw province value so it matches the lookup keys
                String province = fields[1].trim().toLowerCase();
                // looks up the clean province name, defaulting to "Unknown" if not recognised
                String cleanProvince = provinceLookup.getOrDefault(province, "Unknown");

                // trims, removes repeated whitespace, and title-cases the sorting center name
                String cleanSortingCenter = titleCase(fields[2].trim().replaceAll("\\s+", " "));

                // trims and lowercases the raw active value so it matches the lookup keys
                String activeKey = fields[3].trim().toLowerCase();
                // looks up the clean boolean value - null if the raw value is unrecognised
                Boolean cleanActive = activeLookup.get(activeKey);

                // builds a cleaned Hubs object from the cleaned fields
                Hubs hub = new Hubs(cleanHubId, cleanProvince, cleanSortingCenter, cleanActive);

                // builds a grouping key from province + sorting center to detect duplicate hub entries
                String key = cleanProvince + "|" + cleanSortingCenter;
                // If this key has no group yet, create an empty list for it; otherwise reuse the existing one
                // then add this hub to it
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(hub);
            }


        } catch (IOException e) {
            // prints the stack trace if the file can't be read or another IO error occurs
            e.printStackTrace();
        }

        // checks the groups to see any duplicate hubs, removes the duplicates
        // iterates over each group of hubs that share the same province + sorting center
        for (List<Hubs> group : groups.values()) {
            Hubs lowestIdHub = group.get(0);
            // counters used to determine the "majority vote" for the active flag
            int trueCount = 0;
            int falseCount = 0;

            // scans every hub in the group to find the lowest ID and checks active/inactive votes
            for (Hubs h : group) {
                // updates lowestIdHub whenever a hub with a smaller ID (alphabetically) is found
                if (h.getHubId().compareTo(lowestIdHub.getHubId()) < 0) {
                    lowestIdHub = h;
                }
                // checks how many hubs in the group are marked active
                if (Boolean.TRUE.equals(h.getActive())) {
                    trueCount++;
                // checks how many hubs in the group are marked inactive
                } else if (Boolean.FALSE.equals(h.getActive())) {
                    falseCount++;
                }
            }

            // holds the final active value decided by majority vote
            Boolean resolvedActive;
            // if more hubs voted active, resolve the group as active
            if (trueCount > falseCount) {
                resolvedActive = true;
            // if more hubs voted inactive, resolve the group as inactive
            } else if (falseCount > trueCount) {
                resolvedActive = false;
            // if it's a tie or no data at all = null
            } else {
                resolvedActive = null;
            }

            // builds the single cleaned hub record representing
            Hubs resolvedHub = new Hubs(
                    lowestIdHub.getHubId(),
                    lowestIdHub.getProvince(),
                    lowestIdHub.getSortingCenter(),
                    resolvedActive
            );

            // adds the cleaned hub to the final list that will be served by the API
            hubs.add(resolvedHub);
        }

        // exposes the cleaned records - Javalin's ctx.json() automatically
        // serialises List<Hubs> to a JSON array
        app.get("/hubs", ctx -> ctx.json(hubs));

    }

    // helper method for title casing the input
    // // capitalises the first letter of each word and lowercases the rest
    private static String titleCase(String input) {
        // splits the input string into individual words on spaces
        String[] words = input.split(" ");
        // accumulates the title-cased words into a single result string
        StringBuilder result = new StringBuilder();

        // processes each word one at a time
        for (String word : words) {
            // skips empty strings
            if (word.isEmpty()) {
                continue;
            }
            // uppercases the first letter and lowercases the remaining letters of the word
            String capitalised = word.substring(0, 1).toUpperCase()
                    + word.substring(1).toLowerCase();
            // appends the capitalised word plus a trailing space to the result
            result.append(capitalised).append(" ");
        }

        // trims the trailing space then returns the final title-cased string
        return result.toString().trim();
    }
}
