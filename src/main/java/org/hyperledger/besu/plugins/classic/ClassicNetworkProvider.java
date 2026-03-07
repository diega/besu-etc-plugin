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

import org.hyperledger.besu.plugin.services.NetworkProvider;

import java.math.BigInteger;
import java.net.URL;
import java.util.Locale;
import java.util.Set;

/** Provides network definitions for Ethereum Classic networks. */
public class ClassicNetworkProvider implements NetworkProvider {

  /** Creates a new {@code ClassicNetworkProvider}. */
  public ClassicNetworkProvider() {}

  @Override
  public Set<String> supportedNetworks() {
    return Set.of("classic", "mordor");
  }

  @Override
  public URL genesisConfig(final String networkName) {
    return switch (networkName.toLowerCase(Locale.ROOT)) {
      case "classic" -> getClass().getResource("/classic.json");
      case "mordor" -> getClass().getResource("/mordor.json");
      default -> throw new IllegalArgumentException("Unsupported network: " + networkName);
    };
  }

  @Override
  public BigInteger networkId(final String networkName) {
    return switch (networkName.toLowerCase(Locale.ROOT)) {
      case "classic" -> BigInteger.ONE;
      case "mordor" -> BigInteger.valueOf(7);
      default -> throw new IllegalArgumentException("Unsupported network: " + networkName);
    };
  }

  @Override
  public BigInteger chainId(final String networkName) {
    return switch (networkName.toLowerCase(Locale.ROOT)) {
      case "classic" -> BigInteger.valueOf(61);
      case "mordor" -> BigInteger.valueOf(63);
      default -> throw new IllegalArgumentException("Unsupported network: " + networkName);
    };
  }

  @Override
  public boolean canSnapSync(final String networkName) {
    return switch (networkName.toLowerCase(Locale.ROOT)) {
      case "classic", "mordor" -> true;
      default -> throw new IllegalArgumentException("Unsupported network: " + networkName);
    };
  }
}
