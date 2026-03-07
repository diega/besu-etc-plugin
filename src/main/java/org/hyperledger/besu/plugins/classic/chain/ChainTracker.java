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
package org.hyperledger.besu.plugins.classic.chain;

import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.services.BlockchainService;

import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks the current chain head and derives depth-based safe/finalized labels for the Classic
 * plugin.
 *
 * <p>For PoW chains these labels are heuristic only: they are derived from canonical-chain depth
 * and do not provide absolute finality. A deep enough reorg can still replace either label.
 *
 * <p>The tracker maintains the current head and updates the safe/finalized labels based on
 * configurable depth parameters:
 *
 * <ul>
 *   <li>Safe block label: head - safeBlockDepth (default 24)
 *   <li>Finalized block label: head - finalizedBlockDepth (default 400)
 * </ul>
 */
public class ChainTracker {

  private static final Logger LOG = LoggerFactory.getLogger(ChainTracker.class);

  private final BlockchainService blockchainService;
  private final int safeBlockDepth;
  private final int finalizedBlockDepth;

  private final AtomicReference<BlockHeader> headRef = new AtomicReference<>();

  /**
   * Creates a new {@code ChainTracker}.
   *
   * @param blockchainService the blockchain service for block lookups
   * @param safeBlockDepth number of canonical confirmations used for the safe label
   * @param finalizedBlockDepth number of canonical confirmations used for the finalized label
   */
  public ChainTracker(
      final BlockchainService blockchainService,
      final int safeBlockDepth,
      final int finalizedBlockDepth) {
    this.blockchainService = blockchainService;
    this.safeBlockDepth = safeBlockDepth;
    this.finalizedBlockDepth = finalizedBlockDepth;
  }

  /** Seeds the tracker with the current chain head from the blockchain service. */
  public void seed() {
    final BlockHeader head = blockchainService.getChainHeadHeader();
    headRef.set(head);
    LOG.info("ChainTracker seeded at block {} ({})", head.getNumber(), head.getBlockHash());
    updateDepthBasedLabels(head);
  }

  /**
   * Updates the chain head and recalculates the depth-based safe/finalized labels.
   *
   * @param newHead the new chain head
   */
  public void updateHead(final BlockHeader newHead) {
    headRef.set(newHead);
    updateDepthBasedLabels(newHead);
  }

  private void updateDepthBasedLabels(final BlockHeader head) {
    final long headNumber = head.getNumber();

    // In PoW this is a local "sufficiently confirmed" label, not protocol finality.
    final long safeNumber = Math.max(0, headNumber - safeBlockDepth);
    blockchainService
        .getBlockByNumber(safeNumber)
        .ifPresent(
            ctx -> {
              try {
                blockchainService.setSafeBlock(ctx.getBlockHeader().getBlockHash());
              } catch (final Exception e) {
                LOG.debug("Could not set safe block at {}: {}", safeNumber, e.getMessage());
              }
            });

    // In PoW this is a deep-confirmation label, not an irrevocable finalized checkpoint.
    final long finalizedNumber = Math.max(0, headNumber - finalizedBlockDepth);
    blockchainService
        .getBlockByNumber(finalizedNumber)
        .ifPresent(
            ctx -> {
              try {
                blockchainService.setFinalizedBlock(ctx.getBlockHeader().getBlockHash());
              } catch (final Exception e) {
                LOG.debug(
                    "Could not set finalized block at {}: {}", finalizedNumber, e.getMessage());
              }
            });
  }
}
