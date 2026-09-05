package io.zell.cwg.workload;

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
