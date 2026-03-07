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

import org.hyperledger.besu.ethereum.mainnet.EpochCalculator;
import org.hyperledger.besu.ethereum.mainnet.EthHash;

/**
 * ECIP-1099 epoch calculator that doubles the epoch length from 30,000 to 60,000 blocks. Originally
 * from EpochCalculator.Ecip1099EpochCalculator, removed from Besu core in PR #9671.
 */
public final class Ecip1099EpochCalculator implements EpochCalculator {

  /** Creates a new ECIP-1099 epoch calculator. */
  public Ecip1099EpochCalculator() {}

  @Override
  public long epochStartBlock(final long block) {
    long epoch = cacheEpoch(block);
    return epoch * (EthHash.EPOCH_LENGTH * 2) + 1;
  }

  @Override
  public long cacheEpoch(final long block) {
    return Long.divideUnsigned(block, EthHash.EPOCH_LENGTH * 2);
  }
}
