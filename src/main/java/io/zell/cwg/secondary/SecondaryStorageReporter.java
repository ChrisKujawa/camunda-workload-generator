package io.zell.cwg.secondary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zell.cwg.artifacts.WorkloadReport.SecondaryStorageReport;
import io.zell.cwg.config.ConfigException;
import io.zell.cwg.config.WorkloadConfig.SecondaryStorageConfig;
import io.zell.cwg.runtime.SecondaryStorageEndpoint;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public final class SecondaryStorageReporter {

  private static final Duration POLL_INTERVAL = Duration.ofSeconds(1);
  private static final ObjectMapper JSON = new ObjectMapper();

  private final HttpClient client;
  private final Clock clock;
  private final Sleeper sleeper;

  public SecondaryStorageReporter() {
    this(HttpClient.newHttpClient(), Clock.systemUTC(), Thread::sleep);
  }

  SecondaryStorageReporter(final HttpClient client, final Clock clock, final Sleeper sleeper) {
    this.client = client;
    this.clock = clock;
    this.sleeper = sleeper;
  }

  public SecondaryStorageReport report(
      final SecondaryStorageConfig config, final Optional<SecondaryStorageEndpoint> endpoint)
      throws IOException {
    if (SecondaryStorageConfig.MODE_DISABLED.equals(config.mode())) {
      return SecondaryStorageReport.skipped();
    }
    final var secondaryStorageEndpoint =
        endpoint.orElseThrow(
            () ->
                new ConfigException(
                    "Configured runtime does not expose a secondary-storage endpoint"));

    final var result =
        config.waitForIngestion()
            ? waitForIngestion(config, secondaryStorageEndpoint)
            : new IngestionResult("queried", fetchStats(secondaryStorageEndpoint));
    return new SecondaryStorageReport(
        config.waitForIngestion(),
        secondaryStorageEndpoint.type(),
        result.status(),
        secondaryStorageEndpoint.mode(),
        secondaryStorageEndpoint.url(),
        result.stats().indexes(),
        result.stats().documents(),
        result.stats().storeSizeBytes());
  }

  private IngestionResult waitForIngestion(
      final SecondaryStorageConfig config, final SecondaryStorageEndpoint endpoint)
      throws IOException {
    final var deadline = Instant.now(clock).plus(config.waitTimeoutDuration());
    StorageStats lastStats = new StorageStats(0, 0, 0);
    while (!Instant.now(clock).isAfter(deadline)) {
      lastStats = fetchStats(endpoint);
      if (lastStats.documents() > 0) {
        return new IngestionResult("ingested", lastStats);
      }
      sleep();
    }
    return new IngestionResult("timed_out", lastStats);
  }

  private StorageStats fetchStats(final SecondaryStorageEndpoint endpoint) throws IOException {
    final var request =
        HttpRequest.newBuilder()
            .uri(URI.create(endpoint.url() + "/_cat/indices?format=json&bytes=b"))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();
    final HttpResponse<String> response;
    try {
      response = client.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while querying secondary storage", e);
    }
    if (response.statusCode() >= 300) {
      throw new IOException(
          "Secondary-storage index query failed with HTTP %d at %s"
              .formatted(response.statusCode(), endpoint.url()));
    }

    final JsonNode indexes = JSON.readTree(response.body());
    if (!indexes.isArray()) {
      throw new IOException("Secondary-storage index query did not return a JSON array");
    }

    long indexCount = 0;
    long documents = 0;
    long storeSizeBytes = 0;
    for (final var index : indexes) {
      if (index.path("index").asText("").startsWith(".")) {
        continue;
      }
      indexCount++;
      documents += longField(index, "docs.count");
      storeSizeBytes += longField(index, "store.size");
    }
    return new StorageStats(indexCount, documents, storeSizeBytes);
  }

  private void sleep() throws IOException {
    try {
      sleeper.sleep(POLL_INTERVAL.toMillis());
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while waiting for secondary-storage ingestion", e);
    }
  }

  private static long longField(final JsonNode node, final String name) {
    final var value = node.path(name).asText("0");
    if (value == null || value.isBlank()) {
      return 0;
    }
    try {
      return Long.parseLong(value);
    } catch (final NumberFormatException e) {
      return 0;
    }
  }

  private record StorageStats(long indexes, long documents, long storeSizeBytes) {}

  private record IngestionResult(String status, StorageStats stats) {}

  @FunctionalInterface
  interface Sleeper {
    void sleep(long millis) throws InterruptedException;
  }
}
