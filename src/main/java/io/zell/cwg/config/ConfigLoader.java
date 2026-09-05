package io.zell.cwg.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigLoader {

  private static final ObjectMapper YAML =
      new ObjectMapper(
              YAMLFactory.builder()
                  .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                  .build())
          .findAndRegisterModules()
          .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  private ConfigLoader() {}

  public static WorkloadConfig load(final Path configFile, final ConfigOverrides overrides)
      throws IOException {
    final var config = WorkloadConfig.defaults();

    if (configFile != null) {
      if (!Files.isRegularFile(configFile)) {
        throw new ConfigException("Config file does not exist: " + configFile);
      }

      final RawWorkloadConfig rawConfig;
      try {
        rawConfig = YAML.readValue(configFile.toFile(), RawWorkloadConfig.class);
      } catch (final UnrecognizedPropertyException e) {
        throw new ConfigException(
            "Invalid config file: unrecognized property '%s'".formatted(e.getPropertyName()));
      } catch (final JsonProcessingException e) {
        throw new ConfigException("Invalid config file: " + e.getOriginalMessage());
      }
      if (rawConfig != null) {
        config.merge(rawConfig);
      }
    }

    config.apply(overrides);
    config.validate();
    return config;
  }

  public static String toYaml(final WorkloadConfig config) throws IOException {
    return YAML.writeValueAsString(config);
  }
}
