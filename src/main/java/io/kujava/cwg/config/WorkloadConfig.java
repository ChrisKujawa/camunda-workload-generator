/*
 * Copyright 2026 camunda-workload-generator contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.kujava.cwg.config;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WorkloadConfig {

  private RuntimeConfig runtime;
  private ResourcesConfig resources;
  private WorkloadSettings workload;
  private SecondaryStorageConfig secondaryStorage;
  private OutputConfig output;

  public WorkloadConfig() {}

  public WorkloadConfig(
      final RuntimeConfig runtime,
      final ResourcesConfig resources,
      final WorkloadSettings workload,
      final OutputConfig output) {
    this(runtime, resources, workload, SecondaryStorageConfig.disabled(), output);
  }

  public WorkloadConfig(
      final RuntimeConfig runtime,
      final ResourcesConfig resources,
      final WorkloadSettings workload,
      final SecondaryStorageConfig secondaryStorage,
      final OutputConfig output) {
    this.runtime = runtime;
    this.resources = resources;
    this.workload = workload;
    this.secondaryStorage = secondaryStorage;
    this.output = output;
  }

  public static WorkloadConfig defaults() {
    return new WorkloadConfig(
        new RuntimeConfig("camunda/camunda:8.8.0"),
        new ResourcesConfig("resources", null, null),
        new WorkloadSettings(1, 0, Map.of(), List.of(), List.of()),
        SecondaryStorageConfig.disabled(),
        new OutputConfig("build/camunda-workload-generator", false));
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
              choose(messages(raw.workload.messages), workload.messages()),
              choose(userTasks(raw.workload.userTasks), workload.userTasks()));
    }
    if (raw.output != null) {
      output =
          new OutputConfig(
              choose(raw.output.path, output.path()),
              choose(raw.output.zipZeebeData, output.zipZeebeData()));
    }
    if (raw.secondaryStorage != null) {
      secondaryStorage =
          new SecondaryStorageConfig(
              choose(raw.secondaryStorage.mode, secondaryStorage.mode()),
              choose(raw.secondaryStorage.type, secondaryStorage.type()),
              choose(raw.secondaryStorage.url, secondaryStorage.url()),
              choose(raw.secondaryStorage.image, secondaryStorage.image()),
              choose(raw.secondaryStorage.waitForIngestion, secondaryStorage.waitForIngestion()),
              choose(raw.secondaryStorage.waitTimeout, secondaryStorage.waitTimeout()));
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
          new ResourcesConfig(
              resources.directory(), overrides.rootProcessId(), resources.payload());
    }
    if (overrides.payload() != null) {
      resources =
          new ResourcesConfig(
              resources.directory(), resources.rootProcessId(), overrides.payload());
    }
    if (overrides.startInstances() != null) {
      workload =
          new WorkloadSettings(
              overrides.startInstances(),
              workload.completeInstances(),
              workload.workerOutputs(),
              workload.messages(),
              workload.userTasks());
    }
    if (overrides.completeInstances() != null) {
      workload =
          new WorkloadSettings(
              workload.startInstances(),
              overrides.completeInstances(),
              workload.workerOutputs(),
              workload.messages(),
              workload.userTasks());
    }
    if (overrides.outputPath() != null) {
      output = new OutputConfig(overrides.outputPath(), output.zipZeebeData());
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
    validateUserTasks(workload.userTasks(), errors);
    validateSecondaryStorage(secondaryStorage, errors);
    if (workload.completeInstances() > workload.startInstances()) {
      errors.add(
          "workload.completeInstances must be less than or equal to workload.startInstances");
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

  public SecondaryStorageConfig getSecondaryStorage() {
    return secondaryStorage;
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
      List<MessageConfig> messages,
      List<UserTaskConfig> userTasks) {

    public WorkloadSettings(
        final int startInstances,
        final int completeInstances,
        final Map<String, Map<String, Object>> workerOutputs,
        final List<MessageConfig> messages) {
      this(startInstances, completeInstances, workerOutputs, messages, List.of());
    }

    public WorkloadSettings {
      workerOutputs = copyWorkerOutputs(workerOutputs);
      messages = copyMessages(messages);
      userTasks = copyUserTasks(userTasks);
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

  public record UserTaskConfig(String elementId, String name, Map<String, Object> variables) {

    public UserTaskConfig {
      variables =
          variables == null
              ? Map.of()
              : Collections.unmodifiableMap(new LinkedHashMap<>(variables));
    }
  }

  public record SecondaryStorageConfig(
      String mode,
      String type,
      String url,
      String image,
      boolean waitForIngestion,
      String waitTimeout) {

    public static final String MODE_DISABLED = "disabled";
    public static final String MODE_MANAGED = "managed";
    public static final String MODE_ATTACHED = "attached";
    public static final String TYPE_OPENSEARCH = "opensearch";
    public static final String TYPE_ELASTICSEARCH = "elasticsearch";
    public static final String DEFAULT_OPENSEARCH_IMAGE = "opensearchproject/opensearch:2.19.6";
    public static final String DEFAULT_WAIT_TIMEOUT = "PT2M";

    public static SecondaryStorageConfig disabled() {
      return new SecondaryStorageConfig(
          MODE_DISABLED, null, null, null, false, DEFAULT_WAIT_TIMEOUT);
    }

    public String effectiveType() {
      return type == null || type.isBlank() ? TYPE_OPENSEARCH : type;
    }

    public String effectiveImage() {
      if (image != null && !image.isBlank()) {
        return image;
      }
      return DEFAULT_OPENSEARCH_IMAGE;
    }

    public Duration waitTimeoutDuration() {
      return Duration.parse(waitTimeout);
    }
  }

  public record OutputConfig(String path, boolean zipZeebeData) {
    public OutputConfig(final String path) {
      this(path, false);
    }
  }

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

  private static List<UserTaskConfig> userTasks(final List<RawWorkloadConfig.UserTaskConfig> raw) {
    if (raw == null) {
      return null;
    }
    return raw.stream()
        .map(
            userTask ->
                userTask == null
                    ? null
                    : new UserTaskConfig(userTask.elementId, userTask.name, userTask.variables))
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

  private static List<UserTaskConfig> copyUserTasks(final List<UserTaskConfig> userTasks) {
    if (userTasks == null || userTasks.isEmpty()) {
      return List.of();
    }
    return Collections.unmodifiableList(new ArrayList<>(userTasks));
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
        errors.add(prefix + " must set exactly one of correlationKey or correlationKeyExpression");
      } else if (hasExpressionKey) {
        if (payload == null || payload.isBlank()) {
          errors.add(prefix + ".correlationKeyExpression requires resources.payload");
        }
        validateCorrelationKeyExpression(prefix, message.correlationKeyExpression(), errors);
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

  private static void validateUserTasks(
      final List<UserTaskConfig> userTasks, final List<String> errors) {
    for (int i = 0; i < userTasks.size(); i++) {
      final var userTask = userTasks.get(i);
      final var prefix = "workload.userTasks[%d]".formatted(i);
      if (userTask == null) {
        errors.add(prefix + " must not be null");
        continue;
      }

      final var hasElementId = userTask.elementId() != null && !userTask.elementId().isBlank();
      final var hasName = userTask.name() != null && !userTask.name().isBlank();
      if (hasElementId == hasName) {
        errors.add(prefix + " must set exactly one of elementId or name");
      }
      userTask
          .variables()
          .keySet()
          .forEach(
              variableName ->
                  requireNonBlank(prefix + ".variables variable name", variableName, errors));
    }
  }

  private static void validateSecondaryStorage(
      final SecondaryStorageConfig secondaryStorage, final List<String> errors) {
    final var prefix = "secondaryStorage";
    requireNonBlank(prefix + ".mode", secondaryStorage.mode(), errors);
    if (!List.of(
            SecondaryStorageConfig.MODE_DISABLED,
            SecondaryStorageConfig.MODE_MANAGED,
            SecondaryStorageConfig.MODE_ATTACHED)
        .contains(secondaryStorage.mode())) {
      errors.add(prefix + ".mode must be one of disabled, managed, attached");
    }

    if (!SecondaryStorageConfig.MODE_DISABLED.equals(secondaryStorage.mode())) {
      final var type = secondaryStorage.effectiveType();
      if (!List.of(
              SecondaryStorageConfig.TYPE_OPENSEARCH, SecondaryStorageConfig.TYPE_ELASTICSEARCH)
          .contains(type)) {
        errors.add(prefix + ".type must be one of opensearch, elasticsearch");
      }
      if (SecondaryStorageConfig.MODE_ATTACHED.equals(secondaryStorage.mode())) {
        requireNonBlank(prefix + ".url", secondaryStorage.url(), errors);
        validateHttpUrl(prefix + ".url", secondaryStorage.url(), errors);
      }
      if (SecondaryStorageConfig.MODE_MANAGED.equals(secondaryStorage.mode())
          && secondaryStorage.image() != null) {
        requireNonBlank(prefix + ".image", secondaryStorage.image(), errors);
      }
      if (SecondaryStorageConfig.MODE_MANAGED.equals(secondaryStorage.mode())
          && SecondaryStorageConfig.TYPE_ELASTICSEARCH.equals(type)
          && secondaryStorage.image() == null) {
        errors.add(prefix + ".image must be set for managed Elasticsearch");
      }
    }

    requireNonBlank(prefix + ".waitTimeout", secondaryStorage.waitTimeout(), errors);
    try {
      if (!Duration.parse(secondaryStorage.waitTimeout()).isPositive()) {
        errors.add(prefix + ".waitTimeout must be positive");
      }
    } catch (final RuntimeException e) {
      errors.add(prefix + ".waitTimeout must be an ISO-8601 duration");
    }
  }

  private static void validateHttpUrl(
      final String name, final String value, final List<String> errors) {
    if (value == null || value.isBlank()) {
      return;
    }
    try {
      final var uri = URI.create(value);
      if (!List.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null) {
        errors.add(name + " must be a valid http(s) URI");
      }
    } catch (final IllegalArgumentException e) {
      errors.add(name + " must be a valid http(s) URI");
    }
  }

  private static void validateCorrelationKeyExpression(
      final String prefix, final String expression, final List<String> errors) {
    final var path = correlationKeyExpressionPath(expression);
    if (path.isBlank()) {
      errors.add(prefix + ".correlationKeyExpression must reference a payload variable");
      return;
    }
    for (final var segment : path.split("\\.", -1)) {
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
