package io.zell.cwg.runtime;

import io.zell.cwg.config.WorkloadConfig;

@FunctionalInterface
public interface CamundaRuntimeFactory {

  CamundaRuntime create(WorkloadConfig config);
}
