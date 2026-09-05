/*
 * Copyright 2026 camunda-workload-generator contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.kujava.cwg.runtime;

import io.kujava.cwg.artifacts.ZeebeDataArtifactWriter;
import io.kujava.cwg.artifacts.ZeebeDataArtifacts;
import io.kujava.cwg.config.WorkloadConfig;
import io.kujava.cwg.config.WorkloadConfig.SecondaryStorageConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public final class ManagedCamundaRuntime
    implements CamundaRuntime, ZeebeDataArtifactSource, SecondaryStorageRuntime {

  private static final int ZEEBE_GATEWAY_PORT = 26500;
  private static final int REST_PORT = 8080;
  private static final int SECONDARY_STORAGE_PORT = 9200;
  private static final int CLEAN_STOP_TIMEOUT_SECONDS = 30;
  private static final String ZEEBE_DATA_DIRECTORY = "/usr/local/camunda/data";
  private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(4);
  private static final Duration TOPOLOGY_REQUEST_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration TOPOLOGY_RETRY_DELAY = Duration.ofSeconds(1);
  private static final int STARTUP_LOG_LINES = 120;

  private final GenericContainer<?> container;
  private final GenericContainer<?> secondaryStorageContainer;
  private final Network network;
  private final SecondaryStorageConfig secondaryStorageConfig;
  private final ZeebeDataArtifactWriter dataArtifactWriter;

  private ManagedCamundaRuntime(
      final GenericContainer<?> container,
      final GenericContainer<?> secondaryStorageContainer,
      final Network network,
      final SecondaryStorageConfig secondaryStorageConfig) {
    this(
        container,
        secondaryStorageContainer,
        network,
        secondaryStorageConfig,
        new ZeebeDataArtifactWriter());
  }

  private ManagedCamundaRuntime(
      final GenericContainer<?> container,
      final GenericContainer<?> secondaryStorageContainer,
      final Network network,
      final SecondaryStorageConfig secondaryStorageConfig,
      final ZeebeDataArtifactWriter dataArtifactWriter) {
    this.container = container;
    this.secondaryStorageContainer = secondaryStorageContainer;
    this.network = network;
    this.secondaryStorageConfig = secondaryStorageConfig;
    this.dataArtifactWriter = dataArtifactWriter;
  }

  public static ManagedCamundaRuntime from(final WorkloadConfig config) {
    final var secondaryStorage = config.getSecondaryStorage();
    final var container = camundaContainer(config.getRuntime().image());

    if (SecondaryStorageConfig.MODE_DISABLED.equals(secondaryStorage.mode())) {
      container.withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_TYPE", "none");
      return new ManagedCamundaRuntime(container, null, null, secondaryStorage);
    }

    if (SecondaryStorageConfig.MODE_ATTACHED.equals(secondaryStorage.mode())) {
      configureCamundaSecondaryStorage(
          container, secondaryStorage.effectiveType(), secondaryStorage.url());
      return new ManagedCamundaRuntime(container, null, null, secondaryStorage);
    }

    final var network = Network.newNetwork();
    final var alias = secondaryStorage.effectiveType();
    final var secondaryStorageContainer =
        secondaryStorageContainer(secondaryStorage).withNetwork(network).withNetworkAliases(alias);
    configureCamundaSecondaryStorage(
        container.withNetwork(network),
        secondaryStorage.effectiveType(),
        "http://%s:%d".formatted(alias, SECONDARY_STORAGE_PORT));
    return new ManagedCamundaRuntime(
        container, secondaryStorageContainer, network, secondaryStorage);
  }

  @Override
  public void start() {
    if (secondaryStorageContainer != null) {
      secondaryStorageContainer.start();
    }
    container.start();
    waitForTopology();
  }

  @Override
  public String gatewayAddress() {
    return "%s:%d".formatted(mappedHost(container), container.getMappedPort(ZEEBE_GATEWAY_PORT));
  }

  @Override
  public String restAddress() {
    return "http://%s:%d".formatted(mappedHost(container), container.getMappedPort(REST_PORT));
  }

  @Override
  public ZeebeDataArtifacts writeZeebeData(final Path outputDirectory, final boolean zip)
      throws IOException {
    return dataArtifactWriter.write(
        outputDirectory,
        targetDirectory -> {
          stopContainerForDataCopy();
          copyZeebeDataDirectory(targetDirectory);
        },
        zip);
  }

  @Override
  public Optional<SecondaryStorageEndpoint> secondaryStorageEndpoint() {
    if (SecondaryStorageConfig.MODE_DISABLED.equals(secondaryStorageConfig.mode())) {
      return Optional.empty();
    }
    if (secondaryStorageContainer == null) {
      return Optional.of(
          new SecondaryStorageEndpoint(
              secondaryStorageConfig.mode(),
              secondaryStorageConfig.effectiveType(),
              secondaryStorageConfig.url()));
    }
    return Optional.of(
        new SecondaryStorageEndpoint(
            secondaryStorageConfig.mode(),
            secondaryStorageConfig.effectiveType(),
            "http://%s:%d"
                .formatted(
                    mappedHost(secondaryStorageContainer),
                    secondaryStorageContainer.getMappedPort(SECONDARY_STORAGE_PORT))));
  }

  @Override
  public void close() {
    RuntimeException failure = cleanup(container::stop, null);
    if (secondaryStorageContainer != null) {
      failure = cleanup(secondaryStorageContainer::stop, failure);
    }
    if (network != null) {
      failure = cleanup(network::close, failure);
    }
    if (failure != null) {
      throw failure;
    }
  }

  private static RuntimeException cleanup(
      final CleanupAction action, final RuntimeException failure) {
    try {
      action.run();
      return failure;
    } catch (final RuntimeException e) {
      if (failure == null) {
        return e;
      }
      failure.addSuppressed(e);
      return failure;
    }
  }

  private static GenericContainer<?> camundaContainer(final String image) {
    return new GenericContainer<>(DockerImageName.parse(image))
        .withExposedPorts(ZEEBE_GATEWAY_PORT, REST_PORT)
        .withEnv("SPRING_PROFILES_ACTIVE", "broker,standalone")
        .withEnv("ZEEBE_BROKER_GATEWAY_ENABLE", "true")
        .withEnv("ZEEBE_BROKER_NETWORK_HOST", "0.0.0.0")
        .withEnv("ZEEBE_BROKER_NETWORK_ADVERTISEDHOST", "127.0.0.1")
        .withEnv("CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI", "true")
        .withEnv("CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED", "false")
        .withEnv("ZEEBE_BROKER_EXPERIMENTAL_ROCKSDB_DISABLEWAL", "false")
        .waitingFor(Wait.forListeningPort().withStartupTimeout(STARTUP_TIMEOUT));
  }

  private static GenericContainer<?> secondaryStorageContainer(
      final SecondaryStorageConfig config) {
    final var container =
        new GenericContainer<>(DockerImageName.parse(config.effectiveImage()))
            .withExposedPorts(SECONDARY_STORAGE_PORT)
            .withEnv("discovery.type", "single-node")
            .waitingFor(Wait.forHttp("/").forStatusCode(200).withStartupTimeout(STARTUP_TIMEOUT));
    if (SecondaryStorageConfig.TYPE_ELASTICSEARCH.equals(config.effectiveType())) {
      return container
          .withEnv("xpack.security.enabled", "false")
          .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");
    }
    return container
        .withEnv("DISABLE_SECURITY_PLUGIN", "true")
        .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m");
  }

  static String mappedHost(final GenericContainer<?> container) {
    return mappedHost(container.getHost());
  }

  static String mappedHost(final String host) {
    return "localhost".equals(host) ? "127.0.0.1" : host;
  }

  private static void configureCamundaSecondaryStorage(
      final GenericContainer<?> container, final String type, final String url) {
    final var typeKey = type.toUpperCase(Locale.ROOT);
    container
        .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_TYPE", type)
        .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_%s_URL".formatted(typeKey), url)
        .withEnv("ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_CONNECT_URL", url)
        .withEnv("ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_CONNECT_TYPE", type)
        .withEnv("DATABASE_URL", url)
        .withEnv("DATABASE_TYPE", type);
  }

  private void waitForTopology() {
    final var deadline = Instant.now().plus(STARTUP_TIMEOUT);
    RuntimeException lastFailure = null;

    while (Instant.now().isBefore(deadline)) {
      try (final var client = CamundaClients.create(gatewayAddress(), restAddress())) {
        client.newTopologyRequest().requestTimeout(TOPOLOGY_REQUEST_TIMEOUT).send().join();
        return;
      } catch (final RuntimeException e) {
        lastFailure = e;
        sleepBeforeRetry();
      }
    }

    throw new IllegalStateException(
        "Timed out waiting for Zeebe gateway topology at %s%nCamunda container logs:%n%s"
            .formatted(gatewayAddress(), tail(container.getLogs(), STARTUP_LOG_LINES)),
        lastFailure);
  }

  static String tail(final String text, final int lines) {
    final var split = text == null ? new String[0] : text.stripTrailing().split("\\R");
    if (split.length == 0) {
      return "<no logs>";
    }
    final var start = Math.max(0, split.length - lines);
    return String.join(
        System.lineSeparator(), java.util.Arrays.copyOfRange(split, start, split.length));
  }

  private static void sleepBeforeRetry() {
    try {
      Thread.sleep(TOPOLOGY_RETRY_DELAY);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for Zeebe gateway readiness", e);
    }
  }

  private void stopContainerForDataCopy() {
    if (container.isRunning()) {
      container
          .getDockerClient()
          .stopContainerCmd(container.getContainerId())
          .withTimeout(CLEAN_STOP_TIMEOUT_SECONDS)
          .exec();
    }
  }

  private void copyZeebeDataDirectory(final Path targetDirectory) throws IOException {
    final var normalizedTargetDirectory = targetDirectory.toAbsolutePath().normalize();
    try (final var archive =
            container
                .getDockerClient()
                .copyArchiveFromContainerCmd(container.getContainerId(), ZEEBE_DATA_DIRECTORY)
                .exec();
        final var tar = new TarArchiveInputStream(archive)) {
      TarArchiveEntry entry;
      while ((entry = tar.getNextTarEntry()) != null) {
        final var relativePath = stripTopLevelDirectory(entry.getName());
        if (relativePath.toString().isEmpty()) {
          continue;
        }

        final var target = normalizedTargetDirectory.resolve(relativePath).normalize();
        if (!target.startsWith(normalizedTargetDirectory)) {
          throw new IOException(
              "Refusing to extract Zeebe data outside target directory: " + entry.getName());
        }

        if (entry.isDirectory()) {
          Files.createDirectories(target);
        } else if (entry.isFile()) {
          final var parent = target.getParent();
          if (parent == null) {
            throw new IOException(
                "Zeebe data archive entry has no parent directory: " + entry.getName());
          }
          Files.createDirectories(parent);
          Files.copy(tar, target);
        } else {
          throw new IOException("Unsupported Zeebe data archive entry: " + entry.getName());
        }
      }
    }
  }

  static Path stripTopLevelDirectory(final String entryName) {
    final var entryPath = Path.of(entryName);
    if (entryPath.getNameCount() <= 1) {
      return Path.of("");
    }
    return entryPath.subpath(1, entryPath.getNameCount()).normalize();
  }

  @FunctionalInterface
  private interface CleanupAction {
    void run();
  }
}
