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
package io.kujava.cwg.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ManagedCamundaRuntimeTest {

  @Test
  void shouldUseIpv4LoopbackForLocalhostMappedPorts() {
    // given
    final var testcontainersHost = "localhost";

    // when
    final var host = ManagedCamundaRuntime.mappedHost(testcontainersHost);

    // then
    assertThat(host).isEqualTo("127.0.0.1");
  }

  @Test
  void shouldKeepNonLocalhostMappedPorts() {
    // given
    final var testcontainersHost = "docker.internal";

    // when
    final var host = ManagedCamundaRuntime.mappedHost(testcontainersHost);

    // then
    assertThat(host).isEqualTo("docker.internal");
  }

  @Test
  void shouldTailStartupLogs() {
    // given
    final var logs =
        """
        one
        two
        three
        """;

    // when
    final var tail = ManagedCamundaRuntime.tail(logs, 2);

    // then
    assertThat(tail).isEqualTo("two%nthree".formatted());
  }

  @Test
  void shouldStripCopiedArchiveTopLevelDirectory() {
    // given
    final var entryName = "data/partitions/1/runtime/state/data.txt";

    // when
    final var relativePath = ManagedCamundaRuntime.stripTopLevelDirectory(entryName);

    // then
    assertThat(relativePath).isEqualTo(Path.of("partitions/1/runtime/state/data.txt"));
  }

  @Test
  void shouldPreserveTraversalWhenStrippingCopiedArchiveTopLevelDirectory() {
    // given
    final var entryName = "data/../../outside";

    // when
    final var relativePath = ManagedCamundaRuntime.stripTopLevelDirectory(entryName);

    // then
    assertThat(relativePath).isEqualTo(Path.of("../../outside"));
  }
}
