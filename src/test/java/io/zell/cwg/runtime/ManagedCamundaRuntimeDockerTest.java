package io.zell.cwg.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zell.cwg.config.WorkloadConfig;
import io.zell.cwg.config.WorkloadConfig.OutputConfig;
import io.zell.cwg.config.WorkloadConfig.ResourcesConfig;
import io.zell.cwg.config.WorkloadConfig.RuntimeConfig;
import io.zell.cwg.config.WorkloadConfig.WorkloadSettings;
import io.zell.cwg.generation.RuntimeWorkloadGenerator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("docker")
@Testcontainers(disabledWithoutDocker = true)
final class ManagedCamundaRuntimeDockerTest {

  @TempDir private Path tempDir;

  @Test
  void shouldStartManagedRuntimeAndDeployResources() throws Exception {
    // given
    final var resources = tempDir.resolve("resources");
    Files.createDirectories(resources);
    Files.writeString(
        resources.resolve("invoice.bpmn"),
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
            xmlns:zeebe="http://camunda.org/schema/zeebe/1.0"
            id="definitions"
            targetNamespace="http://camunda.io/schema/bpmn">
          <bpmn:process id="invoice" isExecutable="true">
            <bpmn:startEvent id="start" />
            <bpmn:sequenceFlow id="flow1" sourceRef="start" targetRef="task" />
            <bpmn:serviceTask id="task">
              <bpmn:extensionElements>
                <zeebe:taskDefinition type="charge-card" />
              </bpmn:extensionElements>
            </bpmn:serviceTask>
            <bpmn:sequenceFlow id="flow2" sourceRef="task" targetRef="end" />
            <bpmn:endEvent id="end" />
          </bpmn:process>
        </bpmn:definitions>
        """);
    final var output = tempDir.resolve("output");

    // when
    final var result =
        new RuntimeWorkloadGenerator()
            .generate(
                new WorkloadConfig(
                    new RuntimeConfig("camunda/camunda:8.8.0"),
                    new ResourcesConfig(resources.toString(), "invoice", null),
                    new WorkloadSettings(3, 2, Map.of(), List.of()),
                    new OutputConfig(output.toString())));

    // then
    assertThat(result.deployedResources()).isEqualTo(1);
    assertThat(result.manifestPath()).exists();
    assertThat(result.reportPath()).exists();
    final var report = new ObjectMapper().readTree(result.reportPath().toFile());
    assertThat(report.get("workload").get("startedInstances").asLong()).isEqualTo(3);
    assertThat(report.get("workload").get("completedInstances").asLong()).isEqualTo(2);
    assertThat(report.get("workload").get("activeInstances").asLong()).isEqualTo(1);
    assertThat(report.get("completedJobs").get("charge-card").asLong()).isEqualTo(2);
  }

  @Test
  void shouldCompleteGatewayPathWithConfiguredWorkerOutputVariables() throws Exception {
    // given
    final var resources = tempDir.resolve("gateway-resources");
    Files.createDirectories(resources);
    Files.writeString(
        resources.resolve("approval.bpmn"),
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
            xmlns:zeebe="http://camunda.org/schema/zeebe/1.0"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            id="definitions"
            targetNamespace="http://camunda.io/schema/bpmn">
          <bpmn:process id="approval" isExecutable="true">
            <bpmn:startEvent id="start" />
            <bpmn:sequenceFlow id="flow1" sourceRef="start" targetRef="task" />
            <bpmn:serviceTask id="task">
              <bpmn:extensionElements>
                <zeebe:taskDefinition type="approve-request" />
              </bpmn:extensionElements>
            </bpmn:serviceTask>
            <bpmn:sequenceFlow id="flow2" sourceRef="task" targetRef="gateway" />
            <bpmn:exclusiveGateway id="gateway" />
            <bpmn:sequenceFlow id="approved" sourceRef="gateway" targetRef="approved_end">
              <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">=approved</bpmn:conditionExpression>
            </bpmn:sequenceFlow>
            <bpmn:endEvent id="approved_end" />
          </bpmn:process>
        </bpmn:definitions>
        """);
    final var output = tempDir.resolve("gateway-output");

    // when
    final var result =
        new RuntimeWorkloadGenerator()
            .generate(
                new WorkloadConfig(
                    new RuntimeConfig("camunda/camunda:8.8.0"),
                    new ResourcesConfig(resources.toString(), "approval", null),
                    new WorkloadSettings(
                        1,
                        1,
                        Map.of("approve-request", Map.<String, Object>of("approved", true)),
                        List.of()),
                    new OutputConfig(output.toString())));

    // then
    final var report = new ObjectMapper().readTree(result.reportPath().toFile());
    assertThat(report.get("workload").get("completedInstances").asLong()).isEqualTo(1);
    assertThat(report.get("completedJobs").get("approve-request").asLong()).isEqualTo(1);
    assertThat(report.get("appliedWorkerOutputs").get("approve-request").asLong()).isEqualTo(1);
  }

