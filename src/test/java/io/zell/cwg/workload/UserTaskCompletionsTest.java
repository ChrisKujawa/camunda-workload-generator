package io.zell.cwg.workload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zell.cwg.bpmn.BpmnAnalysis;
import io.zell.cwg.config.ConfigException;
import io.zell.cwg.config.WorkloadConfig;
import io.zell.cwg.resources.ResourceScanResult;
import io.zell.cwg.resources.WorkloadResourceAnalysis;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class UserTaskCompletionsTest {

  @Test
  void shouldSelectUserTaskByElementId() {
    // given
    final var config =
        config(new WorkloadConfig.UserTaskConfig("approve_invoice", null, Map.of("ok", true)));

    // when
    final var completions = UserTaskCompletions.from(config, analysis(userTask("approve_invoice", "Approve")));

    // then
    assertThat(completions)
        .containsExactly(new UserTaskCompletions.UserTaskCompletion("approve_invoice", Map.of("ok", true)));
  }

  @Test
  void shouldSelectUserTaskByName() {
    // given
    final var config =
        config(new WorkloadConfig.UserTaskConfig(null, "Approve invoice", Map.of()));

    // when
    final var completions =
        UserTaskCompletions.from(config, analysis(userTask("approve_invoice", "Approve invoice")));

    // then
    assertThat(completions)
        .containsExactly(new UserTaskCompletions.UserTaskCompletion("approve_invoice", Map.of()));
  }

  @Test
  void shouldRejectUnknownElementId() {
    // given
    final var config =
        config(new WorkloadConfig.UserTaskConfig("missing", null, Map.of()));

    // when / then
    assertThatThrownBy(
            () ->
                UserTaskCompletions.from(
                    config, analysis(userTask("approve_invoice", "Approve"))))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("No user task found with elementId 'missing'");
  }

  @Test
  void shouldRejectMissingSelectorDuringResolution() {
    // given
    final var config =
        config(new WorkloadConfig.UserTaskConfig(null, null, Map.of()));

    // when / then
    assertThatThrownBy(
            () ->
                UserTaskCompletions.from(
                    config, analysis(userTask("approve_invoice", "Approve"))))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("User task completion must set elementId or name");
  }

  @Test
  void shouldRejectAmbiguousTaskName() {
    // given
    final var config =
        config(new WorkloadConfig.UserTaskConfig(null, "Approve", Map.of()));

    // when / then
    assertThatThrownBy(
            () ->
                UserTaskCompletions.from(
                    config,
                    analysis(userTask("approve_a", "Approve"), userTask("approve_b", "Approve"))))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining(
            "User task name 'Approve' matches multiple element IDs: approve_a, approve_b");
  }

  @Test
  void shouldRejectAmbiguousElementId() {
    // given
    final var config =
        config(new WorkloadConfig.UserTaskConfig("approve", null, Map.of()));

    // when / then
    assertThatThrownBy(
            () ->
                UserTaskCompletions.from(
                    config,
                    analysis(userTask("approve", "Approve A"), userTask("approve", "Approve B"))))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("User task elementId 'approve' matches multiple tasks");
  }

  private static WorkloadConfig config(final WorkloadConfig.UserTaskConfig userTask) {
    return new WorkloadConfig(
        new WorkloadConfig.RuntimeConfig("camunda/camunda:8.8.0"),
        new WorkloadConfig.ResourcesConfig("resources", "invoice", null),
        new WorkloadConfig.WorkloadSettings(1, 1, Map.of(), List.of(), List.of(userTask)),
        new WorkloadConfig.OutputConfig("build/output"));
  }

  private static WorkloadResourceAnalysis analysis(final BpmnAnalysis.UserTask... userTasks) {
    return new WorkloadResourceAnalysis(
        new ResourceScanResult(List.of(), List.of(), List.of()),
        List.of(
            new BpmnAnalysis(
                Path.of("process.bpmn"),
                List.of("invoice"),
                List.of(),
                List.of(),
                List.of(userTasks),
                List.of(),
                List.of(),
                List.of())));
  }

  private static BpmnAnalysis.UserTask userTask(final String elementId, final String name) {
    return new BpmnAnalysis.UserTask(elementId, name, "invoice");
  }
}
