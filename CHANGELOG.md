# Changelog

All notable changes to Soul Souls are recorded here. The project follows
`MAJOR.MINOR.PATCH`: PATCH for fixes, MINOR for new Souls and abilities, MAJOR for changes
that need a config or save migration.

## 1.3.0

Reworks across most of the roster, and the removal of a few things that did not work.

### Removed
- **Double-sneak activation.** It was unreliable, so it is gone entirely along with every
  mention of it. `/souls ability` is the only trigger again.
- **`/souls give`.** Souls are handed out by `/souls set <player> <soul>` only.
- **Name colouring.** Player names are plain again; only the hearts carry the Soul's colour,
  in the nameplate and in the tab list alike.
- **Levitation on assignment**, which fired far too often.
- **Integrity's launch** and **Justice's Marksman** ability, both replaced below.

### Reworked
- **Integrity** loses its active ability. New passive: an explosion repairs your armour
  instead of hurting you, paid for out of your experience, with a sound to match.
- **Bravery** now stores every hit it takes. After 10 hits the next strike discharges the
  whole stored total into the target's armour as durability damage - their gear suffers,
  their health does not.
- **Humility** is dark grey and works the other way round: enough damage from one person
  leaves it cowed, with Resistance II and Weakness, until it hits that person back. Landing
  that hit grants Strength for a minute, and then the cycle begins again.
  Hearts: grey and white.
- **Justice** hunts. `/justice target <player>` marks somebody and costs you a heart until
  you kill them; `/justice location` points at them; the target glows red until you are
  within 20 blocks. The kill returns your heart and adds one, stacking to 20 hearts.
- **Memory** now charges its scan rather than firing instantly, ignores invisible players,
  and is interrupted by damage with a 500 second penalty. `/memory list` shows everyone
  remembered - green for alive, red for dead or offline - and `/memory reset` clears it.
  `/memories` works as an alias. Hearts: blue and yellow.
- **Determination** gains **TRUE PLAYER**: ten player kills in a row summon a Warden's roar,
  slow everyone within 10 blocks and grant Strength V for ten seconds. Dying breaks the run.
- **Perseverance** now reports who has been hurting you, worst first:
  `john had damaged you 50.0 hearts.`
- **Patience** gains a heart a minute up to 20, then sheds one every ten minutes.
- **Fury** holds its victims twice as long and keeps its Strength for 20 seconds.
- **Fun** spawns twice as many villagers, and clears them away when the cooldown ends.

### Settings
New tunables include `fury_hold_seconds`, `fury_strength_seconds`, `memory_scan_radius`,
`memory_scan_charge_seconds`, `memory_interrupt_penalty_seconds`, `memory_max_remembered`,
`justice_reveal_distance`, `justice_heart_cost`, `bravery_hits_to_charge`,
`humility_threat_damage`, `humility_emboldened_seconds`, `true_player_kills_required`,
`integrity_repair_per_xp` and `perseverance_report_window_seconds`.

## 1.2.0

Four new Souls, an in-game way to fire abilities, and a reworked Hatred.

### Activation
- **Double-sneak** fires your Soul's ability, so you no longer have to type a command
  mid-fight. `/souls ability` still works and does exactly the same thing.

### New Souls
- **Curiosity** (`#00CED1`) - finds the nearest structure within 1000 blocks, with sound.
  Each kind of structure can only be found once, so it is not a repeatable compass.
  500 second cooldown.
- **Humility** (`#9E9E9E`) - protection inverted. No armour is netherite-grade, anything up
  to iron is diamond-grade, and full diamond or netherite gives nothing at all.
- **Fury** (`#8B0000`) - damage builds up; at 20 it screams like a Warden. Everyone within
  5 blocks is pinned and blinded while Fury gets Strength IV and heals. 200 second cooldown.
- **Memory** (`#FFD700`) - tells you exactly where you died, and remembers everyone you
  scan. Read the list back with `/memory memories`.

### Changed
- **Hatred** now has 20 hearts and suffers nothing: Wither and Poison heal it, Weakness
  gives Strength, Slowness gives Speed, Mining Fatigue gives Haste, Blindness gives Night
  Vision, and any other harmful effect is stripped. Its name is now white against the black
  hearts so it can be read.
