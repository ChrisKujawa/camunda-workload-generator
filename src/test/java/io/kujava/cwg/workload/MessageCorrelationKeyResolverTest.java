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

import io.kujava.cwg.config.ConfigException;
import io.kujava.cwg.config.WorkloadConfig;
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
