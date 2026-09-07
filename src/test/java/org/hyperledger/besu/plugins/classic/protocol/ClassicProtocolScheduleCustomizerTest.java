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

import org.hyperledger.besu.config.GenesisConfig;
import org.hyperledger.besu.ethereum.mainnet.ProtocolScheduleCustomization;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class ClassicProtocolScheduleCustomizerTest {

  /**
   * The fork boundaries ETC mainnet announces, taken from core-geth's own fork ID vectors
   * (core/forkid/forkid_test.go, the "classic" case). Every peer computes its fork ID from exactly
   * this list and rejects a handshake that disagrees, so these numbers are the contract rather than
   * an implementation detail of this plugin.
   */
  private static final List<Long> ETC_MAINNET_FORKS =
      List.of(
          1_150_000L,
          2_500_000L,
          3_000_000L,
          5_000_000L,
          5_900_000L,
          8_772_000L,
          9_573_000L,
          10_500_839L,
          11_700_000L,
          13_189_133L,
          14_525_000L,
          19_250_000L);

  /** The same, for Mordor. Atlantis sits at genesis there, which is not a fork ID boundary. */
  private static final List<Long> MORDOR_FORKS =
      List.of(301_243L, 999_983L, 2_520_000L, 3_985_893L, 5_520_000L, 9_957_000L);

  private final ClassicProtocolScheduleCustomizer customizer =
      new ClassicProtocolScheduleCustomizer();

  @Test
  void declinesNonEtcChains() {
    assertThat(
            customizer.customize(
                GenesisConfig.fromConfig("{\"config\":{\"chainId\":1}}").getConfigOptions()))
        .isEmpty();
  }

  @Test
  void contributesOnlyBlockActivations() {
    final ProtocolScheduleCustomization customization = customize("/classic.json");

    assertThat(customization.name()).isEqualTo("classic");
    assertThat(customization.toForkIdActivations().timestamps()).isEmpty();
    // the DAO point is ETH's, not ETC's
    assertThat(customization.toForkIdActivations().blockNumbers()).doesNotContain(1_920_000L);
  }

  @Test
  void theAdvertisedForkIdMatchesTheEtcMainnetSchedule() {
    assertThat(advertisedForkBlocks("/classic.json")).isEqualTo(ETC_MAINNET_FORKS);
  }

  @Test
  void theAdvertisedForkIdMatchesTheMordorSchedule() {
    assertThat(advertisedForkBlocks("/mordor.json")).isEqualTo(MORDOR_FORKS);
  }

  /**
   * The fork blocks the plugin itself declares, minus genesis, which the fork ID drops because the
   * genesis hash already covers it.
   *
   * <p>Deliberately the customization's own activations rather than what they fold into: several
   * ETC forks land on a block Besu also recognizes under a mainnet key, so folding would pass even
   * if the plugin declared none of them. What has to hold is that the plugin states the whole ETC
   * schedule on its own, since that is what survives Besu changing which keys it reads.
   */
  private List<Long> advertisedForkBlocks(final String resource) {
    return customize(resource).toForkIdActivations().blockNumbers().stream()
        .filter(block -> block > 0L)
        .toList();
  }

  private ProtocolScheduleCustomization customize(final String resource) {
    final Optional<ProtocolScheduleCustomization> customization =
        customizer.customize(EtcGenesis.config(resource).getConfigOptions());
    assertThat(customization).as("customization for %s", resource).isPresent();
    return customization.orElseThrow();
  }
}
