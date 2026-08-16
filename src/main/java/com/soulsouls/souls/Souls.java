package com.soulsouls.souls;

import com.soulsouls.soul.Soul;
import com.soulsouls.soul.SoulDifficulty;
import com.soulsouls.soul.SoulRarity;
import com.soulsouls.soul.SoulRegistry;
import com.soulsouls.souls.ability.BraveryMomentumAbility;
import com.soulsouls.souls.ability.BraveryStoredDamageAbility;
import com.soulsouls.souls.ability.CourageDashAbility;
import com.soulsouls.souls.ability.DedicationAbility;
import com.soulsouls.souls.ability.DedicationMissionAbility;
import com.soulsouls.souls.ability.CuriosityAbility;
import com.soulsouls.souls.ability.FunAbility;
import com.soulsouls.souls.ability.FunPartyAbility;
import com.soulsouls.souls.ability.FuryAbility;
import com.soulsouls.souls.ability.HumilityAbility;
import com.soulsouls.souls.ability.MemoryAbility;
import com.soulsouls.souls.ability.HatredAbility;
import com.soulsouls.souls.ability.IntegrityFallAbility;
import com.soulsouls.souls.ability.JusticeTargetAbility;
import com.soulsouls.souls.ability.KindnessAbility;
import com.soulsouls.souls.ability.PatienceCycleAbility;
import com.soulsouls.souls.ability.PerseveranceAbility;
import com.soulsouls.souls.ability.RefuseDeathAbility;
import com.soulsouls.souls.ability.TruePlayerAbility;
import com.soulsouls.souls.ability.RetributionAbility;

/**
 * Every Soul in the mod, in the order they appear in {@code /souls list}.
 *
 * <h2>Adding a Soul</h2>
 * Add one {@code SoulRegistry.register(...)} call below and you are done. The config file
 * gains a matching block (with these values as its defaults) the next time the server
 * starts, the commands pick it up for tab-completion, the random roll includes it, and a
 * scoreboard team is created for its name colour.
 *
 * <p>For example, the Hope Soul that grows stronger in thunderstorms:
 * <pre>{@code
 * SoulRegistry.register(Soul.builder("hope", "HOPE")
 *         .color(0xFFF7A8)
 *         .rarity(SoulRarity.LEGENDARY)
 *         .difficulty(SoulDifficulty.HARD)
 *         .description("Draws power from the storm.")
 *         .chance(2.0)
 *         .maxHealth(20.0)
 *         .ability(new StormbornAbility())   // implements SoulAbility, ticks on world weather
 *         .build());
 * }</pre>
 *
 * <p>The numbers here are only defaults. Once the config file exists, it wins - which is
 * what makes {@code /souls settings ...} and {@code /souls reload} work.
 */
public final class Souls {
    private Souls() {
    }

