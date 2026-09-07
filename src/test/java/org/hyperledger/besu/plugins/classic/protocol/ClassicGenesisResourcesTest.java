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

import org.hyperledger.besu.config.GenesisConfigOptions;

import java.util.OptionalLong;

import org.junit.jupiter.api.Test;

/**
 * Pins the ETC keys in the genesis files this plugin ships. The plugin reads them from whatever
 * config the node was started with, so a key that disappeared or changed would not fail loudly: the
 * era length in particular would silently fall back to a different reward schedule.
 */
class ClassicGenesisResourcesTest {

  @Test
  void classicEraRoundsIsFiveMillion() {
    assertThat(eraRounds(EtcGenesis.mainnet())).isEqualTo(OptionalLong.of(5_000_000L));
  }

  @Test
  void mordorEraRoundsIsTwoMillion() {
    assertThat(eraRounds(EtcGenesis.mordor())).isEqualTo(OptionalLong.of(2_000_000L));
  }

  @Test
  void classicForkBlocksMatchGenesis() {
    final GenesisConfigOptions config = EtcGenesis.mainnet();
    assertThat(config.getCustomConfigLong("dieHardBlock")).isEqualTo(OptionalLong.of(3_000_000L));
    assertThat(config.getCustomConfigLong("gothamBlock")).isEqualTo(OptionalLong.of(5_000_000L));
    assertThat(config.getCustomConfigLong("ecip1041Block")).isEqualTo(OptionalLong.of(5_900_000L));
    assertThat(config.getCustomConfigLong("thanosBlock")).isEqualTo(OptionalLong.of(11_700_000L));
    assertThat(config.getCustomConfigLong("mystiqueBlock")).isEqualTo(OptionalLong.of(14_525_000L));
    assertThat(config.getCustomConfigLong("spiralBlock")).isEqualTo(OptionalLong.of(19_250_000L));
  }

  private static OptionalLong eraRounds(final GenesisConfigOptions config) {
    return config.getCustomConfigLong("ecip1017EraRounds");
  }
}
