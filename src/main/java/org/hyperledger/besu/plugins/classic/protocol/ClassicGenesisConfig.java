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

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Parses ETC-specific genesis config fields directly from the plugin's bundled JSON resources.
 *
 * <p>This bypasses {@code GenesisConfigOptions.asMap()} which only emits mainnet-known keys, making
 * ETC-only keys (dieHardBlock, gothamBlock, etc.) invisible. Instead, the plugin reads its own
 * classic.json / mordor.json to extract all fork block numbers.
 */
public final class ClassicGenesisConfig {

  private static final BigInteger CLASSIC_CHAIN_ID = BigInteger.valueOf(61);
  private static final BigInteger MORDOR_CHAIN_ID = BigInteger.valueOf(63);

  /**
   * Keys excluded from fork ID computation per ECIP-1091. The classicForkBlock (The DAO fork
   * divergence point) is not a consensus fork on ETC and must not be included in EIP-2124 fork IDs.
   */
  private static final Set<String> FORK_ID_EXCLUDED_KEYS = Set.of("classicForkBlock");

  private final Map<String, Long> blockNumbers;
  private final OptionalLong eraRounds;

  /**
   * Returns true if the given chain ID is an ETC network (mainnet or Mordor).
   *
   * @param chainId the chain ID to check
   * @return true if ETC chain ID
   */
  public static boolean isEtcChainId(final BigInteger chainId) {
    return CLASSIC_CHAIN_ID.equals(chainId) || MORDOR_CHAIN_ID.equals(chainId);
  }

  /**
   * Creates a ClassicGenesisConfig by reading the bundled genesis JSON for the given chain ID.
   * Returns empty for non-ETC chain IDs instead of throwing.
   *
   * @param chainId the chain ID (61 for ETC mainnet, 63 for Mordor)
   * @return the parsed config, or empty if not an ETC chain
   */
  public static Optional<ClassicGenesisConfig> fromChainId(final BigInteger chainId) {
    final String resource;
    if (CLASSIC_CHAIN_ID.equals(chainId)) {
      resource = "/classic.json";
    } else if (MORDOR_CHAIN_ID.equals(chainId)) {
      resource = "/mordor.json";
    } else {
      return Optional.empty();
    }
    try (InputStream is = ClassicGenesisConfig.class.getResourceAsStream(resource)) {
      if (is == null) {
        throw new IllegalStateException("Genesis resource not found: " + resource);
      }
      return Optional.of(new ClassicGenesisConfig(new ObjectMapper().readTree(is).get("config")));
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read genesis resource: " + resource, e);
    }
  }

  private ClassicGenesisConfig(final JsonNode configNode) {
    this.blockNumbers = new HashMap<>();
    configNode
        .fields()
        .forEachRemaining(
            entry -> {
              if (entry.getValue().isNumber() && entry.getKey().endsWith("Block")) {
                blockNumbers.put(entry.getKey(), entry.getValue().longValue());
              }
            });
    this.eraRounds =
        configNode.has("ecip1017EraRounds")
            ? OptionalLong.of(configNode.get("ecip1017EraRounds").longValue())
            : OptionalLong.empty();
  }

  /**
   * Returns the dieHardBlock number.
   *
   * @return the block number, or empty if not set
   */
  public OptionalLong getDieHardBlock() {
    return getBlock("dieHardBlock");
  }

  /**
   * Returns the gothamBlock number.
   *
   * @return the block number, or empty if not set
   */
  public OptionalLong getGothamBlock() {
    return getBlock("gothamBlock");
  }

  /**
   * Returns the ecip1041Block (Defuse Difficulty Bomb) number.
   *
   * @return the block number, or empty if not set
   */
  public OptionalLong getEcip1041Block() {
    return getBlock("ecip1041Block");
  }

  /**
   * Returns the thanosBlock number.
   *
   * @return the block number, or empty if not set
   */
  public OptionalLong getThanosBlock() {
    return getBlock("thanosBlock");
  }

  /**
   * Returns the mystiqueBlock number.
   *
   * @return the block number, or empty if not set
   */
  public OptionalLong getMystiqueBlock() {
    return getBlock("mystiqueBlock");
  }

  /**
   * Returns the spiralBlock number.
   *
   * @return the block number, or empty if not set
   */
  public OptionalLong getSpiralBlock() {
    return getBlock("spiralBlock");
  }

  /**
   * Returns the ECIP-1017 era rounds value.
   *
   * @return the era rounds, or empty if not set
   */
  public OptionalLong getEcip1017EraRounds() {
    return eraRounds;
  }

  /**
   * Returns fork block numbers for EIP-2124 fork ID computation, sorted and distinct. Excludes
   * classicForkBlock (The DAO divergence point) per ECIP-1091.
   *
   * @return sorted, distinct list of fork block numbers for fork ID
   */
  public List<Long> getForkIdBlockNumbers() {
    return blockNumbers.entrySet().stream()
        .filter(e -> !FORK_ID_EXCLUDED_KEYS.contains(e.getKey()))
        .map(Map.Entry::getValue)
        .distinct()
        .sorted()
        .toList();
  }

  private OptionalLong getBlock(final String key) {
    final Long value = blockNumbers.get(key);
    return value != null ? OptionalLong.of(value) : OptionalLong.empty();
  }
}
