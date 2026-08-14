package simulations;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class VentaServiceSimulation extends Simulation {

    private static final String VENTA_URL = System.getProperty("ventaUrl", "http://localhost:8082");
    private static final String STOCK_URL = System.getProperty("stockUrl", "http://localhost:8081");
    private static final Random random = new Random();

    HttpProtocolBuilder httpProtocol = http
            .acceptHeader("application/json")
            .contentTypeHeader("application/json");

    // Feeder to generate random order data
    Iterator<Map<String, Object>> orderFeeder = Stream.generate((Supplier<Map<String, Object>>) () ->
            Map.of(
                    "customerId", "customer-" + UUID.randomUUID().toString().substring(0, 8),
                    "quantity", random.nextInt(5) + 1,
                    "totalAmount", 100.0 + random.nextDouble() * 2000.0
            )
    ).iterator();

    // Feeder to generate products for the saga flow
    Iterator<Map<String, Object>> productFeeder = Stream.generate((Supplier<Map<String, Object>>) () ->
            Map.of(
                    "productName", "StressProduct-" + random.nextInt(100000),
                    "price", 50.0 + random.nextDouble() * 500.0,
                    "stock", random.nextInt(100) + 10
            )
    ).iterator();

    // Full saga flow: Create product -> Create order -> Check order status
    ScenarioBuilder fullSagaScenario = scenario("Venta - Full Saga Flow")
            .feed(productFeeder)
            .feed(orderFeeder)
            // First create a product so we have a valid productId
            .exec(
                    http("POST /api/products (setup)")
                            .post(STOCK_URL + "/api/products")
                            .body(StringBody("""
                                    {
                                        "name": "#{productName}",
                                        "price": #{price},
                                        "stock": #{stock},
                                        "category": "Electronics"
                                    }
                                    """))
                            .check(status().is(201))
                            .check(jsonPath("$.id").saveAs("productId"))
            )
            .pause(Duration.ofMillis(200), Duration.ofMillis(500))
            // Create the order (triggers saga)
            .exec(
                    http("POST /api/ventas (create order)")
                            .post(VENTA_URL + "/api/ventas")
                            .body(StringBody("""
                                    {
                                        "customerId": "#{customerId}",
                                        "productId": "#{productId}",
                                        "quantity": #{quantity},
                                        "totalAmount": #{totalAmount}
                                    }
                                    """))
                            .check(status().in(200, 201, 202))
                            .check(jsonPath("$.id").saveAs("ventaId"))
            )
            .pause(Duration.ofSeconds(2), Duration.ofSeconds(4))
            // Check order status after saga completion
            .exec(
                    http("GET /api/ventas/{id} (check status)")
                            .get(VENTA_URL + "/api/ventas/#{ventaId}")
                            .check(status().is(200))
            );

    // High-throughput order creation (fire-and-forget pattern)
    ScenarioBuilder burstOrderScenario = scenario("Venta - Burst Orders")
            .feed(orderFeeder)
            .feed(productFeeder)
            .exec(
                    http("POST /api/products (setup)")
                            .post(STOCK_URL + "/api/products")
                            .body(StringBody("""
                                    {
                                        "name": "#{productName}",
                                        "price": #{price},
                                        "stock": 9999,
                                        "category": "Food"
                                    }
                                    """))
                            .check(status().is(201))
                            .check(jsonPath("$.id").saveAs("productId"))
            )
            .pause(Duration.ofMillis(100))
            .exec(
                    http("POST /api/ventas (burst)")
                            .post(VENTA_URL + "/api/ventas")
                            .body(StringBody("""
                                    {
                                        "customerId": "#{customerId}",
                                        "productId": "#{productId}",
                                        "quantity": #{quantity},
                                        "totalAmount": #{totalAmount}
                                    }
                                    """))
                            .check(status().in(200, 201, 202))
            )
            .pause(Duration.ofMillis(50), Duration.ofMillis(200));

    // List orders scenario
    ScenarioBuilder listOrdersScenario = scenario("Venta - List Orders")
            .exec(
                    http("GET /api/ventas")
                            .get(VENTA_URL + "/api/ventas")
                            .check(status().is(200))
            )
            .pause(Duration.ofMillis(500), Duration.ofSeconds(1));

    {
        setUp(
                fullSagaScenario.injectOpen(
                        nothingFor(Duration.ofSeconds(3)),
                        rampUsers(30).during(Duration.ofSeconds(20)),
                        constantUsersPerSec(5).during(Duration.ofSeconds(40))
                ),
                burstOrderScenario.injectOpen(
                        nothingFor(Duration.ofSeconds(10)),
                        rampUsers(50).during(Duration.ofSeconds(15)),
                        constantUsersPerSec(15).during(Duration.ofSeconds(30))
                ),
                listOrdersScenario.injectOpen(
                        nothingFor(Duration.ofSeconds(5)),
                        rampUsers(20).during(Duration.ofSeconds(10)),
                        constantUsersPerSec(5).during(Duration.ofSeconds(40))
                )
        ).protocols(httpProtocol)
                .assertions(
                        global().responseTime().max().lt(10000),
                        global().successfulRequests().percent().gt(85.0)
                );
    }
}
