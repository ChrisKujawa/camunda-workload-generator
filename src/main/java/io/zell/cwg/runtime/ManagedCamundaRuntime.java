package io.zell.cwg.runtime;

import io.camunda.zeebe.client.ZeebeClient;
import io.zell.cwg.config.WorkloadConfig;
import java.time.Duration;
import java.time.Instant;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public final class ManagedCamundaRuntime implements CamundaRuntime {

  private static final int ZEEBE_GATEWAY_PORT = 26500;
  private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(4);
  private static final Duration TOPOLOGY_REQUEST_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration TOPOLOGY_RETRY_DELAY = Duration.ofSeconds(1);

  private final GenericContainer<?> container;

  private ManagedCamundaRuntime(final GenericContainer<?> container) {
    this.container = container;
  }

  public static ManagedCamundaRuntime from(final WorkloadConfig.RuntimeConfig config) {
    final var container =
        new GenericContainer<>(DockerImageName.parse(config.image()))
            .withExposedPorts(ZEEBE_GATEWAY_PORT)
            .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_TYPE", "none")
            .withEnv("SPRING_PROFILES_ACTIVE", "broker,standalone")
            .withEnv("ZEEBE_BROKER_EXPERIMENTAL_ROCKSDB_DISABLEWAL", "false")
            .waitingFor(Wait.forListeningPort().withStartupTimeout(STARTUP_TIMEOUT));
    return new ManagedCamundaRuntime(container);
  }

  @Override
  public void start() {
    container.start();
    waitForTopology();
  }

  @Override
  public String gatewayAddress() {
    return "%s:%d".formatted(container.getHost(), container.getMappedPort(ZEEBE_GATEWAY_PORT));
  }

  @Override
  public void close() {
    container.stop();
  }

  private void waitForTopology() {
    final var deadline = Instant.now().plus(STARTUP_TIMEOUT);
    RuntimeException lastFailure = null;

    while (Instant.now().isBefore(deadline)) {
      try (final var client =
          ZeebeClient.newClientBuilder()
              .gatewayAddress(gatewayAddress())
              .usePlaintext()
              .build()) {
        client
            .newTopologyRequest()
            .requestTimeout(TOPOLOGY_REQUEST_TIMEOUT)
            .send()
            .join();
        return;
      } catch (final RuntimeException e) {
        lastFailure = e;
        sleepBeforeRetry();
      }
    }

    throw new IllegalStateException(
        "Timed out waiting for Zeebe gateway topology at %s".formatted(gatewayAddress()),
        lastFailure);
  }

  private static void sleepBeforeRetry() {
    try {
      Thread.sleep(TOPOLOGY_RETRY_DELAY);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for Zeebe gateway readiness", e);
    }
  }
}
