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
- Grace (`grace:` in config, `/smp` -> PVP -> Grace): Resistance IV and Saturation for 30 minutes plus a sidebar clock (vanilla clock icon, white bold small-caps title). Players get it on their first join only; the clock turns green, yellow, orange, red; optional restart after respawn. Time only counts while online. `/smp grace start|stop [player|all]`.
- Blue theme `#052e74 #36578f #6981ab #9babc7 #cdd5e3`: blue text is drawn as gradients, accents (lore states, keywords) are vibrant. `[ꜱᴍᴘ]` uses the gradient `#C3C3C3 -> #E7E7E7`, `[MSG]` is `#850120`. Saved configs are recoloured once on first start (`theme-version`).
- Grace scoreboard: small-caps `ɢʀᴀᴄᴇ` title, time centred under it (`grace.scoreboard.pad`).
- `/ban <player> <duration> [reason]` (and `/unban`, `/pardon`) replace the vanilla commands, e.g. `/ban MrNanzo 7d Griefing`. Without a duration it is permanent.
- Ban screen shows the reason, time left, duration, expiry, who banned and where to appeal (`tempban.appeal`, e.g. your Discord link), in the theme gradients. Saved configs get the new screen automatically.
- Ban animation (`tempban.animation`, `/smp` -> Administrator -> Ban Animation): a giant mace charges above an online player, slams down with a red shockwave and red particles, then the ban screen shows.
- Ban animation: the player is turned to look up at the mace as it slants down from in front of them, and its sounds are only played to players within `tempban.animation.sound-radius` blocks (default 24).
- Ban screen heading is red and bold. During the animation the ground jumps: blocks around the player rise and drop back in a wave with the shockwave (`tempban.animation.ground-radius`, visual only).
- Custom weapon whitelist (`/smp` -> Spear Ban -> Custom weapon whitelist, or hold the item and `/smp whitelist add|remove|list`): when a base item such as the iron spear is banned, your own custom spears stay usable. An entry matches the material plus the details recorded from the item: item model, custom model data, name and text tags written by custom-item plugins. Plain vanilla items can't be whitelisted.
- `/spawn`, `/setspawn`, `/warp <name>`, `/warps` (menu), `/setwarp <name>`, `/delwarp <name>`. Players wait `spawn.delay-seconds` (default 5, change with `/smp spawn delay <seconds>`) with an action-bar countdown; moving or taking damage cancels the teleport. Separate Spawn and Warps entries in `/smp` -> Util (left click opens, right click enables or disables; command hints in the tooltip). `smpcore.spawn.bypass` skips the wait. Locations are saved in `locations.yml`.
- Random TP (`random-tp:` in config, `/smp` -> World -> Random TP): new players are sent to a random safe spot on their first join, within `radius` blocks of the centre (the spawn by default, or `world`, or `"x,z"`). Adjust the radius in the menu (click: 500, Shift: 200) or test it with `/smp rtp [player]`. `smpcore.randomtp.bypass` skips it.
- Feature names in the main menu each get their own dark-light-dark gradient (Random TP is `#00D0FF #C4EEFF #00D0FF`).
- Feature names in the main menu are drawn in small caps.
- Staff tools (`staff:` in config, `/smp` -> Administrator): `/vanish` (alias `/v`) hides you from players, the tab list, name completion, the server list, `/list`, `/msg` and join/leave/death/advancement messages, and survives relogs; `smpcore.vanish.see` can see vanished staff. `/freeze <player>` stops movement, building, interaction, damage and commands (and survives relogs, optional `quit-command`). `/invsee <player>` and `/endersee <player>` open inventories, view only unless `smpcore.invsee.modify`.

## Settings menu layout change
- The category tabs and the second glass row are gone; the item grid starts right under the top border (7x4 per page).
- Top row, first slot: a nether star that cycles All / PVP / Administrator / World / Util (left click = next, right click = previous).
- Top row, ninth slot: a command block that opens the Commands menu, listing every command (/vanish, /spawn, /warp ...) with usage, aliases, permission and feature state. Commands registered in plugin.yml but missing from the list are added automatically.

