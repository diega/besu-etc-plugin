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
package org.hyperledger.besu.plugins.classic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.config.GenesisConfig;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.mainnet.BalConfiguration;
import org.hyperledger.besu.ethereum.mainnet.DifficultyCalculator;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.services.ForkIdProvider;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugins.classic.protocol.ClassicDifficultyCalculators;

import java.math.BigInteger;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class ClassicPluginIntegrationTest {

  private static final String MINIMAL_ETC_GENESIS_CONFIG =
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

  @Test
  void registerPublishesEtcScopedForkIdProvider() {
    final ServiceManager.SimpleServiceManager serviceManager =
        new ServiceManager.SimpleServiceManager();
    new ClassicPlugin().register(serviceManager);

    final ForkIdProvider forkIdProvider =
        serviceManager.getService(ForkIdProvider.class).orElseThrow();

    assertThat(forkIdProvider.supportsChainId(BigInteger.valueOf(61))).isTrue();
    assertThat(forkIdProvider.supportsChainId(BigInteger.valueOf(63))).isTrue();
    assertThat(forkIdProvider.supportsChainId(BigInteger.ONE)).isFalse();
  }

  @Test
  void registerCustomizesCoreProtocolScheduleWithClassicDifficultyRules() {
    final ServiceManager.SimpleServiceManager serviceManager =
        new ServiceManager.SimpleServiceManager();
    new ClassicPlugin().register(serviceManager);

    final ProtocolSchedule protocolSchedule =
        MainnetProtocolSchedule.fromConfig(
            GenesisConfig.fromConfig(MINIMAL_ETC_GENESIS_CONFIG).getConfigOptions(),
            Optional.empty(),
            Optional.of(EvmConfiguration.DEFAULT),
            MiningConfiguration.MINING_DISABLED,
            new BadBlockManager(),
            false,
            BalConfiguration.DEFAULT,
            mock(MetricsSystem.class),
            serviceManager);

    assertDifficultyCalculator(
        protocolSchedule, 3_000_000L, ClassicDifficultyCalculators.DIFFICULTY_BOMB_PAUSED);
    assertDifficultyCalculator(
        protocolSchedule, 5_000_000L, ClassicDifficultyCalculators.DIFFICULTY_BOMB_DELAYED);
    assertDifficultyCalculator(
        protocolSchedule, 5_900_000L, ClassicDifficultyCalculators.DIFFICULTY_BOMB_REMOVED);
    assertDifficultyCalculator(protocolSchedule, 8_772_000L, ClassicDifficultyCalculators.EIP100);
    assertDifficultyCalculator(protocolSchedule, 14_525_000L, ClassicDifficultyCalculators.EIP100);
  }

  private void assertDifficultyCalculator(
      final ProtocolSchedule protocolSchedule,
      final long blockNumber,
      final DifficultyCalculator expectedCalculator) {
    assertThat(
            protocolSchedule
                .getByBlockHeader(new BlockHeaderTestFixture().number(blockNumber).buildHeader())
                .getDifficultyCalculator())
        .as("block %d", blockNumber)
        .isSameAs(expectedCalculator);
  }
}
