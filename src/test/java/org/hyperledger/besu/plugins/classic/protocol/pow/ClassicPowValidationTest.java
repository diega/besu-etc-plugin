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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.config.GenesisConfig;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.mainnet.BalConfiguration;
import org.hyperledger.besu.ethereum.mainnet.BlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugins.classic.ClassicPlugin;
import org.hyperledger.besu.plugins.classic.protocol.Ecip1099EpochCalculator;

import java.io.IOException;
import java.util.Optional;

import com.google.common.io.Resources;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the plugin still performs Ethash proof-of-work validation after the upstream PoW
 * removal (#10656/#10659/#10662). Exercises the vendored Ethash stack directly, against a known
 * answer, and end-to-end through the assembled ETC protocol schedule.
 *
 * <p>Block fixtures 300005/300006 are real, consecutive Ethereum mainnet PoW headers (block 300005
 * is the parent of 300006). ETC is identical to Ethereum for blocks below 1,920,000, so they are
 * valid ETC blocks too. They exercise the pre-Thanos path (default 30k-block epoch calculator),
 * which the plugin now installs from genesis.
 */
class ClassicPowValidationTest {

  // ETC milestones, matching ClassicProtocolScheduleGoldenTest. Block 300006 is in the Frontier era.
  private static final String ETC_GENESIS_CONFIG =
      "{"
          + "\"config\":{"
          + "\"chainId\":61,"
          + "\"homesteadBlock\":1150000,"
          + "\"eip150Block\":2500000,"
          + "\"eip158Block\":3000000,"
          + "\"byzantiumBlock\":8772000,"
          + "\"constantinopleBlock\":9573000,"
          + "\"petersburgBlock\":9573000,"
          + "\"istanbulBlock\":10500839,"
          + "\"berlinBlock\":13189133"
          + "}"
          + "}";

  private static BlockHeader parent; // block 300005
  private static BlockHeader header; // block 300006

  @BeforeAll
  static void loadHeaders() throws IOException {
    parent = readHeader(300005);
    header = readHeader(300006);
  }

  // --- Vendored Ethash core, against a fixed known answer (no fixtures, no DAG epoch cost) ---

  @Test
  void vendoredEthHashMatchesKnownAnswer() {
    final int[] cache = EthHash.mkCache(1024, 1L, new EpochCalculator.DefaultEpochCalculator());
    final PoWSolution solution =
        EthHash.hashimotoLight(
            32 * 1024,
            cache,
            Bytes.fromHexString(
                "c9149cc0386e689d789a1c2f3d5d169a61a6218ed30e74414dc736e442ef3d1f"),
            0L);

    assertThat(solution.getSolution().toHexString())
        .isEqualTo("0xd3539235ee2e6f8db665c0a72169f55b7f6c605712330b778ec3944f0eb5a557");
    assertThat(solution.getMixHash().getBytes().toHexString())
        .isEqualTo("0xe4073cffaef931d37117cefd9afd27ea0f1cad6a981dd2605c4a1ac97c519800");
  }

  // --- Vendored proof-of-work header rule, against real headers ---

  @Test
  void vendoredPowRuleAcceptsRealHeader() {
    assertThat(defaultPowRule().validate(header, parent)).isTrue();
  }

  @Test
  void vendoredPowRuleRejectsTamperedNonce() {
    final BlockHeader tampered =
        BlockHeaderBuilder.fromHeader(header)
            .nonce(header.getNonce() ^ 1L)
            .blockHeaderFunctions(new MainnetBlockHeaderFunctions())
            .buildBlockHeader();
    assertThat(defaultPowRule().validate(tampered, parent)).isFalse();
  }

  @Test
  void vendoredPowRuleRejectsTamperedMixHash() {
    final BlockHeader tampered =
        BlockHeaderBuilder.fromHeader(header)
            .mixHash(Hash.ZERO)
            .blockHeaderFunctions(new MainnetBlockHeaderFunctions())
            .buildBlockHeader();
    assertThat(defaultPowRule().validate(tampered, parent)).isFalse();
  }

  // --- End-to-end: the assembled ETC schedule installs PoW validation ---

  @Test
  void etcScheduleValidatesRealHeaderAndRejectsTamperedPow() {
    final ProtocolSchedule schedule = buildClassicSchedule();
    final ProtocolSpec spec = schedule.getByBlockHeader(header);
    final BlockHeaderValidator validator = spec.getBlockHeaderValidator();
    final ProtocolContext context = mock(ProtocolContext.class);

    assertThat(validator.validateHeader(header, parent, context, HeaderValidationMode.FULL))
        .as("a real ETC-equivalent header validates through the assembled schedule")
        .isTrue();

    final BlockHeader tampered =
        BlockHeaderBuilder.fromHeader(header)
            .nonce(header.getNonce() ^ 1L)
            .blockHeaderFunctions(new MainnetBlockHeaderFunctions())
            .buildBlockHeader();
    assertThat(validator.validateHeader(tampered, parent, context, HeaderValidationMode.FULL))
        .as("tampered PoW is rejected — proves the schedule installs PoW validation for the era")
        .isFalse();
  }

  // --- ECIP-1099 epoch doubling (cheap; no DAG cache) ---

  @Test
  void ecip1099DoublesEpochLengthRelativeToDefault() {
    final EpochCalculator defaultCalc = new EpochCalculator.DefaultEpochCalculator();
    final EpochCalculator ecip1099 = new Ecip1099EpochCalculator();

    // Default epoch length is 30,000; ECIP-1099 doubles it to 60,000.
    assertThat(defaultCalc.cacheEpoch(EthHash.EPOCH_LENGTH)).isEqualTo(1L);
    assertThat(ecip1099.cacheEpoch(EthHash.EPOCH_LENGTH)).isEqualTo(0L);
    assertThat(ecip1099.cacheEpoch(EthHash.EPOCH_LENGTH * 2)).isEqualTo(1L);
    assertThat(ecip1099.epochStartBlock(EthHash.EPOCH_LENGTH * 2))
        .isEqualTo(EthHash.EPOCH_LENGTH * 2 + 1);
  }

  private static ProofOfWorkValidationRule defaultPowRule() {
    return new ProofOfWorkValidationRule(
        new EpochCalculator.DefaultEpochCalculator(), PoWHasher.ETHASH_LIGHT);
  }

  private static ProtocolSchedule buildClassicSchedule() {
    final ServiceManager.SimpleServiceManager serviceManager =
        new ServiceManager.SimpleServiceManager();
    new ClassicPlugin().register(serviceManager);
    return MainnetProtocolSchedule.fromConfig(
        GenesisConfig.fromConfig(ETC_GENESIS_CONFIG).getConfigOptions(),
        Optional.empty(),
        Optional.of(EvmConfiguration.DEFAULT),
        MiningConfiguration.MINING_DISABLED,
        new BadBlockManager(),
        false,
        BalConfiguration.DEFAULT,
        mock(MetricsSystem.class),
        Optional.of(serviceManager));
  }

  private static BlockHeader readHeader(final long number) throws IOException {
    final RLPInput input =
        new BytesValueRLPInput(
            Bytes.wrap(
                Resources.toByteArray(
                    ClassicPowValidationTest.class.getResource("block_" + number + ".blocks"))),
            false);
    input.enterList();
    return BlockHeader.readFrom(input, new MainnetBlockHeaderFunctions());
  }
}
