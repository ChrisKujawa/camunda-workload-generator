package io.zell.cwg.resources;

import java.util.List;

public record ResourceScanResult(
    List<ResourceFile> deployableResources,
    List<ResourceFile> payloadOrConfigResources,
    List<ResourceFile> otherResources) {

  public ResourceScanResult {
    deployableResources = List.copyOf(deployableResources);
    payloadOrConfigResources = List.copyOf(payloadOrConfigResources);
    otherResources = List.copyOf(otherResources);
  }

  public List<ResourceFile> bpmnResources() {
    return deployableResources.stream().filter(resource -> resource.type() == ResourceType.BPMN).toList();
  }
}
