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

import java.util.LinkedHashMap;
import java.util.Map;

public record WorkloadExecution(
    long startedInstances,
    long completedInstances,
    long activeInstances,
    long createdIncidents,
    Map<String, Long> completedJobs,
    Map<String, Long> appliedWorkerOutputs,
    Map<String, Long> publishedMessages,
    Map<String, Long> completedUserTasks) {

  public WorkloadExecution(
      final long startedInstances,
      final long completedInstances,
      final long activeInstances,
      final long createdIncidents,
      final Map<String, Long> completedJobs,
      final Map<String, Long> appliedWorkerOutputs,
      final Map<String, Long> publishedMessages) {
    this(
        startedInstances,
        completedInstances,
        activeInstances,
        createdIncidents,
        completedJobs,
        appliedWorkerOutputs,
        publishedMessages,
        Map.of());
  }

  public WorkloadExecution {
    completedJobs = new LinkedHashMap<>(completedJobs);
    appliedWorkerOutputs = new LinkedHashMap<>(appliedWorkerOutputs);
    publishedMessages = new LinkedHashMap<>(publishedMessages);
    completedUserTasks = new LinkedHashMap<>(completedUserTasks);
  }

  public static WorkloadExecution skipped() {
    return new WorkloadExecution(0, 0, 0, 0, Map.of(), Map.of(), Map.of(), Map.of());
  }
}
