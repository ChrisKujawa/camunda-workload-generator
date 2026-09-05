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
              YAMLFactory.builder().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER).build())
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
