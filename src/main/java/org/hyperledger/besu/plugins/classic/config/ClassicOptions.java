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
package org.hyperledger.besu.plugins.classic.config;

import picocli.CommandLine;

/** PicoCLI options for the Classic plugin. */
public class ClassicOptions {

  /** Creates a new {@code ClassicOptions} with default values. */
  public ClassicOptions() {}

  @CommandLine.Option(
      names = {"--plugin-classic-safe-block-depth"},
      description =
          "Number of canonical confirmations used for the heuristic safe label in PoW (default: ${DEFAULT-VALUE})")
  private int safeBlockDepth = 24;

  @CommandLine.Option(
      names = {"--plugin-classic-finalized-block-depth"},
      description =
          "Number of canonical confirmations used for the heuristic finalized label in PoW (default: ${DEFAULT-VALUE})")
  private int finalizedBlockDepth = 400;

  /**
   * Returns the safe block depth.
   *
   * @return the number of canonical confirmations used for the heuristic safe label
   */
  public int getSafeBlockDepth() {
    return safeBlockDepth;
  }

  /**
   * Returns the finalized block depth.
   *
   * @return the number of canonical confirmations used for the heuristic finalized label
   */
  public int getFinalizedBlockDepth() {
    return finalizedBlockDepth;
  }
}
