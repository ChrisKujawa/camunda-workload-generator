package io.zell.cwg.examples;

import static org.assertj.core.api.Assertions.assertThat;

import io.zell.cwg.config.ConfigLoader;
import io.zell.cwg.config.ConfigOverrides;
import io.zell.cwg.resources.WorkloadResourceAnalyzer;
import io.zell.cwg.workload.PayloadVariablesLoader;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class RealisticExampleTest {

  private static final Path CONFIG = Path.of("examples/realistic/workload.yaml");

  @Test
  void shouldLoadAndAnalyzeRealisticExampleResources() throws Exception {
    // given
    final var config = ConfigLoader.load(CONFIG, ConfigOverrides.none());

    // when
    final var analysis =
        new WorkloadResourceAnalyzer().analyze(Path.of(config.getResources().directory()));
    final var payload = new PayloadVariablesLoader().load(config);

    // then
    assertThat(config.getResources().rootProcessId()).isEqualTo("bankDisputeHandling");
    assertThat(config.getResources().payload()).isEqualTo("payload.json");
    assertThat(analysis.processIds()).contains("bankDisputeHandling", "refundingProcess");
    assertThat(analysis.staticJobTypes())
        .extracting("type")
        .containsExactlyInAnyOrder(
            "customer_notification",
            "extract_data_from_document",
            "inform_about_failed_claim",
            "refunding");
    assertThat(analysis.dmnReferences())
        .extracting("decisionId")
        .contains("determine-fraud-rating-1dmmwcu");
    assertThat(analysis.userTasks()).extracting("elementName").containsExactly("Decide on fraud case");
    assertThat(analysis.callActivities()).extracting("calledProcessId").contains("refundingProcess");
    assertThat(payload).containsEntry("customer_claim_frequency", 1);
    assertThat(payload).containsEntry("vendor_claim_frequency", 5);
  }
}
