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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ConfigLoaderTest {

  @TempDir private Path tempDir;

  @Test
  void shouldApplyDefaultsThenConfigFileThenCliOverrides() throws Exception {
    // given
    final var configFile = tempDir.resolve("workload.yaml");
    Files.writeString(
        configFile,
        """
        runtime:
          image: camunda/camunda:8.8.1
        resources:
          directory: config-resources
          rootProcessId: invoice
          payload: config-payload.json
        workload:
          startInstances: 10
          workerOutputs:
            charge-card:
              approved: true
          messages:
            - name: payment-received
              correlationKeyExpression: =customer.id
              variables:
                paid: true
          userTasks:
            - name: Approve invoice
              variables:
                approved: true
        output:
          path: build/config-output
          zipZeebeData: true
        secondaryStorage:
          mode: attached
          type: opensearch
          url: http://localhost:9200
          waitForIngestion: true
          waitTimeout: PT30S
        """);

    // when
    final var config =
        ConfigLoader.load(
            configFile,
            new ConfigOverrides(
                "camunda/camunda:8.8.2",
                null,
                "order",
                "cli-payload.json",
                null,
                7,
                "build/cli-output"));

    // then
    assertThat(config.getRuntime().image()).isEqualTo("camunda/camunda:8.8.2");
    assertThat(config.getResources().directory()).isEqualTo("config-resources");
    assertThat(config.getResources().rootProcessId()).isEqualTo("order");
    assertThat(config.getResources().payload()).isEqualTo("cli-payload.json");
    assertThat(config.getWorkload().startInstances()).isEqualTo(10);
    assertThat(config.getWorkload().completeInstances()).isEqualTo(7);
    assertThat(config.getWorkload().workerOutputs())
        .containsEntry("charge-card", Map.of("approved", true));
    assertThat(config.getWorkload().messages())
        .containsExactly(
            new WorkloadConfig.MessageConfig(
                "payment-received",
                null,
                "=customer.id",
                Map.of("paid", true),
                WorkloadConfig.MessageConfig.AFTER_PROCESS_START));
    assertThat(config.getWorkload().userTasks())
        .containsExactly(
            new WorkloadConfig.UserTaskConfig(null, "Approve invoice", Map.of("approved", true)));
    assertThat(config.getOutput().path()).isEqualTo("build/cli-output");
    assertThat(config.getOutput().zipZeebeData()).isTrue();
    assertThat(config.getSecondaryStorage().mode()).isEqualTo("attached");
    assertThat(config.getSecondaryStorage().effectiveType()).isEqualTo("opensearch");
    assertThat(config.getSecondaryStorage().url()).isEqualTo("http://localhost:9200");
    assertThat(config.getSecondaryStorage().waitForIngestion()).isTrue();
    assertThat(config.getSecondaryStorage().waitTimeout()).isEqualTo("PT30S");
  }

  @Test
  void shouldUseDefaultsWhenConfigFileIsMissing() throws Exception {
    // when
    final var config = ConfigLoader.load(null, ConfigOverrides.none());

    // then
    assertThat(config.getRuntime().image()).isEqualTo("camunda/camunda:8.8.0");
    assertThat(config.getResources().directory()).isEqualTo("resources");
    assertThat(config.getResources().payload()).isNull();
    assertThat(config.getWorkload().startInstances()).isEqualTo(1);
    assertThat(config.getWorkload().completeInstances()).isZero();
    assertThat(config.getWorkload().workerOutputs()).isEmpty();
    assertThat(config.getWorkload().messages()).isEmpty();
    assertThat(config.getWorkload().userTasks()).isEmpty();
    assertThat(config.getOutput().path()).isEqualTo("build/camunda-workload-generator");
    assertThat(config.getOutput().zipZeebeData()).isFalse();
    assertThat(config.getSecondaryStorage().mode()).isEqualTo("disabled");
  }

  @Test
  void shouldRejectUnknownConfigProperties() throws Exception {
    // given
    final var configFile = tempDir.resolve("workload.yaml");
    Files.writeString(configFile, "unexpected: true%n".formatted());

    // when / then
    assertThatThrownBy(() -> ConfigLoader.load(configFile, ConfigOverrides.none()))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("unrecognized property 'unexpected'");
  }

  @Test
  void shouldRejectCompletingMoreInstancesThanStarted() throws Exception {
    // given
    final var configFile = tempDir.resolve("workload.yaml");
    Files.writeString(
        configFile,
        """
        workload:
          startInstances: 2
          completeInstances: 3
        """);

    // when / then
    assertThatThrownBy(() -> ConfigLoader.load(configFile, ConfigOverrides.none()))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining(
            "workload.completeInstances must be less than or equal to workload.startInstances");
  }

  @Test
  void shouldRejectUserTaskCompletionWithoutSecondaryStorage() throws Exception {
    // given
    final var configFile = tempDir.resolve("workload.yaml");
    Files.writeString(
        configFile,
        """
        workload:
          startInstances: 1
          completeInstances: 1
          userTasks:
            - name: Approve invoice
              variables:
                approved: true
        """);

    // when / then
    assertThatThrownBy(() -> ConfigLoader.load(configFile, ConfigOverrides.none()))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining(
            "workload.userTasks requires secondaryStorage.mode managed or attached when completing"
                + " process instances");
  }

  @Test
  void shouldRejectInvalidSecondaryStorageConfig() throws Exception {
    // given
    final var configFile = tempDir.resolve("workload.yaml");
    Files.writeString(
        configFile,
        """
        secondaryStorage:
          mode: attached
          type: solr
          waitTimeout: soon
        """);

    // when / then
    assertThatThrownBy(() -> ConfigLoader.load(configFile, ConfigOverrides.none()))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("secondaryStorage.type must be one of opensearch, elasticsearch")
        .hasMessageContaining("secondaryStorage.url must not be blank")
        .hasMessageContaining("secondaryStorage.waitTimeout must be an ISO-8601 duration");
  }

  @Test
  void shouldRequireImageForManagedElasticsearch() throws Exception {
    // given
    final var configFile = tempDir.resolve("workload.yaml");
    Files.writeString(
        configFile,
        """
        secondaryStorage:
          mode: managed
          type: elasticsearch
        """);

    // when / then
    assertThatThrownBy(() -> ConfigLoader.load(configFile, ConfigOverrides.none()))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("secondaryStorage.image must be set for managed Elasticsearch");
  }

  @Test
  void shouldRejectInvalidAttachedSecondaryStorageUrl() throws Exception {
    // given
    final var configFile = tempDir.resolve("workload.yaml");
    Files.writeString(
        configFile,
        """
        secondaryStorage:
          mode: attached
          type: opensearch
          url: localhost:9200
        """);

    // when / then
    assertThatThrownBy(() -> ConfigLoader.load(configFile, ConfigOverrides.none()))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("secondaryStorage.url must be a valid http(s) URI");
  }

  @Test
  void shouldRejectBlankPayloadPath() throws Exception {
    // given
    final var configFile = tempDir.resolve("workload.yaml");
    Files.writeString(
        configFile,
        """
        resources:
          payload: " "
        """);

    // when / then
    assertThatThrownBy(() -> ConfigLoader.load(configFile, ConfigOverrides.none()))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("resources.payload must not be blank");
  }

  @Test
  void shouldRejectBlankWorkerOutputJobType() {
    // given
    final var config =
        new WorkloadConfig(
            new WorkloadConfig.RuntimeConfig("camunda/camunda:8.8.0"),
            new WorkloadConfig.ResourcesConfig("resources", "invoice", null),
            new WorkloadConfig.WorkloadSettings(
                1, 0, Map.of(" ", Map.<String, Object>of("approved", true)), List.of()),
            new WorkloadConfig.OutputConfig("build/output"));

    // when / then
    assertThatThrownBy(config::validate)
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("workload.workerOutputs job type must not be blank");
  }

  @Test
  void shouldRejectBlankWorkerOutputVariableName() {
    // given
    final var config =
        new WorkloadConfig(
            new WorkloadConfig.RuntimeConfig("camunda/camunda:8.8.0"),
            new WorkloadConfig.ResourcesConfig("resources", "invoice", null),
            new WorkloadConfig.WorkloadSettings(
                1, 0, Map.of("charge-card", Map.<String, Object>of(" ", true)), List.of()),
            new WorkloadConfig.OutputConfig("build/output"));

    // when / then
    assertThatThrownBy(config::validate)
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("workload.workerOutputs.charge-card variable name must not be blank");
  }

  @Test
  void shouldRejectMessageWithoutExactlyOneCorrelationKeySource() {
    // given
    final var config =
        configWithMessage(
            new WorkloadConfig.MessageConfig("payment-received", null, null, Map.of(), null));

    // when / then
    assertThatThrownBy(config::validate)
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining(
            "workload.messages[0] must set exactly one of correlationKey or correlationKeyExpression");
  }

  @Test
  void shouldRejectUnsupportedMessageTiming() {
    // given
    final var config =
        configWithMessage(
            new WorkloadConfig.MessageConfig(
                "payment-received", "order-1", null, Map.of(), "beforeProcessStart"));

    // when / then
    assertThatThrownBy(config::validate)
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("workload.messages[0].timing must be afterProcessStart");
  }

  @Test
  void shouldRejectMessageCorrelationExpressionWithoutPayloadPath() {
    // given
    final var config =
        configWithMessage(
            new WorkloadConfig.MessageConfig("payment-received", null, "=", Map.of(), null));

    // when / then
    assertThatThrownBy(config::validate)
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining(
            "workload.messages[0].correlationKeyExpression must reference a payload variable");
  }

  @Test
  void shouldRejectMessageCorrelationExpressionWithBlankPathSegment() {
    // given
    final var config =
        configWithMessage(
            new WorkloadConfig.MessageConfig(
                "payment-received", null, "=customer..id", Map.of(), null));

    // when / then
    assertThatThrownBy(config::validate)
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining(
            "workload.messages[0].correlationKeyExpression must not contain blank path segments");
  }

  @Test
  void shouldRejectMessageCorrelationExpressionWithTrailingBlankPathSegment() {
    // given
    final var config =
        configWithMessage(
            new WorkloadConfig.MessageConfig(
                "payment-received", null, "=customer.id.", Map.of(), null));

    // when / then
    assertThatThrownBy(config::validate)
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining(
            "workload.messages[0].correlationKeyExpression must not contain blank path segments");
  }

  @Test
  void shouldRejectMessageCorrelationExpressionWithoutPayload() {
    // given
    final var config =
        new WorkloadConfig(
            new WorkloadConfig.RuntimeConfig("camunda/camunda:8.8.0"),
            new WorkloadConfig.ResourcesConfig("resources", "invoice", null),
            new WorkloadConfig.WorkloadSettings(
                1,
                0,
                Map.of(),
                List.of(
                    new WorkloadConfig.MessageConfig(
                        "payment-received", null, "=customer.id", Map.of(), null))),
            new WorkloadConfig.OutputConfig("build/output"));

    // when / then
    assertThatThrownBy(config::validate)
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining(
            "workload.messages[0].correlationKeyExpression requires resources.payload");
  }

  @Test
  void shouldRejectNullMessageEntry() {
    // given
    final var config =
        new WorkloadConfig(
            new WorkloadConfig.RuntimeConfig("camunda/camunda:8.8.0"),
            new WorkloadConfig.ResourcesConfig("resources", "invoice", null),
            new WorkloadConfig.WorkloadSettings(
                1, 0, Map.of(), Collections.<WorkloadConfig.MessageConfig>singletonList(null)),
            new WorkloadConfig.OutputConfig("build/output"));

    // when / then
    assertThatThrownBy(config::validate)
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("workload.messages[0] must not be null");
  }

  @Test
  void shouldRejectUserTaskWithoutExactlyOneSelector() {
    // given
    final var config =
        configWithUserTask(
            new WorkloadConfig.UserTaskConfig("approve_invoice", "Approve invoice", Map.of()));

    // when / then
    assertThatThrownBy(config::validate)
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("workload.userTasks[0] must set exactly one of elementId or name");
  }

  @Test
  void shouldRejectBlankUserTaskVariableName() {
    // given
    final var config =
        configWithUserTask(
            new WorkloadConfig.UserTaskConfig(
                "approve_invoice", null, Map.<String, Object>of(" ", true)));

    // when / then
    assertThatThrownBy(config::validate)
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("workload.userTasks[0].variables variable name must not be blank");
  }

  @Test
  void shouldRejectNullUserTaskEntry() {
    // given
    final var config =
        new WorkloadConfig(
            new WorkloadConfig.RuntimeConfig("camunda/camunda:8.8.0"),
            new WorkloadConfig.ResourcesConfig("resources", "invoice", null),
            new WorkloadConfig.WorkloadSettings(
                1,
                0,
                Map.of(),
                List.of(),
                Collections.<WorkloadConfig.UserTaskConfig>singletonList(null)),
            new WorkloadConfig.OutputConfig("build/output"));

    // when / then
    assertThatThrownBy(config::validate)
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("workload.userTasks[0] must not be null");
  }

  private static WorkloadConfig configWithMessage(final WorkloadConfig.MessageConfig message) {
    return new WorkloadConfig(
        new WorkloadConfig.RuntimeConfig("camunda/camunda:8.8.0"),
        new WorkloadConfig.ResourcesConfig("resources", "invoice", "payload.json"),
        new WorkloadConfig.WorkloadSettings(1, 0, Map.of(), List.of(message)),
        new WorkloadConfig.OutputConfig("build/output"));
  }

  private static WorkloadConfig configWithUserTask(final WorkloadConfig.UserTaskConfig userTask) {
    return new WorkloadConfig(
        new WorkloadConfig.RuntimeConfig("camunda/camunda:8.8.0"),
        new WorkloadConfig.ResourcesConfig("resources", "invoice", null),
        new WorkloadConfig.WorkloadSettings(1, 0, Map.of(), List.of(), List.of(userTask)),
        new WorkloadConfig.OutputConfig("build/output"));
  }
}
