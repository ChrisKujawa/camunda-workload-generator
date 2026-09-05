package io.zell.cwg.workload;

import io.zell.cwg.bpmn.BpmnAnalysis;
import io.zell.cwg.config.ConfigException;
import io.zell.cwg.config.WorkloadConfig;
import io.zell.cwg.resources.WorkloadResourceAnalysis;
import java.util.List;
import java.util.Map;

final class UserTaskCompletions {

  private UserTaskCompletions() {}

  static List<UserTaskCompletion> from(
      final WorkloadConfig config, final WorkloadResourceAnalysis resourceAnalysis) {
    return config.getWorkload().userTasks().stream()
        .map(userTask -> completion(userTask, resourceAnalysis))
        .toList();
  }

  private static UserTaskCompletion completion(
      final WorkloadConfig.UserTaskConfig config, final WorkloadResourceAnalysis resourceAnalysis) {
    if (config.elementId() != null && !config.elementId().isBlank()) {
      final var elementId = config.elementId().strip();
      final var exists =
          resourceAnalysis.userTasks().stream()
              .anyMatch(userTask -> elementId.equals(userTask.elementId()));
      if (!exists) {
        throw new ConfigException("No user task found with elementId '%s'".formatted(elementId));
      }
      return new UserTaskCompletion(elementId, config.variables());
    }

    final var name = config.name().strip();
    final var matches =
        resourceAnalysis.userTasks().stream()
            .filter(userTask -> name.equals(userTask.elementName()))
            .toList();
    if (matches.isEmpty()) {
      throw new ConfigException("No user task found with name '%s'".formatted(name));
    }
    if (matches.size() > 1) {
      final var elementIds =
          matches.stream().map(BpmnAnalysis.UserTask::elementId).sorted().toList();
      throw new ConfigException(
          "User task name '%s' matches multiple element IDs: %s"
              .formatted(name, String.join(", ", elementIds)));
    }
    return new UserTaskCompletion(matches.get(0).elementId(), config.variables());
  }

  record UserTaskCompletion(String elementId, Map<String, Object> variables) {}
}
