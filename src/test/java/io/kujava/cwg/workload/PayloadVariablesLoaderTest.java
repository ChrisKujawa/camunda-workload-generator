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
package io.kujava.cwg.workload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.kujava.cwg.config.ConfigException;
import io.kujava.cwg.config.WorkloadConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PayloadVariablesLoaderTest {

  @TempDir private Path tempDir;

  @Test
  void shouldLoadPayloadRelativeToResourcesDirectory() throws Exception {
    // given
    final var resources = tempDir.resolve("resources");
    Files.createDirectories(resources.resolve("payloads"));
    Files.writeString(
        resources.resolve("payloads/start.json"),
        """
        {
          "customer": {
            "id": "C-123"
          },
          "items": ["a", "b"],
          "amount": 42,
          "vip": true
        }
        """);
    final var config = config(resources, "payloads/start.json");

    // when
    final var variables = new PayloadVariablesLoader().load(config);

    // then
    assertThat(variables)
        .containsEntry("items", List.of("a", "b"))
        .containsEntry("amount", 42)
        .containsEntry("vip", true);
    assertThat(variables.get("customer")).isEqualTo(Map.of("id", "C-123"));
  }

  @Test
  void shouldReturnEmptyVariablesWithoutPayload() throws Exception {
    // when
    final var variables = new PayloadVariablesLoader().load(config(tempDir, null));

    // then
    assertThat(variables).isEmpty();
  }

  @Test
  void shouldRejectMissingPayloadFile() {
    // when / then
    assertThatThrownBy(() -> new PayloadVariablesLoader().load(config(tempDir, "missing.json")))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("Payload file does not exist");
  }

  @Test
  void shouldRejectInvalidPayloadJson() throws Exception {
    // given
    Files.writeString(tempDir.resolve("payload.json"), "{");

    // when / then
    assertThatThrownBy(() -> new PayloadVariablesLoader().load(config(tempDir, "payload.json")))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("Invalid payload JSON");
  }

  @Test
  void shouldRejectEmptyPayloadJson() throws Exception {
    // given
    Files.writeString(tempDir.resolve("payload.json"), "");

    // when / then
    assertThatThrownBy(() -> new PayloadVariablesLoader().load(config(tempDir, "payload.json")))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("Payload file must contain a JSON object");
  }

  @Test
  void shouldRejectUnreadablePayloadFile() throws Exception {
    // given
    final var payload = tempDir.resolve("payload.json");
    Files.writeString(payload, "{}");
    assumeTrue(Files.getFileAttributeView(payload, PosixFileAttributeView.class) != null);
    Files.setPosixFilePermissions(payload, Set.of());

    try {
      // when / then
      assertThatThrownBy(() -> new PayloadVariablesLoader().load(config(tempDir, "payload.json")))
          .isInstanceOf(ConfigException.class)
          .hasMessageContaining("Failed to read payload file");
    } finally {
      Files.setPosixFilePermissions(
          payload,
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.GROUP_READ,
              PosixFilePermission.OTHERS_READ));
    }
  }

  @Test
  void shouldRejectNonObjectPayloadJson() throws Exception {
    // given
    Files.writeString(tempDir.resolve("payload.json"), "[]");

    // when / then
    assertThatThrownBy(() -> new PayloadVariablesLoader().load(config(tempDir, "payload.json")))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("Payload file must contain a JSON object");
  }

  private static WorkloadConfig config(final Path resources, final String payload) {
    return new WorkloadConfig(
        new WorkloadConfig.RuntimeConfig("camunda/camunda:8.8.0"),
        new WorkloadConfig.ResourcesConfig(resources.toString(), "invoice", payload),
        new WorkloadConfig.WorkloadSettings(1, 0, Map.of(), List.of()),
        new WorkloadConfig.OutputConfig("build/output"));
  }
}
