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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.Test;

class ClassicForkIdProviderTest {

  private final ClassicForkIdProvider provider = new ClassicForkIdProvider();

  @Test
  void supportsOnlyEtcChains() {
    assertThat(provider.supportsChainId(BigInteger.valueOf(61))).isTrue();
    assertThat(provider.supportsChainId(BigInteger.valueOf(63))).isTrue();
    assertThat(provider.supportsChainId(BigInteger.ONE)).isFalse();
  }

  @Test
  void returnsEtcForkBlocksAndExcludesClassicForkDivergencePoint() {
    final List<Long> forks = provider.getForkBlockNumbers(BigInteger.valueOf(61));

    assertThat(forks).isNotEmpty();
    assertThat(forks).contains(2_500_000L);
    assertThat(forks).doesNotContain(1_920_000L);
  }

  @Test
  void failsFastForNonEtcChains() {
    assertThatThrownBy(() -> provider.getForkBlockNumbers(BigInteger.ONE))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> provider.getForkTimestamps(BigInteger.ONE))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
