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
        new WorkloadSettings(1, 0, Map.of(), List.of()),
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
              choose(raw.workload.workerOutputs, workload.workerOutputs()),
              choose(messages(raw.workload.messages), workload.messages()));
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
              overrides.startInstances(),
              workload.completeInstances(),
              workload.workerOutputs(),
              workload.messages());
    }
    if (overrides.completeInstances() != null) {
      workload =
          new WorkloadSettings(
              workload.startInstances(),
              overrides.completeInstances(),
              workload.workerOutputs(),
              workload.messages());
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
    validateMessages(workload.messages(), resources.payload(), errors);
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
      int startInstances,
      int completeInstances,
      Map<String, Map<String, Object>> workerOutputs,
      List<MessageConfig> messages) {

    public WorkloadSettings {
      workerOutputs = copyWorkerOutputs(workerOutputs);
      messages = copyMessages(messages);
    }
  }

  public record MessageConfig(
      String name,
      String correlationKey,
      String correlationKeyExpression,
      Map<String, Object> variables,
      String timing) {

    public static final String AFTER_PROCESS_START = "afterProcessStart";

    public MessageConfig {
      variables =
          variables == null
              ? Map.of()
              : Collections.unmodifiableMap(new LinkedHashMap<>(variables));
      timing = timing == null ? AFTER_PROCESS_START : timing;
    }
  }

  public record OutputConfig(String path) {}

  private static List<MessageConfig> messages(final List<RawWorkloadConfig.MessageConfig> raw) {
    if (raw == null) {
      return null;
    }
    return raw.stream()
        .map(
            message ->
                message == null
                    ? null
                    : new MessageConfig(
                        message.name,
                        message.correlationKey,
                        message.correlationKeyExpression,
                        message.variables,
                        message.timing))
        .toList();
  }

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

  private static List<MessageConfig> copyMessages(final List<MessageConfig> messages) {
    if (messages == null || messages.isEmpty()) {
      return List.of();
    }
    return Collections.unmodifiableList(new ArrayList<>(messages));
  }

  private static void validateMessages(
      final List<MessageConfig> messages, final String payload, final List<String> errors) {
    for (int i = 0; i < messages.size(); i++) {
      final var message = messages.get(i);
      final var prefix = "workload.messages[%d]".formatted(i);
      if (message == null) {
        errors.add(prefix + " must not be null");
        continue;
      }
      requireNonBlank(prefix + ".name", message.name(), errors);

      final var hasStaticKey =
          message.correlationKey() != null && !message.correlationKey().isBlank();
      final var hasExpressionKey =
          message.correlationKeyExpression() != null
              && !message.correlationKeyExpression().isBlank();
      if (hasStaticKey == hasExpressionKey) {
        errors.add(
            prefix
                + " must set exactly one of correlationKey or correlationKeyExpression");
      } else if (hasExpressionKey) {
        if (payload == null || payload.isBlank()) {
          errors.add(prefix + ".correlationKeyExpression requires resources.payload");
        }
        validateCorrelationKeyExpression(
            prefix, message.correlationKeyExpression(), errors);
      }
      if (message.timing() == null || message.timing().isBlank()) {
        errors.add(prefix + ".timing must not be blank");
      } else if (!MessageConfig.AFTER_PROCESS_START.equals(message.timing())) {
        errors.add(prefix + ".timing must be afterProcessStart");
      }
      message
          .variables()
          .keySet()
          .forEach(
              variableName ->
                  requireNonBlank(prefix + ".variables variable name", variableName, errors));
    }
  }

  private static void validateCorrelationKeyExpression(
      final String prefix, final String expression, final List<String> errors) {
    final var path = correlationKeyExpressionPath(expression);
    if (path.isBlank()) {
      errors.add(prefix + ".correlationKeyExpression must reference a payload variable");
      return;
    }
    for (final var segment : path.split("\\.")) {
      if (segment.isBlank()) {
        errors.add(prefix + ".correlationKeyExpression must not contain blank path segments");
        return;
      }
    }
  }

  public static String correlationKeyExpressionPath(final String expression) {
    final var stripped = expression == null ? "" : expression.strip();
    if (stripped.startsWith("=")) {
      return stripped.substring(1).strip();
    }
    return stripped;
  }
}
