package io.zell.cwg.workload;

import java.util.Map;

final class WorkerOutputVariables {

  private WorkerOutputVariables() {}

  static Map<String, Object> forJobType(
      final String jobType, final Map<String, Map<String, Object>> workerOutputs) {
    return workerOutputs.getOrDefault(jobType, Map.of());
  }
}