## Combat system
- Hitting or being hit by a player starts a combat timer (`combat.seconds`, default 20, `/smp combat seconds <n>`). Action bar: `🕛 ᴄᴏᴍʙᴀᴛ : 20` in the `[MSG]` red `#850120`. Messages are in `combat.messages` (MiniMessage).
- Command blocking (`combat.commands`): `mode: blacklist` blocks the `blocked` list, `mode: whitelist` blocks everything except `allowed`. Aliases and `minecraft:` prefixes are caught (blocking `msg` also blocks `/tell`).
- Logging out in combat kills the player and broadcasts it (`combat.logout`). Staff kicks are not punished.
- Safe zones (`combat.safe-zone`): the spawn area (`spawn-radius`, `/smp safezone spawn <radius>`) plus zones made with `/smp safezone add <name> <radius>` where you stand (`remove`, `list`). Inside a safe zone players can't hit or be hit by other players, and a player in combat can't walk, pearl or chorus into one.
- `/smp combat clear <player|all>` ends combat early. Menu: `/smp` -> PVP -> Combat. Bypass: `smpcore.bypass.combat`.
- Replaces the old `tweaks.combat-log` switch.

## 1.4.1 changes (combat)
- Safe zones are now set up automatically: every WorldGuard region with the flag `pvp: deny` counts as a safe zone (no jar needed, WorldGuard is only a soft dependency), and `combat.safe-zone.auto.warps-radius` can turn every warp into one. The spawn area and `/smp safezone` zones still work. `/smp safezone list` shows the WorldGuard status.
- New messages: "You are out of combat" (chat, big title and sound), a title when you get tagged, and an action-bar message when you enter or leave a safe zone (`combat.safe-zone.auto.notify`). All texts are in `combat.messages`, titles can be switched off with `combat.titles`. Saved configs get the new texts once (`combat.messages-version`).

