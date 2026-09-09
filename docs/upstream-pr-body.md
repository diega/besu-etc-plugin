# Title

Let a plugin contribute protocol-schedule rules for a chain Besu does not ship

# Branch

`pr/protocol-schedule-customization` — 7 commits, +2394/−69, 38 files (1194 lines of main code, 1199 of test).

Three independent fixes were split out into their own PRs. This branch does not contain them and
does not depend on any of them:
- `fix/eth-config-next-fork` — `eth_config` reported a block milestone as the next fork.
- `fix/dao-restoration-milestone` — the DAO recovery reinstates the spec in force at the fork rather
  than at the block where it reinstates, so a milestone opening inside the window is reverted.
- `feat/genesis-custom-config-long` — lets a customizer read its own chain's activation keys.

# Body (follows .github/pull_request_template.md)

## PR description

A protocol schedule is built from the genesis keys Besu defines, and nothing asks plugins for
anything while it is built. A chain with rules Besu does not carry — its own gas schedule at a fork
with no key, a different block reward, a difficulty rule of its own — has no way in through the
plugin system.

There is a way around it, and I use it today: `BesuCommand`'s constructor takes a
`BesuController.Builder`, so a launcher can assemble a schedule itself and inject it, against
published artifacts and with no changes here. It works, but it means a second entry point that has
to track this one, and a fork ID re-derived by hand beside the one the node advertises. This PR is
the version of that which does not need either.

A plugin registers a `ProtocolScheduleCustomizer`, which Besu asks about the genesis config the node
started with. The customizer either declines the chain or returns a named
`ProtocolScheduleCustomization`: a list of `ProtocolSpecModification`s, each a
`UnaryOperator<ProtocolSpecBuilder>` at a typed block or timestamp activation. Besu composes them
through `ProtocolSpecAdapters`, where Clique, BFT and the merge already put their own modifiers.

The activations Besu advertises under EIP-2124 come from those same modifications, so a chain
declares each boundary once instead of stating its rules and its fork ID separately and letting them
drift. This removes a duplicated declaration; it does not prove the rules and the advertised
schedule agree, since a modifier can still do less than its activation implies.

### Scope, and what this does not promise

**Besu ships no chain that needs this, and nothing here changes what the shipped ones do.** The hook
is inert unless a plugin registers a customizer that claims the running chain: with none registered
every factory composes the empty customization and the schedule is built exactly as before. This
branch changes no stock behaviour at all — the only such change the work turned up, in the DAO
restoration, is a latent bug of its own and went to a separate PR rather than riding along here.

**Nothing enters `besu-plugin-api`, and not by preference — it cannot.** A customizer's modifier is
a `UnaryOperator<ProtocolSpecBuilder>`, and `ProtocolSpecBuilder` lives in `:ethereum:core`.
`plugin-api` depends on `plugin-api-*` slices of other modules and never on `:ethereum:core` itself,
so the type is not expressible there without inverting that dependency. The extension point lives in
`ethereum/core` instead, and every new type on it is `@Unstable`.

**That covers less than it sounds, and the gap is worth stating.** `@Unstable` promises that
signatures may change — its javadoc says "deleting methods, changing signatures, and adding checked
exceptions" — and a signature change breaks such a plugin at compile time, which is the intended
outcome. A `ProtocolSpecBuilder` method that keeps its signature and changes what it means is
invisible to that mechanism and to any guard this PR could carry: it would mis-apply a deployed
customization silently until the plugin's author re-read the builder. That residual is the plugin
author's, release by release. It is the same residual every in-tree caller of `ProtocolSpecBuilder`
carries; the difference is that in-tree callers get updated in the PR that changes the meaning and
an out-of-tree one does not. Breakage of such a plugin is not a Besu regression. The alternative —
a typed, versioned surface in `besu-plugin-api` for each thing a chain may want to change — is a
much larger and different PR, which this one does not attempt.

### Where the code went

This is four times the ~300 lines CONTRIBUTING suggests, so here is the accounting rather than an
apology. Of the 1259 lines of main code, the mechanism is a small part; most of it is what makes
the mechanism safe to merge.

