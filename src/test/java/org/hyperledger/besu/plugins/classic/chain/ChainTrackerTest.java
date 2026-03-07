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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.plugin.data.BlockContext;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.services.BlockchainService;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChainTrackerTest {

  private BlockchainService blockchainService;
  private ChainTracker chainTracker;

  @BeforeEach
  void setUp() {
    blockchainService = mock(BlockchainService.class);
    when(blockchainService.getBlockByNumber(anyLong())).thenReturn(Optional.empty());
  }

  @Test
  void seedSetsHeadFromBlockchainService() {
    final BlockHeader head = mock(BlockHeader.class);
    when(head.getNumber()).thenReturn(500L);
    when(head.getBlockHash()).thenReturn(Hash.ZERO);
    when(blockchainService.getChainHeadHeader()).thenReturn(head);
    final BlockHeader safeHeader = mock(BlockHeader.class);
    final Hash safeHash = Hash.fromHexStringLenient("0x1");
    when(safeHeader.getBlockHash()).thenReturn(safeHash);
    final BlockContext safeContext = mock(BlockContext.class);
    when(safeContext.getBlockHeader()).thenReturn(safeHeader);
    when(blockchainService.getBlockByNumber(476L)).thenReturn(Optional.of(safeContext));
    final BlockHeader finalizedHeader = mock(BlockHeader.class);
    final Hash finalizedHash = Hash.fromHexStringLenient("0x2");
    when(finalizedHeader.getBlockHash()).thenReturn(finalizedHash);
    final BlockContext finalizedContext = mock(BlockContext.class);
    when(finalizedContext.getBlockHeader()).thenReturn(finalizedHeader);
    when(blockchainService.getBlockByNumber(100L)).thenReturn(Optional.of(finalizedContext));

    chainTracker = new ChainTracker(blockchainService, 24, 400);
    chainTracker.seed();

    verify(blockchainService).setSafeBlock(safeHash);
    verify(blockchainService).setFinalizedBlock(finalizedHash);
  }

  @Test
  void updateHeadRecalculatesDepthBasedLabels() {
    final BlockHeader initialHead = mock(BlockHeader.class);
    when(initialHead.getNumber()).thenReturn(100L);
    when(initialHead.getBlockHash()).thenReturn(Hash.ZERO);
    when(blockchainService.getChainHeadHeader()).thenReturn(initialHead);

    chainTracker = new ChainTracker(blockchainService, 24, 400);
    chainTracker.seed();

    final BlockHeader newHead = mock(BlockHeader.class);
    when(newHead.getNumber()).thenReturn(200L);
    when(newHead.getBlockHash()).thenReturn(Hash.ZERO);
    final BlockHeader safeHeader = mock(BlockHeader.class);
    final Hash safeHash = Hash.fromHexStringLenient("0x3");
    when(safeHeader.getBlockHash()).thenReturn(safeHash);
    final BlockContext safeContext = mock(BlockContext.class);
    when(safeContext.getBlockHeader()).thenReturn(safeHeader);
    when(blockchainService.getBlockByNumber(176L)).thenReturn(Optional.of(safeContext));

    chainTracker.updateHead(newHead);

    verify(blockchainService).setSafeBlock(safeHash);
    verify(blockchainService, never()).setFinalizedBlock(any());
  }

  @Test
  void updateHeadDoesNothingWhenDepthTargetsAreMissing() {
    final BlockHeader head = mock(BlockHeader.class);
    when(head.getNumber()).thenReturn(10L);
    when(head.getBlockHash()).thenReturn(Hash.ZERO);

    chainTracker = new ChainTracker(blockchainService, 24, 400);
    chainTracker.updateHead(head);

    verify(blockchainService, never()).setSafeBlock(any());
    verify(blockchainService, never()).setFinalizedBlock(any());
  }
}
