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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.config.GenesisConfig;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.mainnet.BalConfiguration;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.evm.gascalculator.BerlinGasCalculator;
import org.hyperledger.besu.evm.gascalculator.DieHardGasCalculator;
import org.hyperledger.besu.evm.gascalculator.IstanbulGasCalculator;
import org.hyperledger.besu.evm.gascalculator.LondonGasCalculator;
import org.hyperledger.besu.evm.gascalculator.PetersburgGasCalculator;
import org.hyperledger.besu.evm.gascalculator.ShanghaiGasCalculator;
import org.hyperledger.besu.evm.gascalculator.TangerineWhistleGasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugins.classic.ClassicPlugin;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Golden test pinning the *resulting* ProtocolSpec components of the assembled ETC protocol
 * schedule, fork by fork.
 *
 * <p>Unlike {@link ClassicProtocolSpecsTest} (which verifies the adapter functions in isolation
 * against a mocked builder), this test builds the full schedule the way the plugin does at runtime
 * (mainnet base schedule + {@code ProtocolScheduleCustomizer}) and asserts the concrete component
 * types that come out. Because ETC delegates the forks it shares with Ethereum (gas schedules, EVM,
 * precompiles) to Besu's mainnet schedule, this test is the tripwire that fires if an upstream
 * mainnet change silently alters ETC behavior — e.g. if the EIP-150 (Tangerine Whistle) gas
 * calculator that ETC inherits ever changes type, or if the plugin stops overriding a fork with the
 * ETC-specific component.
 */
class ClassicProtocolScheduleGoldenTest {

  // ETC milestones (mainnet block numbers). ETC-only blocks (dieHard, gotham, thanos, mystique,
  // spiral) are sourced by the adapters from the bundled classic.json, not from this config.
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

  private static final long TANGERINE_WHISTLE = 2_500_000L;
  private static final long DIE_HARD = 3_000_000L;
  private static final long GOTHAM = 5_000_000L;
  private static final long ATLANTIS = 8_772_000L;
  private static final long AGHARTA = 9_573_000L;
  private static final long PHOENIX = 10_500_839L;
  private static final long THANOS = 11_700_000L;
  private static final long MAGNETO = 13_189_133L;
  private static final long MYSTIQUE = 14_525_000L;
  private static final long SPIRAL = 19_250_000L;

  @Test
  void gasCalculatorPerFork() {
    final ProtocolSchedule schedule = buildClassicSchedule();

    // Tangerine Whistle: EIP-150 gas, DELEGATED to Besu mainnet. Tripwire for upstream drift.
    assertThat(specAt(schedule, TANGERINE_WHISTLE).getGasCalculator())
        .isInstanceOf(TangerineWhistleGasCalculator.class);
    // DieHard (EIP-160): overridden by the plugin.
    assertThat(specAt(schedule, DIE_HARD).getGasCalculator())
        .isInstanceOf(DieHardGasCalculator.class);
    // Mystique: London gas, overridden by the plugin.
    assertThat(specAt(schedule, MYSTIQUE).getGasCalculator())
        .isInstanceOf(LondonGasCalculator.class);
    // Spiral: Shanghai gas, overridden by the plugin.
    assertThat(specAt(schedule, SPIRAL).getGasCalculator())
        .isInstanceOf(ShanghaiGasCalculator.class);
  }

  @Test
  void delegatedForkGasCalculatorsTrackMainnet() {
    final ProtocolSchedule schedule = buildClassicSchedule();

    // Highest-drift-risk forks: they have NO ETC-specific adapter, so ETC inherits the mainnet gas
    // schedule wholesale (Agharta = Constantinople+Petersburg, Phoenix = Istanbul, Magneto =
    // Berlin). Pinning the inherited gas calculator type is the tripwire that fires if an upstream
    // mainnet change ever alters what ETC silently delegates here.
    assertThat(specAt(schedule, AGHARTA).getGasCalculator())
        .isInstanceOf(PetersburgGasCalculator.class);
    assertThat(specAt(schedule, PHOENIX).getGasCalculator())
        .isInstanceOf(IstanbulGasCalculator.class);
    assertThat(specAt(schedule, MAGNETO).getGasCalculator())
        .isInstanceOf(BerlinGasCalculator.class);
  }

  @Test
  void classicBlockProcessorAndMaxRewardFromGothamOnward() {
    final ProtocolSchedule schedule = buildClassicSchedule();

    for (final long block : new long[] {GOTHAM, ATLANTIS, THANOS, MYSTIQUE, SPIRAL}) {
      final ProtocolSpec spec = specAt(schedule, block);
      assertThat(spec.getBlockProcessor())
          .as("block processor at %d", block)
          .isInstanceOf(ClassicBlockProcessor.class);
      assertThat(spec.getBlockReward())
          .as("max block reward at %d", block)
          .isEqualTo(Wei.fromEth(5));
    }
  }

  @Test
  void mainnetBlockProcessorBeforeGotham() {
    final ProtocolSchedule schedule = buildClassicSchedule();

    // ECIP-1017 era rewards only start at Gotham; earlier forks must NOT use ClassicBlockProcessor.
    assertThat(specAt(schedule, DIE_HARD).getBlockProcessor())
        .isNotInstanceOf(ClassicBlockProcessor.class);
  }

  private ProtocolSchedule buildClassicSchedule() {
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

  private ProtocolSpec specAt(final ProtocolSchedule schedule, final long blockNumber) {
    return schedule.getByBlockHeader(
        new BlockHeaderTestFixture().number(blockNumber).buildHeader());
  }
}
