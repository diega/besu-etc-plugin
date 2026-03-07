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

import static org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSpecs.powHasher;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.config.PowAlgorithm;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.feemarket.CoinbaseFeePriceCalculator;
import org.hyperledger.besu.ethereum.mainnet.DifficultyCalculator;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpecBuilder;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidatorFactory;
import org.hyperledger.besu.evm.contractvalidation.MaxCodeSizeRule;
import org.hyperledger.besu.evm.contractvalidation.PrefixCodeRule;
import org.hyperledger.besu.evm.gascalculator.DieHardGasCalculator;
import org.hyperledger.besu.evm.gascalculator.LondonGasCalculator;
import org.hyperledger.besu.evm.gascalculator.ShanghaiGasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;

/**
 * Creates protocol spec adapter functions for Ethereum Classic hardforks. Each adapter modifies the
 * {@link ProtocolSpecBuilder} provided by the mainnet protocol schedule to apply ETC-specific
 * changes (gas calculators, block processors, EVM rules).
 *
 * <p>Adapters use floor semantics: an adapter registered at block N applies to all milestones at
 * block numbers &gt;= N until the next adapter entry. Each adapter is self-contained and
 * accumulates all changes needed for its range.
 *
 * <h3>ETC ↔ ETH Hardfork Mapping</h3>
 *
 * <p>ETC hardforks that map 1:1 to ETH mainnet are expressed using mainnet genesis keys. ETC-only
 * hardforks use their own keys and are applied via adapters.
 *
 * <pre>
 * ETC Hardfork        Block (mainnet)  Mainnet Key              ETC-only Key       Adapter Changes
 * ────────────────     ───────────────  ─────────────────────    ────────────────   ──────────────────────────────
 * Frontier             0               (implicit)               —                  (identity)
 * Homestead            1,150,000       homesteadBlock           —                  (identity)
 * TangerineWhistle     2,500,000       tangerineWhistleBlock    ecip1015Block      + replay protection (ECIP-1015)
 * DieHard              3,000,000       —                        dieHardBlock       + DieHardGasCalculator (EIP-160)
 * Gotham               5,000,000       —                        gothamBlock        + ClassicBlockProcessor (ECIP-1017) + delayed bomb
 * DefuseBomb           5,900,000       —                        ecip1041Block      + removed bomb (ECIP-1041)
 * Atlantis             8,772,000       byzantiumBlock           atlantisBlock      reset: ClassicBP + EIP-100 difficulty
 * Agharta              9,573,000       constantinople+petersburg aghartaBlock      (inherits ClassicBP)
 * Phoenix              10,500,839      istanbulBlock            phoenixBlock       (inherits ClassicBP)
 * Thanos               11,700,000      —                        thanosBlock        + ECIP-1099 epoch headers + EIP-100 difficulty
 * Magneto              13,189,133      berlinBlock              magnetoBlock       (inherits ClassicBP+Thanos)
 * Mystique             14,525,000      —                        mystiqueBlock      + LondonGasCalc + PrefixCodeRule + EIP-100 difficulty
 * Spiral               19,250,000      —                        spiralBlock        + ShanghaiGasCalc + PUSH0 + warm coinbase + EIP-100 difficulty
 * </pre>
 */
public class ClassicProtocolSpecs {
  private static final Wei MAX_BLOCK_REWARD = Wei.fromEth(5);

  private ClassicProtocolSpecs() {}

