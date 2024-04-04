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

public class HttpClientAgent {
  private static final Map<String, String> recordedHttpResponses = new HashMap<>();
  private static final Map<String, ResultSet> recordedDatabaseResults = new HashMap<>();

  public static void premain(String agentArgs, Instrumentation inst) {
    patchHttpClient(inst);
    patchJdbcDriver(inst);
  }

  private static void patchHttpClient(Instrumentation inst) {
    new AgentBuilder.Default()
        .type(ElementMatchers.named("java.net.http.HttpClient"))
        .transform((builder, typeDescription, classLoader, module) ->
            builder.method(ElementMatchers.named("send"))
                .intercept(MethodDelegation.to(HttpClientInterceptor.class))
        )
        .installOn(inst);
  }

  private static void patchJdbcDriver(Instrumentation inst) {
    new AgentBuilder.Default()
        .type(ElementMatchers.named("java.sql.Statement"))
        .transform((builder, typeDescription, classLoader, module) ->
            builder.method(ElementMatchers.named("executeQuery"))
                .intercept(MethodDelegation.to(StatementInterceptor.class))
        )
        .installOn(inst);
  }

  public static class HttpClientInterceptor {
    public static Object intercept(@This Object obj, @Origin Method method, @AllArguments Object[] args) {
      String mode = System.getenv("HT_MODE");
      if (mode == null || mode.equals("RECORD")) {
        HttpRequest request = (HttpRequest) args[0];
        String url = request.uri().toString();
        try {
          HttpResponse<String> response = (HttpResponse<String>) method.invoke(obj, args);
          if (mode != null) {
            recordedHttpResponses.put(url, response.body());
          }
          return response;
        } catch (Exception e) {
          if (mode != null) {
            recordedHttpResponses.put(url, "Error: " + e.getMessage());
          }
          throw e;
        }
      } else {
        HttpRequest request = (HttpRequest) args[0];
        String url = request.uri().toString();
        return HttpResponse.BodySubscribers.ofString(recordedHttpResponses.get(url));
      }
    }
  }

  public static class StatementInterceptor {
    public static Object intercept(@This Object obj, @Origin Method method, @AllArguments Object[] args) {
      String mode = System.getenv("HT_MODE");
      if (mode == null || mode.equals("RECORD")) {
        try {
          ResultSet resultSet = (ResultSet) method.invoke(obj, args);
          if (mode != null) {
            recordedDatabaseResults.put(args[0].toString(), resultSet);
          }
          return resultSet;
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      } else {
        return recordedDatabaseResults.get(args[0].toString());
      }
    }
  }
}
