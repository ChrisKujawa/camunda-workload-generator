package io.zell.cwg.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
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
        workload:
          startInstances: 10
        output:
          path: build/config-output
        """);

    // when
    final var config =
        ConfigLoader.load(
            configFile,
            new ConfigOverrides(
                "camunda/camunda:8.8.2", null, "order", null, 7, "build/cli-output"));

    // then
    assertThat(config.getRuntime().image()).isEqualTo("camunda/camunda:8.8.2");
    assertThat(config.getResources().directory()).isEqualTo("config-resources");
    assertThat(config.getResources().rootProcessId()).isEqualTo("order");
    assertThat(config.getWorkload().startInstances()).isEqualTo(10);
    assertThat(config.getWorkload().completeInstances()).isEqualTo(7);
    assertThat(config.getOutput().path()).isEqualTo("build/cli-output");
  }

  @Test
  void shouldUseDefaultsWhenConfigFileIsMissing() throws Exception {
    // when
    final var config = ConfigLoader.load(null, ConfigOverrides.none());

    // then
    assertThat(config.getRuntime().image()).isEqualTo("camunda/camunda:8.8.0");
    assertThat(config.getResources().directory()).isEqualTo("resources");
    assertThat(config.getWorkload().startInstances()).isEqualTo(1);
    assertThat(config.getWorkload().completeInstances()).isZero();
    assertThat(config.getOutput().path()).isEqualTo("build/camunda-workload-generator");
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
}
