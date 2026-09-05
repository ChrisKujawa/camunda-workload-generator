package io.zell.cwg.examples;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zell.cwg.config.ConfigLoader;
import io.zell.cwg.config.ConfigOverrides;
import io.zell.cwg.generation.RuntimeWorkloadGenerator;
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
    assertThat(report.get("workload").get("completedInstances").asLong()).isEqualTo(3);
    assertThat(report.get("workload").get("activeInstances").asLong()).isEqualTo(2);
    assertThat(report.get("completedJobs").get("customer_notification").asLong()).isEqualTo(3);
    assertThat(report.get("completedJobs").get("extract_data_from_document").asLong())
        .isEqualTo(3);
    assertThat(report.get("completedJobs").get("refunding").asLong()).isEqualTo(3);
    assertThat(report.get("completedUserTasks").get("decide_on_fraud_case").asLong())
        .isEqualTo(3);
    assertThat(report.get("zeebeData").get("files").asLong()).isPositive();
    assertThat(report.get("secondaryStorage").get("status").asText()).isEqualTo("skipped");
  }
}
