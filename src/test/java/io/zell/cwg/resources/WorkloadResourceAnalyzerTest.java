package io.zell.cwg.resources;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WorkloadResourceAnalyzerTest {

  @TempDir private Path tempDir;

  @Test
  void shouldAnalyzeCallActivitiesAcrossMultipleBpmnResources() throws Exception {
    // given
    Files.writeString(
        tempDir.resolve("parent.bpmn"),
        """
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
            xmlns:zeebe="http://camunda.org/schema/zeebe/1.0">
          <process id="parent">
            <callActivity id="call_child">
              <extensionElements>
                <zeebe:calledElement processId="child" />
              </extensionElements>
            </callActivity>
          </process>
        </definitions>
        """);
    Files.writeString(
        tempDir.resolve("child.bpmn"),
        """
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
          <process id="child" />
        </definitions>
        """);

    // when
    final var analysis = new WorkloadResourceAnalyzer().analyze(tempDir);

    // then
    assertThat(analysis.processIds()).containsExactly("child", "parent");
    assertThat(analysis.callActivities())
        .extracting(callActivity -> callActivity.calledProcessId())
        .containsExactly("child");
  }
}
