package io.zell.cwg.bpmn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BpmnAnalyzerTest {

  @TempDir private Path tempDir;

  @Test
  void shouldReportStaticBpmnMetadata() throws Exception {
    // given
    final var bpmnFile = tempDir.resolve("invoice.bpmn");
    Files.writeString(
        bpmnFile,
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
            xmlns:zeebe="http://camunda.org/schema/zeebe/1.0"
            targetNamespace="http://camunda.org/schema/1.0/bpmn">
          <bpmn:message id="Message_Invoice" name="invoice-received" />
          <bpmn:process id="invoice" isExecutable="true">
            <bpmn:startEvent id="start">
              <bpmn:messageEventDefinition id="MessageEvent_Invoice" messageRef="Message_Invoice" />
            </bpmn:startEvent>
            <bpmn:serviceTask id="charge_card" name="Charge card">
              <bpmn:extensionElements>
                <zeebe:taskDefinition type="charge-card" />
              </bpmn:extensionElements>
            </bpmn:serviceTask>
            <bpmn:serviceTask id="dynamic_worker">
              <bpmn:extensionElements>
                <zeebe:taskDefinition type="= jobType" />
              </bpmn:extensionElements>
            </bpmn:serviceTask>
            <bpmn:businessRuleTask id="decide_invoice">
              <bpmn:extensionElements>
                <zeebe:calledDecision decisionId="invoiceDecision" />
              </bpmn:extensionElements>
            </bpmn:businessRuleTask>
            <bpmn:callActivity id="call_subprocess" name="Call subprocess">
              <bpmn:extensionElements>
                <zeebe:calledElement processId="subprocess" />
              </bpmn:extensionElements>
            </bpmn:callActivity>
          </bpmn:process>
        </bpmn:definitions>
        """);

    // when
    final var analysis = new BpmnAnalyzer().analyze(bpmnFile);

    // then
    assertThat(analysis.processIds()).containsExactly("invoice");
    assertThat(analysis.staticJobTypes())
        .extracting(BpmnAnalysis.StaticJobType::elementId, BpmnAnalysis.StaticJobType::type)
        .containsExactly(tuple("charge_card", "charge-card"));
    assertThat(analysis.callActivities())
        .extracting(BpmnAnalysis.CallActivity::elementId, BpmnAnalysis.CallActivity::calledProcessId)
        .containsExactly(tuple("call_subprocess", "subprocess"));
    assertThat(analysis.messageReferences())
        .extracting(BpmnAnalysis.MessageReference::elementId, BpmnAnalysis.MessageReference::messageRef)
        .containsExactly(tuple("start", "Message_Invoice"));
    assertThat(analysis.dmnReferences())
        .extracting(BpmnAnalysis.DmnReference::elementId, BpmnAnalysis.DmnReference::decisionId)
        .containsExactly(tuple("decide_invoice", "invoiceDecision"));
  }
}
