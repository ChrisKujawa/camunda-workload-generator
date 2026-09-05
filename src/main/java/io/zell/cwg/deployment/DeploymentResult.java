package io.zell.cwg.deployment;

import io.zell.cwg.resources.ResourceFile;
import java.util.List;

public record DeploymentResult(List<ResourceFile> deployedResources) {

  public DeploymentResult {
    deployedResources = List.copyOf(deployedResources);
  }

  public int deployedResourceCount() {
    return deployedResources.size();
  }
}
