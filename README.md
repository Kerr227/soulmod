# Soul Souls

An Undertale-inspired Soul system for **Minecraft Java Edition 1.21.11** (Fabric, Java 21).

Every player is permanently bound to a Soul the first time they join. The Soul decides the
colour of their name, how much health they have, and what they can do. Soul data is stored
server-side and survives disconnects, deaths, dimension changes and restarts.

---

## Contents

- [Installation](#installation)
- [Building from source](#building-from-source)
- [The Souls](#the-souls)
- [Commands](#commands)
- [Configuration](#configuration)
- [How name colouring works](#how-name-colouring-works)
- [Where the data lives](#where-the-data-lives)
- [Adding a new Soul](#adding-a-new-soul)
- [Design notes and deviations from the brief](#design-notes-and-deviations-from-the-brief)
- [Versioning](#versioning)

---

## Installation

**Requirements**

| | |
|---|---|
| Minecraft | 1.21.11 |
| Mod loader | Fabric Loader 0.19.3 or newer |
| Fabric API | 0.141.6+1.21.11 or newer |
| Java | 21 |

**Server (recommended)**

1. Install Fabric Loader for 1.21.11 on your server.
2. Put `fabric-api-0.141.6+1.21.11.jar` in the server's `mods/` folder.
3. Put `soulsouls-1.0.0.jar` in the same `mods/` folder.
4. Start the server once. It creates `config/soulsouls.json` with every Soul and every
   tunable value filled in.
5. Edit the config if you want, then run `/souls reload` (no restart needed).

**Single player**

Same idea: install Fabric for 1.21.11, drop Fabric API and `soulsouls-1.0.0.jar` into
`.minecraft/mods/`, and launch the Fabric profile.

**Clients do not need the mod.** Everything runs on the server and vanilla clients see the
titles, sounds, particles and coloured names normally. Installing it client-side as well is
harmless.

---

## Building from source

The Gradle wrapper is included, so you do not need Gradle installed - only a JDK 21.

```bash
git clone https://github.com/Kerr227/soulmod.git
cd soulmod
./gradlew build          # Windows: gradlew.bat build
```

The finished mod is `build/libs/soulsouls-1.0.0.jar`. Ignore `soulsouls-1.0.0-sources.jar`
and any `-dev`/`-remapped` files - only the plain one goes in `mods/`.

The first build downloads Minecraft and the Fabric toolchain and takes a few minutes;
later builds are much faster.

Toolchain versions live in `gradle.properties`:

```properties
minecraft_version=1.21.11
yarn_mappings=1.21.11+build.6
loader_version=0.19.3
loom_version=1.16.3
fabric_version=0.141.6+1.21.11
```

> **Note on Loom.** This project pins Fabric Loom `1.16.3` and depends on the individual
> Fabric API modules it uses rather than the whole `fabric-api` bundle. Newer Loom versions
> plus the full bundle fail to set up Minecraft 1.21.11 with
> `Javadoc provided by mod (fabric-content-registries-v0) must be have an intermediary
> source namespace`. If you change either, expect to revisit the other.

There is also a development helper for checking Yarn signatures against the pinned
Minecraft version, which is handy when writing new abilities:

```bash
./gradlew probe --no-configuration-cache      # reads dev/probe-list.txt
```

---

## The Souls

Chance is a relative weight, not a percentage: a Soul with chance 20 is rolled twenty times
as often as one with chance 1.

| Soul | Colour | Difficulty | Chance | Health | What it does |
|---|---|---|---|---|---|
| **Determination** | `#FF0000` bright red | LEGENDARY | 1 | 15 hearts | 40% chance to refuse death outright |
| **Patience** | `#00FFFF` cyan | NORMAL | 20 | 10 → 20 hearts | Gains a heart every 10 min to 20, then loses one a minute back to 10, forever |
| **Bravery** | `#FF8000` orange | NORMAL | 20 | 10 hearts | Speed I while moving, fades shortly after you stop |
| **Justice** | `#FFFF00` yellow | HARD | 12 | 10 hearts | Arrows hit 15% harder; Focus ability for a bigger multiplier |
| **Kindness** | `#00FF00` green | NORMAL | 15 | 10 hearts | Out-of-combat regen, Resistance when hurt, heals nearby allies |
| **Integrity** | `#0000FF` blue | NORMAL | 18 | 10 hearts | Takes no fall damage |
| **Perseverance** | `#800080` purple | HARD | 12 | 10 hearts | Resistance after heavy hits, Strength at low health, last stand near death |
| **Courage** | `#FFD700` gold | LEGENDARY | 2 | 11 hearts | Bravery + Justice. Dash, Speed and Strength, Resistance for nearby allies |
| **Retribution** | `#9370DB` medium purple | LEGENDARY | 2 | 10 hearts | Justice + Perseverance. Avenges bonded allies; marks their killer |
| **Patient Justice** | `#B0FFB0` pastel green | LEGENDARY | 2 | 10 hearts | Patience + Justice. Charge up, then release a burst on nearby hostiles |
| **Dedication** | `#FF9BE0` pastel magenta | EXTREME | 1 | 10 hearts | A lethal hit leaves you at 1 HP with 10s to heal, or you die |
| **Hatred / Regret** | `#101010` black | EXTREME | 1 | 10 hearts | Hits apply Wither; kills build instability that turns on you |
| **Fun** | `#FF69B4` hot pink | EASY | 4 | 10 hearts | Luck, and a harmless surprise every few minutes |

There are no Faded Souls, no mixed or four-trait Souls, and no Soul evolution. Courage,
Retribution and Patient Justice are described as combinations for flavour only - they are
ordinary Souls you can roll or be given directly.

---

## Commands

All under `/souls`. Anything that touches another player or the config needs permission
level 2 (operator).

| Command | Who | What it does |
|---|---|---|
| `/souls info` | anyone | Your Soul, colour, rarity, difficulty, max health, abilities and cooldowns |
| `/souls list` | anyone | Every Soul with its difficulty and chance |
| `/souls ability` | anyone | Uses your Soul's active ability, if it has one |
| `/souls bond <player>` | anyone | Offers a Soul bond; both players must run it. Used by Retribution |
| `/souls give <soul>` | anyone\* | Equips a Soul on yourself. Replies `success while equipping new soul!` |
| `/souls reset` | anyone\* | Clears your Soul and immediately rolls a new one |
| `/souls give <player> <soul>` | op | Gives another player a Soul |
| `/souls set <player> <soul>` | op | Same thing, spelled the way admins expect |
| `/souls reload` | op | Re-reads `config/soulsouls.json` and applies it live |
| `/souls debug [player]` | op | Soul, colour, health, refuse chance, cooldowns and raw ability state |
| `/souls settings chance <soul> <percentage>` | op | Sets a Soul's roll weight |
| `/souls settings refuse <percentage>` | op | Sets Determination's death-refusal chance |
| `/souls settings difficulty <soul> <difficulty>` | op | EASY, NORMAL, HARD, EXTREME or LEGENDARY |

\* `/souls give <soul>` and `/souls reset` are player-usable because the brief asks for
them. If you would rather Souls stayed random, set `allow_player_give` and
`allow_player_reset` to `false` in the config - operators keep the admin forms either way.

`/souls settings ...` writes to the config file immediately, so the change survives a
restart.

**Active abilities.** `/souls ability` is the trigger for Justice's Focus, Kindness's
healing aura, Courage's charge and Patient Justice's charge/release. Players who want it on
a key can bind one client-side to the command; Minecraft has no server-side keybind API, so
a command is the only way to offer this to vanilla clients.

---

## Configuration

`config/soulsouls.json`, created on first start. It is self-repairing: new Souls and new
tunables are added automatically on the next start, and a file that fails to parse is moved
to `soulsouls.json.broken` rather than being thrown away.

```jsonc
{
  "config_version": 1,
  "assign_on_first_join": true,
  "announce_assignment_to_server": false,
  "use_scoreboard_teams": true,
  "rgb_tab_list_names": true,
  "assignment_sound": "minecraft:entity.ender_dragon.growl",
  "ability_tick_interval": 5,
  "autosave_interval_seconds": 60,
  "determination_refuse_chance": 40.0,
  "allow_player_give": true,
  "allow_player_reset": true,
  "souls": {
    "determination": {
      "enabled": true,
      "color": "#FF0000",
      "chance": 1.0,
      "rarity": "legendary",
      "difficulty": "LEGENDARY",
      "max_health": 30.0,
      "values": {
        "refuse_cooldown_seconds": 300.0,
        "refuse_health_restored": 6.0,
        "refuse_resistance_seconds": 5.0
      }
    }
    // ... one block per Soul
  }
}
```

Per-Soul keys:

| Key | Meaning |
|---|---|
| `enabled` | A disabled Soul is never rolled and cannot be given out |
| `color` | `#RRGGBB` (also accepts `0xRRGGBB`) |
| `chance` | Relative roll weight; `0` means never |
| `rarity` | Display only: `common`, `uncommon`, `rare`, `very_rare`, `legendary`, `secret` |
| `difficulty` | Display only: `EASY`, `NORMAL`, `HARD`, `EXTREME`, `LEGENDARY` |
| `max_health` | In half-hearts. `20` = 10 hearts, `30` = 15 hearts |
| `values` | Every ability number for that Soul |

Some `values` worth knowing:

| Key | Default | Effect |
|---|---|---|
| `patience_grow_minutes` | 10 | Minutes per heart gained |
| `patience_fade_minutes` | 1 | Minutes per heart lost once full |
| `patience_max_bonus_hearts` | 10 | Cap above the base 10 hearts |
| `bravery_speed_amplifier` | 0 | 0 = Speed I, 1 = Speed II |
| `bravery_linger_seconds` | 2 | How long Speed lasts after you stop |
| `justice_projectile_multiplier` | 1.15 | Arrow damage multiplier |
| `integrity_fall_immunity` | 1 | Set to 0 to switch fall immunity off |
| `hatred_wither_seconds` | 4 | Wither duration on hit |
| `hatred_wither_amplifier` | 0 | 0 = Wither I |
| `hatred_touch_cooldown_seconds` | 3 | Minimum gap between applications |
| `hatred_affects_mobs` | 0 | Set to 1 to Wither mobs too |
| `dedication_duration_seconds` | 10 | Time to heal before you die anyway |
| `dedication_cooldown_seconds` | 900 | 15 minutes |

Ability cooldowns are all `*_cooldown_seconds` keys and are stored as wall-clock deadlines,
so they cannot be skipped by relogging or by waiting for a restart.

---

## How name colouring works

Souls use full RGB colours, but the name floating above a player's head is drawn by the
vanilla client, which only knows the 16 chat colours. So the mod does two things:

- **Scoreboard teams** (`use_scoreboard_teams`) put each player on a `soul_<id>` team whose
  colour is the closest vanilla colour to the Soul's RGB. This is what colours the name
  above the head, the tab list and the chat name, and it needs nothing on the client.
- **A tab-list mixin** (`rgb_tab_list_names`) replaces the tab-list name with the exact RGB
  colour, so pastel green really is pastel green there.

Titles, action bars and everything the mod writes itself always use the true RGB colour.

If another mod or plugin owns your scoreboard teams, set `use_scoreboard_teams` to `false`;
you keep the RGB tab list and lose the name-tag colour.

---

## Where the data lives

Per-player Soul data is written to `<world>/soulsouls/players.json` - inside the world save,
so backing up or copying a world carries the Souls with it. It is written atomically
(temp file, then move), every 60 seconds when something changed, whenever a player leaves
and on shutdown.

```jsonc
{
  "3f2a...-...": {
    "uuid": "3f2a...-...",
    "last_known_name": "Steve",
    "soul_id": "determination",
    "assigned_at": 1786500000000,
    "cooldowns": { "refuse": 1786500300000 },
    "state": { "bonus_health": 0.0 },
    "bonds": [],
    "bond_requests": []
  }
}
```

`cooldowns` are epoch-millisecond deadlines and `state` is a free-form number map owned by
abilities, which is why a new Soul never needs a save-format change.

---

## Adding a new Soul

Two files, and nothing else in the mod has to change - the config file, tab-completion,
`/souls list` and the random roll all read from the registry.

**1. Write the ability** in `src/main/java/com/soulsouls/souls/ability/`:

```java
public class StormbornAbility implements SoulAbility {
    @Override public String id() { return "stormborn"; }
    @Override public String displayName() { return "Stormborn"; }

    // These appear in the config file automatically and are read back with ctx.value(...).
    @Override
    public Map<String, Double> defaultValues() {
        return Map.of("hope_storm_strength_amplifier", 0.0);
    }

    @Override
    public void tick(AbilityContext ctx) {
        if (ctx.world().isThundering()) {
            ctx.refreshEffect(StatusEffects.STRENGTH, 60,
                    (int) ctx.value("hope_storm_strength_amplifier"));
        }
    }
}
```

**2. Register the Soul** in `com.soulsouls.souls.Souls#registerAll`:

```java
SoulRegistry.register(Soul.builder("hope", "HOPE")
        .color(0xFFF7A8)
        .rarity(SoulRarity.LEGENDARY)
        .difficulty(SoulDifficulty.HARD)
        .description("Draws power from the storm.")
        .chance(2.0)
        .maxHealth(20.0)
        .ability(new StormbornAbility())
        .build());
```

Start the server and `hope` is in the config file, in `/souls list`, in tab-completion, in
the random roll, and has its own colour team.

**The hooks an ability can implement** (all optional, all no-ops by default):

| Hook | When |
|---|---|
| `onAssign` / `onRemove` | The Soul is given or taken away |
| `onJoin` / `onRespawn` | Player joins; player respawns or changes dimension |
| `tick` | Every `ability_tick_interval` ticks |
| `allowDamage` | Return `false` to cancel incoming damage |
| `allowDeath` | Return `false` to refuse a death (you must heal them) |
| `afterDamage` | They took damage and survived |
| `onAttack` / `onKill` | They hit something; they killed something |
| `onProjectileFired` | An arrow they fired entered the world |
| `onOtherPlayerDeath` | Any other player died anywhere |
| `activate` | `/souls ability` was used (also set `isActive()` to `true`) |
| `describe` | Extra lines for `/souls info` |
| `cooldownKeys` | Cooldowns to show in `/souls info` and `/souls debug` |

Use `ctx.value("key")` for config numbers, `ctx.startCooldown`/`ctx.isReady` for cooldowns,
and `ctx.setState`/`ctx.state` for anything you need to persist. All three survive relogs
and restarts for free.

---

## Design notes and deviations from the brief

Where an Undertale idea has no exact vanilla equivalent, the closest practical
implementation was used. The differences that matter:

- **Courage protecting allies.** "Take some of an ally's incoming damage" would need the
  damage pipeline rewritten and does not survive contact with other combat mods, so allies
  in range get Resistance instead. They take less damage; it is absorbed rather than
  transferred to the Courage player.
- **Retribution's bonus against a specific killer.** Damage scaling against one named player
  needs a damage-pipeline hook. Vengeance grants Strength and Speed (which apply generally)
  and marks the killer with Glowing so they can be tracked. The state expires on a timer and
  has a 10-minute cooldown, so nobody is permanently marked.
- **Justice's ranged bonus** multiplies the projectile's damage after the weapon and its
  enchantments are applied, so it stacks correctly with Power rather than overwriting it.
- **Patient Justice's release** damages hostile mobs in a radius. It deliberately does not
  hit players or passive animals.
- **Hatred's instability drain** never kills you on its own - it stops at half a heart.
- **Active abilities are a command** (`/souls ability`) because Minecraft has no server-side
  keybind API and the mod must work for vanilla clients.
- **Patience counts online time**, not wall-clock time. Counting wall-clock time would let a
  player log off at 20 hearts and return at 20 hearts; counting in-memory ticks would reset
  progress on every restart. Online time is saved, so logging out, dying, changing dimension
  and restarting just pause the cycle.
- **Chat name colouring** comes from the scoreboard team, which is the vanilla mechanism.
  A server with a chat-formatting plugin that rewrites names may override it.

---

## Versioning

`MAJOR.MINOR.PATCH`, set by `mod_version` in `gradle.properties`.

- **PATCH** - fixes, no behaviour change (`1.0.1`)
- **MINOR** - new Souls or abilities, config gains keys but stays compatible (`1.1.0`)
- **MAJOR** - save format or config format changes in a way that needs migration (`2.0.0`)

Current release: **1.0.0** - the 13 Souls above, the command tree, the config system and
per-player persistence.
