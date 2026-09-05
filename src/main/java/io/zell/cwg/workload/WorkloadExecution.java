package io.zell.cwg.workload;

import java.util.LinkedHashMap;
import java.util.Map;

public record WorkloadExecution(
    long startedInstances,
    long completedInstances,
    long activeInstances,
    long createdIncidents,
    Map<String, Long> completedJobs,
    Map<String, Long> appliedWorkerOutputs) {

  public WorkloadExecution {
    completedJobs = new LinkedHashMap<>(completedJobs);
    appliedWorkerOutputs = new LinkedHashMap<>(appliedWorkerOutputs);
  }

  public static WorkloadExecution skipped() {
    return new WorkloadExecution(0, 0, 0, 0, Map.of(), Map.of());
  }
}
