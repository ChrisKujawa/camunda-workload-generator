package io.zell.cwg.resources;

import io.zell.cwg.bpmn.BpmnAnalysis;
import io.zell.cwg.bpmn.BpmnAnalyzer;
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
