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
                    new WorkloadSettings(3, 2, Map.of()),
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
            <bpmn:exclusiveGateway id="gateway" default="rejected" />
            <bpmn:sequenceFlow id="approved" sourceRef="gateway" targetRef="approved_end">
              <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">=approved</bpmn:conditionExpression>
            </bpmn:sequenceFlow>
            <bpmn:sequenceFlow id="rejected" sourceRef="gateway" targetRef="rejected_end" />
            <bpmn:endEvent id="approved_end" />
            <bpmn:endEvent id="rejected_end" />
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
                        1, 1, Map.of("approve-request", Map.<String, Object>of("approved", true))),
                    new OutputConfig(output.toString())));

    // then
    final var report = new ObjectMapper().readTree(result.reportPath().toFile());
    assertThat(report.get("workload").get("completedInstances").asLong()).isEqualTo(1);
    assertThat(report.get("completedJobs").get("approve-request").asLong()).isEqualTo(1);
    assertThat(report.get("appliedWorkerOutputs").get("approve-request").asLong()).isEqualTo(1);
  }
}
