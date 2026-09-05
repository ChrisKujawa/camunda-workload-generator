package io.zell.cwg.deployment;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.DeployResourceCommandStep1.DeployResourceCommandStep2;
import io.zell.cwg.resources.ResourceFile;
import java.util.List;

public final class ZeebeResourceDeployment implements ResourceDeployment {

  @Override
  public DeploymentResult deploy(final String gatewayAddress, final List<ResourceFile> resources) {
    if (resources.isEmpty()) {
      return new DeploymentResult(resources);
    }

    try (final var client =
        ZeebeClient.newClientBuilder().gatewayAddress(gatewayAddress).usePlaintext().build()) {
      DeployResourceCommandStep2 command = null;
      for (final var resource : resources) {
        if (command == null) {
          command = client.newDeployResourceCommand().addResourceFile(resource.path().toString());
        } else {
          command = command.addResourceFile(resource.path().toString());
        }
      }
      command.send().join();
      return new DeploymentResult(resources);
    }
  }
}
