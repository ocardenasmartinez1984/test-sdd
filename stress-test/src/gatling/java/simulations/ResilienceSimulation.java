package simulations;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class ResilienceSimulation extends Simulation {

    private static final String GATEWAY_URL = System.getProperty("gatewayUrl", "http://localhost:8080");

    HttpProtocolBuilder httpProtocol = http
            .baseUrl(GATEWAY_URL)
            .acceptHeader("application/json")
            .contentTypeHeader("application/json");

    // Test circuit breaker by rapid-firing requests
    ScenarioBuilder circuitBreakerTest = scenario("Resilience - Circuit Breaker Trigger")
            .repeat(100).on(
                    exec(
                            http("Rapid Stock Query")
                                    .get("/api/v1/stock")
                                    .check(status().in(200, 429, 503))
                    ).pause(Duration.ofMillis(5))
            );

    // Concurrent writes to same product
    ScenarioBuilder concurrentWriteTest = scenario("Resilience - Concurrent Writes")
            .exec(
                    http("Create Shared Product")
                            .post("/api/v1/stock")
                            .body(StringBody("{\"name\": \"SharedProduct\", \"sku\": \"SKU-SHARED\", \"price\": 100, \"quantity\": 1000}"))
                            .check(status().in(200, 409))
                            .check(jsonPath("$.id").optional().saveAs("sharedProductId"))
            )
            .pause(Duration.ofMillis(500))
            .repeat(20).on(
                    exec(
                            http("Concurrent Order")
                                    .post("/api/v1/ventas")
                                    .body(StringBody("{\"customerId\": \"concurrent-user\", \"productId\": \"#{sharedProductId}\", \"quantity\": 1, \"totalAmount\": 100.0}"))
                                    .check(status().in(200, 201, 202, 400, 409, 500))
                    ).pause(Duration.ofMillis(50))
            );

    // Large payload stress test
    ScenarioBuilder largePayloadTest = scenario("Resilience - Large Payloads")
            .exec(
                    http("Large Product Name")
                            .post("/api/v1/stock")
                            .body(StringBody("{\"name\": \"" + "X".repeat(5000) + "\", \"sku\": \"SKU-LARGE\", \"price\": 10, \"quantity\": 5}"))
                            .check(status().in(200, 400, 413, 500))
            );

    // Health endpoint under load
    ScenarioBuilder healthUnderLoad = scenario("Resilience - Health Under Load")
            .repeat(50).on(
                    exec(
                            http("Health Check")
                                    .get("/actuator/health")
                                    .check(status().in(200, 503))
                    ).pause(Duration.ofMillis(100))
            );

    {
        setUp(
                circuitBreakerTest.injectOpen(
                        atOnceUsers(10)
                ),
                concurrentWriteTest.injectOpen(
                        nothingFor(Duration.ofSeconds(2)),
                        rampUsers(20).during(Duration.ofSeconds(10))
                ),
                largePayloadTest.injectOpen(
                        nothingFor(Duration.ofSeconds(5)),
                        atOnceUsers(5)
                ),
                healthUnderLoad.injectOpen(
                        nothingFor(Duration.ofSeconds(3)),
                        rampUsers(10).during(Duration.ofSeconds(5))
                )
        ).protocols(httpProtocol)
                .assertions(
                        global().responseTime().max().lt(30000),
                        forAll().failedRequests().percent().lt(50.0)
                );
    }
}