The seven new types come to 531 lines of file, of which **259 are code** — the rest is 98 lines of
licence header, ~121 of javadoc (public API in this tree is javadoc-enforced with `-Xwerror`) and
53 blank:

| new type | code |
| --- | --- |
| `ProtocolScheduleCustomization` (mostly `validateAgainst` and its refusal messages) | 118 |
| `ProtocolScheduleServiceImpl` (the registry) | 58 |
| `ProtocolScheduleActivation` | 30 |
| `ForkIdActivations` | 23 |
| `ProtocolSpecModification` | 14 |
| `ProtocolScheduleCustomizer` | 9 |
| `ProtocolScheduleService` | 7 |

Both new interfaces declare one method. The typed vocabulary — sealed activation, modification,
customizer, service — is 60 lines together. On the modified side, the two files that do the actual
work are `ProtocolSpecAdapters` (+146) and `ProtocolScheduleBuilder` (+130). **Composition and
insertion, which is the whole mechanism, is under 280 lines and would fit the guideline on its
own.**

What multiplies it is three things that are not the mechanism, and one that is neither:

- **The fork ID is derived from the rules, not declared beside them** (~150 lines across `config`,
  plus three consumer sites of 2-4 lines each). `GenesisConfigOptions` grows a second accessor pair:
  `getForkBlockNumbers`/`getForkBlockTimestamps` stay the forks the config declares and are what
  schedule construction reads, while `getForkIdBlockNumbers`/`getForkIdBlockTimestamps` add what was
  contributed and are what the fork ID is built from. The single reader that makes the split
  load-bearing is `MergeProtocolSchedule.unapplyModificationsFromShanghaiOnwards`: fold the two
  together and a contributed timestamp below Shanghai displaces the Paris cutoff, leaving a
  post-merge chain running pre-merge rules. Without this guarantee a plugin would state its rules
  and its fork ID separately, which is the drift this whole thing exists to make impossible.
- **A builder refuses rather than half-applies** (`validateAgainst`, plus
  `supportsProtocolScheduleCustomization` in four builders). The check runs in the schedule builder,
  the one point every construction path shares, rather than in the factories that call it.
- **Resolution happens once and reaches both halves of a transition** (~143 lines in `app/`). This
  is the wiring: resolve where the controller builder is selected, then travel down as an ordinary
  builder setting.
- **Boilerplate that buys nothing but costs less than the alternative**: the three factory overloads
  are 120 lines of passthrough. Changing the existing signatures instead would touch more files, not
  fewer — evmtool, the reference tests and several fixtures call them.

Activation domains being typed and never ordered against each other is folded into the mechanism
figure above: the sealed `ProtocolScheduleActivation`, the typed modifier maps, and domain-aware
insertion.

If you want to argue the size down, the guarantees are the place to push, not the code. Drop the
derived fork ID and roughly 150 lines go with it — along with the property that makes the feature
worth having.

### Reading the commits

They build bottom-up, and nothing contributes an activation to the advertised fork ID until the code
that applies the matching rules exists. Each builds and passes its module test suites on its own.

1. **`refactor: let the genesis config carry additional EIP-2124 fork activations`** — a typed
   carrier for activations that do not come from a genesis key, behind its own accessors so the
   forks the config declares keep meaning what they meant to schedule construction.
2. **`feat: add typed protocol-schedule activations and modifications`** — the vocabulary, as
   immutable values with no consumer: activation, modification, customization, and the derivation of
   the fork-ID activations from the modifications.
3. **`feat: compose contributed modifications into the protocol schedule`** — the composition rules,
   and which builder each inserted spec is made from. A consensus mechanism's modifiers keep the
   magnitude-ordered lookup they have always had and mutate the instance their milestone was built
   from, so consecutive ones accumulate; a contributed modification resolves within its own domain
   and builds from a fresh definition. Those two only coexist safely where a structural modifier
   activates at a milestone the genesis declares, so it replaces that milestone's entry rather than
   borrowing another one's already-overlaid instance — the builder refuses any other combination
   while a customization is present, rather than silently reinstating an overlay a later
   modification replaced.
