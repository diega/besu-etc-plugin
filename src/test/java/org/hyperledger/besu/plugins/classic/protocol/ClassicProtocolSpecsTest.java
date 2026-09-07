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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.config.GenesisConfig;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.ethereum.mainnet.DifficultyCalculator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolScheduleActivation;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpecBuilder;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpecModification;

import java.util.List;
import java.util.NavigableMap;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

class ClassicProtocolSpecsTest {

  @Test
  void returnsNoErasForNonEtcChains() {
    final GenesisConfigOptions config =
        GenesisConfig.fromConfig("{\"config\":{\"chainId\":1}}").getConfigOptions();

    assertThat(ClassicProtocolSpecs.createEras(config)).isEmpty();
    assertThat(ClassicProtocolSpecs.createModifications(config)).isEmpty();
  }

  @Test
  void opensAnEraAtEachEtcRuleChange() {
    assertThat(ClassicProtocolSpecs.createEras(EtcGenesis.mainnet()).keySet())
        .containsExactly(
            0L,
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
  void appliesExpectedDifficultyCalculatorAtEachEtcEra() {
    final NavigableMap<Long, Function<ProtocolSpecBuilder, ProtocolSpecBuilder>> eras =
        ClassicProtocolSpecs.createEras(EtcGenesis.mainnet());

    assertDifficultyAt(eras, 3_000_000L, ClassicDifficultyCalculators.DIFFICULTY_BOMB_PAUSED);
    assertDifficultyAt(eras, 5_000_000L, ClassicDifficultyCalculators.DIFFICULTY_BOMB_DELAYED);
    assertDifficultyAt(eras, 5_900_000L, ClassicDifficultyCalculators.DIFFICULTY_BOMB_REMOVED);
    assertDifficultyAt(eras, 8_772_000L, ClassicDifficultyCalculators.EIP100);
    assertDifficultyAt(eras, 11_700_000L, ClassicDifficultyCalculators.EIP100);
    assertDifficultyAt(eras, 14_525_000L, ClassicDifficultyCalculators.EIP100);
    assertDifficultyAt(eras, 19_250_000L, ClassicDifficultyCalculators.EIP100);
  }

  @Test
  void aForkThatChangesNoRuleRestatesTheEraItFallsIn() {
    final List<ProtocolSpecModification> modifications =
        ClassicProtocolSpecs.createModifications(EtcGenesis.mainnet());

    // Homestead restates Frontier; Agharta and Phoenix restate Atlantis; Magneto restates Thanos.
    // Declaring them as identity instead would drop the rules of the era they open in.
    assertThat(modifierAt(modifications, 1_150_000L)).isSameAs(modifierAt(modifications, 0L));
    assertThat(modifierAt(modifications, 9_573_000L)).isSameAs(modifierAt(modifications, 8_772_000L));
    assertThat(modifierAt(modifications, 10_500_839L))
        .isSameAs(modifierAt(modifications, 8_772_000L));
    assertThat(modifierAt(modifications, 13_189_133L))
        .isSameAs(modifierAt(modifications, 11_700_000L));
  }

  private static Object modifierAt(
      final List<ProtocolSpecModification> modifications, final long block) {
    return modifications.stream()
        .filter(
            modification ->
                modification.activation() instanceof ProtocolScheduleActivation.BlockNumber
                    && modification.activation().value() == block)
        .findFirst()
        .orElseThrow(() -> new AssertionError("no modification at block " + block))
        .modifier();
  }

  private static void assertDifficultyAt(
      final NavigableMap<Long, Function<ProtocolSpecBuilder, ProtocolSpecBuilder>> eras,
      final long milestone,
      final DifficultyCalculator expectedCalculator) {
    final Function<ProtocolSpecBuilder, ProtocolSpecBuilder> era = eras.get(milestone);
    assertThat(era).as("era at block %d", milestone).isNotNull();

    final ProtocolSpecBuilder builder = mock(ProtocolSpecBuilder.class, RETURNS_SELF);
    final ProtocolSpecBuilder updatedBuilder = era.apply(builder);
    assertThat(updatedBuilder).isSameAs(builder);

    verify(builder).difficultyCalculator(expectedCalculator);
  }
}
