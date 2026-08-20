package simulations;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import java.time.Duration;

public class AuthServiceSimulation extends Simulation {

    private static final String BASE_URL = System.getProperty("baseUrl", "http://localhost:8084");

    HttpProtocolBuilder httpProtocol = http
            .baseUrl(BASE_URL)
            .acceptHeader("application/json")
            .contentTypeHeader("application/json");

    ScenarioBuilder loginScenario = scenario("Auth - Login")
            .exec(
                    http("POST /api/v1/auth/login")
                            .post("/api/v1/auth/login")
                            .body(StringBody("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                            .check(status().is(200))
                            .check(jsonPath("$.token").saveAs("authToken"))
            )
            .pause(Duration.ofMillis(100), Duration.ofMillis(500));

    ScenarioBuilder loginAndValidateScenario = scenario("Auth - Login & Validate Token")
            .exec(
                    http("POST /api/v1/auth/login")
                            .post("/api/v1/auth/login")
                            .body(StringBody("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                            .check(status().is(200))
                            .check(jsonPath("$.token").saveAs("authToken"))
            )
            .pause(Duration.ofMillis(200), Duration.ofMillis(800))
            .exec(
                    http("GET /api/v1/auth/validate")
                            .get("/api/v1/auth/validate")
                            .header("Authorization", "Bearer #{authToken}")
                            .check(status().is(200))
            );

    ScenarioBuilder invalidLoginScenario = scenario("Auth - Invalid Login (error handling)")
            .exec(
                    http("POST /api/v1/auth/login (invalid)")
                            .post("/api/v1/auth/login")
                            .body(StringBody("{\"username\":\"invalid\",\"password\":\"wrong\"}"))
                            .check(status().in(401, 403))
            )
            .pause(Duration.ofMillis(50), Duration.ofMillis(200));

    {
        setUp(
                loginScenario.injectOpen(
                        nothingFor(Duration.ofSeconds(2)),
                        rampUsers(50).during(Duration.ofSeconds(10)),
                        constantUsersPerSec(20).during(Duration.ofSeconds(30)),
                        rampUsersPerSec(20).to(50).during(Duration.ofSeconds(20))
                ),
                loginAndValidateScenario.injectOpen(
                        nothingFor(Duration.ofSeconds(5)),
                        rampUsers(30).during(Duration.ofSeconds(15)),
                        constantUsersPerSec(10).during(Duration.ofSeconds(30))
                ),
                invalidLoginScenario.injectOpen(
                        nothingFor(Duration.ofSeconds(3)),
                        rampUsers(20).during(Duration.ofSeconds(10)),
                        constantUsersPerSec(5).during(Duration.ofSeconds(20))
                )
        ).protocols(httpProtocol)
                .assertions(
                        global().responseTime().max().lt(5000),
                        global().successfulRequests().percent().gt(95.0)
                );
    }
}
