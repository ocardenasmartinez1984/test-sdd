package simulations;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class StockServiceSimulation extends Simulation {

    private static final String BASE_URL = System.getProperty("baseUrl", "http://localhost:8081");
    private static final Random random = new Random();

    HttpProtocolBuilder httpProtocol = http
            .baseUrl(BASE_URL)
            .acceptHeader("application/json")
            .contentTypeHeader("application/json");

    Iterator<Map<String, Object>> productFeeder = Stream.generate((Supplier<Map<String, Object>>) () ->
            Map.of(
                    "productName", "Product-" + random.nextInt(100000),
                    "price", 10.0 + random.nextDouble() * 990.0,
                    "stock", random.nextInt(1000) + 1,
                    "category", new String[]{"Electronics", "Food", "Clothing", "Tools", "Sports"}[random.nextInt(5)]
            )
    ).iterator();

    ScenarioBuilder createProductScenario = scenario("Stock - Create Products")
            .feed(productFeeder)
            .exec(
                    http("POST /api/products")
                            .post("/api/products")
                            .body(StringBody("""
                                    {
                                        "name": "#{productName}",
                                        "price": #{price},
                                        "stock": #{stock},
                                        "category": "#{category}"
                                    }
                                    """))
                            .check(status().is(201))
                            .check(jsonPath("$.id").saveAs("productId"))
            )
            .pause(Duration.ofMillis(100), Duration.ofMillis(300));

    ScenarioBuilder listProductsScenario = scenario("Stock - List Products")
            .exec(
                    http("GET /api/products")
                            .get("/api/products")
                            .check(status().is(200))
            )
            .pause(Duration.ofMillis(200), Duration.ofMillis(600));

    ScenarioBuilder createAndUpdateScenario = scenario("Stock - Create & Update Product")
            .feed(productFeeder)
            .exec(
                    http("POST /api/products (create)")
                            .post("/api/products")
                            .body(StringBody("""
                                    {
                                        "name": "#{productName}",
                                        "price": #{price},
                                        "stock": #{stock},
                                        "category": "#{category}"
                                    }
                                    """))
                            .check(status().is(201))
                            .check(jsonPath("$.id").saveAs("productId"))
            )
            .pause(Duration.ofMillis(300), Duration.ofMillis(800))
            .exec(
                    http("PUT /api/products/{id} (update stock)")
                            .put("/api/products/#{productId}")
                            .body(StringBody("""
                                    {
                                        "name": "#{productName}-updated",
                                        "price": #{price},
                                        "stock": 9999,
                                        "category": "#{category}"
                                    }
                                    """))
                            .check(status().is(200))
            )
            .pause(Duration.ofMillis(100), Duration.ofMillis(300))
            .exec(
                    http("GET /api/products/{id}")
                            .get("/api/products/#{productId}")
                            .check(status().is(200))
                            .check(jsonPath("$.name").is("#{productName}-updated"))
            );

    {
        setUp(
                createProductScenario.injectOpen(
                        nothingFor(Duration.ofSeconds(2)),
                        rampUsers(100).during(Duration.ofSeconds(15)),
                        constantUsersPerSec(30).during(Duration.ofSeconds(30))
                ),
                listProductsScenario.injectOpen(
                        nothingFor(Duration.ofSeconds(3)),
                        rampUsers(50).during(Duration.ofSeconds(10)),
                        constantUsersPerSec(15).during(Duration.ofSeconds(40))
                ),
                createAndUpdateScenario.injectOpen(
                        nothingFor(Duration.ofSeconds(5)),
                        rampUsers(40).during(Duration.ofSeconds(20)),
                        constantUsersPerSec(10).during(Duration.ofSeconds(30))
                )
        ).protocols(httpProtocol)
                .assertions(
                        global().responseTime().max().lt(5000),
                        global().successfulRequests().percent().gt(90.0)
                );
    }
}
