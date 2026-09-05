package io.zell.cwg.workload;

import io.zell.cwg.config.WorkloadConfig;
import io.zell.cwg.resources.WorkloadResourceAnalysis;

@FunctionalInterface
public interface WorkloadExecutor {

  WorkloadExecution execute(
      String gatewayAddress, WorkloadConfig config, WorkloadResourceAnalysis resourceAnalysis);
}
