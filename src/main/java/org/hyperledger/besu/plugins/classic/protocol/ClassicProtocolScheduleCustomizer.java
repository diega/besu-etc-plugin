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

import org.hyperledger.besu.config.ForkIdActivations;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolScheduleCustomizer;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpecBuilder;

import java.util.Map;
import java.util.function.Function;

/**
 * ETC protocol-schedule customizer. Contributes the ETC hardfork adapters and the matching EIP-2124
 * fork activations, so the advertised fork ID is derived from the same source that defines the ETC
 * fork boundaries.
 */
public class ClassicProtocolScheduleCustomizer implements ProtocolScheduleCustomizer {

  /** Creates a new {@code ClassicProtocolScheduleCustomizer}. */
  public ClassicProtocolScheduleCustomizer() {}

  @Override
  public Map<Long, Function<ProtocolSpecBuilder, ProtocolSpecBuilder>> createAdapters(
      final GenesisConfigOptions config) {
    return ClassicProtocolSpecs.createAdapters(config);
  }

  @Override
  public ForkIdActivations forkIdActivations(final GenesisConfigOptions config) {
    return config
        .getChainId()
        .flatMap(ClassicGenesisConfig::fromChainId)
        .map(etc -> ForkIdActivations.ofBlockNumbers(etc.getForkIdBlockNumbers()))
        .orElseGet(ForkIdActivations::empty);
  }
}