  /**
   * Creates the map of block-number-keyed adapter functions for the given ETC genesis config.
   *
   * @param config the genesis configuration options
   * @return a map from block number to adapter function
   */
  public static Map<Long, Function<ProtocolSpecBuilder, ProtocolSpecBuilder>> createAdapters(
      final GenesisConfigOptions config) {
    final Map<Long, Function<ProtocolSpecBuilder, ProtocolSpecBuilder>> adapters = new HashMap<>();
    final Optional<BigInteger> chainId = config.getChainId();

    // Only create adapters for ETC chains; return empty map for non-ETC networks
    if (chainId.isEmpty() || !ClassicGenesisConfig.isEtcChainId(chainId.get())) {
      return adapters;
    }

    // Parse ETC-specific keys directly from genesis JSON (bypasses asMap() whitelist)
    final ClassicGenesisConfig etcConfig =
        ClassicGenesisConfig.fromChainId(chainId.get())
            .orElseThrow(() -> new IllegalStateException("ETC config must have a chainId"));

    final OptionalLong eraRounds = etcConfig.getEcip1017EraRounds();

    // Base adapter components
    final Function<ProtocolSpecBuilder, ProtocolSpecBuilder> replayProtection =
        replayProtectionAdapter(chainId);
    final Function<ProtocolSpecBuilder, ProtocolSpecBuilder> dieHardGas =
        ClassicProtocolSpecs::applyDieHardGas;
    final Function<ProtocolSpecBuilder, ProtocolSpecBuilder> classicBP =
        classicBlockProcessorAdapter(eraRounds);
    final Function<ProtocolSpecBuilder, ProtocolSpecBuilder> pausedDifficulty =
        difficultyCalculatorAdapter(ClassicDifficultyCalculators.DIFFICULTY_BOMB_PAUSED);
    final Function<ProtocolSpecBuilder, ProtocolSpecBuilder> delayedDifficulty =
        difficultyCalculatorAdapter(ClassicDifficultyCalculators.DIFFICULTY_BOMB_DELAYED);
    final Function<ProtocolSpecBuilder, ProtocolSpecBuilder> removedDifficulty =
        difficultyCalculatorAdapter(ClassicDifficultyCalculators.DIFFICULTY_BOMB_REMOVED);
    final Function<ProtocolSpecBuilder, ProtocolSpecBuilder> eip100Difficulty =
        difficultyCalculatorAdapter(ClassicDifficultyCalculators.EIP100);
    final Function<ProtocolSpecBuilder, ProtocolSpecBuilder> thanosHeaders =
        ClassicProtocolSpecs::applyThanosHeaders;

    // --- ETC Mainnet adapters (only if the relevant blocks are configured) ---

    // TangerineWhistle (ecip1015Block / tangerineWhistleBlock): replay protection
    final OptionalLong twBlock = config.getTangerineWhistleBlockNumber();
    twBlock.ifPresent(block -> adapters.put(block, replayProtection));

    // DieHard: + DieHardGasCalculator (EIP-160 equivalent)
    etcConfig
        .getDieHardBlock()
        .ifPresent(
            block ->
                adapters.put(
                    block, replayProtection.andThen(dieHardGas).andThen(pausedDifficulty)));

    // Gotham: + ClassicBlockProcessor (ECIP-1017 era-based rewards)
    etcConfig
        .getGothamBlock()
        .ifPresent(
            block ->
                adapters.put(
                    block,
                    replayProtection
                        .andThen(dieHardGas)
                        .andThen(classicBP)
                        .andThen(delayedDifficulty)));

    // DefuseBomb (ECIP-1041): remove difficulty bomb
    etcConfig
        .getEcip1041Block()
        .ifPresent(
            block ->
                adapters.put(
                    block,
                    replayProtection
                        .andThen(dieHardGas)
                        .andThen(classicBP)
                        .andThen(removedDifficulty)));

    // Atlantis (byzantiumBlock): reset to ClassicBP + EIP-100 difficulty
    final OptionalLong byzantiumBlock = config.getByzantiumBlockNumber();
    byzantiumBlock.ifPresent(block -> adapters.put(block, classicBP.andThen(eip100Difficulty)));

    // Thanos: ClassicBP + ECIP-1099 epoch/header validation + EIP-100 difficulty
    etcConfig
        .getThanosBlock()
        .ifPresent(
            block ->
                adapters.put(block, classicBP.andThen(thanosHeaders).andThen(eip100Difficulty)));

    // Mystique: ClassicBP + LondonGasCalculator + PrefixCodeRule + EIP-100 difficulty
    etcConfig
        .getMystiqueBlock()
        .ifPresent(
            block ->
                adapters.put(
                    block,
                    classicBP
                        .andThen(thanosHeaders)
                        .andThen(eip100Difficulty)
                        .andThen(ClassicProtocolSpecs::applyMystique)));

    // Spiral: ClassicBP + ShanghaiGasCalculator + PUSH0 + warm coinbase + EIP-100 difficulty
    etcConfig
        .getSpiralBlock()
        .ifPresent(
            block ->
                adapters.put(
                    block,
                    classicBP
                        .andThen(thanosHeaders)
                        .andThen(eip100Difficulty)
                        .andThen(spiralAdapter(chainId))));

    return adapters;
  }

