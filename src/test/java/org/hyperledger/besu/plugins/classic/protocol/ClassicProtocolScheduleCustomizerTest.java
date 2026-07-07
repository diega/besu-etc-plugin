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

import org.hyperledger.besu.config.ForkIdActivations;
import org.hyperledger.besu.config.GenesisConfig;

import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

class ClassicProtocolScheduleCustomizerTest {

  private final ClassicProtocolScheduleCustomizer customizer =
      new ClassicProtocolScheduleCustomizer();

  @Test
  void contributesEtcForkBlocksAndNoTimestamps() {
    final ForkIdActivations activations =
        customizer.forkIdActivations(genesisFromResource("/classic.json").getConfigOptions());

    assertThat(activations.blockNumbers()).contains(2_500_000L);
    assertThat(activations.blockNumbers()).doesNotContain(1_920_000L); // DAO point not in ETC
    assertThat(activations.timestamps()).isEmpty();
  }

  @Test
  void contributesNothingForNonEtcChains() {
    final ForkIdActivations activations =
        customizer.forkIdActivations(
            GenesisConfig.fromConfig("{\"config\":{\"chainId\":1}}").getConfigOptions());

    assertThat(activations.blockNumbers()).isEmpty();
    assertThat(activations.timestamps()).isEmpty();
  }

  /**
   * The fork ID Besu derives from the customizer must be identical to the one the plugin declared
   * before the fork-ID-from-rules design: folding the customizer's activations into the ETC genesis
   * must yield exactly {@link ClassicGenesisConfig#getForkIdBlockNumbers()}. This holds only if the
   * genesis config's own recognized fork blocks are a subset of that authoritative list — if Besu
   * ever picked up an extra fork key, the advertised fork ID would drift and this test would fail.
   */
  @Test
  void foldedForkIdBlocksMatchTheAuthoritativeEtcSchedule() {
    final GenesisConfig genesis = genesisFromResource("/classic.json");
    final ForkIdActivations activations =
        customizer.forkIdActivations(genesis.getConfigOptions());

    final List<Long> foldedForkBlocks =
        genesis.withAdditionalForkIdActivations(activations).getConfigOptions().getForkBlockNumbers();

    final List<Long> authoritative =
        ClassicGenesisConfig.fromChainId(BigInteger.valueOf(61))
            .orElseThrow()
            .getForkIdBlockNumbers();

    assertThat(foldedForkBlocks).isEqualTo(authoritative);
  }

  private static GenesisConfig genesisFromResource(final String resource) {
    try (InputStream is = ClassicProtocolScheduleCustomizerTest.class.getResourceAsStream(resource)) {
      return GenesisConfig.fromConfig(new String(is.readAllBytes(), StandardCharsets.UTF_8));
    } catch (final Exception e) {
      throw new IllegalStateException("Unable to load genesis resource " + resource, e);
    }
  }
}
