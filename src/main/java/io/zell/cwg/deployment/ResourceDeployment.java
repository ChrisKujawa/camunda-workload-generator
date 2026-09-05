package io.zell.cwg.deployment;

import io.zell.cwg.resources.ResourceFile;
import java.util.List;

@FunctionalInterface
public interface ResourceDeployment {

  DeploymentResult deploy(String gatewayAddress, List<ResourceFile> resources);
}
