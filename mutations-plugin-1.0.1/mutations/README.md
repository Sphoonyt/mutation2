# 🧬 Mutations Plugin

A comprehensive Minecraft plugin featuring 10 unique mutations with abilities and passives.

## Requirements
- Paper **1.21.11** server
- Java **21+**
- Maven (for building)

## Building
```bash
cd mutations/
mvn clean package
```
The compiled JAR will appear in `target/Mutations-1.0.0.jar` — drop it in your `plugins/` folder.

---

## 📋 Commands
| Command | Description |
|---|---|
| `/mutation set <player> <mutation>` | Assign a mutation |
| `/mutation remove <player>` | Remove a mutation |
| `/mutation list` | List all mutation IDs |
| `/mutation info [player]` | View mutation & cooldowns |
| `/mutation clear <player>` | Reset cooldowns |

**Permission:** `mutations.admin` (default: op)

---

## 🎮 Mutation IDs

| ID | Mutation |
|---|---|
| `wind` | 🌪️ Wind Mutation |
| `blood_soldier` | 🩸 Blood-Soldier Mutation |
| `frozen` | ❄️ Frozen Mutation |
| `bypass` | 🚫 Bypass Mutation |
| `rock` | 🪨 Rock Mutation |
| `hellfire` | 🔥 Hellfire Mutation |
| `dragonborne_poison` | 🐉 Dragonborne (Poison) |
| `dragonborne_fire` | 🐉 Dragonborne (Fire) |
| `dragonborne_armor` | 🐉 Dragonborne (Armor) |
| `light` | ☀️ Light Mutation |
| `true_shot` | 🏹 True Shot Mutation |
| `love` | 💖 Love Mutation |

---

## 🎯 Ability Activation

Each mutation gives players **3 ability items** placed in hotbar slots 3, 4, and 5.

- **Slot 3 → [A1]** Right-click to activate Ability 1
- **Slot 4 → [A2]** Right-click to activate Ability 2
- **Slot 5 → [A3]** Right-click to activate Ability 3

Passives are **always active** automatically.

---

## 🌪️ Wind Mutation
- **A1 - Tornado Eruption (18s):** AOE burst → 1s stun, then pushes enemies 4 blocks
- **A2 - Wind Shuriken (16s):** Projectile → 3 damage, launches 13 blocks upward
- **A3 - Wind Leap (8s):** Launch 7 blocks up or forward
- **Passive - Air Step:** Up to 3 mid-air jumps (1.5s internal CD), arrows bounce off

## 🩸 Blood-Soldier Mutation
- **A1 - Nauseating Blood (17s):** After 4 consecutive hits → Slowness 2s on nearby enemies
- **A2 - Soldier's Sacrifice (60s):** Sacrifice 2 hearts → Strength II 8s (hearts return)
- **A3 - Blood Katana (25s, 8s):** Summon blood sword (1.5 hearts), hits steal saturation +1 bonus dmg
- **Passive - Blood Harvest:** Every 15 hits → steal 2 hearts from target for 8s

## ❄️ Frozen Mutation
- **A1 - Frozen Recovery (30s):** Encase in ice 3s → heal 3 hearts, then Slowness II 3s
- **A2 - Ice Spike Line (14s):** 8-block spike line → 3 damage + Slowness 2s
- **A3 - Glacial Domain (90s):** 12-block ice field 10s → enemies inside get Slowness II
- **Passive - Ice Runner:** Speed II when standing on ice

## 🚫 Bypass Mutation
- **A1 - Rapier State (30s, 7s):** Sword ignores 50% armor, player gets Weakness I
- **A2 - Mutation Lock (45s):** Disables nearest mutation player's abilities for 10s
- **A3 - Phantom Dash (16s):** Dash 7 blocks → 5 hearts damage to hit enemies
- **Passive - Phase Defense:** 30% chance to ignore 25% damage

## 🪨 Rock Mutation
- **A1 - Stoneburst Slam (16s):** 5-block AOE → 4 damage + 2-block knockback
- **A2 - Skin Hardening (40s, 6s):** Resistance II + Slowness I
- **A3 - Boulder Barrage (20s):** 3 boulders → 3 damage each
- **Passive - Stone Recovery:** Stand still 3s → repair armor 1%/second

