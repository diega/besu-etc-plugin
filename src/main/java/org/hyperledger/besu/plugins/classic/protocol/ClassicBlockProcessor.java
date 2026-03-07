/*
 * Copyright ConsenSys AG.
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

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.mainnet.AbstractBlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.AbstractBlockProcessor.TransactionReceiptFactory;
import org.hyperledger.besu.ethereum.mainnet.BalConfiguration;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.MiningBeneficiaryCalculator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.math.BigInteger;
import java.util.List;
import java.util.OptionalLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Block processor for Ethereum Classic that implements ECIP-1017 monetary policy (5M era
 * disinflation schedule).
 */
public class ClassicBlockProcessor extends AbstractBlockProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(ClassicBlockProcessor.class);

  private static final long DEFAULT_ERA_LENGTH = 5_000_000L;

  private final long eraLength;

  /**
   * Creates a new {@code ClassicBlockProcessor}.
   *
   * @param transactionProcessor the transaction processor
   * @param transactionReceiptFactory the transaction receipt factory
   * @param blockReward the base block reward
   * @param miningBeneficiaryCalculator the mining beneficiary calculator
   * @param skipZeroBlockRewards whether to skip zero block rewards
   * @param eraLen the ECIP-1017 era length in blocks
   * @param protocolSchedule the protocol schedule
   * @param balConfiguration the BAL configuration
   */
  public ClassicBlockProcessor(
      final MainnetTransactionProcessor transactionProcessor,
      final TransactionReceiptFactory transactionReceiptFactory,
      final Wei blockReward,
      final MiningBeneficiaryCalculator miningBeneficiaryCalculator,
      final boolean skipZeroBlockRewards,
      final OptionalLong eraLen,
      final ProtocolSchedule protocolSchedule,
      final BalConfiguration balConfiguration) {
    super(
        transactionProcessor,
        transactionReceiptFactory,
        blockReward,
        miningBeneficiaryCalculator,
        skipZeroBlockRewards,
        protocolSchedule,
        balConfiguration);
    eraLength = eraLen.orElse(DEFAULT_ERA_LENGTH);
  }

  @Override
  protected boolean rewardCoinbase(
      final MutableWorldState worldState,
      final BlockHeader header,
      final List<BlockHeader> ommers,
      final boolean skipZeroBlockRewards) {
    if (skipZeroBlockRewards && blockReward.isZero()) {
      return true;
    }
    final Wei coinbaseReward = getCoinbaseReward(blockReward, header.getNumber(), ommers.size());
    final WorldUpdater updater = worldState.updater();
    final MutableAccount coinbase = updater.getOrCreate(header.getCoinbase());

    coinbase.incrementBalance(coinbaseReward);
    for (final BlockHeader ommerHeader : ommers) {
      if (ommerHeader.getNumber() - header.getNumber() > MAX_GENERATION) {
        LOG.warn(
            "Block processing error: ommer block number {} more than {} generations current block number {}",
            ommerHeader.getNumber(),
            MAX_GENERATION,
            header.getNumber());
        return false;
      }

      final MutableAccount ommerCoinbase = updater.getOrCreate(ommerHeader.getCoinbase());
      final Wei ommerReward =
          getOmmerReward(blockReward, header.getNumber(), ommerHeader.getNumber());
      ommerCoinbase.incrementBalance(ommerReward);
    }

    updater.commit();
    return true;
  }

  private Wei calculateOmmerReward(final int era, final long distance) {
    Wei winnerReward = getBlockWinnerRewardByEra(era);
    if (era < 1) {
      return winnerReward.subtract(winnerReward.multiply(distance).divide(8));
    }
    return winnerReward.divide(32);
  }

  private int getBlockEra(final long blockNumber, final long eraLength) {
    if (blockNumber < 0) return 0;
    long remainder = (blockNumber - 1) % eraLength;
    long base = blockNumber - remainder;
    long d = base / eraLength;
    return Math.toIntExact(d);
  }

  private Wei getBlockWinnerRewardByEra(final int era) {
    if (era == 0) {
      return this.blockReward;
    }

    // MaxBlockReward _r_ * (4/5)**era == MaxBlockReward * (4**era) / (5**era)
    BigInteger disinflationRateQuotient = BigInteger.valueOf(4);
    BigInteger q = disinflationRateQuotient.pow(era);

    BigInteger disinflationRateDivisor = BigInteger.valueOf(5);
    BigInteger d = disinflationRateDivisor.pow(era);

    BigInteger maximumBlockReward = this.blockReward.toBigInteger();
    BigInteger r = maximumBlockReward.multiply(q);
    r = r.divide(d);
    return Wei.of(r);
  }

  @Override
  public Wei getOmmerReward(
      final Wei blockReward, final long blockNumber, final long ommerBlockNumber) {
    final int blockEra = getBlockEra(blockNumber, eraLength);
    final long distance = blockNumber - ommerBlockNumber;
    return calculateOmmerReward(blockEra, distance);
  }

  @Override
  public Wei getCoinbaseReward(
      final Wei blockReward, final long blockNumber, final int ommersSize) {
    final int blockEra = getBlockEra(blockNumber, eraLength);
    final Wei winnerReward = getBlockWinnerRewardByEra(blockEra);
    return winnerReward.plus(winnerReward.multiply(ommersSize).divide(32));
  }
}
