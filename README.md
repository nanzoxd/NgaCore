# SMPCore

All-in-one SMP rules plugin for Paper 1.21.x (Java 21). One command, `/smp`, opens a GUI for everything.

## Build
```
mvn package
```
The jar is `target/SMPCore-1.3.0.jar`. It is compiled against paper-api 1.21.8 (Dialog API) and needs Paper 1.21.7 or newer.

## Modules (all editable in `/smp`)
| Module | What it does |
|---|---|
| Damage Limiters | Cap final damage per hit: mace, spear, trident, bow, crossbow, TNT, TNT minecart, end crystal, anchor/bed, wind charge |
| Cooldowns | Mace, spear lunge, wind charge, ender pearl, golden apples, chorus fruit, elytra rocket, trident. Uses the vanilla cooldown overlay on the item |
| Netherite Ban | Toggle each netherite piece, ingot, scrap, block, ancient debris and template. Blocks crafting, smithing, pickup, use and mining, and sweeps inventories |
| Item Ban | Same engine for any item (`/smp itemban add|remove|list`) |
| Potion Limiter | Per-effect ban or max level. Blocks brewing and use. `allow-command-effects` toggle keeps `/effect give` working |
| Enchant Limiter | Global and per-item (wildcards) level limits for anvil and enchanting table |
| Dimension Ban | Close the Nether and/or End, also blocks teleports into them |
| TempBan | `/tempban ban|unban|list|history|gui`, picker GUI, custom message, presets, custom kick screen, history |
| Death Ban | Temp-ban on death |
| Chat Moderation | Cooldown, duplicate block, caps, links, blocked words, @mentions with sound, /msg /reply, join/leave, death messages with hover stats, hidden advancement spam |

## Permissions
`smpcore.admin`, `smpcore.tempban`, `smpcore.chat.bypass`, `smpcore.bypass.{damage,cooldown,itemban,potion,enchant,dimension,deathban}`

## Notes
- Spear items are looked up by name, so spear cooldown and damage only apply on versions that have spears.
- Cooldown for spear lunge is best-effort: it starts one tick after a right-click with a spear.
- A cancelled brew leaves the ingredients in the stand; the stand simply won't finish a banned potion.

## 1.1.0 additions
- Potion and Enchant limiters are dialogs: search box, every name in pages of 12, then a slider for the limit (0 = banned).
- Death Ban: `death-ban.spectator` keeps the player in spectator mode for the duration instead of banning.
- `/smp` and `/settings` open one big paged settings menu (SMPUtils-style glass border, a new accent colour per page) with every feature, plus search. Tooltips use mouse-icon lines such as "▌▌ Disabled". Left click enables, right click disables; value items also use middle click to reset. Death Ban and Dimension Ban use left and right click for two options each.
- Spear Ban: ban or allow each spear (uses the item ban engine).
- `/punish <player>`: ban, mute, kick, warn. Warnings escalate to a temp-ban (`punish.escalation`).
- Dragon Health, `/pvp`, and 20+ simple switches under `tweaks:` in config.yml.
- Ported from SMPUtils (switches under `tweaks:`): ban crystal PvP, bed bombing, ender pearls, TNT minecarts, villager killing, spawner mining, explosives and draining; disable phantoms, no mob spawning/griefing; book limit; void protection; cobweb decay; XP clumps; apple and heavy core drops; player head drop.
- Not ported (need packets or large systems): anti-xray, anti-minimap, anti-indicators, mod blocker, anti-alt/VPN, homes, tpa/rtp, kits, rituals, loot boxes, custom recipes, invsee, vanish, tree/vein miner.

## 1.2.0 additions
- New look: the palette `#3a5a73 #6d93ab #abc4d1 #e3dccf #f2ede4` carries all text; red, orange, yellow, green and blue only mark key words. Prefix is now a bold gray gradient `[ꜱᴍᴘ]`, so messages read like `[ꜱᴍᴘ] pvp disabled`.
- Evoker Totem Drop (`tweaks.evoker-totem-drop`): turn it off and evokers no longer drop a totem of undying.
- Ban Ender Pearls (`tweaks.ban-pearl`) now only blocks throwing. Pearls stay in the inventory and are never removed.
- Xaero Minimap (`xaero:` in config, `/smp` -> Xaero Minimap): left click = fair play mode, right click = disable the minimap for everyone, optional per-world overrides. Replaces XaeroForceDisabler. Bypass: `smpcore.bypass.xaero`.

## 1.3.0 changes
- Tooltip icons: `▆▆` mouse bars (clicked button green, other gray) and a green `◉` for middle click / reset.
- Netherite Ban now works like NoNetheritePlus: only armour, tools and the spear, and a banned piece just can't be upgraded at the Smithing Table. Players can still hold, wear and use netherite they own. New config section `netherite-ban:`; old netherite entries are moved out of `item-ban.banned` automatically.
- Spears are listed wooden -> netherite, sit right before Netherite Ban in the settings list, and fill one clean row in their menu.
- Settings menu has category tabs (All / PVP / Administrator / World / Util) in the top row.
- Dialog buttons: red `← Previous`, `Next →`, `✖ Back` / `✖ Close`.
- Insta Restock (`instarestock:` in config, `/smp` -> Util): villagers and wandering traders restock when a trade screen opens or a trade is made. Options: wandering traders, price-penalty reset, cooldown, profession blacklist.
- The settings menu has a second glass row under the category tabs; the item grid is 7x3 per page.
- Grace (`grace:` in config, `/smp` -> PVP -> Grace): Resistance IV and Saturation for 30 minutes plus a sidebar clock `🕛 Grace / 30:00`. Every player gets it each time they join; the clock turns green, yellow, orange, red; optional restart after respawn. Time only counts while online. `/smp grace start|stop [player|all]`.
- Blue theme `#052e74 #36578f #6981ab #9babc7 #cdd5e3`: blue text is drawn as gradients, accents (lore states, keywords) are vibrant. `[ꜱᴍᴘ]` uses the gradient `#C3C3C3 -> #E7E7E7`, `[MSG]` is `#850120`. Saved configs are recoloured once on first start (`theme-version`).
- Grace scoreboard: small-caps `ɢʀᴀᴄᴇ` title, time centred under it (`grace.scoreboard.pad`).
- `/ban <player> <duration> [reason]` (and `/unban`, `/pardon`) replace the vanilla commands, e.g. `/ban MrNanzo 7d Griefing`. Without a duration it is permanent.
- Ban screen shows the reason, time left, duration, expiry, who banned and where to appeal (`tempban.appeal`, e.g. your Discord link), in the theme gradients. Saved configs get the new screen automatically.
- Ban animation (`tempban.animation`, `/smp` -> Administrator -> Ban Animation): a giant mace charges above an online player, slams down with a red shockwave and red particles, then the ban screen shows.
- Ban animation: the player is turned to look up at the mace as it slants down from in front of them, and its sounds are only played to players within `tempban.animation.sound-radius` blocks (default 24).
- Ban screen heading is red and bold. During the animation the ground jumps: blocks around the player rise and drop back in a wave with the shockwave (`tempban.animation.ground-radius`, visual only).
- Custom weapon whitelist (`/smp` -> Spear Ban -> Custom weapon whitelist, or hold the item and `/smp whitelist add|remove|list`): when a base item such as the iron spear is banned, your own custom spears stay usable. An entry matches the material plus the details recorded from the item: item model, custom model data, name and text tags written by custom-item plugins. Plain vanilla items can't be whitelisted.