## 🔥 Hellfire Mutation
- **A1 - Hellfire Rush (16s):** Zigzag dash → 4 damage, 3s burn, fire trail
- **A2 - Flame Whip (15s):** 3-hit combo (2+2+3 damage), final hit + fire line
- **A3 - Hell's Pull (20s):** Pull enemies within 6 blocks → 3 damage + 4s burn
- **Passive - Infernal Focus:** Permanent fire resistance

## 🐉 Dragonborne (Poison)
- **A1 - Venom Burst (30s):** 4-block toxic cloud → 3 dmg, Poison+Slow, final burst 5 dmg
- **A2 - Toxic Fang Strike (14s):** Poison projectile → 4 dmg, Poison II, 5-block knockback
- **A3 - Serpent Glide (12s):** Dash leaving venom trail (2 DPS + slow)
- **Passive - Venomborne Stride:** 2 air jumps, each releases 1 dmg poison puff

## 🐉 Dragonborne (Fire)
- **A1 - Blazing Eruption (18s):** Fiery AOE → 4 dmg + 4s burn
- **A2 - Flame Talon Shot (16s):** Explosive projectile → 4 dmg, launch 4 blocks, 7s burn
- **A3 - Inferno Leap (12s):** Fire leap → leaves fire ground (1 DPS for 4s)
- **Passive - Emberflight:** 2 air jumps, each deals 1 fire dmg below

## 🐉 Dragonborne (Armor)
- **A1 - Ironclad Surge (35s, 5s):** Resistance II + knockback resistance → fragment burst 5 dmg 1s stun
- **A2 - Steel Fist Barrage (16s):** 3-punch combo (2.5+2.5+3.5 dmg), final punch 5-block knockback
- **A3 - Armor Construct (30s, 5s):** 3 revolving armor stands → destruction burst 3 AOE dmg
- **Passive - Dragon Scale Resilience:** 8% dmg reduction, weak hits → no knockback, attacker gets Weakness I

## ☀️ Light Mutation
- **A1 - Luminous Roar (18s):** 12-block beam → 5 dmg, Slow+Blind 2s, 5s trail 1.5 DPS
- **A2 - Solar Wing Strike (12s):** 6-block dash → 4 dmg, Solar Flare 3 dmg + knockback
- **A3 - Celestial Expanse (22s, 4s):** Expanding aura → 1 DPS, Slow I, final burst 6 dmg Blind knockback
- **Passive - Radiant Core:** 8% dmg reduction, every 8s 15% blind chance, regen 0.5♥ every 5s in light

## 🏹 True Shot Mutation
- **A1 - Penetrating Volley (16s):** 3 rapid arrows → +30% speed, Pierce 2, 3 dmg each (+2 bonus if all hit)
- **A2 - Guided Light Arrow (18s):** Slight homing arrow → 4 dmg, marks target (+20% ranged dmg)
- **A3 - True Shot Apex (25s):** Infinite pierce arrow → 6 true dmg, shockwave 2 dmg + Weakness
- **Passive - Perfect Aim:** 10% faster arrows, no drop first 20 blocks, Crouch 1s → +20% dmg, True Arrow every 12s

## 💖 Love Mutation
- **A1 - Heart Pulse Burst (18s):** 4-block shockwave → 3 dmg, Charm 2s (-15% speed+dmg)
- **A2 - Adoration Beam (16s):** 10-block beam → 5 dmg (+2 if Devotion active)
- **A3 - Love Overdrive (45s, 10s):** +30% speed, +20% dmg, +2 hearts for 10s
- **Passive - Devotion Boost:** Look at ally 1s → Devotion 15s (+8% speed/dmg, heals on hit) | Sneak+Jump = Solo Focus

---

## Notes
- The Dragonborne mutation has only **one style per player** — choose via the mutation ID
- Stuns use Blindness + Slowness + Mining Fatigue effects
- "True damage" (armor-bypassing) is approximated by boosting raw damage to compensate
