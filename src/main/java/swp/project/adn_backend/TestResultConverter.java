package swp.project.adn_backend;

import org.json.JSONObject;
import org.json.XML;
import org.json.JSONException;

import java.io.IOException;
import java.nio.file.*;

public class TestResultConverter {

    public static void main(String[] args) throws IOException {
        String inputDir = "target/surefire-reports";
        String outputDir = "target/json-reports";
        Files.createDirectories(Paths.get(outputDir));

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(inputDir), "TEST-*.xml")) {
            for (Path entry : stream) {
                try {
                    String xmlContent = Files.readString(entry);
                    JSONObject json = XML.toJSONObject(xmlContent);
                    String jsonPath = outputDir + "/" + entry.getFileName().toString().replace(".xml", ".json");
                    Files.writeString(Paths.get(jsonPath), json.toString(2)); // pretty print
                    System.out.println("✅ Converted: " + entry.getFileName());
                } catch (JSONException e) {
                    System.err.println("❌ Failed to convert " + entry.getFileName() + ": " + e.getMessage());
                }
            }
        }
    }
}
