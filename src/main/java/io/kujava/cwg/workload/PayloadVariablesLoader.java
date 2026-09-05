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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kujava.cwg.config.ConfigException;
import io.kujava.cwg.config.WorkloadConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class PayloadVariablesLoader {

  private static final ObjectMapper JSON =
      new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
  private static final TypeReference<Map<String, Object>> VARIABLES_TYPE = new TypeReference<>() {};

  public Map<String, Object> load(final WorkloadConfig config) throws IOException {
    final var payload = config.getResources().payload();
    if (payload == null) {
      return Map.of();
    }

    final var payloadPath = resolve(config);
    if (!Files.isRegularFile(payloadPath)) {
      throw new ConfigException("Payload file does not exist: %s".formatted(payloadPath));
    }

    final JsonNode payloadJson;
    try {
      payloadJson = JSON.readTree(payloadPath.toFile());
    } catch (final JsonProcessingException e) {
      throw new ConfigException(
          "Invalid payload JSON in %s: %s".formatted(payloadPath, e.getOriginalMessage()));
    } catch (final IOException e) {
      throw new ConfigException(
          "Failed to read payload file %s: %s".formatted(payloadPath, e.getMessage()));
    }

    if (payloadJson == null || !payloadJson.isObject()) {
      throw new ConfigException(
          "Payload file must contain a JSON object: %s".formatted(payloadPath));
    }
    return JSON.convertValue(payloadJson, VARIABLES_TYPE);
  }

  public static Path resolve(final WorkloadConfig config) {
    final var payloadPath = Path.of(config.getResources().payload());
    if (payloadPath.isAbsolute()) {
      return payloadPath.normalize();
    }
    return Path.of(config.getResources().directory()).resolve(payloadPath).normalize();
  }
}
