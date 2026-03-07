# Besu ETC Plugin

Ethereum Classic ([ETC](https://ethereumclassic.org/)) support plugin for [Hyperledger Besu](https://github.com/hyperledger/besu). It customizes Besu's native protocol schedule and network compatibility behavior for ETC while keeping sync, import, and block validation in the upstream execution pipeline.

## Architecture

```
ClassicPlugin (lifecycle & wiring)
├── Chain
│   └── ChainTracker         — depth-based safe/finalized labels
├── Protocol
│   ├── ClassicProtocolSpecs — ETC hardfork schedule (Frontier → Magneto)
│   ├── ClassicDifficultyCalculators — per-era difficulty formulas
│   ├── ClassicBlockProcessor— block rewards (5 → 3.2 → 2.56 ETH)
│   ├── ClassicEVMs          — EVM opcodes per hardfork
│   ├── ClassicGenesisConfig — genesis JSON handling (fork blocks, era rounds)
│   └── ClassicForkIdProvider— EIP-2124 fork IDs with ECIP-1091 exclusions
└── ClassicNetworkProvider   — registers --network=classic / --network=mordor
```

## Prerequisites

This plugin requires a fork of Besu with plugin extension points for external consensus layers. The fork lives at [diega/besu](https://github.com/diega/besu) on the `plugin-extensions` branch.

## Building

### Local development (composite build)

1. Clone the Besu fork next to this repository:
   ```bash
   git clone -b plugin-extensions https://github.com/diega/besu.git ../besu
   ```

2. Create `local.properties`:
   ```bash
   cp local.properties.example local.properties
   # Edit besuDir if your Besu checkout is elsewhere
   ```

3. Build:
   ```bash
   ./gradlew compileJava   # compile
   ./gradlew test          # run tests
   ./gradlew jar           # produce plugin JAR
   ```

With composite build, IntelliJ can navigate (Ctrl+click) directly into Besu fork sources.

### CI build (mavenLocal)

Without `local.properties`, the build resolves dependencies from `mavenLocal` and remote Maven repositories:

```bash
# In the Besu fork checkout:
cd ../besu
./gradlew publishToMavenLocal -x test -x javadoc

# Then build the plugin:
cd ../besu-etc-plugin
./gradlew build
```

## Running

Copy the plugin JAR into Besu's `plugins/` directory:

```bash
cp build/libs/besu-etc-plugin-*.jar ../besu/build/install/besu/plugins/
```

Then start Besu with the ETC network:

```bash
../besu/build/install/besu/bin/besu --network=classic --data-path=data --Xeth-capability-max=68
```

**Note:** ETC networks require `--Xeth-capability-max=68` because ETC peers only support eth/68 and below (eth/69+ removed Total Difficulty from the handshake, which ETC still needs as a PoW chain).

## Configuration

```
--network=classic                                  # ETC mainnet (chain ID 61)
--network=mordor                                   # Mordor testnet (chain ID 63)

--Xeth-capability-max=68                           # Required: cap eth protocol to eth/68 (ETC is PoW, needs TD)

--plugin-classic-safe-block-depth=24               # Confirmation depth for "safe" (default: 24)
--plugin-classic-finalized-block-depth=400         # Deep-confirmation depth for "finalized" (default: 400)
```

## Release

Pushing a tag `v*` triggers the GitHub Actions release workflow, which:
1. Checks out the Besu fork and publishes to mavenLocal
2. Builds the plugin
3. Builds a Besu distribution with the plugin included
4. Creates a GitHub Release with the tarball
