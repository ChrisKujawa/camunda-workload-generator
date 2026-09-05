package io.zell.cwg.workload;

import io.zell.cwg.config.WorkloadConfig;
import io.zell.cwg.resources.WorkloadResourceAnalysis;
import java.util.Map;

@FunctionalInterface
public interface WorkloadExecutor {

  WorkloadExecution execute(
      String gatewayAddress,
      WorkloadConfig config,
      WorkloadResourceAnalysis resourceAnalysis,
      Map<String, Object> payloadVariables);
}
