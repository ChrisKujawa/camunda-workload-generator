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
package io.kujava.cwg.generation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kujava.cwg.config.ConfigException;
import io.kujava.cwg.config.WorkloadConfig;
import io.kujava.cwg.config.WorkloadConfig.OutputConfig;
import io.kujava.cwg.config.WorkloadConfig.ResourcesConfig;
import io.kujava.cwg.config.WorkloadConfig.RuntimeConfig;
import io.kujava.cwg.config.WorkloadConfig.WorkloadSettings;
import io.kujava.cwg.deployment.DeploymentResult;
import io.kujava.cwg.runtime.CamundaRuntime;
import io.kujava.cwg.runtime.ZeebeDataArtifactSource;
import io.kujava.cwg.secondary.SecondaryStorageReporter;
import io.kujava.cwg.workload.WorkloadExecution;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RuntimeWorkloadGeneratorTest {

  @TempDir private Path tempDir;

  @Test
  void shouldStartRuntimeDeployResourcesAndWriteMetadata() throws Exception {
    // given
    final var resources = tempDir.resolve("resources");
    Files.createDirectories(resources);
    Files.writeString(
        resources.resolve("invoice.bpmn"),
        """
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
            xmlns:zeebe="http://camunda.org/schema/zeebe/1.0">
          <process id="invoice">
            <serviceTask id="charge_card">
              <extensionElements>
                <zeebe:taskDefinition type="charge-card" />
              </extensionElements>
            </serviceTask>
          </process>
        </definitions>
        """);
    Files.writeString(
        resources.resolve("payload.json"),
        """
        {
          "customerId": "C-123",
          "amount": 42,
          "vip": true
        }
        """);
    final var output = tempDir.resolve("output");
    final var runtime = new FakeRuntime();
    final var deployedPaths = new ArrayList<Path>();
    final var executedPayloads = new ArrayList<Map<String, Object>>();
    final var generator =
        new RuntimeWorkloadGenerator(
            new io.kujava.cwg.resources.WorkloadResourceAnalyzer(),
            ignored -> runtime,
            (gatewayAddress, restAddress, deployableResources) -> {
              assertThat(gatewayAddress).isEqualTo("localhost:26500");
              assertThat(restAddress).isEqualTo("http://localhost:8080");
              deployableResources.forEach(resource -> deployedPaths.add(resource.relativePath()));
              return new DeploymentResult(deployableResources);
            },
            (gatewayAddress, restAddress, config, analysis, payloadVariables) -> {
              assertThat(restAddress).isEqualTo("http://localhost:8080");
              executedPayloads.add(payloadVariables);
              return new WorkloadExecution(
                  3,
                  2,
                  1,
                  0,
                  java.util.Map.of("charge-card", 2L),
                  java.util.Map.of("charge-card", 1L),
                  java.util.Map.of("payment-received", 2L),
                  java.util.Map.of("approve_invoice", 2L));
            },
            new io.kujava.cwg.workload.PayloadVariablesLoader(),
            new SecondaryStorageReporter(),
            new io.kujava.cwg.artifacts.ManifestWriter(),
            new io.kujava.cwg.artifacts.ReportWriter(),
            Clock.fixed(Instant.parse("2026-09-05T05:00:00Z"), ZoneOffset.UTC));

    // when
    final var result =
        generator.generate(
            new WorkloadConfig(
                new RuntimeConfig("camunda/camunda:8.8.0"),
                new ResourcesConfig(resources.toString(), "invoice", "payload.json"),
                new WorkloadSettings(
                    3,
                    2,
                    Map.of("charge-card", Map.<String, Object>of("approved", true)),
                    List.of(
                        new WorkloadConfig.MessageConfig(
                            "payment-received", "C-123", null, Map.of("paid", true), null))),
                new OutputConfig(output.toString(), true)));

    // then
    assertThat(runtime.started).isTrue();
    assertThat(runtime.closed).isTrue();
    assertThat(deployedPaths).containsExactly(Path.of("invoice.bpmn"));
    assertThat(executedPayloads)
        .singleElement()
        .satisfies(
            payload -> {
              assertThat(payload).containsEntry("customerId", "C-123");
              assertThat(payload).containsEntry("amount", 42);
              assertThat(payload).containsEntry("vip", true);
            });
    assertThat(result.deployedResources()).isEqualTo(1);
    assertThat(Files.readString(result.manifestPath()))
        .contains("\"image\" : \"camunda/camunda:8.8.0\"")
        .contains("\"rootProcessId\" : \"invoice\"")
        .contains("\"payload\" : \"payload.json\"")
        .contains("\"path\" : \"invoice.bpmn\"")
        .contains("\"zeebeData\" : \"zeebe-data/\"")
        .contains("\"zeebeDataZip\" : \"zeebe-data.zip\"");
    final var report = new ObjectMapper().readTree(result.reportPath().toFile());
    assertThat(output.resolve("zeebe-data/partitions/1/runtime/state/data.txt"))
        .hasContent("zeebe data");
    assertThat(output.resolve("zeebe-data.zip")).exists().isRegularFile();
    try (final var zipFile = new ZipFile(output.resolve("zeebe-data.zip").toFile())) {
      assertThat(zipFile.stream().map(ZipEntry::getName).toList())
          .containsExactly("zeebe-data/partitions/1/runtime/state/data.txt");
    }
    assertThat(Files.readString(result.reportPath()))
        .contains("\"startedInstances\" : 3")
        .contains("\"completedInstances\" : 2")
        .contains("\"activeInstances\" : 1")
        .contains("\"detectedJobTypes\" : [ \"charge-card\" ]")
        .contains("\"completedJobs\"")
        .contains("\"appliedWorkerOutputs\"")
        .contains("\"publishedMessages\"")
        .contains("\"completedUserTasks\"")
        .contains("\"zeebeData\"");
    assertThat(report.get("completedJobs").get("charge-card").asLong()).isEqualTo(2);
    assertThat(report.get("appliedWorkerOutputs").get("charge-card").asLong()).isEqualTo(1);
    assertThat(report.get("publishedMessages").get("payment-received").asLong()).isEqualTo(2);
    assertThat(report.get("completedUserTasks").get("approve_invoice").asLong()).isEqualTo(2);
    assertThat(report.get("zeebeData").get("directory").asText()).isEqualTo("zeebe-data/");
    assertThat(report.get("zeebeData").get("zip").asText()).isEqualTo("zeebe-data.zip");
    assertThat(report.get("zeebeData").get("files").asLong()).isEqualTo(1);
    assertThat(report.get("zeebeData").get("bytes").asLong()).isEqualTo(10);
  }

  @Test
  void shouldRejectGenerateWhenNoDeployableResourcesExist() throws Exception {
    // given
    final var resources = tempDir.resolve("empty-resources");
    Files.createDirectories(resources);
    final var runtime = new FakeRuntime();
    final var generator =
        new RuntimeWorkloadGenerator(
            new io.kujava.cwg.resources.WorkloadResourceAnalyzer(),
            ignored -> runtime,
            (gatewayAddress, restAddress, deployableResources) ->
                new DeploymentResult(deployableResources),
            (gatewayAddress, restAddress, config, analysis, payloadVariables) ->
                WorkloadExecution.skipped(),
            new io.kujava.cwg.workload.PayloadVariablesLoader(),
            new SecondaryStorageReporter(),
            new io.kujava.cwg.artifacts.ManifestWriter(),
            new io.kujava.cwg.artifacts.ReportWriter(),
            Clock.fixed(Instant.parse("2026-09-05T05:00:00Z"), ZoneOffset.UTC));

    // when
    final Throwable thrown =
        org.assertj.core.api.Assertions.catchThrowable(
            () ->
                generator.generate(
                    new WorkloadConfig(
                        new RuntimeConfig("camunda/camunda:8.8.0"),
                        new ResourcesConfig(resources.toString(), "invoice", null),
                        new WorkloadSettings(3, 0, Map.of(), List.of()),
                        new OutputConfig(tempDir.resolve("output").toString()))));

    // then
    assertThat(thrown)
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("No deployable BPMN, DMN, or form resources found");
    assertThat(runtime.started).isFalse();
  }

  @Test
  void shouldRejectGenerateWhenPayloadFileIsMissingBeforeStartingRuntime() throws Exception {
    // given
    final var resources = tempDir.resolve("resources");
    Files.createDirectories(resources);
    Files.writeString(
        resources.resolve("invoice.bpmn"),
        """
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
          <process id="invoice" />
        </definitions>
        """);
    final var runtime = new FakeRuntime();
    final var generator =
        new RuntimeWorkloadGenerator(
            new io.kujava.cwg.resources.WorkloadResourceAnalyzer(),
            ignored -> runtime,
            (gatewayAddress, restAddress, deployableResources) ->
                new DeploymentResult(deployableResources),
            (gatewayAddress, restAddress, config, analysis, payloadVariables) ->
                WorkloadExecution.skipped(),
            new io.kujava.cwg.workload.PayloadVariablesLoader(),
            new SecondaryStorageReporter(),
            new io.kujava.cwg.artifacts.ManifestWriter(),
            new io.kujava.cwg.artifacts.ReportWriter(),
            Clock.fixed(Instant.parse("2026-09-05T05:00:00Z"), ZoneOffset.UTC));

    // when
    final Throwable thrown =
        org.assertj.core.api.Assertions.catchThrowable(
            () ->
                generator.generate(
                    new WorkloadConfig(
                        new RuntimeConfig("camunda/camunda:8.8.0"),
                        new ResourcesConfig(resources.toString(), "invoice", "missing.json"),
                        new WorkloadSettings(1, 0, Map.of(), List.of()),
                        new OutputConfig(tempDir.resolve("output").toString()))));

    // then
    assertThat(thrown)
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("Payload file does not exist");
    assertThat(runtime.started).isFalse();
  }

  @Test
  void shouldRejectUnsupportedRuntimeBeforeStartingRuntime() throws Exception {
    // given
    final var resources = tempDir.resolve("unsupported-runtime-resources");
    Files.createDirectories(resources);
    Files.writeString(
        resources.resolve("invoice.bpmn"),
        """
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
          <process id="invoice" />
        </definitions>
        """);
    final var runtime = new NonArtifactRuntime();
    final var generator =
        new RuntimeWorkloadGenerator(
            new io.kujava.cwg.resources.WorkloadResourceAnalyzer(),
            ignored -> runtime,
            (gatewayAddress, restAddress, deployableResources) -> {
              throw new AssertionError("deployment must not run");
            },
            (gatewayAddress, restAddress, config, analysis, payloadVariables) -> {
              throw new AssertionError("workload must not run");
            },
            new io.kujava.cwg.workload.PayloadVariablesLoader(),
            new SecondaryStorageReporter(),
            new io.kujava.cwg.artifacts.ManifestWriter(),
            new io.kujava.cwg.artifacts.ReportWriter(),
            Clock.fixed(Instant.parse("2026-09-05T05:00:00Z"), ZoneOffset.UTC));

    // when
    final Throwable thrown =
        org.assertj.core.api.Assertions.catchThrowable(
            () ->
                generator.generate(
                    new WorkloadConfig(
                        new RuntimeConfig("camunda/camunda:8.8.0"),
                        new ResourcesConfig(resources.toString(), "invoice", null),
                        new WorkloadSettings(1, 0, Map.of(), List.of()),
                        new OutputConfig(tempDir.resolve("output").toString()))));

    // then
    assertThat(thrown)
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("does not support Zeebe data artifact output");
    assertThat(runtime.started).isFalse();
    assertThat(runtime.closed).isTrue();
  }

  private static final class FakeRuntime implements CamundaRuntime, ZeebeDataArtifactSource {

    private boolean started;
    private boolean closed;

    @Override
    public void start() {
      started = true;
    }

    @Override
    public String gatewayAddress() {
      return "localhost:26500";
    }

    @Override
    public void close() {
      closed = true;
    }

    @Override
    public io.kujava.cwg.artifacts.ZeebeDataArtifacts writeZeebeData(
        final Path outputDirectory, final boolean zip) throws java.io.IOException {
      final var writer = new io.kujava.cwg.artifacts.ZeebeDataArtifactWriter();
      return writer.write(
          outputDirectory,
          targetDirectory -> {
            final var dataFile = targetDirectory.resolve("partitions/1/runtime/state/data.txt");
            Files.createDirectories(dataFile.getParent());
            Files.writeString(dataFile, "zeebe data");
          },
          zip);
    }
  }

  private static final class NonArtifactRuntime implements CamundaRuntime {

    private boolean started;
    private boolean closed;

    @Override
    public void start() {
      started = true;
    }

    @Override
    public String gatewayAddress() {
      return "localhost:26500";
    }

    @Override
    public void close() {
      closed = true;
    }
  }
}
