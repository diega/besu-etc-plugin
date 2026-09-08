# Besu ETC Plugin

Ethereum Classic ([ETC](https://ethereumclassic.org/)) support plugin for [Hyperledger Besu](https://github.com/hyperledger/besu). It contributes ETC's protocol rules to Besu's native protocol schedule while keeping sync, import, and block validation in the upstream execution pipeline.

## Architecture

```
ClassicPlugin (lifecycle & wiring)
├── Chain
│   └── ChainTracker         — depth-based safe/finalized labels
└── Protocol
    ├── ClassicProtocolScheduleCustomizer — what Besu asks for the chain's rules
    ├── ClassicProtocolSpecs — ETC hardfork schedule (Frontier → Spiral)
    ├── ClassicDifficultyCalculators — per-era difficulty formulas
    ├── ClassicBlockProcessor— block rewards (5 → 3.2 → 2.56 ETH)
    ├── ClassicEVMs          — EVM opcodes per hardfork
    └── ClassicChains        — the chain IDs this plugin claims
```

The plugin declares one modification per fork ETC announces, and Besu derives the EIP-2124 fork ID
from those same activations. Forks that change no ETC rule (Homestead, Agharta, Phoenix, Magneto)
restate the rules of the era they fall in, so the schedule and the advertised fork ID cannot state
different boundaries. `ClassicProtocolScheduleCustomizerTest` pins both against core-geth's own fork
ID vectors.

Network naming is not the plugin's: genesis, network id and the eth capability cap come from a Besu
profile (see `dist/profiles`), which is the mechanism Besu defines for a network it does not ship.

## Prerequisites

This plugin needs a Besu that carries the protocol-schedule customization extension point
(`ProtocolScheduleService`, `ProtocolScheduleCustomizer`) and the generic genesis accessor the
customizer reads its chain's keys through (`getCustomConfigLong`). Those are proposed upstream as
three separate branches of [diega/besu](https://github.com/diega/besu), so build against
`etc-integration`, which is the three of them stacked:

| branch | what it carries |
| --- | --- |
| `fix/eth-config-next-fork` | `eth_config` no longer reports a block milestone as the next fork |
| `feat/genesis-custom-config-long` | `GenesisConfigOptions.getCustomConfigLong` |
| `pr/protocol-schedule-customization` | the extension point itself |
| `etc-integration` | all three, and what this plugin builds against |

## Building

### Local development (composite build)

1. Clone the Besu fork next to this repository:
   ```bash
   git clone -b etc-integration https://github.com/diega/besu.git ../besu
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

Install the plugin JAR, a genesis file and a profile:

```bash
BESU=../besu/build/install/besu
cp build/libs/besu-etc-plugin-*.jar "$BESU/plugins/"
cp src/main/resources/classic.json  "$BESU/etc/"
cp dist/profiles/classic.toml       "$BESU/profiles/"
```

Besu resolves `genesis-file` relative to the working directory rather than to the profile, so edit
the path in `classic.toml` to wherever `classic.json` ended up. Then:

```bash
"$BESU/bin/besu" --profile=classic --data-path=data
```

**Note:** the profile sets `Xeth-capability-max=68`, which ETC networks require: ETC peers only
support eth/68 and below (eth/69+ removed Total Difficulty from the handshake, which ETC still
needs as a PoW chain).

## Configuration

```
--profile=classic                                  # ETC mainnet (chain ID 61)
--profile=mordor                                   # Mordor testnet (chain ID 63)

--plugin-classic-safe-block-depth=24               # Confirmation depth for "safe" (default: 24)
--plugin-classic-finalized-block-depth=400         # Deep-confirmation depth for "finalized" (default: 400)
```

## Release

Pushing a tag `v*` triggers the GitHub Actions release workflow, which:
1. Checks out the Besu fork and publishes to mavenLocal
2. Builds the plugin
3. Builds a Besu distribution with the plugin included
4. Creates a GitHub Release with the tarball
