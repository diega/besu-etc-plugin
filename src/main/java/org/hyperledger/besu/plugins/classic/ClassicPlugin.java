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

import org.hyperledger.besu.ethereum.mainnet.ProtocolScheduleCustomizer;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.services.BesuEvents;
import org.hyperledger.besu.plugin.services.BlockchainService;
import org.hyperledger.besu.plugin.services.ForkIdProvider;
import org.hyperledger.besu.plugin.services.NetworkProvider;
import org.hyperledger.besu.plugin.services.PicoCLIOptions;
import org.hyperledger.besu.plugins.classic.chain.ChainTracker;
import org.hyperledger.besu.plugins.classic.config.ClassicOptions;
import org.hyperledger.besu.plugins.classic.protocol.ClassicForkIdProvider;
import org.hyperledger.besu.plugins.classic.protocol.ClassicProtocolSpecs;

import java.math.BigInteger;

import com.google.auto.service.AutoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Classic Plugin — Ethereum Classic (ETC) support for Hyperledger Besu.
 *
 * <p>This plugin configures ETC networks (mainnet chain ID 61, Mordor testnet chain ID 63) by
 * wiring ETC-specific protocol rules and network compatibility settings into Besu's native
 * execution stack. It manages:
 *
 * <ul>
 *   <li>Protocol schedule customization for ETC hardfork rules
 *   <li>Network compatibility services such as fork ID and eth capability restriction
 *   <li>Depth-based safe/finalized label tracking for PoW
 * </ul>
 */
@AutoService(BesuPlugin.class)
public class ClassicPlugin implements BesuPlugin {

  /** Creates a new {@code ClassicPlugin}. */
  public ClassicPlugin() {}

  private static final Logger LOG = LoggerFactory.getLogger(ClassicPlugin.class);

  /** ETC mainnet chain ID. */
  private static final BigInteger CLASSIC_CHAIN_ID = BigInteger.valueOf(61);

  /** Mordor testnet chain ID. */
  private static final BigInteger MORDOR_CHAIN_ID = BigInteger.valueOf(63);

  private ServiceManager serviceManager;
  private final ClassicOptions options = new ClassicOptions();

  @Override
  public String getName() {
    return "classic";
  }

  @Override
  public void register(final ServiceManager context) {
    this.serviceManager = context;

    // Register network definitions for --network=classic / --network=mordor.
    context.addService(NetworkProvider.class, new ClassicNetworkProvider());

    // Inject ETC-specific protocol schedule adapters into Besu's native validation/import flow.
    context.addService(ProtocolScheduleCustomizer.class, ClassicProtocolSpecs::createAdapters);

    // Provide ETC-specific fork blocks for EIP-2124 fork ID computation.
    context.addService(ForkIdProvider.class, new ClassicForkIdProvider());

    // Register plugin-specific CLI options.
    context
        .getService(PicoCLIOptions.class)
        .ifPresent(opts -> opts.addPicoCLIOptions("classic", options));

    LOG.info("Classic plugin registered");
  }

  @Override
  public void start() {
    // Get required services
    final BlockchainService blockchainService =
        serviceManager
            .getService(BlockchainService.class)
            .orElseThrow(() -> new RuntimeException("BlockchainService not available"));
    final BesuEvents besuEvents = serviceManager.getService(BesuEvents.class).orElse(null);

    // Only activate tracking for ETC networks served by this plugin.
    final var chainId = blockchainService.getChainId();
    if (chainId.isEmpty()
        || (!chainId.get().equals(CLASSIC_CHAIN_ID) && !chainId.get().equals(MORDOR_CHAIN_ID))) {
      LOG.info(
          "Classic plugin inactive: chain ID {} is not ETC (61) or Mordor (63)",
          chainId.orElse(BigInteger.ZERO));
      return;
    }

    final long chainIdLong = chainId.get().longValue();
    LOG.info("Classic plugin activating for chain ID {}", chainIdLong);

    // Initialize depth-based safe/finalized labels from the current canonical head.
    final ChainTracker chainTracker =
        new ChainTracker(
            blockchainService, options.getSafeBlockDepth(), options.getFinalizedBlockDepth());
    chainTracker.seed();

    // Keep the depth-based labels in sync as Besu imports new canonical blocks.
    if (besuEvents != null) {
      besuEvents.addBlockAddedListener(
          addedBlockContext -> {
            chainTracker.updateHead(addedBlockContext.getBlockHeader());
          });
    }

    LOG.info("Classic plugin started");
  }

  @Override
  public void stop() {
    LOG.info("Classic plugin stopped");
  }
}
