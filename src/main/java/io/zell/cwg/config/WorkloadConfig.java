package io.zell.cwg.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WorkloadConfig {

  private RuntimeConfig runtime;
  private ResourcesConfig resources;
  private WorkloadSettings workload;
  private OutputConfig output;

  public WorkloadConfig() {}

  public WorkloadConfig(
      final RuntimeConfig runtime,
      final ResourcesConfig resources,
      final WorkloadSettings workload,
      final OutputConfig output) {
    this.runtime = runtime;
    this.resources = resources;
    this.workload = workload;
    this.output = output;
  }

  public static WorkloadConfig defaults() {
    return new WorkloadConfig(
        new RuntimeConfig("camunda/camunda:8.8.0"),
        new ResourcesConfig("resources", null, null),
        new WorkloadSettings(1, 0, Map.of()),
        new OutputConfig("build/camunda-workload-generator"));
  }

  public void merge(final RawWorkloadConfig raw) {
    if (raw.runtime != null && raw.runtime.image != null) {
      runtime = new RuntimeConfig(raw.runtime.image);
    }
    if (raw.resources != null) {
      resources =
          new ResourcesConfig(
              choose(raw.resources.directory, resources.directory()),
              choose(raw.resources.rootProcessId, resources.rootProcessId()),
              choose(raw.resources.payload, resources.payload()));
    }
    if (raw.workload != null) {
      workload =
          new WorkloadSettings(
              choose(raw.workload.startInstances, workload.startInstances()),
              choose(raw.workload.completeInstances, workload.completeInstances()),
              choose(raw.workload.workerOutputs, workload.workerOutputs()));
    }
    if (raw.output != null && raw.output.path != null) {
      output = new OutputConfig(raw.output.path);
    }
  }

  public void apply(final ConfigOverrides overrides) {
    if (overrides == null) {
      return;
    }
    if (overrides.image() != null) {
      runtime = new RuntimeConfig(overrides.image());
    }
    if (overrides.resourcesDirectory() != null) {
      resources =
          new ResourcesConfig(
              overrides.resourcesDirectory(), resources.rootProcessId(), resources.payload());
    }
    if (overrides.rootProcessId() != null) {
      resources =
          new ResourcesConfig(resources.directory(), overrides.rootProcessId(), resources.payload());
    }
    if (overrides.payload() != null) {
      resources =
          new ResourcesConfig(resources.directory(), resources.rootProcessId(), overrides.payload());
    }
    if (overrides.startInstances() != null) {
      workload =
          new WorkloadSettings(
              overrides.startInstances(), workload.completeInstances(), workload.workerOutputs());
    }
    if (overrides.completeInstances() != null) {
      workload =
          new WorkloadSettings(
              workload.startInstances(), overrides.completeInstances(), workload.workerOutputs());
    }
    if (overrides.outputPath() != null) {
      output = new OutputConfig(overrides.outputPath());
    }
  }

  public void validate() {
    final List<String> errors = new ArrayList<>();
    requireNonBlank("runtime.image", runtime.image(), errors);
    requireNonBlank("resources.directory", resources.directory(), errors);
    if (resources.rootProcessId() != null) {
      requireNonBlank("resources.rootProcessId", resources.rootProcessId(), errors);
    }
    if (resources.payload() != null) {
      requireNonBlank("resources.payload", resources.payload(), errors);
    }
    requireNonNegative("workload.startInstances", workload.startInstances(), errors);
    requireNonNegative("workload.completeInstances", workload.completeInstances(), errors);
    validateWorkerOutputs(workload.workerOutputs(), errors);
    if (workload.completeInstances() > workload.startInstances()) {
      errors.add("workload.completeInstances must be less than or equal to workload.startInstances");
    }
    requireNonBlank("output.path", output.path(), errors);

    if (!errors.isEmpty()) {
      throw new ConfigException(
          "Invalid workload config:%n- ".formatted() + String.join("%n- ".formatted(), errors));
    }
  }

  public RuntimeConfig getRuntime() {
    return runtime;
  }

  public ResourcesConfig getResources() {
    return resources;
  }

  public WorkloadSettings getWorkload() {
    return workload;
  }

  public OutputConfig getOutput() {
    return output;
  }

  private static <T> T choose(final T candidate, final T fallback) {
    return candidate == null ? fallback : candidate;
  }

  private static void requireNonBlank(
      final String name, final String value, final List<String> errors) {
    if (value == null || value.isBlank()) {
      errors.add(name + " must not be blank");
    }
  }

  private static void requireNonNegative(
      final String name, final int value, final List<String> errors) {
    if (value < 0) {
      errors.add(name + " must be greater than or equal to 0");
    }
  }

  public record RuntimeConfig(String image) {}

  public record ResourcesConfig(String directory, String rootProcessId, String payload) {}

  public record WorkloadSettings(
      int startInstances, int completeInstances, Map<String, Map<String, Object>> workerOutputs) {

    public WorkloadSettings {
      workerOutputs = copyWorkerOutputs(workerOutputs);
    }
  }

  public record OutputConfig(String path) {}

  private static Map<String, Map<String, Object>> copyWorkerOutputs(
      final Map<String, Map<String, Object>> workerOutputs) {
    if (workerOutputs == null || workerOutputs.isEmpty()) {
      return Map.of();
    }

    final var outputs = new LinkedHashMap<String, Map<String, Object>>();
    workerOutputs.forEach(
        (jobType, variables) ->
            outputs.put(
                jobType,
                variables == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(variables))));
    return Collections.unmodifiableMap(outputs);
  }

  private static void validateWorkerOutputs(
      final Map<String, Map<String, Object>> workerOutputs, final List<String> errors) {
    workerOutputs.forEach(
        (jobType, variables) -> {
          requireNonBlank("workload.workerOutputs job type", jobType, errors);
          variables
              .keySet()
              .forEach(
                  variableName ->
                      requireNonBlank(
                          "workload.workerOutputs.%s variable name".formatted(jobType),
                          variableName,
                          errors));
        });
  }
}
