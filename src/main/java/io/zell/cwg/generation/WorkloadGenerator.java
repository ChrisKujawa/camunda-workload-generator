package io.zell.cwg.generation;

import io.zell.cwg.config.WorkloadConfig;
import java.io.IOException;

@FunctionalInterface
public interface WorkloadGenerator {

  GenerationResult generate(WorkloadConfig config) throws IOException;
}
