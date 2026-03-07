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

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

/**
 * Provides ETC fork block numbers for EIP-2124 fork ID computation. ETC does not have
 * timestamp-based forks, so fork timestamps are always empty.
 */
public class ClassicForkIdProvider implements ForkIdProvider {

  /** Creates a new {@code ClassicForkIdProvider}. */
  public ClassicForkIdProvider() {}

  @Override
  public boolean supportsChainId(final BigInteger chainId) {
    return ClassicGenesisConfig.isEtcChainId(chainId);
  }

  @Override
  public List<Long> getForkBlockNumbers(final BigInteger chainId) {
    return requireEtcConfig(chainId).getForkIdBlockNumbers();
  }

  @Override
  public List<Long> getForkTimestamps(final BigInteger chainId) {
    requireEtcConfig(chainId);
    return Collections.emptyList();
  }

  private ClassicGenesisConfig requireEtcConfig(final BigInteger chainId) {
    if (!supportsChainId(chainId)) {
      throw new IllegalArgumentException(
          "Unsupported chain ID for ClassicForkIdProvider: " + chainId);
    }
    return ClassicGenesisConfig.fromChainId(chainId)
        .orElseThrow(
            () -> new IllegalStateException("Missing ETC genesis config for chain ID " + chainId));
  }
}
