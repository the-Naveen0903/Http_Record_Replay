import java.sql.ResultSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

@RestController
public class Controller 
{

  //Initializing default values from properties file.
  @Value("${http.endpoint}")
  private String httpEndpoint;

  @Value("${spring.datasource.url}")
  private String databaseUrl;

  @Value("${spring.datasource.username}")
  private String databaseUsername;

  @Value("${spring.datasource.password}")
  private String databasePassword;

  @PostMapping("/api/createNewPost")
  public ResponseEntity<Map<String, String>> createNewPost(@RequestBody Map<String, String> request) 
  {
    Map<String, String> response = new HashMap<>();
    String mode = System.getenv("HT_MODE");
    if (mode == null || mode.equals("RECORD")) 
    {
      String postName = request.get("post_name");
      String postContents = request.get("post_contents");
      String dbPost = insertIntoDatabase(postName, postContents);
      response.put("db_post", dbPost);
      String httpResponse = makeHttpCall(httpEndpoint);
      response.put("http_outbound", httpResponse);
    } 
    else 
    {
      response.put("db_post", "Hardcoded database row for replay mode");
      response.put("http_outbound", "Hardcoded HTTP response for replay mode");
    }
    return ResponseEntity.ok(response);
  }

  //Inserting data into the database
  private String insertIntoDatabase(String postName, String postContents) 
  {
    try (Connection conn = DriverManager.getConnection(databaseUrl, databaseUsername, databasePassword)) 
    {
      String sql = "INSERT INTO posts (name, contents) VALUES (?, ?)";
      PreparedStatement stmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS);
      stmt.setString(1, postName);
      stmt.setString(2, postContents);
      stmt.executeUpdate();
      ResultSet rs = stmt.getGeneratedKeys();
      if (rs.next()) {
        long id = rs.getLong(1);
        return "Database row with ID: " + id;
      }
    } 
    catch (SQLException e) 
    {
      return "Error: " + e.getMessage();
    }
    return "Error: Failed to insert into database";
  }

  //Method to make an HTTP call
  private String makeHttpCall(String url) 
  {
    try 
    {
      HttpClient client = HttpClient.newHttpClient();
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      return response.body();
    } 
    catch (Exception e) 
    {
      return "Error: " + e.getMessage();
    }
  }
}