4. **`feat: refuse customizations whose activations the schedule cannot honor`** — the activation
   shapes that cannot be applied where they claim to apply. Checked in the schedule builder, which
   is the one point every path to a schedule shares. One of them is the DAO recovery window,
   `[daoForkBlock, daoForkBlock + 10]`: the recovery writes its own spec at the fork and reinstates
   the previous one at the far end, so it owns that whole span and nothing contributed can hold
   anywhere in it. Refusing the far end is also what keeps this branch independent of the DAO fix
   above.
5. **`feat: let the schedule factories take a customization`** — mainnet, fixed-difficulty and merge,
   which is one pattern applied three times.
6. **`feat: add a Besu-owned registry for protocol-schedule customizers`** — registration,
   at-most-one resolution, freeze, reset. Still nothing resolves it in production.
7. **`feat: resolve the customization when building a node, or refuse to start`** — the first commit
   that contributes to a fork ID. Resolution happens where the controller builder is selected, so
   the result travels as an ordinary builder setting and a transition schedule hands it to both
   halves through the propagation every other setting already uses.

### Testing

Every commit was verified with `:config:test :ethereum:core:test :consensus:merge:test
:consensus:qbft:test :consensus:ibft:test :consensus:clique:test :app:test`, plus javadoc and
spotless for the modules it touches, and `:ethereum:api:test` separately.

New coverage includes: a contributed modification followed by a later one, asserting the second era
is not left with the first's overlay; a contributed block modification asserted not to apply in the
timestamp era; a contributed timestamp activation on the merge path, asserting Paris survives it; a
modification activating at `daoForkBlock + 10` asserted to survive the restoration; a structural
modifier and a contributed one at the same block, asserting the order they compose in and that each
still applies past the other's later boundary; a schedule built straight from composed adapters,
asserting the refusal is the builder's rather than the factories'; fork-ID derivation through a real
`ForkIdManager`; contributed activations asserted to reach the advertised fork ID and to leave the
declared forks alone; each rejected activation shape; at-most-one resolution and its two-provider
failure; the registry's freeze/resolve/reset lifecycle; a transition asserted to hand the same
customization to both halves and to refuse one when either half would ignore it; and a customization
refused on a Clique builder.

### Who uses it

The first consumer is [besu-etc-plugin](https://github.com/diega/besu-etc-plugin), which runs a
chain Besu no longer ships. I maintain it, and it is migrated to the contract in this PR: it
declares ETC's twelve fork boundaries as modifications and lets Besu derive the fork ID from them,
pinned in its own tests against core-geth's fork ID vectors. It takes its network naming from a Besu
profile rather than asking for anything else here. It vendors whatever validation its chain needs,
and nothing in this PR asks for rules to come back into the tree.

## Fixed Issue(s)

<!-- optional: open a tracking issue and link it here, or leave as is -->

### Thanks for sending a pull request! Have you done the following?

- [x] Checked out our [contribution guidelines](https://github.com/besu-eth/besu/blob/main/CONTRIBUTING.md)?
- [ ] Considered documentation and added the `doc-change-required` label to this PR if updates are required in the [Besu documentation](https://github.com/besu-eth/besu-docs).
- [x] Considered the changelog and [included an update if required](https://github.com/besu-eth/besu/blob/main/CONTRIBUTING.md#changelog).
- [ ] For database changes (e.g. KeyValueSegmentIdentifier) considered compatibility and performed forwards and backwards compatibility tests

### Locally, you can run these tests to catch failures early:

- [x] spotless: `./gradlew spotlessApply`
- [ ] unit tests: `./gradlew build`
<!-- the module suites, javadoc and spotless listed under Testing pass at every commit; tick this
     once you have run the whole build -->
- [ ] acceptance tests: `./gradlew acceptanceTest`
- [ ] integration tests: `./gradlew integrationTest`
- [ ] reference tests: `./gradlew ethereum:referenceTests:referenceTests`
- [ ] hive tests: [Engine or other RPCs modified?](https://github.com/besu-eth/besu/wiki/Working-through-Hive-tests)
