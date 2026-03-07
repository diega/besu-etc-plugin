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

import org.hyperledger.besu.plugin.services.ForkIdProvider.ForkSchedule;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

class ClassicForkIdProviderTest {

  private final ClassicForkIdProvider provider = new ClassicForkIdProvider();

  @Test
  void providesScheduleOnlyForEtcChains() {
    assertThat(provider.forkScheduleFor(BigInteger.valueOf(61))).isPresent(); // ETC
    assertThat(provider.forkScheduleFor(BigInteger.valueOf(63))).isPresent(); // Mordor
    assertThat(provider.forkScheduleFor(BigInteger.ONE)).isEmpty(); // ETH mainnet -> use genesis
  }

  @Test
  void returnsEtcForkBlocksAndNoTimestamps() {
    final ForkSchedule schedule = provider.forkScheduleFor(BigInteger.valueOf(61)).orElseThrow();

    assertThat(schedule.blockNumbers()).contains(2_500_000L);
    assertThat(schedule.blockNumbers()).doesNotContain(1_920_000L); // DAO point not in the genesis
    assertThat(schedule.timestamps()).isEmpty();
  }
}
