package org.eclipse.che.workspace.infrastructure.openshift.provision.limits.ram;

import static org.eclipse.che.workspace.infrastructure.openshift.Names.machineName;

import com.google.inject.Inject;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import java.util.Collection;
import java.util.Map;
import javax.inject.Named;
import org.eclipse.che.api.core.model.workspace.config.MachineConfig;
import org.eclipse.che.api.core.model.workspace.runtime.Machine;
import org.eclipse.che.api.core.model.workspace.runtime.RuntimeIdentity;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.environment.InternalMachineConfig;
import org.eclipse.che.workspace.infrastructure.openshift.environment.OpenShiftEnvironment;
import org.eclipse.che.workspace.infrastructure.openshift.provision.ConfigurationProvisioner;

/**
 * Sets Ram limit to openshift machine
 *
 * @author Anton Korneta
 */
public class RamLimitProvisioner implements ConfigurationProvisioner {

  private final long defaultMachineMemorySizeBytes;

  @Inject
  public RamLimitProvisioner(
      @Named("che.workspace.default_memory_mb") long defaultMachineMemorySizeMB) {
    this.defaultMachineMemorySizeBytes = defaultMachineMemorySizeMB * 1_024 * 1_024;
  }

  @Override
  public void provision(OpenShiftEnvironment osEnv, RuntimeIdentity identity)
      throws InfrastructureException {
    final Map<String, InternalMachineConfig> machines = osEnv.getMachines();
    final Collection<Pod> pods = osEnv.getPods().values();
    for (Pod pod : pods) {
      for (Container container : pod.getSpec().getContainers()) {
        final InternalMachineConfig machineConfig = machines.get(machineName(pod, container));
        final ResourceRequirements rr = new ResourceRequirementsBuilder()
            .addToLimits("", new Quantity("2", "Gi")).build();
        machineConfig.getAttributes().get(Machine.MEMORY_LIMIT_ATTRIBUTE);
        container.setResources(rr);
      }
    }
  }
}
