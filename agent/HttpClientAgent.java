import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;


public class HttpClientAgent 
{
  //store recorded HTTP responses and database result sets
  private static final Map<String, String> recordHttpResponse = new HashMap<>();
  private static final Map<String, ResultSet> recordedDbResults = new HashMap<>();

  //initializing Java agent
  public static void premain(String agentArgs, Instrumentation inst) 
  {
    //Patch the HttpClient and JDBC driver classes
    patchHttpClient(inst);
    patchJdbcDriver(inst);
  }

  //HttpClient class to intercept the 'send' method
  private static void patchHttpClient(Instrumentation inst) 
  {
    new AgentBuilder.Default()
        .type(ElementMatchers.named("java.net.http.HttpClient"))
        .transform((builder, typeDescription, classLoader, module) ->
            builder.method(ElementMatchers.named("send"))
                .intercept(MethodDelegation.to(HttpClientInterceptor.class))
        )
        .installOn(inst);
  }

  //JDBC driver class to intercept the executeQuery method
  private static void patchJdbcDriver(Instrumentation inst) 
  {
    new AgentBuilder.Default()
        .type(ElementMatchers.named("java.sql.Statement"))
        .transform((builder, typeDescription, classLoader, module) ->
            builder.method(ElementMatchers.named("executeQuery"))
                .intercept(MethodDelegation.to(StatementInterceptor.class))
        )
        .installOn(inst);
  }


  //intercepting HTTP calls
  public static class HttpClientInterceptor 
  {
    public static Object intercept(@This Object obj, @Origin Method method, @AllArguments Object[] args) 
    {

      String mode = System.getenv("HT_MODE"); //Checking HT_MODE environment variable

      if (mode == null || mode.equals("RECORD")) 
      {
        //RECORD mode
        HttpRequest request = (HttpRequest) args[0];
        String url = request.uri().toString();

        try 
        {
          //Executing the original send method
          HttpClient client = HttpClient.newHttpClient();
          HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

          if (mode != null) 
          {
            //Record the HTTP response
            recordHttpResponse.put(url, response.body());
          }
          return response;
        } 

        catch (Exception e) 
        {
          if (mode != null) 
          {
            //Record the error message
          
            recordHttpResponse.put(url, "Error: " + e.getMessage());
          }
          throw new RuntimeException(e);
        }
      } 
      else 
      {
        //Replay mode
        HttpRequest request = (HttpRequest) args[0];
        String url = request.uri().toString();

        String httpHost = System.getenv("HTTP_HOST");
        
        if (httpHost != null) 
        {
          // Override the original HTTP host with the provided value
          HttpRequest newRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://" + httpHost + request.uri().getPath()))
            .headers(request.headers().map())
            .build();
            request = newRequest;
         }

          String responseBody = recordedHttpResponses.get(url);
          return HttpResponse.BodySubscribers.ofString(responseBody != null ? responseBody : "");
            
      }
    }
  }

  //Interceptor class for the JDBC driver

  public static class StatementInterceptor 
  {
    public static Object intercept(@This Object obj, @Origin Method method, @AllArguments Object[] args) 
    {
      String mode = System.getenv("HT_MODE");
      if (mode == null || mode.equals("RECORD")) 
      {
        // RECORD mode

        try 
        {
          // RECORD mode
          PreparedStatement statement = (PreparedStatement) obj;
          
          ResultSet resultSet = statement.executeQuery(); // Executing the original executeQuery method
          String sql = statement.toString();

          recordedDbResults.put(sql, resultSet);
          return resultSet;
         } 
        catch (Exception e) 
        {
          throw new RuntimeException(e);
        }

      } 
      else 
      {
        String sql = args[0].toString();
        return recordedDbResults.get(sql); // Return the recorded database result set
      }
    }

  }

  // Utility method to get a database connection
    public static Connection getConnection() 
    {
      try {
            String dbUrl = System.getenv("DB_URL");
            String dbUser = System.getenv("DB_USER");
            String dbPassword = System.getenv("DB_PASSWORD");
            String dbHost = System.getenv("DB_HOST");

            if (dbHost != null) 
            {
                // Override the original database host with the provided value
                dbUrl = "jdbc:postgresql://" + dbHost + "/your-db-name";
            }

            return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
        } 
      catch (Exception e) 
      {
        throw new RuntimeException(e);
      }
    }
}
