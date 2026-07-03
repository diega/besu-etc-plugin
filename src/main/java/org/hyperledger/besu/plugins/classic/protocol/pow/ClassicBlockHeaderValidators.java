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
package org.hyperledger.besu.plugins.classic.protocol.pow;

import static org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderValidator.MAX_GAS_LIMIT;
import static org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderValidator.MINIMUM_SECONDS_SINCE_PARENT;
import static org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderValidator.MIN_GAS_LIMIT;
import static org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderValidator.TIMESTAMP_TOLERANCE_S;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.BlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.AncestryValidationRule;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.ExtraDataMaxLengthValidationRule;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.GasLimitRangeAndDeltaValidationRule;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.GasUsageValidationRule;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.TimestampBoundedByFutureParameter;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.TimestampMoreRecentThanParent;

import java.util.Optional;

/**
 * Ethereum-Classic proof-of-work header validators.
 *
 * <p>The {@link #createPgaBlockHeaderValidator} and {@link #createLegacyFeeMarketOmmerValidator}
 * method bodies are reproduced verbatim from {@code MainnetBlockHeaderValidator}, which carried
 * these legacy (non-EIP-1559) PoW validator chains before the upstream PoW removal
 * (PRs #10656/#10659/#10662). ETC remains a PoW chain across every milestone, so the plugin installs
 * these validators for all eras through {@link
 * org.hyperledger.besu.plugins.classic.protocol.ClassicProtocolSpecs}.
 *
 * <p>The {@link CalculatedDifficultyValidationRule} reads its {@code DifficultyCalculator} from the
 * surrounding {@code ProtocolSpec} at build time, so the difficulty formula stays whatever the
 * milestone configured (paused/delayed/removed bomb, or EIP-100).
 */
public final class ClassicBlockHeaderValidators {

  private ClassicBlockHeaderValidators() {}

  public static BlockHeaderValidator.Builder createPgaBlockHeaderValidator(
      final EpochCalculator epochCalculator, final PoWHasher hasher) {
    return new BlockHeaderValidator.Builder()
        .addRule(CalculatedDifficultyValidationRule::new)
        .addRule(new AncestryValidationRule())
        .addRule(new GasLimitRangeAndDeltaValidationRule(MIN_GAS_LIMIT, MAX_GAS_LIMIT))
        .addRule(new GasUsageValidationRule())
        .addRule(new TimestampMoreRecentThanParent(MINIMUM_SECONDS_SINCE_PARENT))
        .addRule(new TimestampBoundedByFutureParameter(TIMESTAMP_TOLERANCE_S))
        .addRule(new ExtraDataMaxLengthValidationRule(BlockHeader.MAX_EXTRA_DATA_BYTES))
        .addRule(new ProofOfWorkValidationRule(epochCalculator, hasher, Optional.empty()));
  }

  public static BlockHeaderValidator.Builder createLegacyFeeMarketOmmerValidator(
      final EpochCalculator epochCalculator, final PoWHasher hasher) {
    return new BlockHeaderValidator.Builder()
        .addRule(CalculatedDifficultyValidationRule::new)
        .addRule(new AncestryValidationRule())
        .addRule(new GasLimitRangeAndDeltaValidationRule(MIN_GAS_LIMIT, MAX_GAS_LIMIT))
        .addRule(new GasUsageValidationRule())
        .addRule(new TimestampMoreRecentThanParent(MINIMUM_SECONDS_SINCE_PARENT))
        .addRule(new ExtraDataMaxLengthValidationRule(BlockHeader.MAX_EXTRA_DATA_BYTES))
        .addRule(new ProofOfWorkValidationRule(epochCalculator, hasher, Optional.empty()));
  }
}
