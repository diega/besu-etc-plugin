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

import java.math.BigInteger;
import java.util.OptionalLong;

import org.junit.jupiter.api.Test;

/**
 * Pins the ECIP-1017 era length and key fork blocks read from the bundled genesis resources.
 *
 * <p>The era length is a consensus value duplicated between the genesis JSON and the {@code
 * DEFAULT_ERA_LENGTH = 5_000_000} fallback in code. For Mordor the genesis value (2,000,000) differs
 * from that fallback, so if the {@code ecip1017EraRounds} key were ever dropped from mordor.json the
 * wrong era length would be applied silently. These tests fail if that key disappears or changes.
 */
class ClassicGenesisConfigTest {

  private static final BigInteger CLASSIC = BigInteger.valueOf(61);
  private static final BigInteger MORDOR = BigInteger.valueOf(63);

  @Test
  void classicEraRoundsIsFiveMillion() {
    assertThat(configFor(CLASSIC).getEcip1017EraRounds()).isEqualTo(OptionalLong.of(5_000_000L));
  }

  @Test
  void mordorEraRoundsIsTwoMillion() {
    // Distinct from the 5_000_000 code fallback: this is exactly the value that must not regress.
    assertThat(configFor(MORDOR).getEcip1017EraRounds()).isEqualTo(OptionalLong.of(2_000_000L));
  }

  @Test
  void forkBlocksMatchGenesis() {
    final ClassicGenesisConfig config = configFor(CLASSIC);
    assertThat(config.getDieHardBlock()).isEqualTo(OptionalLong.of(3_000_000L));
    assertThat(config.getGothamBlock()).isEqualTo(OptionalLong.of(5_000_000L));
    assertThat(config.getEcip1041Block()).isEqualTo(OptionalLong.of(5_900_000L));
    assertThat(config.getThanosBlock()).isEqualTo(OptionalLong.of(11_700_000L));
    assertThat(config.getMystiqueBlock()).isEqualTo(OptionalLong.of(14_525_000L));
    assertThat(config.getSpiralBlock()).isEqualTo(OptionalLong.of(19_250_000L));
  }

  @Test
  void nonEtcChainHasNoConfig() {
    assertThat(ClassicGenesisConfig.fromChainId(BigInteger.ONE)).isEmpty();
  }

  private static ClassicGenesisConfig configFor(final BigInteger chainId) {
    return ClassicGenesisConfig.fromChainId(chainId)
        .orElseThrow(() -> new AssertionError("expected ETC config for chainId " + chainId));
  }
}
