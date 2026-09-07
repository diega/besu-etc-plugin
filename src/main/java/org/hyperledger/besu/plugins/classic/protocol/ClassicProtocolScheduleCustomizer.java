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

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolScheduleCustomization;
import org.hyperledger.besu.ethereum.mainnet.ProtocolScheduleCustomizer;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpecModification;

import java.util.List;
import java.util.Optional;

/**
 * ETC protocol-schedule customizer. Contributes the ETC hardfork rules; Besu derives the EIP-2124
 * fork ID from the activations those rules carry, so the schedule the node runs and the one it
 * advertises cannot state different boundaries.
 */
public class ClassicProtocolScheduleCustomizer implements ProtocolScheduleCustomizer {

  /** Creates a new {@code ClassicProtocolScheduleCustomizer}. */
  public ClassicProtocolScheduleCustomizer() {}

  @Override
  public Optional<ProtocolScheduleCustomization> customize(final GenesisConfigOptions config) {
    final List<ProtocolSpecModification> modifications =
        ClassicProtocolSpecs.createModifications(config);
    return modifications.isEmpty()
        ? Optional.empty()
        : Optional.of(new ProtocolScheduleCustomization("classic", modifications));
  }
}
