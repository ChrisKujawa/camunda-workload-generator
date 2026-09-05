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

    final var variablePath =
        io.zell.cwg.config.WorkloadConfig.correlationKeyExpressionPath(
            message.correlationKeyExpression());
    if (variablePath.isBlank() || hasBlankPathSegment(variablePath)) {
      throw new ConfigException(
          "Message correlation key expression for message '%s' must reference a payload variable"
              .formatted(message.name()));
    }
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
    for (final var segment : path.split("\\.", -1)) {
      if (segment.isBlank() || !(current instanceof Map<?, ?> currentMap)) {
        return null;
      }
      current = currentMap.get(segment);
    }
    return current;
  }

  private static boolean hasBlankPathSegment(final String path) {
    for (final var segment : path.split("\\.", -1)) {
      if (segment.isBlank()) {
        return true;
      }
    }
    return false;
  }
}
