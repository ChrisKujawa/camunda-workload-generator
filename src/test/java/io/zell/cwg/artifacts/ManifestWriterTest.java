package io.zell.cwg.artifacts;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zell.cwg.config.WorkloadConfig;
import io.zell.cwg.resources.WorkloadResourceAnalyzer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ManifestWriterTest {

  @TempDir private Path tempDir;

  @Test
  void shouldWriteManifestJsonWithoutStartingRuntime() throws Exception {
    // given
    Files.writeString(
        tempDir.resolve("invoice.bpmn"),
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
    Files.writeString(tempDir.resolve("invoice.dmn"), "test");
    Files.writeString(tempDir.resolve("payload.json"), "{}");
    final var config =
        new WorkloadConfig(
            new WorkloadConfig.RuntimeConfig("camunda/camunda:8.8.0"),
            new WorkloadConfig.ResourcesConfig(tempDir.toString(), "invoice", "payload.json"),
            new WorkloadConfig.WorkloadSettings(10, 4, Map.of(), List.of()),
            new WorkloadConfig.OutputConfig(tempDir.resolve("out").toString()));
    final var resourceAnalysis = new WorkloadResourceAnalyzer().analyze(tempDir);
    final var manifest =
        WorkloadManifest.from(config, resourceAnalysis, "2026-09-05T05:30:00Z");

    // when
    final var manifestFile = new ManifestWriter().write(tempDir.resolve("out"), manifest);

    // then
    final JsonNode json = new ObjectMapper().readTree(manifestFile.toFile());
    assertThat(json.get("schemaVersion").asText()).isEqualTo("1");
    assertThat(json.get("runtime").get("image").asText()).isEqualTo("camunda/camunda:8.8.0");
    assertThat(json.get("workload").get("rootProcessId").asText()).isEqualTo("invoice");
    assertThat(json.get("workload").get("startInstances").asInt()).isEqualTo(10);
    assertThat(json.get("resources").get("payload").asText()).isEqualTo("payload.json");
    assertThat(json.get("resources").get("deployableResources").get(0).get("path").asText())
        .isEqualTo("invoice.bpmn");
    assertThat(json.get("resources").get("payloadOrConfigResources").get(0).get("path").asText())
        .isEqualTo("payload.json");
    assertThat(json.get("resources").get("processIds").get(0).asText()).isEqualTo("invoice");
    assertThat(json.get("resources").get("staticJobTypes").get(0).get("type").asText())
        .isEqualTo("charge-card");
    assertThat(json.get("artifacts").get("manifest").asText()).isEqualTo("manifest.json");
  }
}
