package simulations;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class SagaEndToEndSimulation extends Simulation {

    private static final String GATEWAY_URL = System.getProperty("gatewayUrl", "http://localhost:8080");
    private static final Random random = new Random();

    HttpProtocolBuilder httpProtocol = http
            .baseUrl(GATEWAY_URL)
            .acceptHeader("application/json")
            .contentTypeHeader("application/json");

    Iterator<Map<String, Object>> productFeeder = Stream.generate((Supplier<Map<String, Object>>) () ->
            Map.of(
                    "productName", "SagaE2E-" + UUID.randomUUID().toString().substring(0, 8),
                    "price", 50.0 + random.nextDouble() * 200.0,
                    "stock", random.nextInt(100) + 50
            )
    ).iterator();

    Iterator<Map<String, Object>> orderFeeder = Stream.generate((Supplier<Map<String, Object>>) () ->
            Map.of(
                    "customerId", "customer-" + UUID.randomUUID().toString().substring(0, 8),
                    "quantity", random.nextInt(3) + 1
            )
    ).iterator();

    // Full E2E SAGA: Auth -> Create Product -> Create Order -> Poll Status -> Verify Dispatch
    ScenarioBuilder fullE2EScenario = scenario("SAGA E2E - Complete Flow")
            .feed(productFeeder)
            .feed(orderFeeder)
            // Step 1: Authenticate
            .exec(
                    http("Login")
                            .post("/api/v1/auth/login")
                            .body(StringBody("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                            .check(status().in(200, 401))
                            .check(jsonPath("$.token").optional().saveAs("authToken"))
            )
            .pause(Duration.ofMillis(100))
            // Step 2: Create product
            .exec(
                    http("Create Product")
                            .post("/api/v1/stock")
                            .body(StringBody("""
                                    {"name": "#{productName}", "sku": "SKU-#{productName}", "price": #{price}, "quantity": #{stock}, "category": "E2E"}
                                    """))
                            .check(status().is(200))
                            .check(jsonPath("$.id").saveAs("productId"))
            )
            .pause(Duration.ofMillis(200))
            // Step 3: Create order (triggers SAGA)
            .exec(
                    http("Create Order (SAGA)")
                            .post("/api/v1/ventas")
                            .body(StringBody("""
                                    {"customerId": "#{customerId}", "productId": "#{productId}", "quantity": #{quantity}, "totalAmount": 100.0}
                                    """))
                            .check(status().in(200, 201, 202))
                            .check(jsonPath("$.id").saveAs("orderId"))
            )
            // Step 4: Poll order status (wait for SAGA completion)
            .pause(Duration.ofSeconds(2))
            .exec(
                    http("Check Order Status")
                            .get("/api/v1/ventas/#{orderId}")
                            .check(status().is(200))
                            .check(jsonPath("$.status").saveAs("orderStatus"))
            )
            .pause(Duration.ofSeconds(1))
            // Step 5: Verify dispatch
            .exec(
                    http("Check Dispatch")
                            .get("/api/v1/despachos/order/#{orderId}")
                            .check(status().in(200, 404))
            );

    // Concurrent cancellation scenario
    ScenarioBuilder cancellationScenario = scenario("SAGA E2E - Cancellation")
            .feed(productFeeder)
            .feed(orderFeeder)
            .exec(
                    http("Create Product for Cancel")
                            .post("/api/v1/stock")
                            .body(StringBody("""
                                    {"name": "#{productName}", "sku": "SKU-#{productName}", "price": #{price}, "quantity": 9999, "category": "E2E"}
                                    """))
                            .check(status().is(200))
                            .check(jsonPath("$.id").saveAs("productId"))
            )
            .pause(Duration.ofMillis(100))
            .exec(
                    http("Create Order for Cancel")
                            .post("/api/v1/ventas")
                            .body(StringBody("""
                                    {"customerId": "#{customerId}", "productId": "#{productId}", "quantity": #{quantity}, "totalAmount": 50.0}
                                    """))
                            .check(status().in(200, 201, 202))
                            .check(jsonPath("$.id").saveAs("orderId"))
            )
            .pause(Duration.ofSeconds(3))
            .exec(
                    http("Cancel Order")
                            .post("/api/v1/ventas/#{orderId}/cancel")
                            .check(status().in(200, 400, 500))
            );

    // Rate limiting test
    ScenarioBuilder rateLimitScenario = scenario("Rate Limit Test")
            .repeat(50).on(
                    exec(
                            http("Rapid Request")
                                    .get("/api/v1/stock")
                                    .check(status().in(200, 429))
                    ).pause(Duration.ofMillis(10))
            );

    // Insufficient stock scenario
    ScenarioBuilder insufficientStockScenario = scenario("SAGA E2E - Insufficient Stock")
            .feed(productFeeder)
            .exec(
                    http("Create Low-Stock Product")
                            .post("/api/v1/stock")
                            .body(StringBody("""
                                    {"name": "#{productName}", "sku": "SKU-#{productName}", "price": 10.0, "quantity": 1, "category": "E2E"}
                                    """))
                            .check(status().is(200))
                            .check(jsonPath("$.id").saveAs("productId"))
            )
            .pause(Duration.ofMillis(200))
            .exec(
                    http("Order More Than Available")
                            .post("/api/v1/ventas")
                            .body(StringBody("""
                                    {"customerId": "fail-customer", "productId": "#{productId}", "quantity": 9999, "totalAmount": 99990.0}
                                    """))
                            .check(status().in(200, 201, 202))
                            .check(jsonPath("$.id").saveAs("orderId"))
            )
            .pause(Duration.ofSeconds(3))
            .exec(
                    http("Verify STOCK_FAILED")
                            .get("/api/v1/ventas/#{orderId}")
                            .check(status().is(200))
                            .check(jsonPath("$.status").is("STOCK_FAILED"))
            );

    {
        setUp(
                fullE2EScenario.injectOpen(
                        nothingFor(Duration.ofSeconds(2)),
                        rampUsers(20).during(Duration.ofSeconds(15)),
                        constantUsersPerSec(5).during(Duration.ofSeconds(30))
                ),
                cancellationScenario.injectOpen(
                        nothingFor(Duration.ofSeconds(10)),
                        rampUsers(10).during(Duration.ofSeconds(10)),
                        constantUsersPerSec(3).during(Duration.ofSeconds(20))
                ),
                rateLimitScenario.injectOpen(
                        nothingFor(Duration.ofSeconds(5)),
                        atOnceUsers(5)
                ),
                insufficientStockScenario.injectOpen(
                        nothingFor(Duration.ofSeconds(15)),
                        rampUsers(10).during(Duration.ofSeconds(10))
                )
        ).protocols(httpProtocol)
                .assertions(
                        global().responseTime().max().lt(15000),
                        global().responseTime().percentile(95.0).lt(5000),
                        global().successfulRequests().percent().gt(90.0)
                );
    }
}
