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

import static java.lang.String.format;
import static org.eclipse.che.api.core.model.workspace.runtime.Machine.MEMORY_LIMIT_ATTRIBUTE;

import java.util.Collection;
import java.util.Map;
import javax.inject.Inject;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.ValidationException;
import org.eclipse.che.api.core.model.workspace.config.Environment;
import org.eclipse.che.api.core.model.workspace.runtime.Machine;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.environment.InternalEnvironment;
import org.eclipse.che.api.workspace.server.spi.environment.InternalEnvironmentFactory;
import org.eclipse.che.api.workspace.server.spi.environment.InternalMachineConfig;

/**
 * Helps to calculate amount of RAM defined in {@link Environment environment}
 *
 * @author Sergii Leschenko
 * @author Anton Korneta
 */
public class EnvironmentRamCalculator {
  private static final long BYTES_TO_MEGABYTES_DIVIDER = 1024L * 1024L;

  private final Map<String, InternalEnvironmentFactory> environmentFactories;

  @Inject
  public EnvironmentRamCalculator(Map<String, InternalEnvironmentFactory> environmentFactories) {
    this.environmentFactories = environmentFactories;
  }

  /**
   * Parses (and fetches if needed) recipe of environment and sums RAM size of all machines in
   * environment in megabytes.
   */
  public long calculate(Environment environment) throws ServerException {
    try {
      return calculate(createInternalEnvironment(environment));
    } catch (InfrastructureException | ValidationException | NotFoundException ex) {
      throw new ServerException(ex);
    }
  }

  public long calculate(InternalEnvironment environment) throws ServerException {
    long sum = 0L;
    for (InternalMachineConfig machine : environment.getMachines().values()) {
      sum += parseMemoryAttribute(machine.getAttributes());
    }
    return sum / BYTES_TO_MEGABYTES_DIVIDER;
  }

  public long calculate(Collection<? extends Machine> machines) throws ServerException {
    long sum = 0L;
    for (Machine machine : machines) {
      sum += parseMemoryAttribute(machine.getAttributes());
    }
    return sum / BYTES_TO_MEGABYTES_DIVIDER;
  }

  private InternalEnvironment createInternalEnvironment(Environment environment)
      throws InfrastructureException, ValidationException, NotFoundException {
    String recipeType = environment.getRecipe().getType();
    InternalEnvironmentFactory factory = environmentFactories.get(recipeType);
    if (factory == null) {
      throw new NotFoundException(
          format("InternalEnvironmentFactory is not configured for recipe type: '%s'", recipeType));
    }
    return factory.create(environment);
  }

  private long parseMemoryAttribute(Map<String, String> attributes) throws ServerException {
    try {
      return Long.parseLong(attributes.get(MEMORY_LIMIT_ATTRIBUTE));
    } catch (NumberFormatException ex) {
      throw new ServerException(
          "Failed to calculate environment RAM size due to invalid attribute format.", ex);
    }
  }
}
