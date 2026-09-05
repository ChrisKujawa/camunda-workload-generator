package io.zell.cwg.workload;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

final class WorkerOutputVariablesTest {

  @Test
  void shouldSelectConfiguredVariablesByJobType() {
    // given
    final var workerOutputs =
        Map.of(
            "charge-card",
            Map.<String, Object>of("approved", true, "riskScore", 12),
            "ship-goods",
            Map.<String, Object>of("shipped", true));

    // when / then
    assertThat(WorkerOutputVariables.forJobType("charge-card", workerOutputs))
        .containsEntry("approved", true)
        .containsEntry("riskScore", 12);
    assertThat(WorkerOutputVariables.forJobType("unknown", workerOutputs)).isEmpty();
  }
}