    public static void registerAll() {
        // ---------------------------------------------------------------- Determination
        SoulRegistry.register(Soul.builder("determination", "DETERMINATION")
                .color(0xFF0000)
                .rarity(SoulRarity.LEGENDARY)
                .difficulty(SoulDifficulty.LEGENDARY)
                .description("The will to keep going when everything says stop.")
                .chance(1.0)
                .maxHealth(30.0)
                .ability(new RefuseDeathAbility())
                .ability(new TruePlayerAbility())
                .build());

        // ---------------------------------------------------------------- Patience
        SoulRegistry.register(Soul.builder("patience", "PATIENCE")
                .color(0x00FFFF)
                .rarity(SoulRarity.COMMON)
                .difficulty(SoulDifficulty.NORMAL)
                .description("Grows stronger the longer you wait - and then lets go.")
                .chance(20.0)
                .maxHealth(20.0)
                .ability(new PatienceCycleAbility())
                .build());

        // ---------------------------------------------------------------- Bravery
        SoulRegistry.register(Soul.builder("bravery", "BRAVERY")
                .color(0xFF8000)
                .rarity(SoulRarity.COMMON)
                .difficulty(SoulDifficulty.NORMAL)
                .description("Takes every blow and gives it back against their armour.")
                .chance(20.0)
                .maxHealth(20.0)
                .ability(new BraveryMomentumAbility())
                .ability(new BraveryStoredDamageAbility())
                .build());

        // ---------------------------------------------------------------- Justice
        SoulRegistry.register(Soul.builder("justice", "JUSTICE")
                .color(0xFFFF00)
                .rarity(SoulRarity.UNCOMMON)
                .difficulty(SoulDifficulty.HARD)
                .description("Judgement delivered from a distance.")
                .chance(12.0)
                .maxHealth(20.0)
                .ability(new JusticeTargetAbility())
                .build());

        // ---------------------------------------------------------------- Kindness
        SoulRegistry.register(Soul.builder("kindness", "KINDNESS")
                .color(0x00FF00)
                .rarity(SoulRarity.UNCOMMON)
                .difficulty(SoulDifficulty.NORMAL)
                .description("Shields itself, and everyone standing near it.")
                .chance(15.0)
                .maxHealth(20.0)
                .ability(new KindnessAbility())
                .build());

        // ---------------------------------------------------------------- Integrity
        SoulRegistry.register(Soul.builder("integrity", "INTEGRITY")
                .color(0x0000FF)
                .rarity(SoulRarity.COMMON)
                .difficulty(SoulDifficulty.NORMAL)
                .description("However far it falls, it lands on its feet.")
                .chance(18.0)
                .maxHealth(20.0)
                .ability(new IntegrityFallAbility())
                .build());

        // ---------------------------------------------------------------- Perseverance
        SoulRegistry.register(Soul.builder("perseverance", "PERSEVERANCE")
                .color(0x800080)
                .rarity(SoulRarity.UNCOMMON)
                .difficulty(SoulDifficulty.HARD)
                .description("Hardest to kill when it is closest to dying.")
                .chance(12.0)
                .maxHealth(20.0)
                .ability(new PerseveranceAbility())
                .build());

        // ---------------------------------------------------------------- Courage (Bravery + Justice)
        SoulRegistry.register(Soul.builder("courage", "COURAGE")
                .color(0xFFD700)
                .rarity(SoulRarity.LEGENDARY)
                .difficulty(SoulDifficulty.LEGENDARY)
                .description("Bravery and Justice together: charges in, and covers its allies.")
                .chance(2.0)
                .maxHealth(22.0)
                // Courage keeps a weaker version of Bravery's momentum alongside its own charge.
                .ability(new BraveryMomentumAbility())
                .ability(new CourageDashAbility())
                .value("bravery_build_up_seconds", 2.5)
                .value("bravery_linger_seconds", 1.5)
                .build());

        // ---------------------------------------------------------------- Retribution (Justice + Perseverance)
        SoulRegistry.register(Soul.builder("retribution", "RETRIBUTION")
                .color(0x9370DB)
                .rarity(SoulRarity.LEGENDARY)
                .difficulty(SoulDifficulty.LEGENDARY)
                .description("Justice and Perseverance together: it remembers who did it.")
                .chance(2.0)
                .maxHealth(20.0)
                .ability(new RetributionAbility())
                .build());


        // ---------------------------------------------------------------- Dedication
        SoulRegistry.register(Soul.builder("dedication", "DEDICATION")
                .color(0x6F47DE)
                .rarity(SoulRarity.VERY_RARE)
                .difficulty(SoulDifficulty.EXTREME)
                .description("Refuses to be finished until it is ready.")
                .chance(1.0)
                .maxHealth(20.0)
                .ability(new DedicationAbility())
                .ability(new DedicationMissionAbility())
                .build());

        // ---------------------------------------------------------------- Hatred / Regret
        SoulRegistry.register(Soul.builder("hatred", "HATRED")
                .color(0x101010)
                .rarity(SoulRarity.SECRET)
                .difficulty(SoulDifficulty.EXTREME)
                .description("It rots whatever it touches, including the one who carries it.")
                .chance(1.0)
                .maxHealth(40.0)
                .ability(new HatredAbility())
                .build());

        // ---------------------------------------------------------------- Fun
        SoulRegistry.register(Soul.builder("fun", "FUN")
                .color(0xFF69B4)
                .rarity(SoulRarity.RARE)
                .difficulty(SoulDifficulty.EASY)
                .description("Nobody knows what it is going to do next. Including it.")
                .chance(4.0)
                .maxHealth(20.0)
                .ability(new FunAbility())
                .ability(new FunPartyAbility())
                .build());

        // ---------------------------------------------------------------- Curiosity
        SoulRegistry.register(Soul.builder("curiosity", "CURIOSITY")
                .color(0x00CED1)
                .rarity(SoulRarity.RARE)
                .difficulty(SoulDifficulty.EASY)
                .description("It has to know what is over the next hill.")
                .chance(6.0)
                .maxHealth(20.0)
                .ability(new CuriosityAbility())
                .build());

        // ---------------------------------------------------------------- Humility
        SoulRegistry.register(Soul.builder("humility", "HUMILITY")
                .color(0x404040)
                .rarity(SoulRarity.UNCOMMON)
                .difficulty(SoulDifficulty.HARD)
                .description("It believes it is the weaker one, until it hits back.")
                .chance(8.0)
                .maxHealth(20.0)
                .ability(new HumilityAbility())
                .build());

        // ---------------------------------------------------------------- Fury
        SoulRegistry.register(Soul.builder("fury", "FURY")
                .color(0x8B0000)
                .rarity(SoulRarity.VERY_RARE)
                .difficulty(SoulDifficulty.HARD)
                .description("Hit it enough and it screams.")
                .chance(4.0)
                .maxHealth(20.0)
                .ability(new FuryAbility())
                .build());

        // ---------------------------------------------------------------- Memory
        SoulRegistry.register(Soul.builder("memory", "MEMORY")
                .color(0xFFD700)
                .rarity(SoulRarity.RARE)
                .difficulty(SoulDifficulty.EASY)
                .description("It forgets nothing: not a face, not a place it fell.")
                .chance(6.0)
                .maxHealth(20.0)
                .ability(new MemoryAbility())
                .build());

        // Add new Souls here. See the class comment for a worked example.
    }
}
