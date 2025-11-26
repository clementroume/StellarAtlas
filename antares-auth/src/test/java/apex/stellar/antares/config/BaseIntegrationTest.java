package apex.stellar.antares.config;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers // <--- Active l'extension JUnit 5
public abstract class BaseIntegrationTest {

  // Le mot-clé 'static' assure que le conteneur est partagé entre toutes les classes de test
  // (Singleton)
  @Container @ServiceConnection // <--- Configure auto magiquement spring.datasource.*
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

  @Container
  @ServiceConnection(name = "redis") // <--- Configure auto magiquement spring.data.redis.*
  static GenericContainer<?> redis =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void registerCustomProperties(DynamicPropertyRegistry registry) {
    // On garde ici uniquement les propriétés métier spécifiques (JWT, Admin)
    // Les propriétés d'infra (DB, Redis) sont gérées par @ServiceConnection
    registry.add(
        "ANTARES_JWT_SECRET",
        () ->
            "YjQ1ZGRjYjU5YjYwNzZkMWY2MzE4YmFiY2Y4ZjgxMGE0YzY4ZmIwYmZkOTRkMjYxYmVjZGU1Y2Y3YWQyYjQzYw==");
    registry.add("application.admin.default-firstname", () -> "Test");
    registry.add("application.admin.default-lastname", () -> "Admin");
    registry.add("application.admin.default-email", () -> "admin.test@antares.com");
    registry.add("application.admin.default-password", () -> "testPassword123!");
    registry.add("application.security.jwt.issuer", () -> "antares-test-issuer");
    registry.add("application.security.jwt.audience", () -> "antares-test-audience");
    registry.add("application.security.jwt.cookie.domain", () -> "antares-domain");
  }
}
