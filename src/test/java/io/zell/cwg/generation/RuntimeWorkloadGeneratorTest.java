package io.zell.cwg.generation;

import static org.assertj.core.api.Assertions.assertThat;

import io.zell.cwg.config.WorkloadConfig;
import io.zell.cwg.config.WorkloadConfig.OutputConfig;
import io.zell.cwg.config.WorkloadConfig.ResourcesConfig;
import io.zell.cwg.config.WorkloadConfig.RuntimeConfig;
import io.zell.cwg.config.WorkloadConfig.WorkloadSettings;
import io.zell.cwg.config.ConfigException;
import io.zell.cwg.deployment.DeploymentResult;
import io.zell.cwg.runtime.CamundaRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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
    final var output = tempDir.resolve("output");
    final var runtime = new FakeRuntime();
    final var deployedPaths = new ArrayList<Path>();
    final var generator =
        new RuntimeWorkloadGenerator(
            new io.zell.cwg.resources.WorkloadResourceAnalyzer(),
            ignored -> runtime,
            (gatewayAddress, deployableResources) -> {
              assertThat(gatewayAddress).isEqualTo("localhost:26500");
              deployableResources.forEach(resource -> deployedPaths.add(resource.relativePath()));
              return new DeploymentResult(deployableResources);
            },
            new io.zell.cwg.artifacts.ManifestWriter(),
            new io.zell.cwg.artifacts.ReportWriter(),
            Clock.fixed(Instant.parse("2026-09-05T05:00:00Z"), ZoneOffset.UTC));

    // when
    final var result =
        generator.generate(
            new WorkloadConfig(
                new RuntimeConfig("camunda/camunda:8.8.0"),
                new ResourcesConfig(resources.toString(), "invoice"),
                new WorkloadSettings(3, 0),
                new OutputConfig(output.toString())));

    // then
    assertThat(runtime.started).isTrue();
    assertThat(runtime.closed).isTrue();
    assertThat(deployedPaths).containsExactly(Path.of("invoice.bpmn"));
    assertThat(result.deployedResources()).isEqualTo(1);
    assertThat(Files.readString(result.manifestPath()))
        .contains("\"image\" : \"camunda/camunda:8.8.0\"")
        .contains("\"rootProcessId\" : \"invoice\"")
        .contains("\"path\" : \"invoice.bpmn\"");
    assertThat(Files.readString(result.reportPath()))
        .contains("\"startedInstances\" : 0")
        .contains("\"detectedJobTypes\" : [ \"charge-card\" ]");
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
                        new ResourcesConfig(resources.toString(), "invoice"),
                        new WorkloadSettings(3, 0),
                        new OutputConfig(tempDir.resolve("output").toString()))));

    // then
    assertThat(thrown)
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("No deployable BPMN, DMN, or form resources found");
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
