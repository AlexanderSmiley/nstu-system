package ru.nstu.system.e2e.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Runs the real {@code auth-service}, {@code student-service} and
 * {@code event-service} artifacts side by side (OpenSpec task 12.1; design.md D1,
 * D3, D21).
 *
 * <p>One PostgreSQL 16 container hosts the {@code auth}/{@code student}/{@code event}
 * schemas (design.md D3) and one RabbitMQ container is the shared broker. Each
 * service is launched as its own JVM from the bootJar built by its included Gradle
 * build, on a dynamically allocated port; the datasource URL selects the service's
 * schema through {@code currentSchema} exactly like {@code docker-compose} does.</p>
 *
 * <p>The environment variables mirror the compose file. The outbox poll interval is
 * shortened to one second so the asynchronous {@code account.created} path is
 * observable within the test; the archive and DLQ schedulers are pushed far away so
 * they cannot interfere with the deterministic assertions.</p>
 */
public final class ServiceCluster implements AutoCloseable {

    /** HS256 secret; must be at least 32 bytes. */
    public static final String JWT_SECRET = "nstu-e2e-integration-secret-0123456789abcdef";

    /** Shared secret for the internal student-profile endpoint (design.md D12). */
    public static final String INTERNAL_TOKEN = "e2e-internal-token";

    public static final String ADMIN_USERNAME = "admin";

    public static final String ADMIN_PASSWORD = "Admin-Init1";

    private static final Duration READY_TIMEOUT = Duration.ofMinutes(3);

    private static final Duration POLL_INTERVAL = Duration.ofMillis(500);

    private final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private final RabbitMQContainer rabbit =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"));

    private final Map<String, Process> processes = new LinkedHashMap<>();

    private final Path logDirectory;

    private final HttpClient http = HttpClient.newHttpClient();

    private int authPort;

    private int studentPort;

    private int eventPort;

    /** Starts both containers and prepares the log directory. */
    public ServiceCluster() {
        postgres.start();
        rabbit.start();
        try {
            logDirectory = Files.createTempDirectory("nstu-e2e-logs-");
        } catch (IOException ex) {
            throw new UncheckedIOException("cannot create log directory", ex);
        }
    }

    /** Allocates ports and launches the three service JVMs. */
    public void startApplications() {
        authPort = freePort();
        studentPort = freePort();
        eventPort = freePort();

        // student-service first: event-service calls it synchronously at runtime and
        // the suite wants it ready before the first join.
        launch("student-service", jarPath("e2e.studentServiceJar", "student-service"), Map.of(
                "SPRING_DATASOURCE_URL", jdbcUrl("student"),
                "SERVER_PORT", String.valueOf(studentPort),
                "NSTU_DLQ_REDRIVE_INTERVAL", "PT24H"));
        launch("auth-service", jarPath("e2e.authServiceJar", "auth-service"), Map.of(
                "SPRING_DATASOURCE_URL", jdbcUrl("auth"),
                "SERVER_PORT", String.valueOf(authPort),
                "ADMIN_USERNAME", ADMIN_USERNAME,
                "ADMIN_PASSWORD", ADMIN_PASSWORD));
        launch("event-service", jarPath("e2e.eventServiceJar", "event-service"), Map.of(
                "SPRING_DATASOURCE_URL", jdbcUrl("event"),
                "SERVER_PORT", String.valueOf(eventPort),
                "STUDENT_SERVICE_URL", studentBaseUrl(),
                "NSTU_ARCHIVE_SWEEP_INTERVAL", "PT24H"));
    }

    /** Waits until every service reports {@code UP} on {@code /actuator/health}. */
    public void awaitReady() {
        awaitHealth("student-service", studentBaseUrl());
        awaitHealth("auth-service", authBaseUrl());
        awaitHealth("event-service", eventBaseUrl());
    }

    public String authBaseUrl() {
        return "http://127.0.0.1:" + authPort;
    }

    public String studentBaseUrl() {
        return "http://127.0.0.1:" + studentPort;
    }

    public String eventBaseUrl() {
        return "http://127.0.0.1:" + eventPort;
    }

    public String rabbitHost() {
        return rabbit.getHost();
    }

    public int rabbitPort() {
        return rabbit.getAmqpPort();
    }

    public String rabbitUsername() {
        return rabbit.getAdminUsername();
    }

