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

    public static void main(String[] args) throws IOException {
        // creates and starts the javalin web server on port 7050
        Javalin app = Javalin.create().start(7050);

        // health check endpoint - confirms the service is up
        app.get("/health", ctx -> ctx.result("OK"));

        // opens the CSV file from the classpath resources and reads it as UTF-8 text,
        // then delegates the actual reading/cleaning/deduping to loadAndCleanHubs
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        IngestionServiceApp.class.getClassLoader().getResourceAsStream("hubs-global.csv"),
                        StandardCharsets.UTF_8))) {
            hubs = loadAndCleanHubs(reader);
        }

        // exposes the cleaned records - Javalin's ctx.json() automatically
        // serialises List<Hubs> to a JSON array
        app.get("/hubs", ctx -> ctx.json(hubs));
    }

    // province lookup to clean province column
    // maps every messy variant of province name to a clean variant
    static Map<String, String> provinceLookup() {
        Map<String, String> lookup = new HashMap<>();
        lookup.put("gauteng", "Gauteng");
        lookup.put("western cape", "Western Cape");
        lookup.put("eastern cape", "Eastern Cape");
        lookup.put("kwazulu-natal", "KwaZulu-Natal");
        lookup.put("kwa-zulu natal", "KwaZulu-Natal");
        lookup.put("kwazulu natal", "KwaZulu-Natal");
        lookup.put("free state", "Free State");
        lookup.put("limpopo", "Limpopo");
        lookup.put("mpumalanga", "Mpumalanga");
        lookup.put("north west", "North West");
        lookup.put("northern cape", "Northern Cape");
        return lookup;
    }

    // boolean lookup to clean active column
    // maps every raw text variant to a Boolean
    static Map<String, Boolean> activeLookup() {
        Map<String, Boolean> lookup = new HashMap<>();
        lookup.put("y", true);
        lookup.put("yes", true);
        lookup.put("true", true);
        lookup.put("1", true);
        lookup.put("n", false);
        lookup.put("no", false);
        lookup.put("false", false);
        lookup.put("0", false);
        return lookup;
    }

    // trims whitespace and uppercases the hub ID
    static String cleanHubId(String raw) {
        return raw.trim().toUpperCase();
    }

    // trims and lowercases the raw province value so it matches the lookup keys,
    // then looks up the clean province name, defaulting to "Unknown" if not recognised
    static String cleanProvince(String raw, Map<String, String> lookup) {
        return lookup.getOrDefault(raw.trim().toLowerCase(), "Unknown");
    }

    // trims, removes repeated whitespace, and title-cases the sorting center name
    static String cleanSortingCenter(String raw) {
        return titleCase(raw.trim().replaceAll("\\s+", " "));
    }

    // trims and lowercases the raw active value so it matches the lookup keys,
    // then looks up the clean boolean value - null if the raw value is unrecognised
    static Boolean cleanActive(String raw, Map<String, Boolean> lookup) {
        return lookup.get(raw.trim().toLowerCase());
    }

    // helper method for title casing the input
    // capitalises the first letter of each word and lowercases the rest
    static String titleCase(String input) {
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

    // splits one raw CSV line into columns and cleans each field into a single Hubs record
    static Hubs parseLine(String line, Map<String, String> provinceLookup, Map<String, Boolean> activeLookup) {
        // splits the line into columns on commas
        // -1 does not ignore trailing empty fields
        String[] fields = line.split(",", -1);

        // builds a cleaned Hubs object from the cleaned fields
        return new Hubs(
                cleanHubId(fields[0]),
                cleanProvince(fields[1], provinceLookup),
                cleanSortingCenter(fields[2]),
                cleanActive(fields[3], activeLookup)
        );
    }

    // checks a group of duplicate hubs (same province + sorting center) and resolves
    // them down to a single record: lowest hub ID wins identity, active flag by majority vote
    static Hubs resolveDuplicate(List<Hubs> group) {
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

        // builds the single cleaned hub record representing the whole group
        return new Hubs(lowestIdHub.getHubId(), lowestIdHub.getProvince(),
                lowestIdHub.getSortingCenter(), resolvedActive);
    }

    // reads every line from the given reader, cleans each one, groups likely
    // duplicates by province+sorting-center, and resolves each group to one record
    static List<Hubs> loadAndCleanHubs(BufferedReader reader) throws IOException {
        Map<String, String> provinceLookup = provinceLookup();
        Map<String, Boolean> activeLookup = activeLookup();

        // groups raw hub rows by province+sorting-center key so duplicates can be resolved later
        Map<String, List<Hubs>> groups = new HashMap<>();

        // reads the file one line at a time until there are no lines left
        String line;
        // skips the CSV header row on the first iteration
        boolean isHeader = true;
        while ((line = reader.readLine()) != null) {
            // skipping header row
            if (isHeader) {
                isHeader = false;
                continue;
            }

            Hubs hub = parseLine(line, provinceLookup, activeLookup);

            // builds a grouping key from the cleaned province + sorting center to detect duplicates
            String key = hub.getProvince() + "|" + hub.getSortingCenter();
            // If this key has no group yet, create an empty list for it; otherwise reuse the existing one
            // then add this hub to it
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(hub);
        }

        // checks the groups to see any duplicate hubs, removes the duplicates
        List<Hubs> cleaned = new ArrayList<>();
        // iterates over each group of hubs that share the same province + sorting center
        for (List<Hubs> group : groups.values()) {
            // adds the cleaned hub to the final list that will be served by the API
            cleaned.add(resolveDuplicate(group));
        }
        return cleaned;
    }
}