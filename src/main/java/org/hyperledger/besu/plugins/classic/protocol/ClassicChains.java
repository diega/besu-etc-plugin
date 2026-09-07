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

import java.math.BigInteger;

/** The chains this plugin claims. */
public final class ClassicChains {

  private static final BigInteger CLASSIC_CHAIN_ID = BigInteger.valueOf(61);
  private static final BigInteger MORDOR_CHAIN_ID = BigInteger.valueOf(63);

  private ClassicChains() {}

  /**
   * Returns true if the given chain ID is an ETC network (mainnet or Mordor).
   *
   * @param chainId the chain ID to check
   * @return true if ETC chain ID
   */
  public static boolean isEtcChainId(final BigInteger chainId) {
    return CLASSIC_CHAIN_ID.equals(chainId) || MORDOR_CHAIN_ID.equals(chainId);
  }
}
