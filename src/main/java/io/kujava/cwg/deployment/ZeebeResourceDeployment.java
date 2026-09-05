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
package io.kujava.cwg.deployment;

import io.camunda.client.api.command.DeployResourceCommandStep1.DeployResourceCommandStep2;
import io.kujava.cwg.resources.ResourceFile;
import io.kujava.cwg.runtime.CamundaClients;
import java.util.List;

public final class ZeebeResourceDeployment implements ResourceDeployment {

  @Override
  public DeploymentResult deploy(
      final String gatewayAddress, final String restAddress, final List<ResourceFile> resources) {
    if (resources.isEmpty()) {
      return new DeploymentResult(resources);
    }

    try (final var client = CamundaClients.create(gatewayAddress, restAddress)) {
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