    public String rabbitPassword() {
        return rabbit.getAdminPassword();
    }

    /**
     * Opens a direct JDBC connection to the shared database (no schema selected by
     * default), used for the bootstrap and password-hash assertions.
     */
    public Connection openConnection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private Map<String, String> baseEnvironment() {
        Map<String, String> environment = new HashMap<>();
        environment.put("SPRING_DATASOURCE_USERNAME", postgres.getUsername());
        environment.put("SPRING_DATASOURCE_PASSWORD", postgres.getPassword());
        environment.put("SPRING_RABBITMQ_HOST", rabbit.getHost());
        environment.put("SPRING_RABBITMQ_PORT", String.valueOf(rabbit.getAmqpPort()));
        environment.put("SPRING_RABBITMQ_USERNAME", rabbit.getAdminUsername());
        environment.put("SPRING_RABBITMQ_PASSWORD", rabbit.getAdminPassword());
        environment.put("JWT_SECRET", JWT_SECRET);
        environment.put("INTERNAL_TOKEN", INTERNAL_TOKEN);
        environment.put("COOKIE_SECURE", "false");
        // Publish outbox rows quickly so the asynchronous account.created path is
        // observable within seconds (design.md D13).
        environment.put("NSTU_OUTBOX_POLL_INTERVAL", "PT1S");
        return environment;
    }

    private void launch(String name, Path jar, Map<String, String> extraEnvironment) {
        if (!Files.isRegularFile(jar)) {
            throw new IllegalStateException("service artifact not found: " + jar
                    + " (the bootJar dependency should have produced it)");
        }
        ProcessBuilder builder = new ProcessBuilder(javaBinary(), "-jar", jar.toString());
        builder.environment().putAll(baseEnvironment());
        builder.environment().putAll(extraEnvironment);
        builder.redirectErrorStream(true);
        builder.redirectOutput(logDirectory.resolve(name + ".log").toFile());
        try {
            processes.put(name, builder.start());
        } catch (IOException ex) {
            throw new UncheckedIOException("cannot start " + name, ex);
        }
    }

    private void awaitHealth(String name, String baseUrl) {
        long deadline = System.nanoTime() + READY_TIMEOUT.toNanos();
        Throwable lastFailure = null;
        while (System.nanoTime() < deadline) {
            if (!processes.get(name).isAlive()) {
                throw new IllegalStateException(name + " exited during startup\n" + logTail(name));
            }
            try {
                HttpResponse<String> response = http.send(
                        HttpRequest.newBuilder(URI.create(baseUrl + "/actuator/health"))
                                .timeout(Duration.ofSeconds(5))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() == 200 && response.body().contains("\"UP\"")) {
                    return;
                }
                lastFailure = new IllegalStateException("health " + response.statusCode() + ": " + response.body());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for " + name, ex);
            } catch (Exception ex) {
                lastFailure = ex;
            }
            sleep(POLL_INTERVAL);
        }
        throw new IllegalStateException(name + " did not become healthy within " + READY_TIMEOUT
                + " (last error: " + lastFailure + ")\n" + logTail(name));
    }

    private String logTail(String name) {
        Path log = logDirectory.resolve(name + ".log");
        try {
            List<String> lines = Files.readAllLines(log, StandardCharsets.UTF_8);
            int from = Math.max(0, lines.size() - 60);
            return lines.subList(from, lines.size()).stream().collect(Collectors.joining("\n"));
        } catch (IOException ex) {
            return "<no log available: " + ex.getMessage() + ">";
        }
    }

    private String jdbcUrl(String schema) {
        return postgres.getJdbcUrl() + "?currentSchema=" + schema;
    }

    private static String javaBinary() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException ex) {
            throw new UncheckedIOException("cannot allocate a free port", ex);
        }
    }

    private static Path jarPath(String systemProperty, String service) {
        String configured = System.getProperty(systemProperty);
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        return Path.of("..", service, "build", "libs", service + "-0.0.1-SNAPSHOT.jar").toAbsolutePath();
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", ex);
        }
    }

    @Override
    public void close() {
        List<Process> toStop = new ArrayList<>(processes.values());
        toStop.forEach(Process::destroy);
        for (Process process : toStop) {
            try {
                if (!process.waitFor(10, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(10, TimeUnit.SECONDS);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
        try {
            rabbit.stop();
        } finally {
            postgres.stop();
        }
    }
}
