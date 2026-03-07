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

/*
 * Originally from: evm/src/main/java/org/hyperledger/besu/evm/ClassicEVMs.java
 * Removed from Besu core in PR #9671 (commit 1167c5a544, 2026-02-10)
 * Moved to plugin to keep ETC-specific logic out of core.
 */
package org.hyperledger.besu.plugins.classic.protocol;

import static org.hyperledger.besu.evm.MainnetEVMs.registerIstanbulOperations;

import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.operation.OperationRegistry;
import org.hyperledger.besu.evm.operation.Push0Operation;

import java.math.BigInteger;

/** Provides EVMs supporting the appropriate operations for ETC network upgrades. */
public class ClassicEVMs {

  private ClassicEVMs() {}

  /**
   * Creates the Spiral EVM (ETC equivalent of Shanghai with Push0 support).
   *
   * @param gasCalculator the gas calculator
   * @param chainId the chain id
   * @param evmConfiguration the evm configuration
   * @return the evm
   */
  public static EVM spiral(
      final GasCalculator gasCalculator,
      final BigInteger chainId,
      final EvmConfiguration evmConfiguration) {
    return new EVM(
        spiralOperations(gasCalculator, chainId, evmConfiguration),
        gasCalculator,
        evmConfiguration,
        EvmSpecVersion.SHANGHAI);
  }

  /**
   * Creates the Spiral operation registry.
   *
   * @param gasCalculator the gas calculator
   * @param chainId the chain id
   * @param evmConfiguration the evm configuration
   * @return the operation registry
   */
  public static OperationRegistry spiralOperations(
      final GasCalculator gasCalculator,
      final BigInteger chainId,
      final EvmConfiguration evmConfiguration) {
    OperationRegistry registry = new OperationRegistry();
    registerIstanbulOperations(registry, gasCalculator, chainId, evmConfiguration);
    registry.put(new Push0Operation(gasCalculator));
    return registry;
  }
}