  @Test
  void shouldCompleteProcessWaitingForConfiguredMessage() throws Exception {
    // given
    final var resources = tempDir.resolve("message-resources");
    Files.createDirectories(resources);
    Files.writeString(resources.resolve("payload.json"), "{\"orderId\":\"order-1\"}");
    Files.writeString(
        resources.resolve("message.bpmn"),
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
            xmlns:zeebe="http://camunda.org/schema/zeebe/1.0"
            id="definitions"
            targetNamespace="http://camunda.io/schema/bpmn">
          <bpmn:message id="payment_message" name="payment-received" />
          <bpmn:process id="message-process" isExecutable="true">
            <bpmn:startEvent id="start" />
            <bpmn:sequenceFlow id="flow1" sourceRef="start" targetRef="wait_payment" />
            <bpmn:intermediateCatchEvent id="wait_payment">
              <bpmn:messageEventDefinition messageRef="payment_message">
                <bpmn:extensionElements>
                  <zeebe:subscription correlationKey="=orderId" />
                </bpmn:extensionElements>
              </bpmn:messageEventDefinition>
            </bpmn:intermediateCatchEvent>
            <bpmn:sequenceFlow id="flow2" sourceRef="wait_payment" targetRef="end" />
            <bpmn:endEvent id="end" />
          </bpmn:process>
        </bpmn:definitions>
        """);
    final var output = tempDir.resolve("message-output");

    // when
    final var result =
        new RuntimeWorkloadGenerator()
            .generate(
                new WorkloadConfig(
                    new RuntimeConfig("camunda/camunda:8.8.0"),
                    new ResourcesConfig(resources.toString(), "message-process", "payload.json"),
                    new WorkloadSettings(
                        1,
                        1,
                        Map.of(),
                        List.of(
                            new WorkloadConfig.MessageConfig(
                                "payment-received",
                                "order-1",
                                null,
                                Map.of("paid", true),
                                null))),
                    new OutputConfig(output.toString())));

    // then
    final var report = new ObjectMapper().readTree(result.reportPath().toFile());
    assertThat(report.get("workload").get("completedInstances").asLong()).isEqualTo(1);
    assertThat(report.get("publishedMessages").get("payment-received").asLong()).isEqualTo(1);
  }

  @Test
  void shouldCompleteConfiguredUserTask() throws Exception {
    // given
    final var resources = tempDir.resolve("user-task-resources");
    Files.createDirectories(resources);
    Files.writeString(
        resources.resolve("user-task.bpmn"),
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
            xmlns:zeebe="http://camunda.org/schema/zeebe/1.0"
            id="definitions"
            targetNamespace="http://camunda.io/schema/bpmn">
          <bpmn:process id="approval" isExecutable="true">
            <bpmn:startEvent id="start" />
            <bpmn:sequenceFlow id="flow1" sourceRef="start" targetRef="approve_invoice" />
            <bpmn:userTask id="approve_invoice" name="Approve invoice">
              <bpmn:extensionElements>
                <zeebe:userTask />
              </bpmn:extensionElements>
            </bpmn:userTask>
            <bpmn:sequenceFlow id="flow2" sourceRef="approve_invoice" targetRef="end" />
            <bpmn:endEvent id="end" />
          </bpmn:process>
        </bpmn:definitions>
        """);
    final var output = tempDir.resolve("user-task-output");

    // when
    final var result =
        new RuntimeWorkloadGenerator()
            .generate(
                new WorkloadConfig(
                    new RuntimeConfig("camunda/camunda:8.8.0"),
                    new ResourcesConfig(resources.toString(), "approval", null),
                    new WorkloadSettings(
                        1,
                        1,
                        Map.of(),
                        List.of(),
                        List.of(
                            new WorkloadConfig.UserTaskConfig(
                                null, "Approve invoice", Map.of("approved", true)))),
                    new OutputConfig(output.toString())));

    // then
    final var report = new ObjectMapper().readTree(result.reportPath().toFile());
    assertThat(report.get("workload").get("completedInstances").asLong()).isEqualTo(1);
    assertThat(report.get("completedUserTasks").get("approve_invoice").asLong()).isEqualTo(1);
  }
}
