/*
 * Copyright contributors to Hyperledger Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.plugins.classic.protocol;

import org.hyperledger.besu.plugin.services.ForkIdProvider;
import org.hyperledger.besu.plugin.services.ForkIdProvider.ForkSchedule;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Optional;

/**
 * Provides the ETC fork schedule for EIP-2124 fork ID computation. ETC does not have
 * timestamp-based forks, so the fork timestamps are always empty.
 */
public class ClassicForkIdProvider implements ForkIdProvider {

  /** Creates a new {@code ClassicForkIdProvider}. */
  public ClassicForkIdProvider() {}

  @Override
  public Optional<ForkSchedule> forkScheduleFor(final BigInteger chainId) {
    return ClassicGenesisConfig.fromChainId(chainId)
        .map(config -> new ForkSchedule(config.getForkIdBlockNumbers(), Collections.emptyList()));
  }
}
