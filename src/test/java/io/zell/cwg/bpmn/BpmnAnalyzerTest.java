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
        .extracting(
            BpmnAnalysis.StaticJobType::elementId,
            BpmnAnalysis.StaticJobType::type,
            BpmnAnalysis.StaticJobType::processId)
        .containsExactly(tuple("charge_card", "charge-card", "invoice"));
    assertThat(analysis.callActivities())
        .extracting(
            BpmnAnalysis.CallActivity::elementId,
            BpmnAnalysis.CallActivity::calledProcessId,
            BpmnAnalysis.CallActivity::processId)
        .containsExactly(tuple("call_subprocess", "subprocess", "invoice"));
    assertThat(analysis.messageReferences())
        .extracting(
            BpmnAnalysis.MessageReference::elementId,
            BpmnAnalysis.MessageReference::messageRef,
            BpmnAnalysis.MessageReference::processId)
        .containsExactly(tuple("start", "Message_Invoice", "invoice"));
    assertThat(analysis.dmnReferences())
        .extracting(
            BpmnAnalysis.DmnReference::elementId,
            BpmnAnalysis.DmnReference::decisionId,
            BpmnAnalysis.DmnReference::processId)
        .containsExactly(tuple("decide_invoice", "invoiceDecision", "invoice"));
  }

  @Test
  void shouldSkipProcessPathsWithoutProcessId() throws Exception {
    // given
    final var bpmnFile = tempDir.resolve("blank-process.bpmn");
    Files.writeString(
        bpmnFile,
        """
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
          <process>
            <startEvent id="blank_start" />
          </process>
          <process id="invoice">
            <startEvent id="start" />
          </process>
        </definitions>
        """);

    // when
    final var analysis = new BpmnAnalyzer().analyze(bpmnFile);

    // then
    assertThat(analysis.processIds()).containsExactly("invoice");
    assertThat(analysis.processPaths())
        .extracting(BpmnAnalysis.ProcessPath::processId)
        .containsExactly("invoice");
  }

  @Test
  void shouldHandleMetadataWithoutOwningProcess() throws Exception {
    // given
    final var bpmnFile = tempDir.resolve("metadata-without-process.bpmn");
    Files.writeString(
        bpmnFile,
        """
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
            xmlns:zeebe="http://camunda.org/schema/zeebe/1.0">
          <extensionElements>
            <zeebe:taskDefinition type="orphan-worker" />
            <zeebe:calledDecision decisionId="orphanDecision" />
          </extensionElements>
          <messageEventDefinition messageRef="orphan-message" />
          <process id="invoice">
            <startEvent id="start" />
          </process>
        </definitions>
        """);

    // when
    final var analysis = new BpmnAnalyzer().analyze(bpmnFile);

    // then
    assertThat(analysis.staticJobTypes())
        .extracting(BpmnAnalysis.StaticJobType::type, BpmnAnalysis.StaticJobType::processId)
        .containsExactly(tuple("orphan-worker", ""));
    assertThat(analysis.messageReferences())
        .extracting(BpmnAnalysis.MessageReference::messageRef, BpmnAnalysis.MessageReference::processId)
        .containsExactly(tuple("orphan-message", ""));
    assertThat(analysis.dmnReferences())
        .extracting(BpmnAnalysis.DmnReference::decisionId, BpmnAnalysis.DmnReference::processId)
        .containsExactly(tuple("orphanDecision", ""));
  }

  @Test
  void shouldEstimateStaticHappyPathFlowNodeInstances() throws Exception {
    // given
    final var bpmnFile = tempDir.resolve("happy-path.bpmn");
    Files.writeString(
        bpmnFile,
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
            xmlns:zeebe="http://camunda.org/schema/zeebe/1.0">
          <bpmn:process id="bankDisputeHandling" isExecutable="true">
            <bpmn:startEvent id="start">
              <bpmn:outgoing>flow_1</bpmn:outgoing>
            </bpmn:startEvent>
            <bpmn:serviceTask id="register_dispute" name="Register dispute">
              <bpmn:extensionElements>
                <zeebe:taskDefinition type="dispute-registration" />
              </bpmn:extensionElements>
              <bpmn:incoming>flow_1</bpmn:incoming>
              <bpmn:outgoing>flow_2</bpmn:outgoing>
            </bpmn:serviceTask>
            <bpmn:exclusiveGateway id="refund_gateway" default="flow_3">
              <bpmn:incoming>flow_2</bpmn:incoming>
              <bpmn:outgoing>flow_3</bpmn:outgoing>
              <bpmn:outgoing>flow_4</bpmn:outgoing>
            </bpmn:exclusiveGateway>
            <bpmn:callActivity id="initiate_refund" name="Initiate refund">
              <bpmn:extensionElements>
                <zeebe:calledElement processId="refundingProcess" />
              </bpmn:extensionElements>
              <bpmn:incoming>flow_3</bpmn:incoming>
              <bpmn:outgoing>flow_5</bpmn:outgoing>
            </bpmn:callActivity>
            <bpmn:endEvent id="refund_end">
              <bpmn:incoming>flow_5</bpmn:incoming>
            </bpmn:endEvent>
            <bpmn:endEvent id="no_refund_end">
              <bpmn:incoming>flow_4</bpmn:incoming>
            </bpmn:endEvent>
            <bpmn:sequenceFlow id="flow_1" sourceRef="start" targetRef="register_dispute" />
            <bpmn:sequenceFlow id="flow_2" sourceRef="register_dispute" targetRef="refund_gateway" />
            <bpmn:sequenceFlow id="flow_3" sourceRef="refund_gateway" targetRef="initiate_refund" />
            <bpmn:sequenceFlow id="flow_4" sourceRef="refund_gateway" targetRef="no_refund_end" />
            <bpmn:sequenceFlow id="flow_5" sourceRef="initiate_refund" targetRef="refund_end" />
          </bpmn:process>
        </bpmn:definitions>
        """);

    // when
    final var analysis = new BpmnAnalyzer().analyze(bpmnFile);

    // then
    assertThat(analysis.processPaths()).hasSize(1);
    final var processPath = analysis.processPaths().get(0);
    assertThat(processPath.processId()).isEqualTo("bankDisputeHandling");
    assertThat(processPath.completePath()).isTrue();
    assertThat(processPath.flowNodeInstances()).isEqualTo(5);
    assertThat(processPath.happyPath())
        .extracting(
            BpmnAnalysis.HappyPathNode::elementId,
            BpmnAnalysis.HappyPathNode::elementType,
            BpmnAnalysis.HappyPathNode::jobType)
        .containsExactly(
            tuple("start", "startEvent", null),
            tuple("register_dispute", "serviceTask", "dispute-registration"),
            tuple("refund_gateway", "exclusiveGateway", null),
            tuple("initiate_refund", "callActivity", null),
            tuple("refund_end", "endEvent", null));
  }

  @Test
  void shouldEstimateLoadTestStyleTenTaskProcess() throws Exception {
    // given
    final var bpmnFile = tempDir.resolve("ten-tasks.bpmn");
    Files.writeString(
        bpmnFile,
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
            xmlns:zeebe="http://camunda.org/schema/zeebe/1.0">
          <bpmn:process id="benchmark" isExecutable="true">
            <bpmn:startEvent id="start"><bpmn:outgoing>flow_1</bpmn:outgoing></bpmn:startEvent>
            <bpmn:serviceTask id="task1"><bpmn:extensionElements><zeebe:taskDefinition type="benchmark-task" /></bpmn:extensionElements><bpmn:incoming>flow_1</bpmn:incoming><bpmn:outgoing>flow_2</bpmn:outgoing></bpmn:serviceTask>
            <bpmn:serviceTask id="task2"><bpmn:extensionElements><zeebe:taskDefinition type="benchmark-task" /></bpmn:extensionElements><bpmn:incoming>flow_2</bpmn:incoming><bpmn:outgoing>flow_3</bpmn:outgoing></bpmn:serviceTask>
            <bpmn:serviceTask id="task3"><bpmn:extensionElements><zeebe:taskDefinition type="benchmark-task" /></bpmn:extensionElements><bpmn:incoming>flow_3</bpmn:incoming><bpmn:outgoing>flow_4</bpmn:outgoing></bpmn:serviceTask>
            <bpmn:intermediateCatchEvent id="timer1"><bpmn:incoming>flow_4</bpmn:incoming><bpmn:outgoing>flow_5</bpmn:outgoing><bpmn:timerEventDefinition><bpmn:timeDuration>PT20M</bpmn:timeDuration></bpmn:timerEventDefinition></bpmn:intermediateCatchEvent>
            <bpmn:serviceTask id="task4"><bpmn:extensionElements><zeebe:taskDefinition type="benchmark-task" /></bpmn:extensionElements><bpmn:incoming>flow_5</bpmn:incoming><bpmn:outgoing>flow_6</bpmn:outgoing></bpmn:serviceTask>
            <bpmn:serviceTask id="task5"><bpmn:extensionElements><zeebe:taskDefinition type="benchmark-task" /></bpmn:extensionElements><bpmn:incoming>flow_6</bpmn:incoming><bpmn:outgoing>flow_7</bpmn:outgoing></bpmn:serviceTask>
            <bpmn:serviceTask id="task6"><bpmn:extensionElements><zeebe:taskDefinition type="benchmark-task" /></bpmn:extensionElements><bpmn:incoming>flow_7</bpmn:incoming><bpmn:outgoing>flow_8</bpmn:outgoing></bpmn:serviceTask>
            <bpmn:intermediateCatchEvent id="timer2"><bpmn:incoming>flow_8</bpmn:incoming><bpmn:outgoing>flow_9</bpmn:outgoing><bpmn:timerEventDefinition><bpmn:timeDuration>PT20M</bpmn:timeDuration></bpmn:timerEventDefinition></bpmn:intermediateCatchEvent>
            <bpmn:serviceTask id="task7"><bpmn:extensionElements><zeebe:taskDefinition type="benchmark-task" /></bpmn:extensionElements><bpmn:incoming>flow_9</bpmn:incoming><bpmn:outgoing>flow_10</bpmn:outgoing></bpmn:serviceTask>
            <bpmn:serviceTask id="task8"><bpmn:extensionElements><zeebe:taskDefinition type="benchmark-task" /></bpmn:extensionElements><bpmn:incoming>flow_10</bpmn:incoming><bpmn:outgoing>flow_11</bpmn:outgoing></bpmn:serviceTask>
            <bpmn:serviceTask id="task9"><bpmn:extensionElements><zeebe:taskDefinition type="benchmark-task" /></bpmn:extensionElements><bpmn:incoming>flow_11</bpmn:incoming><bpmn:outgoing>flow_12</bpmn:outgoing></bpmn:serviceTask>
            <bpmn:serviceTask id="task10"><bpmn:extensionElements><zeebe:taskDefinition type="benchmark-task" /></bpmn:extensionElements><bpmn:incoming>flow_12</bpmn:incoming><bpmn:outgoing>flow_13</bpmn:outgoing></bpmn:serviceTask>
            <bpmn:endEvent id="end"><bpmn:incoming>flow_13</bpmn:incoming></bpmn:endEvent>
            <bpmn:sequenceFlow id="flow_1" sourceRef="start" targetRef="task1" />
            <bpmn:sequenceFlow id="flow_2" sourceRef="task1" targetRef="task2" />
            <bpmn:sequenceFlow id="flow_3" sourceRef="task2" targetRef="task3" />
            <bpmn:sequenceFlow id="flow_4" sourceRef="task3" targetRef="timer1" />
            <bpmn:sequenceFlow id="flow_5" sourceRef="timer1" targetRef="task4" />
            <bpmn:sequenceFlow id="flow_6" sourceRef="task4" targetRef="task5" />
            <bpmn:sequenceFlow id="flow_7" sourceRef="task5" targetRef="task6" />
            <bpmn:sequenceFlow id="flow_8" sourceRef="task6" targetRef="timer2" />
            <bpmn:sequenceFlow id="flow_9" sourceRef="timer2" targetRef="task7" />
            <bpmn:sequenceFlow id="flow_10" sourceRef="task7" targetRef="task8" />
            <bpmn:sequenceFlow id="flow_11" sourceRef="task8" targetRef="task9" />
            <bpmn:sequenceFlow id="flow_12" sourceRef="task9" targetRef="task10" />
            <bpmn:sequenceFlow id="flow_13" sourceRef="task10" targetRef="end" />
          </bpmn:process>
        </bpmn:definitions>
        """);

    // when
    final var analysis = new BpmnAnalyzer().analyze(bpmnFile);

    // then
    final var processPath = analysis.processPaths().get(0);
    assertThat(processPath.processId()).isEqualTo("benchmark");
    assertThat(processPath.completePath()).isTrue();
    assertThat(processPath.flowNodeInstances()).isEqualTo(14);
    assertThat(analysis.staticJobTypes())
        .extracting(BpmnAnalysis.StaticJobType::type)
        .containsOnly("benchmark-task");
  }
}