- **Bravery** gains Strength alongside Speed while its momentum is up.
- **Justice** arrows now hit for double, growing by 0.1 per consecutive hit up to +1.0. One
  miss puts it back to standard.
- **Integrity** can double-sneak to launch itself straight up - safe, because it ignores the
  landing.
- **Fun** gains a party trick: villagers, a tamed wolf pack, or ten seconds of coloured
  fireworks that only hurt other people. Each shouts a line, heard within 10 blocks and
  switchable with `soul_voice_lines`.
- **Retribution** now shows a title naming the killer to hunt.
- **Determination** plays a beacon shutting down at normal speed and throws red sparks when
  it refuses a death.
- **Patience** plays a small chime on each step, switchable with `patience_sounds`.
- The tab list now matches the name above the head: hearts either side, rather than a
  bracketed Soul name.
- `/souls give <soul>` refuses a Soul you already have, with `cant have the soul twice!`.
- The assignment ceremony no longer applies Levitation. Blindness and the fall immunity are
  unchanged; set `assignment_levitation_amplifier` above -1 to bring it back.

### Removed
- **Patient Justice**. Existing players carrying it are left without a Soul and are given a
  new one on their next join.

## 1.1.0

Presentation and command changes.

- **Hearts above the head.** The name floating above a player is now wrapped in hearts in
  the Soul's colour - `❤ Steve ❤` - via the scoreboard team's prefix and suffix.
- **Soul tag in the tab list.** Tab entries now read `⌊INTEGRITY⌉ Steve`. Both the brackets
  and the heart are configurable in case your font lacks the characters.
- **`/souls info <player>`** looks up somebody else's Soul.
- **`/souls give <soul> <player>`** - the Soul now always comes first, so the self and
  admin forms cannot be confused. (`/souls set <player> <soul>` is unchanged.)
- **The reveal is now a six-second ceremony**: the title reads `YOUR SOUL IS:` with the
  Soul's name between two hearts, and for those six seconds the player gets Levitation I,
  blindness, and fall immunity that lasts ten seconds past the end so the drop cannot kill
  them.
- **Death sound**: a beacon deactivating at double speed.

## 1.0.0

First release. Minecraft 1.21.11, Fabric, Java 21.

### Soul system
- Every player is assigned a Soul on first join, weighted by the configured chances, and
  keeps it permanently across disconnects, deaths, dimension changes and restarts.
- Assignment is announced with a `YOUR SOUL IS` title, the Soul's name in its own colour,
  an Ender Dragon sound and a particle burst.
- Soul definitions are data (`Soul`) plus behaviour (`SoulAbility`), so a new Soul is one
  registration call and one class.

### The 13 Souls
Determination, Patience, Bravery, Justice, Kindness, Integrity, Perseverance, Courage,
Retribution, Patient Justice, Dedication, Hatred/Regret and Fun. No Faded Souls, no mixed
Souls and no Soul evolution.

### Commands
`/souls info`, `list`, `ability`, `bond`, `give <soul>`, `give <player> <soul>`,
`set <player> <soul>`, `reset`, `reload`, `debug [player]`, and
`settings chance|refuse|difficulty`.

### Configuration
- `config/soulsouls.json` holds colours, chances, rarities, difficulties, max health and
  every ability number.
- The file repairs itself: missing Souls and missing values are filled in from code on
  start, and an unparseable file is kept as `.broken` instead of being discarded.
- `/souls reload` applies changes live.

### Multiplayer and persistence
- Per-player data is stored in `<world>/soulsouls/players.json` and written atomically.
- Cooldowns and timers are wall-clock deadlines, so they cannot be skipped by relogging or
  by waiting for a server restart.
- Max health is re-checked on every ability tick, so it survives respawns, dimension
  changes and anything else that rebuilds a player's attributes.
- Admin commands require permission level 2.

### Verification
- CI builds the mod against real Minecraft 1.21.11 and boots a dedicated server with it
  installed, checking that the server reaches "Done", the mod initialises, the mixin
  applies and `config/soulsouls.json` is generated.

### Name colouring
- Scoreboard teams colour the name above the head, the tab list and chat for vanilla
  clients, using the closest vanilla colour to the Soul's RGB.
- A tab-list mixin adds the exact RGB colour, and is written so that a future mapping
  change disables it rather than crashing the game.
