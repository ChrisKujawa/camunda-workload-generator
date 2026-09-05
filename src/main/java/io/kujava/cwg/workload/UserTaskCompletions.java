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
package io.kujava.cwg.workload;

import io.kujava.cwg.bpmn.BpmnAnalysis;
import io.kujava.cwg.config.ConfigException;
import io.kujava.cwg.config.WorkloadConfig;
import io.kujava.cwg.resources.WorkloadResourceAnalysis;
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
    if (config == null) {
      throw new ConfigException("User task completion entry must not be null");
    }
    if (config.elementId() != null && !config.elementId().isBlank()) {
      final var elementId = config.elementId().strip();
      final var matches =
          resourceAnalysis.userTasks().stream()
              .filter(userTask -> elementId.equals(userTask.elementId()))
              .toList();
      if (matches.isEmpty()) {
        throw new ConfigException("No user task found with elementId '%s'".formatted(elementId));
      }
      if (matches.size() > 1) {
        throw new ConfigException(
            "User task elementId '%s' matches multiple tasks".formatted(elementId));
      }
      return new UserTaskCompletion(elementId, config.variables());
    }

    if (config.name() == null || config.name().isBlank()) {
      throw new ConfigException("User task completion must set elementId or name");
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
