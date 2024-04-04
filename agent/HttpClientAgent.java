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
          HttpResponse<String> response = (HttpResponse<String>) method.invoke(obj, args);

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
          throw e;
        }
      } 
      else 
      {
        //Replay mode
        HttpRequest request = (HttpRequest) args[0];
        String url = request.uri().toString();

        return HttpResponse.BodySubscribers.ofString(recordHttpResponse.get(url)); // Return the recorded HTTP response
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
          ResultSet resultSet = (ResultSet) method.invoke(obj, args); //Executing the original executeQuery method
          if (mode != null) 
          {
            recordedDbResults.put(args[0].toString(), resultSet);
          }
          return resultSet;
        } 
        catch (Exception e) 
        {
          throw new RuntimeException(e);
        }
      } 
      else 
      {
        return recordedDbResults.get(args[0].toString()); // REPLAY mode - Return the recorded database result set
      }
    }

  }
}