  // --- Adapter component functions ---

  private static Function<ProtocolSpecBuilder, ProtocolSpecBuilder> replayProtectionAdapter(
      final Optional<BigInteger> chainId) {
    return builder ->
        builder
            .isReplayProtectionSupported(true)
            .transactionValidatorFactoryBuilder(
                (evm, gasLimitCalculator, feeMarket) ->
                    new TransactionValidatorFactory(
                        evm.getGasCalculator(), gasLimitCalculator, true, chainId));
  }

  private static ProtocolSpecBuilder applyDieHardGas(final ProtocolSpecBuilder builder) {
    return builder.gasCalculator(DieHardGasCalculator::new);
  }

  private static Function<ProtocolSpecBuilder, ProtocolSpecBuilder> classicBlockProcessorAdapter(
      final OptionalLong eraRounds) {
    return builder ->
        builder
            .blockReward(MAX_BLOCK_REWARD)
            .blockProcessorBuilder(
                (transactionProcessor,
                    transactionReceiptFactory,
                    blockReward,
                    miningBeneficiaryCalculator,
                    skipZeroBlockRewards,
                    protocolSchedule,
                    balConfig) ->
                    new ClassicBlockProcessor(
                        transactionProcessor,
                        transactionReceiptFactory,
                        blockReward,
                        miningBeneficiaryCalculator,
                        skipZeroBlockRewards,
                        eraRounds,
                        protocolSchedule,
                        balConfig));
  }

  private static Function<ProtocolSpecBuilder, ProtocolSpecBuilder> difficultyCalculatorAdapter(
      final DifficultyCalculator difficultyCalculator) {
    return builder -> builder.difficultyCalculator(difficultyCalculator);
  }

  private static ProtocolSpecBuilder applyThanosHeaders(final ProtocolSpecBuilder builder) {
    return builder
        .blockHeaderValidatorBuilder(
            (feeMarket, gasCalculator, gasLimitCalculator) ->
                MainnetBlockHeaderValidator.createPgaBlockHeaderValidator(
                    new Ecip1099EpochCalculator(), powHasher(PowAlgorithm.ETHASH)))
        .ommerHeaderValidatorBuilder(
            (feeMarket, gasCalculator, gasLimitCalculator) ->
                MainnetBlockHeaderValidator.createLegacyFeeMarketOmmerValidator(
                    new Ecip1099EpochCalculator(), powHasher(PowAlgorithm.ETHASH)));
  }

  private static ProtocolSpecBuilder applyMystique(final ProtocolSpecBuilder builder) {
    return builder
        .gasCalculator(LondonGasCalculator::new)
        .contractCreationProcessorBuilder(
            evm ->
                new ContractCreationProcessor(
                    evm, true, List.of(MaxCodeSizeRule.from(evm), PrefixCodeRule.of()), 1));
  }

  private static Function<ProtocolSpecBuilder, ProtocolSpecBuilder> spiralAdapter(
      final Optional<BigInteger> chainId) {
    return builder ->
        builder
            .gasCalculator(ShanghaiGasCalculator::new)
            .evmBuilder(
                (gasCalculator, jdCacheConfig) ->
                    ClassicEVMs.spiral(
                        gasCalculator, chainId.orElse(BigInteger.ZERO), EvmConfiguration.DEFAULT))
            .transactionProcessorBuilder(
                (gasCalculator,
                    feeMarket,
                    transactionValidatorFactory,
                    contractCreationProcessor,
                    messageCallProcessor) ->
                    MainnetTransactionProcessor.builder()
                        .gasCalculator(gasCalculator)
                        .transactionValidatorFactory(transactionValidatorFactory)
                        .contractCreationProcessor(contractCreationProcessor)
                        .messageCallProcessor(messageCallProcessor)
                        .clearEmptyAccounts(true)
                        .warmCoinbase(true)
                        .maxStackSize(EvmConfiguration.DEFAULT.evmStackSize())
                        .feeMarket(feeMarket)
                        .coinbaseFeePriceCalculator(CoinbaseFeePriceCalculator.frontier())
                        .build());
  }
}
