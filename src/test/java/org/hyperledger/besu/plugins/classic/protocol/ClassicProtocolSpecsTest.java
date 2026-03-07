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
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.ethereum.mainnet.DifficultyCalculator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpecBuilder;

import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

class ClassicProtocolSpecsTest {

  @Test
  void returnsNoAdaptersForNonEtcChains() {
    final GenesisConfigOptions config = mockConfig(BigInteger.ONE);

    final Map<Long, Function<ProtocolSpecBuilder, ProtocolSpecBuilder>> adapters =
        ClassicProtocolSpecs.createAdapters(config);

    assertThat(adapters).isEmpty();
  }

  @Test
  void includesDifficultyMilestonesForEtcMainnet() {
    final GenesisConfigOptions config = mockConfig(BigInteger.valueOf(61));

    final Map<Long, Function<ProtocolSpecBuilder, ProtocolSpecBuilder>> adapters =
        ClassicProtocolSpecs.createAdapters(config);

    assertThat(adapters.keySet())
        .contains(
            2_500_000L,
            3_000_000L,
            5_000_000L,
            5_900_000L,
            8_772_000L,
            11_700_000L,
            14_525_000L,
            19_250_000L);
  }

  @Test
  void appliesExpectedDifficultyCalculatorAtEachEtcMilestone() {
    final GenesisConfigOptions config = mockConfig(BigInteger.valueOf(61));
    final Map<Long, Function<ProtocolSpecBuilder, ProtocolSpecBuilder>> adapters =
        ClassicProtocolSpecs.createAdapters(config);

    assertDifficultyAt(adapters, 3_000_000L, ClassicDifficultyCalculators.DIFFICULTY_BOMB_PAUSED);
    assertDifficultyAt(adapters, 5_000_000L, ClassicDifficultyCalculators.DIFFICULTY_BOMB_DELAYED);
    assertDifficultyAt(adapters, 5_900_000L, ClassicDifficultyCalculators.DIFFICULTY_BOMB_REMOVED);
    assertDifficultyAt(adapters, 8_772_000L, ClassicDifficultyCalculators.EIP100);
    assertDifficultyAt(adapters, 11_700_000L, ClassicDifficultyCalculators.EIP100);
    assertDifficultyAt(adapters, 14_525_000L, ClassicDifficultyCalculators.EIP100);
    assertDifficultyAt(adapters, 19_250_000L, ClassicDifficultyCalculators.EIP100);
  }

  private static GenesisConfigOptions mockConfig(final BigInteger chainId) {
    final GenesisConfigOptions config = mock(GenesisConfigOptions.class);
    when(config.getChainId()).thenReturn(Optional.of(chainId));
    when(config.getTangerineWhistleBlockNumber()).thenReturn(OptionalLong.of(2_500_000L));
    when(config.getByzantiumBlockNumber()).thenReturn(OptionalLong.of(8_772_000L));
    return config;
  }

  private static void assertDifficultyAt(
      final Map<Long, Function<ProtocolSpecBuilder, ProtocolSpecBuilder>> adapters,
      final long milestone,
      final DifficultyCalculator expectedCalculator) {
    final Function<ProtocolSpecBuilder, ProtocolSpecBuilder> adapter = adapters.get(milestone);
    assertThat(adapter).as("adapter at block %d", milestone).isNotNull();

    final ProtocolSpecBuilder builder = mock(ProtocolSpecBuilder.class, RETURNS_SELF);
    final ProtocolSpecBuilder updatedBuilder = adapter.apply(builder);
    assertThat(updatedBuilder).isSameAs(builder);

    verify(builder).difficultyCalculator(expectedCalculator);
  }
}