## 1.4.2: rejoin time
- New feature **Rejoin Time** (`combat.rejoin`, off by default, switch in `/smp` -> PVP -> Rejoin Time or the Combat menu). When it is on, a player who disconnects in combat is not punished at once. They have `combat.rejoin.seconds` (30, change with `/smp combat rejoin <seconds>`) to log back in.
  - Back in time: no penalty, and (`retag`) they are tagged again for the combat time they had left, so they can't escape by relogging.
  - Too late: it is announced when the time runs out, and the player is killed the moment they next join (an offline player can't be killed through the server API). They appear where they logged out, so their items drop there.
- Pending rejoins are saved in `combat-rejoin.yml`, so a restart doesn't lose them. A server stop is never treated as a combat logout.

## 1.4.3: announcements
- `/announce <message>` (alias `/ann`, permission `smpcore.announce`): a big small-caps **ᴀɴɴᴏᴜɴᴄᴇᴍᴇɴᴛ** title in the gradient `#00EEFF -> #D5FBFF` with a white glint that sweeps over it, the message centred under it, and the line `ᴀɴɴᴏᴜɴᴄᴇᴍᴇɴᴛ → message` in chat. Works from the console too.
- A Minecraft subtitle can't wrap, so a long message is word-wrapped (`announce.wrap-chars`, 32) and the lines show one after another under the title (`announce.page-seconds`, 4). The full text is always in chat.
- Sounds: `announce.title-sounds` is a list that plays in order with a delay in ticks (default: beacon activate, chime 0.3s later, level-up 0.6s later). Add or change entries freely.
- `/broadcast <message>` (aliases `/bc`, `/bcast`, permission `smpcore.broadcast`): chat only, no title, own sound (`announce.broadcast-sound`).
- Switch in `/smp` -> Administrator -> Announcements (`announce.enabled`). Texts, colours, glint speed and width are in the `announce:` section of config.yml.
- Colour presets (`announce.presets`, current one in `announce.preset`): ice (default), gold, crimson, emerald, violet, sunset, royal, or add your own. In `/smp` -> Administrator -> Announcements hover the bell and press **Q** for the next preset, **Ctrl+Q** for the previous one; the title is previewed to you with the new colours and the choice is saved. It recolours the title, the chat label and the message text.


## 1.4.4: small changes
- **Rejoin time** is no longer its own entry. It is on the **Combat** icon in the settings menu: press **Q** to turn it on or off (the tooltip shows its state and the time to rejoin). It is still in the Combat menu too.
- **Spawn, Warps, Vanish, Freeze and Inventory See** are no longer in the main feature list, because they are commands. Their switches are in the **Commands** menu (command block, top right): left click on a command enables the feature behind it, right click disables it. On `/spawn`, press **Q** for the spawn settings (cancel on move, cancel on damage, respawn at spawn).

## 1.4.5: item ban with a menu
- `/smp` -> Item Ban now has a **hopper** at the top. Put any item on your cursor and click it (or hold the item and click): **left click** bans that **exact item** (a Sharpness V book, a Strength II potion, a named or custom item), **right click** (or Shift) bans the **whole item type**. Plain items always ban the whole type. Click a banned entry to allow it again. Exact items keep their own look in the list.
- A banned item can't be picked up, crafted, used, placed, **put into a chest or taken out of one** (also hoppers and number-key swaps), and is removed from inventories (existing options). Switch for the chest part: `item-ban.block-containers`.
- What counts as "the exact item": material, enchantments, stored enchantments (books), potion type and effects, custom name, item model, custom model data and text tags. Damage and amount don't matter.
- `/smp itemban list` shows the exact items too. Exact items are saved in `item-ban.exact`.

## 1.5.0
- **Item Limit** (`/smp` -> PVP -> Item Limit, `item-limit` in config, `/smp itemlimit add <limit>|list|remove`): limit how many of an item a player can carry, for example 2 enchanted golden apples. Anything over the limit is dropped from the inventory (from the end first), and picking up only fills the free room. Same hopper as the item ban: left click = this exact item (enchanted book, potion, named item), right click = the whole type. Change a limit with LMB/RMB (Shift = 8), Q removes it. Bypass: `smpcore.bypass.itemlimit`.
- **Anti Health Indicator** (`/smp` -> Administrator, `anti-health` in config): hides the health of other entities from health bar mods and hacked clients, plus absorption, air, your own saturation and the enchanting seed, with the same exceptions as the AntiHealthIndicator plugin (vehicle, tamed dogs, ender dragon, wither, iron golem cracks). **Needs ProtocolLib**; SMPCore runs fine without it (the feature just does nothing). Built against ProtocolLib 5.3.0 (`pom.xml`), untested with Minecraft 26.x data indexes: if the health still shows, set `anti-health.absorption-index` and tell me your version.
- **`/smp maintenance <on|off|status>`**: switch maintenance mode from chat or the console (also in the Commands menu: left click = on, right click = off).

## 1.5.1: fixes
- **Dimension ban and boats:** a boat, minecart or horse with a player on board goes through a portal as one entity, so the passenger never triggered the ban. The vehicle is now checked too (also when a passenger rides a passenger), and a player who still ends up in a closed dimension is sent back (safety net).
- **Item ban / item limit and potions:** exact potions, splash and lingering potions and tipped arrows now show their effect and tier in the menu and in the chat message, for example `Strength · Tier 2 is banned`. Other tiers stay allowed.
- **Enchant limiter and existing items:** the limiter used to check only the anvil and the enchanting table. Now `enchant-limiter.clean-existing: true` also lowers or removes over-limit enchantments on items that already exist: chests, barrels, shulker boxes and loot chests are cleaned when opened, picked up items and inventories (also ender chests) are cleaned when they change, on join, every 5 seconds, and right after `/enchant` (from a player or the console). Enchanted books are handled through their stored enchantments. Bypass: `smpcore.bypass.enchant`.

## 1.5.2 changes
- Potion and Enchant limiter dialogs show vanilla icons (enchant = item such as chestplate / TNT, potion = effect sprite) using built-in Adventure sprites, so no resource pack is needed. Needs Paper 1.21.9+, otherwise skipped. Switch: `dialog-icons`.
- Grace: only on a player's first join; sidebar uses the vanilla clock sprite instead of the emoji; the title is white, bold, small caps.
- Announcements: the title and the message under it are shown in small caps (chat line unchanged).
