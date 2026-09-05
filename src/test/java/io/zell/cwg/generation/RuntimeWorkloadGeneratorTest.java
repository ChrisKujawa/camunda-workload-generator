package io.zell.cwg.generation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zell.cwg.config.ConfigException;
import io.zell.cwg.config.WorkloadConfig;
import io.zell.cwg.config.WorkloadConfig.OutputConfig;
import io.zell.cwg.config.WorkloadConfig.ResourcesConfig;
import io.zell.cwg.config.WorkloadConfig.RuntimeConfig;
import io.zell.cwg.config.WorkloadConfig.WorkloadSettings;
import io.zell.cwg.deployment.DeploymentResult;
import io.zell.cwg.runtime.CamundaRuntime;
import io.zell.cwg.workload.WorkloadExecution;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
            new io.zell.cwg.resources.WorkloadResourceAnalyzer(),
            ignored -> runtime,
            (gatewayAddress, deployableResources) -> {
              assertThat(gatewayAddress).isEqualTo("localhost:26500");
              deployableResources.forEach(resource -> deployedPaths.add(resource.relativePath()));
              return new DeploymentResult(deployableResources);
            },
            (gatewayAddress, config, analysis, payloadVariables) -> {
              executedPayloads.add(payloadVariables);
              return new WorkloadExecution(
                  3,
                  2,
                  1,
                  0,
                  java.util.Map.of("charge-card", 2L),
                  java.util.Map.of("charge-card", 1L),
                  java.util.Map.of("payment-received", 2L));
            },
            new io.zell.cwg.workload.PayloadVariablesLoader(),
            new io.zell.cwg.artifacts.ManifestWriter(),
            new io.zell.cwg.artifacts.ReportWriter(),
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
                new OutputConfig(output.toString())));

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
        .contains("\"path\" : \"invoice.bpmn\"");
    final var report = new ObjectMapper().readTree(result.reportPath().toFile());
    assertThat(Files.readString(result.reportPath()))
        .contains("\"startedInstances\" : 3")
        .contains("\"completedInstances\" : 2")
        .contains("\"activeInstances\" : 1")
        .contains("\"detectedJobTypes\" : [ \"charge-card\" ]")
        .contains("\"completedJobs\"")
        .contains("\"appliedWorkerOutputs\"")
        .contains("\"publishedMessages\"");
    assertThat(report.get("completedJobs").get("charge-card").asLong()).isEqualTo(2);
    assertThat(report.get("appliedWorkerOutputs").get("charge-card").asLong()).isEqualTo(1);
    assertThat(report.get("publishedMessages").get("payment-received").asLong()).isEqualTo(2);
  }

  @Test
  void shouldRejectGenerateWhenNoDeployableResourcesExist() throws Exception {
    // given
    final var resources = tempDir.resolve("empty-resources");
    Files.createDirectories(resources);
    final var runtime = new FakeRuntime();
    final var generator =
        new RuntimeWorkloadGenerator(
            new io.zell.cwg.resources.WorkloadResourceAnalyzer(),
            ignored -> runtime,
            (gatewayAddress, deployableResources) -> new DeploymentResult(deployableResources),
            (gatewayAddress, config, analysis, payloadVariables) -> WorkloadExecution.skipped(),
            new io.zell.cwg.workload.PayloadVariablesLoader(),
            new io.zell.cwg.artifacts.ManifestWriter(),
            new io.zell.cwg.artifacts.ReportWriter(),
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
            new io.zell.cwg.resources.WorkloadResourceAnalyzer(),
            ignored -> runtime,
            (gatewayAddress, deployableResources) -> new DeploymentResult(deployableResources),
            (gatewayAddress, config, analysis, payloadVariables) -> WorkloadExecution.skipped(),
            new io.zell.cwg.workload.PayloadVariablesLoader(),
            new io.zell.cwg.artifacts.ManifestWriter(),
            new io.zell.cwg.artifacts.ReportWriter(),
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

  private static final class FakeRuntime implements CamundaRuntime {

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
