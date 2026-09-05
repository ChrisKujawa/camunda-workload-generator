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
package io.kujava.cwg.resources;

import io.kujava.cwg.bpmn.BpmnAnalysis;
import io.kujava.cwg.bpmn.BpmnAnalyzer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;

public final class WorkloadResourceAnalyzer {

  private final ResourceScanner scanner;
  private final BpmnAnalyzer bpmnAnalyzer;

  public WorkloadResourceAnalyzer() {
    this(new ResourceScanner(), new BpmnAnalyzer());
  }

  WorkloadResourceAnalyzer(final ResourceScanner scanner, final BpmnAnalyzer bpmnAnalyzer) {
    this.scanner = scanner;
    this.bpmnAnalyzer = bpmnAnalyzer;
  }

  public WorkloadResourceAnalysis analyze(final Path resourcesDirectory) throws IOException {
    final var scan = scanner.scan(resourcesDirectory);
    final var bpmnAnalyses = new ArrayList<BpmnAnalysis>();
    for (final var resource : scan.bpmnResources()) {
      bpmnAnalyses.add(bpmnAnalyzer.analyze(resource.path()));
    }
    return new WorkloadResourceAnalysis(scan, bpmnAnalyses);
  }
}
