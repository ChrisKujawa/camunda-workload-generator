package io.zell.cwg.config;

import java.util.ArrayList;
import java.util.List;

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
        new ResourcesConfig("resources", null),
        new WorkloadSettings(1, 0),
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
              choose(raw.resources.rootProcessId, resources.rootProcessId()));
    }
    if (raw.workload != null) {
      workload =
          new WorkloadSettings(
              choose(raw.workload.startInstances, workload.startInstances()),
              choose(raw.workload.completeInstances, workload.completeInstances()));
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
      resources = new ResourcesConfig(overrides.resourcesDirectory(), resources.rootProcessId());
    }
    if (overrides.rootProcessId() != null) {
      resources = new ResourcesConfig(resources.directory(), overrides.rootProcessId());
    }
    if (overrides.startInstances() != null) {
      workload = new WorkloadSettings(overrides.startInstances(), workload.completeInstances());
    }
    if (overrides.completeInstances() != null) {
      workload = new WorkloadSettings(workload.startInstances(), overrides.completeInstances());
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
    requireNonNegative("workload.startInstances", workload.startInstances(), errors);
    requireNonNegative("workload.completeInstances", workload.completeInstances(), errors);
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

  public record ResourcesConfig(String directory, String rootProcessId) {}

  public record WorkloadSettings(int startInstances, int completeInstances) {}

  public record OutputConfig(String path) {}
}
