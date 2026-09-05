package io.zell.cwg.workload;

import io.zell.cwg.config.ConfigException;
import io.zell.cwg.config.WorkloadConfig.MessageConfig;
import java.util.Map;

final class MessageCorrelationKeyResolver {

  private MessageCorrelationKeyResolver() {}

  static String resolve(
      final MessageConfig message, final Map<String, Object> processStartVariables) {
    if (message.correlationKey() != null && !message.correlationKey().isBlank()) {
      return message.correlationKey();
    }

    final var expression = message.correlationKeyExpression().strip();
    final var variablePath = expression.startsWith("=") ? expression.substring(1) : expression;
    final var resolved = resolvePath(processStartVariables, variablePath);
    if (resolved == null) {
      throw new ConfigException(
          "Message correlation key expression '%s' for message '%s' did not resolve"
              .formatted(message.correlationKeyExpression(), message.name()));
    }
    return String.valueOf(resolved);
  }

  private static Object resolvePath(final Map<String, Object> variables, final String path) {
    Object current = variables;
    for (final var segment : path.split("\\.")) {
      if (segment.isBlank() || !(current instanceof Map<?, ?> currentMap)) {
        return null;
      }
      current = currentMap.get(segment);
    }
    return current;
  }
}
