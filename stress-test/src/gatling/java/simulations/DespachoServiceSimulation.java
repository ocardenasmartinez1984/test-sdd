package simulations;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import java.time.Duration;

public class DespachoServiceSimulation extends Simulation {

    private static final String BASE_URL = System.getProperty("despachoUrl", "http://localhost:8083");

    HttpProtocolBuilder httpProtocol = http
            .baseUrl(BASE_URL)
            .acceptHeader("application/json")
            .contentTypeHeader("application/json");

    ScenarioBuilder listDispatches = scenario("Despacho - List All")
            .exec(
                    http("GET /api/v1/despachos")
                            .get("/api/v1/despachos")
                            .check(status().is(200))
            )
            .pause(Duration.ofMillis(200), Duration.ofMillis(500));

    ScenarioBuilder filterByStatus = scenario("Despacho - Filter by Status")
            .exec(
                    http("GET /api/v1/despachos/status/PREPARANDO")
                            .get("/api/v1/despachos/status/PREPARANDO")
                            .check(status().is(200))
            )
            .pause(Duration.ofMillis(100))
            .exec(
                    http("GET /api/v1/despachos/status/ENVIADO")
                            .get("/api/v1/despachos/status/ENVIADO")
                            .check(status().is(200))
            )
            .pause(Duration.ofMillis(100))
            .exec(
                    http("GET /api/v1/despachos/status/ENTREGADO")
                            .get("/api/v1/despachos/status/ENTREGADO")
                            .check(status().is(200))
            );

    ScenarioBuilder trackingLookup = scenario("Despacho - Tracking Lookup")
            .exec(
                    http("GET /api/v1/despachos/tracking/TRK-NOTEXIST")
                            .get("/api/v1/despachos/tracking/TRK-NOTEXIST")
                            .check(status().in(200, 404))
            )
            .pause(Duration.ofMillis(100), Duration.ofMillis(300));

    {
        setUp(
                listDispatches.injectOpen(
                        rampUsers(50).during(Duration.ofSeconds(10)),
                        constantUsersPerSec(20).during(Duration.ofSeconds(30))
                ),
                filterByStatus.injectOpen(
                        nothingFor(Duration.ofSeconds(5)),
                        rampUsers(30).during(Duration.ofSeconds(10)),
                        constantUsersPerSec(10).during(Duration.ofSeconds(25))
                ),
                trackingLookup.injectOpen(
                        nothingFor(Duration.ofSeconds(3)),
                        rampUsers(40).during(Duration.ofSeconds(10)),
                        constantUsersPerSec(15).during(Duration.ofSeconds(25))
                )
        ).protocols(httpProtocol)
                .assertions(
                        global().responseTime().max().lt(5000),
                        global().responseTime().percentile(95.0).lt(2000),
                        global().successfulRequests().percent().gt(98.0)
                );
    }
}
