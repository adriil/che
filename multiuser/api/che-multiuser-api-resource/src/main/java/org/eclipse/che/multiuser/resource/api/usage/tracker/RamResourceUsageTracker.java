/*
 * Copyright (c) 2012-2017 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.multiuser.resource.api.usage.tracker;

import static org.eclipse.che.api.core.model.workspace.WorkspaceStatus.STOPPED;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.eclipse.che.account.api.AccountManager;
import org.eclipse.che.account.shared.model.Account;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.Pages;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.model.workspace.WorkspaceStatus;
import org.eclipse.che.api.workspace.server.WorkspaceManager;
import org.eclipse.che.api.workspace.server.WorkspaceRuntimes;
import org.eclipse.che.api.workspace.server.model.impl.WorkspaceImpl;
import org.eclipse.che.api.workspace.server.spi.RuntimeContext;
import org.eclipse.che.multiuser.resource.api.ResourceUsageTracker;
import org.eclipse.che.multiuser.resource.api.type.RamResourceType;
import org.eclipse.che.multiuser.resource.model.Resource;
import org.eclipse.che.multiuser.resource.spi.impl.ResourceImpl;

/**
 * Tracks usage of {@link RamResourceType} resource.
 *
 * @author Sergii Leschenko
 */
@Singleton
public class RamResourceUsageTracker implements ResourceUsageTracker {
  private final Provider<WorkspaceManager> workspaceManagerProvider;
  private final AccountManager accountManager;
  private final EnvironmentRamCalculator environmentRamCalculator;
  private final WorkspaceRuntimes workspaceRuntimes;

  @Inject
  public RamResourceUsageTracker(
      Provider<WorkspaceManager> workspaceManagerProvider,
      WorkspaceRuntimes workspaceRuntimes,
      AccountManager accountManager,
      EnvironmentRamCalculator environmentRamCalculator) {
    this.workspaceManagerProvider = workspaceManagerProvider;
    this.workspaceRuntimes = workspaceRuntimes;
    this.accountManager = accountManager;
    this.environmentRamCalculator = environmentRamCalculator;
  }

  @Override
  public Optional<Resource> getUsedResource(String accountId)
      throws NotFoundException, ServerException {
    final Account account = accountManager.getById(accountId);
    List<WorkspaceImpl> activeWorkspaces =
        Pages.stream(
                (maxItems, skipCount) ->
                    workspaceManagerProvider
                        .get()
                        .getByNamespace(account.getName(), true, maxItems, skipCount))
            .filter(ws -> STOPPED != ws.getStatus())
            .collect(Collectors.toList());
    long currentlyUsedRamMB = 0;
    for (WorkspaceImpl activeWorkspace : activeWorkspaces) {
      if (WorkspaceStatus.STARTING.equals(activeWorkspace.getStatus())) {
        final Optional<RuntimeContext> runtimeContext =
            workspaceRuntimes.getRuntimeContext(activeWorkspace.getId());
        if (runtimeContext.isPresent()) {
          currentlyUsedRamMB +=
              environmentRamCalculator.calculate(runtimeContext.get().getEnvironment());
        }
      } else {
        // TODO GET RAM FROM RUNTIME MACHINES
        currentlyUsedRamMB +=
            environmentRamCalculator.calculate(activeWorkspace.getRuntime().getMachines().values());
      }
    }

    if (currentlyUsedRamMB > 0) {
      return Optional.of(
          new ResourceImpl(RamResourceType.ID, currentlyUsedRamMB, RamResourceType.UNIT));
    } else {
      return Optional.empty();
    }
  }
}
