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
package io.kujava.cwg.examples;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kujava.cwg.config.ConfigLoader;
import io.kujava.cwg.config.ConfigOverrides;
import io.kujava.cwg.generation.RuntimeWorkloadGenerator;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("docker")
@Testcontainers(disabledWithoutDocker = true)
final class RealisticExampleDockerTest {

  private static final Path CONFIG = Path.of("examples/realistic/workload.yaml");

  @TempDir private Path tempDir;

  @Test
  void shouldGenerateRealisticExampleArtifacts() throws Exception {
    // given
    final var output = tempDir.resolve("realistic-output");
    final var config =
        ConfigLoader.load(
            CONFIG, new ConfigOverrides(null, null, null, null, null, null, output.toString()));

    // when
    final var result = new RuntimeWorkloadGenerator().generate(config);

    // then
    assertThat(result.deployedResources()).isEqualTo(4);
    assertThat(result.manifestPath()).exists();
    assertThat(result.reportPath()).exists();
    assertThat(output.resolve("zeebe-data")).isDirectory();
    try (final var zeebeDataFiles = Files.walk(output.resolve("zeebe-data"))) {
      assertThat(zeebeDataFiles.filter(Files::isRegularFile).count()).isPositive();
    }

    final var report = new ObjectMapper().readTree(result.reportPath().toFile());
    assertThat(report.get("workload").get("startedInstances").asLong()).isEqualTo(5);
    assertThat(report.get("workload").get("completedInstances").asLong()).isZero();
    assertThat(report.get("workload").get("activeInstances").asLong()).isEqualTo(5);
    assertThat(report.get("completedJobs").isEmpty()).isTrue();
    assertThat(report.get("completedUserTasks").isEmpty()).isTrue();
    assertThat(report.get("zeebeData").get("files").asLong()).isPositive();
    assertThat(report.get("secondaryStorage").get("status").asText()).isEqualTo("skipped");
  }
}
