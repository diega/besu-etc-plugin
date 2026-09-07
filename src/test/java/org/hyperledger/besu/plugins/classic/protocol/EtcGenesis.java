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

import org.hyperledger.besu.config.GenesisConfig;
import org.hyperledger.besu.config.GenesisConfigOptions;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Loads the genesis files the plugin ships. The plugin reads its ETC activation keys from the
 * config the node was started with, so tests run against the same file an operator points Besu at
 * rather than a hand-written subset of it.
 */
final class EtcGenesis {

  private EtcGenesis() {}

  static GenesisConfig config(final String resource) {
    try (InputStream is = EtcGenesis.class.getResourceAsStream(resource)) {
      if (is == null) {
        throw new IllegalStateException("Genesis resource not found: " + resource);
      }
      return GenesisConfig.fromConfig(new String(is.readAllBytes(), StandardCharsets.UTF_8));
    } catch (final Exception e) {
      throw new IllegalStateException("Unable to load genesis resource " + resource, e);
    }
  }

  static GenesisConfigOptions mainnet() {
    return config("/classic.json").getConfigOptions();
  }

  static GenesisConfigOptions mordor() {
    return config("/mordor.json").getConfigOptions();
  }
}
