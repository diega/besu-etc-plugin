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

import org.hyperledger.besu.datatypes.NetworkSpec;
import org.hyperledger.besu.plugin.services.NetworkProvider;

import java.math.BigInteger;
import java.net.URL;
import java.util.Locale;
import java.util.Optional;

/** Provides network definitions for Ethereum Classic networks. */
public class ClassicNetworkProvider implements NetworkProvider {

  /** Creates a new {@code ClassicNetworkProvider}. */
  public ClassicNetworkProvider() {}

  @Override
  public Optional<NetworkSpec> findNetwork(final String networkName) {
    return switch (networkName.toLowerCase(Locale.ROOT)) {
      case "classic" -> Optional.of(spec(BigInteger.ONE, "/classic.json"));
      case "mordor" -> Optional.of(spec(BigInteger.valueOf(7), "/mordor.json"));
      default -> Optional.empty();
    };
  }

  private NetworkSpec spec(final BigInteger networkId, final String genesisResource) {
    return new ClassicNetworkSpec(networkId, getClass().getResource(genesisResource), true);
  }

  /** The chain ID is read from the genesis config; ETC networks support snap sync. */
  private record ClassicNetworkSpec(
      BigInteger getNetworkId, URL getGenesisConfigUrl, boolean canSnapSync)
      implements NetworkSpec {}
}
