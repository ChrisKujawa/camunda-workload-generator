package io.zell.cwg.secondary;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.zell.cwg.config.WorkloadConfig.SecondaryStorageConfig;
import io.zell.cwg.runtime.SecondaryStorageEndpoint;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class SecondaryStorageReporterTest {

  @Test
  void shouldReportAttachedSecondaryStorageStats() throws Exception {
    // given
    final var server =
        startIndexServer(
            """
            [
              {"index":".plugins-ml-config","docs.count":"100","store.size":"1000"},
              {"index":"operate-list-view","docs.count":"7","store.size":"42"},
              {"index":"closed-index","docs.count":"-","store.size":"-"}
            ]
            """);
    final var endpoint =
        new SecondaryStorageEndpoint("attached", "opensearch", "http://localhost:" + server.getAddress().getPort());
    final var reporter =
        new SecondaryStorageReporter(
            HttpClient.newHttpClient(),
            Clock.fixed(Instant.parse("2026-09-05T05:00:00Z"), ZoneOffset.UTC),
            ignored -> {});

    try {
      // when
      final var report =
          reporter.report(
              new SecondaryStorageConfig(
                  "attached", "opensearch", endpoint.url(), null, false, "PT1S"),
              Optional.of(endpoint));

      // then
      assertThat(report.ingestionWaited()).isFalse();
      assertThat(report.type()).isEqualTo("opensearch");
      assertThat(report.status()).isEqualTo("queried");
      assertThat(report.mode()).isEqualTo("attached");
      assertThat(report.indexes()).isEqualTo(2);
      assertThat(report.documents()).isEqualTo(7);
      assertThat(report.storeSizeBytes()).isEqualTo(42);
    } finally {
      server.stop(0);
    }
  }

  @Test
  void shouldWaitForIngestionUntilDocumentsAreVisible() throws Exception {
    // given
    final var responses = new AtomicInteger();
    final var server =
        startIndexServer(
            () ->
                responses.incrementAndGet() == 1
                    ? "[]"
                    : "[{\"index\":\"operate-list-view\",\"docs.count\":\"1\",\"store.size\":\"10\"}]");
    final var endpoint =
        new SecondaryStorageEndpoint("attached", "opensearch", "http://localhost:" + server.getAddress().getPort());
    final var reporter =
        new SecondaryStorageReporter(
            HttpClient.newHttpClient(),
            Clock.fixed(Instant.parse("2026-09-05T05:00:00Z"), ZoneOffset.UTC),
            ignored -> {});

    try {
      // when
      final var report =
          reporter.report(
              new SecondaryStorageConfig(
                  "attached", "opensearch", endpoint.url(), null, true, "PT1S"),
              Optional.of(endpoint));

      // then
      assertThat(report.ingestionWaited()).isTrue();
      assertThat(report.status()).isEqualTo("ingested");
      assertThat(report.documents()).isEqualTo(1);
      assertThat(responses).hasValue(2);
    } finally {
      server.stop(0);
    }
  }

  @Test
  void shouldReportTimedOutStatusWhenIngestionDoesNotCatchUp() throws Exception {
    // given
    final var server = startIndexServer("[]");
    final var endpoint =
        new SecondaryStorageEndpoint(
            "attached", "opensearch", "http://localhost:" + server.getAddress().getPort());
    final var clock = new TickingClock(Instant.parse("2026-09-05T05:00:00Z"));
    final var reporter =
        new SecondaryStorageReporter(
            HttpClient.newHttpClient(), clock, ignored -> clock.advance(Duration.ofSeconds(2)));

    try {
      // when
      final var report =
          reporter.report(
              new SecondaryStorageConfig(
                  "attached", "opensearch", endpoint.url(), null, true, "PT1S"),
              Optional.of(endpoint));

      // then
      assertThat(report.ingestionWaited()).isTrue();
      assertThat(report.status()).isEqualTo("timed_out");
      assertThat(report.documents()).isZero();
    } finally {
      server.stop(0);
    }
  }

  private static HttpServer startIndexServer(final String response) throws Exception {
    return startIndexServer(() -> response);
  }

  private static HttpServer startIndexServer(final ResponseSupplier responseSupplier)
      throws Exception {
    final var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/_cat/indices",
        exchange -> {
          final var response = responseSupplier.get().getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.start();
    return server;
  }

  @FunctionalInterface
  private interface ResponseSupplier {
    String get();
  }

  private static final class TickingClock extends Clock {

    private Instant instant;

    private TickingClock(final Instant instant) {
      this.instant = instant;
    }

    private void advance(final Duration duration) {
      instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(final ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
