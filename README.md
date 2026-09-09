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

Network naming is not the plugin's: the network id and the eth capability cap come from a Besu
profile (see `dist/profiles`), which is the mechanism Besu defines for a network it does not ship.
The genesis is the one piece a profile cannot carry, and `dist/bin/besu-etc` supplies it.

## Prerequisites

This plugin needs a Besu that carries the protocol-schedule customization extension point
(`ProtocolScheduleService`, `ProtocolScheduleCustomizer`) and the generic genesis accessor the
customizer reads its chain's keys through (`getCustomConfigLong`). Both are proposed upstream from
[diega/besu](https://github.com/diega/besu), and
[`etc-integration`](https://github.com/diega/besu/tree/etc-integration) is the branch carrying
whatever those proposals currently amount to. Build against that.

That branch is recut whenever the proposals move or upstream does, so what it stacks is deliberately
not listed here: read it from the branch.

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

The release archives ship a Besu distribution with the plugin, both profiles, both genesis files and
a `bin/besu-etc` launcher. Unpack anywhere and run it:

```bash
tar xzf besu-etc-26.7.0-etc-SNAPSHOT.tar.gz
besu/bin/besu-etc --data-path=data                 # ETC mainnet
besu/bin/besu-etc --profile=mordor --data-path=data
```

`besu-etc` exists for one reason: Besu resolves `genesis-file` against the working directory rather
than against the profile, so a profile cannot name a genesis that travels with the installation. The
launcher resolves its own location, follows symlinks, and passes the path as `BESU_GENESIS_FILE`,
which sits below the command line in Besu's precedence — so `--genesis-file` still overrides it, and
so does a `BESU_GENESIS_FILE` you export yourself. Everything else goes straight through to `besu`.
`bin/besu-etc.bat` does the same on Windows. Calling `bin/besu` directly needs `--genesis-file`.

To run against a local build instead, install the plugin JAR, a genesis file and a profile:

```bash
BESU=../besu/build/install/besu
cp build/libs/besu-etc-plugin-*.jar "$BESU/plugins/"
cp src/main/resources/classic.json src/main/resources/mordor.json "$BESU/etc/"
cp dist/profiles/*.toml             "$BESU/profiles/"
cp dist/bin/besu-etc*               "$BESU/bin/"
```

Then:

```bash
"$BESU/bin/besu-etc" --data-path=data
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

The GitHub Actions release workflow runs on a push to `main` that touches
`.github/workflows/release.yml`, and on manual dispatch. It:
1. Checks out the Besu fork at the branch named in the workflow and publishes it to mavenLocal
2. Builds the plugin
3. Builds a Besu distribution with the plugin, profiles, genesis files and the `besu-etc` launcher
4. Publishes a prerelease with the tarball, the zip, the plugin jar and a sha256 for each
