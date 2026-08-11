# Changelog

All notable changes to Soul Souls are recorded here. The project follows
`MAJOR.MINOR.PATCH`: PATCH for fixes, MINOR for new Souls and abilities, MAJOR for changes
that need a config or save migration.

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

### Name colouring
- Scoreboard teams colour the name above the head, the tab list and chat for vanilla
  clients, using the closest vanilla colour to the Soul's RGB.
- A tab-list mixin adds the exact RGB colour, and is written so that a future mapping
  change disables it rather than crashing the game.
