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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.config.GenesisConfig;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.mainnet.BalConfiguration;
import org.hyperledger.besu.ethereum.mainnet.DifficultyCalculator;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolScheduleCustomization;
import org.hyperledger.besu.ethereum.mainnet.ProtocolScheduleCustomizer;
import org.hyperledger.besu.ethereum.mainnet.ProtocolScheduleService;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugins.classic.protocol.ClassicDifficultyCalculators;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class ClassicPluginIntegrationTest {

  @Test
  void registerHandsBesuAnEtcScopedCustomizer() {
    final List<ProtocolScheduleCustomizer> registered = registerPlugin();

    assertThat(registered).hasSize(1);
    assertThat(registered.get(0).customize(etcMainnetConfig())).isPresent();
    assertThat(
            registered
                .get(0)
                .customize(GenesisConfig.fromConfig("{\"config\":{\"chainId\":1}}").getConfigOptions()))
        .isEmpty();
  }

  @Test
  void registerFailsOnABesuWithoutTheProtocolScheduleService() {
    assertThatThrownBy(() -> new ClassicPlugin().register(new ServiceManager.SimpleServiceManager()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ProtocolScheduleService");
  }

  @Test
  void theRegisteredCustomizerCarriesTheClassicDifficultyRules() {
    final ProtocolScheduleCustomization customization =
        registerPlugin().get(0).customize(etcMainnetConfig()).orElseThrow();

    final ProtocolSchedule protocolSchedule =
        MainnetProtocolSchedule.fromConfig(
            etcMainnetConfig(),
            Optional.empty(),
            Optional.of(EvmConfiguration.DEFAULT),
            MiningConfiguration.MINING_DISABLED,
            new BadBlockManager(),
            false,
            BalConfiguration.DEFAULT,
            mock(MetricsSystem.class),
            customization);

    assertDifficultyCalculator(
        protocolSchedule, 3_000_000L, ClassicDifficultyCalculators.DIFFICULTY_BOMB_PAUSED);
    assertDifficultyCalculator(
        protocolSchedule, 5_000_000L, ClassicDifficultyCalculators.DIFFICULTY_BOMB_DELAYED);
    assertDifficultyCalculator(
        protocolSchedule, 5_900_000L, ClassicDifficultyCalculators.DIFFICULTY_BOMB_REMOVED);
    assertDifficultyCalculator(protocolSchedule, 8_772_000L, ClassicDifficultyCalculators.EIP100);
    assertDifficultyCalculator(protocolSchedule, 14_525_000L, ClassicDifficultyCalculators.EIP100);
  }

  /** Registers the plugin against a service manager that only records what it registers. */
  private static List<ProtocolScheduleCustomizer> registerPlugin() {
    final List<ProtocolScheduleCustomizer> registered = new ArrayList<>();
    final ServiceManager.SimpleServiceManager serviceManager =
        new ServiceManager.SimpleServiceManager();
    serviceManager.addService(
        ProtocolScheduleService.class, (ProtocolScheduleService) registered::add);
    new ClassicPlugin().register(serviceManager);
    return registered;
  }

  private static GenesisConfigOptions etcMainnetConfig() {
    try (InputStream is = ClassicPluginIntegrationTest.class.getResourceAsStream("/classic.json")) {
      return GenesisConfig.fromConfig(new String(is.readAllBytes(), StandardCharsets.UTF_8))
          .getConfigOptions();
    } catch (final Exception e) {
      throw new IllegalStateException("Unable to load /classic.json", e);
    }
  }

  private void assertDifficultyCalculator(
      final ProtocolSchedule protocolSchedule,
      final long blockNumber,
      final DifficultyCalculator expectedCalculator) {
    assertThat(
            protocolSchedule
                .getByBlockHeader(new BlockHeaderTestFixture().number(blockNumber).buildHeader())
                .getDifficultyCalculator())
        .as("difficulty calculator at block %d", blockNumber)
        .isSameAs(expectedCalculator);
  }
}
