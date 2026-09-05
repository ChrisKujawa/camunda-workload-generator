package io.zell.cwg.workload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zell.cwg.config.ConfigException;
import io.zell.cwg.config.WorkloadConfig;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class MessageCorrelationKeyResolverTest {

  @Test
  void shouldUseStaticCorrelationKey() {
    // given
    final var message =
        new WorkloadConfig.MessageConfig("payment-received", "order-1", null, Map.of(), null);

    // when / then
    assertThat(MessageCorrelationKeyResolver.resolve(message, Map.of("orderId", "ignored")))
        .isEqualTo("order-1");
  }

  @Test
  void shouldResolveCorrelationKeyExpressionFromPayloadVariables() {
    // given
    final var message =
        new WorkloadConfig.MessageConfig(
            "payment-received", null, "=customer.orderId", Map.of(), null);

    // when / then
    assertThat(
            MessageCorrelationKeyResolver.resolve(
                message, Map.of("customer", Map.of("orderId", "order-1"))))
        .isEqualTo("order-1");
  }

  @Test
  void shouldResolveCorrelationKeyExpressionWithWhitespaceAfterEquals() {
    // given
    final var message =
        new WorkloadConfig.MessageConfig(
            "payment-received", null, "= customer.orderId", Map.of(), null);

    // when / then
    assertThat(
            MessageCorrelationKeyResolver.resolve(
                message, Map.of("customer", Map.of("orderId", "order-1"))))
        .isEqualTo("order-1");
  }

  @Test
  void shouldRejectUnresolvedCorrelationKeyExpression() {
    // given
    final var message =
        new WorkloadConfig.MessageConfig("payment-received", null, "=orderId", Map.of(), null);

    // when / then
    assertThatThrownBy(() -> MessageCorrelationKeyResolver.resolve(message, Map.of()))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("did not resolve");
  }

  @Test
  void shouldRejectCorrelationKeyExpressionWithTrailingBlankPathSegment() {
    // given
    final var message =
        new WorkloadConfig.MessageConfig("payment-received", null, "=orderId.", Map.of(), null);

    // when / then
    assertThatThrownBy(
            () -> MessageCorrelationKeyResolver.resolve(message, Map.of("orderId", "order-1")))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("must reference a payload variable");
  }

  @Test
  void shouldRejectProgrammaticMessageWithoutCorrelationKeyExpression() {
    // given
    final var message =
        new WorkloadConfig.MessageConfig("payment-received", null, null, Map.of(), null);

    // when / then
    assertThatThrownBy(() -> MessageCorrelationKeyResolver.resolve(message, Map.of()))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("must reference a payload variable");
  }
}
