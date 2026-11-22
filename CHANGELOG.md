# Changelog

## 1.0.13
- **Bow boost: the arrow now gets chased.** Two causes for "the arrow shoots but never hits me": the macro pressed sprint but never *forward*, so the sprint was dropped at once, and it let go of sprint right after the shot, so you stopped while the arrow flew on ahead. Now forward is held for the whole boost, and after the shot the macro tracks YOUR arrow and holds or releases forward/sprint depending on whether it is ahead of you or behind you (`bowBoostChaseArrow`, `bowBoostRunTicks`). The log records how close the arrow came (`closest approach ... blocks`) so draw ticks and pitch can be tuned from facts. Whether the arrow lands on you depends on a narrow window (a different draw tick or a few degrees can change it), so both are in the menu.
- **Auto totem works for items in the main inventory again.** The old code looked up a click method and enum under their pre-26 names, which no longer exist; the method is now found by its shape (see `ContainerClicks`). The check for an open screen read a field that is gone in 26.2, so it never worked; it now uses `Gui.screen()`. A cooldown stops a late server answer from being swapped straight back out. The switch is remembered between launches.
- **Restock (new).** A stackable item in your hotbar (and offhand) that drops below half of its maximum stack (configurable) is topped up from another stack of the same item in your inventory. Pick up the donor stack, put it on the low stack, put any left-over back. Configurable: on/off (menu or "Toggle Restock" key, unbound by default), percentage, hotbar or whole inventory, all items or only a list, offhand, delay. Not done while a macro runs, a screen is open, in creative, or when the cursor is busy. Never drains hotbar stacks and never merges items with different enchantments or components.
- **PvP Macro menu (new).** Right Shift (rebindable). 10 pages: General, Macros (per-macro on/off), Swaps, Elytra, Pearl Catch, Insta Cart and Xbow, Bow Boost, Auto Totem, Restock, Restock Item List. Every change is saved at once.
- **Riptide mace removed** (macro, key, and its two config keys).
- New files: `ContainerClicks.java`, `Stacks.java`, `Restock.java`, `PvpMacroScreen.java`. Changed: `AutoTotem`, `BowBoost`, `Config`, `Combos`, `PvpMacroMod`, `Reflect`, lang file, `fabric.mod.json`.

## 1.0.12
- **Lunge Swap** now selects the wind charge by itself when you are not holding it, then waits `lungeSwitchWaitTicks` (default 3) before swapping to the spear so the server really has the charge in hand. If you already hold the charge there is no wait.
- **Carting works with any rail**: Insta Cart and Xbow use whichever rail is in the hotbar (rail, powered, detector or activator). A rail you already hold is kept.
- **Mace macros while gliding**: Stun Slam, Sword Swap and Breach Swap now check whether you are gliding in an elytra. If so they do the Elytra Mace trick around their hits (leave the glide by packet right before the first hit, resume it right after the last), so "elytra stun slam" etc. happen on the same key. Turn off with `flightMaceMacros=false`. Not used in `elytraMaceMode: chestplate`.
- New config keys: `lungeSwitchWaitTicks`, `flightMaceMacros`.

## 1.0.11 (not compiled or run in game by me) - built on the 1.0.8 sources you sent back
### Bow boost never worked: real cause found and fixed
Step.REPEAT ("run this step again next tick") was never handled by MacroRunner. It stored the value (-2) as the delay, so any step that
returned it froze the macro. Bow Boost returns it every tick, so it stopped after its first step ("sprint") and stayed stuck, and any other
key presses just queued behind it. MacroRunner now runs the same step again next tick, and gives up after 200 ticks with a message.
The pearl "find the pearl" step needed the same fix. Bow boost draw time stays at 5 ticks (4 is the minimum that fires).
### Progress lines off by default
showProgress / progressInChat default to false (a one-time migration switches them off), so no more "sprint > done". Failure reasons
(no arrows, not on the ground, ...) are still shown, and the messages use LocalPlayer.sendOverlayMessage (confirmed in 26.2).
### Xbow with flint and steel
- The macro now uses what is in the hotbar: your config preference (expoUseLava) is followed unless that item is missing and the other one is there.
- Flint and steel is now placed the same way the lava path works: aim at the fire spot (between you and the cart), wait for the server to
  have the angle, then use it on that block with the game's own crosshair hit (computed top-face hit as fallback). Before, it used a computed hit
  without aiming first.
### Pearl catch in any direction, Bow boost 78 degrees, already-held item skip, auto select
Carried over from 1.0.8-1.0.10 (aim mode with prediction, pearlFindTicks 6, pearlCatchMode "straight" for the old behaviour).
### Files
MacroRunner, Config, Steps, Combos, fabric.mod.json (version). Everything else is as in the version you sent.
### Untested
All of it. Flint: if it still fails, set "showProgress": true and look for "fire: ..." lines in logs/latest.log.

## 1.0.10 (not compiled or run in game by me)
- Messages: Steps.msg now calls LocalPlayer.sendOverlayMessage(Component) first (confirmed in 26.2, the Attribute Swap mod calls it). The old
  method lookup may never have found an action-bar method on 26.2, which would mean none of the "PvP Macro: ..." messages (progress lines,
  "no bow in hotbar", auto totem status) were ever visible. The old lookup stays as a fallback.
- The progress line is now also printed in the chat window (config progressInChat), because the action bar disappears after a few seconds.
- Files: Steps, MacroRunner, Config, fabric.mod.json (version).

## 1.0.9 (not compiled or run in game by me)
### Bow boost did nothing: probable cause fixed
The automatic bow boost released the bow after 3 ticks. A bow needs a minimum pull before it fires and the server counts the draw about a
tick shorter than the client, so no arrow was shot (the same problem insta cart had at 3 ticks, which is why bowMinChargeTicks is 5).
bowBoostDrawTicks now defaults to 5 (a one-time migration replaces the old saved 3) and cannot go below 4.
Bow boost now also ends with an action-bar line such as "sprint > jump > top of jump > aim+draw > shot > look back > done",
or "... > STOP: <reason>" (no arrows, not on the ground, never reached the top of the jump, ...).
### Pearl catch in any direction (config pearlCatchMode "aim", default)
Before: the wind charge flew along the same line the pearl was thrown on. That only meets the pearl when you throw straight up, because the pearl
drops with gravity on every other angle while the wind charge flies straight. Now: throw the pearl in any direction, wait (pearlFindTicks, 6)
for it to appear on your screen, turn the camera to where the pearl will be when a wind charge (speed 1.5) gets there (its position and velocity, 1% drag,
0.03 gravity, plus the camera sync ticks), throw the wind charge, put the camera back. If the pearl is not found it throws straight like before.
"straight" mode keeps the old behaviour (pearlCatchDelayTicks).
### Other
- Progress trail: labels no longer repeat while a step waits.
- Files: Steps, Config, Combos, BowBoost, fabric.mod.json (version). Xbow and insta cart untouched.
### Untested
The aim prediction (it is an estimate, tune aimSyncTicks / pearlFindTicks), and everything that needs the game.

## 1.0.8 (not compiled or run in game by me)
Studied two mods you sent: Bow Boostin (CC0, Minecraft 1.21.11) and InstantPearlCatch (Unlicense, server-side, Minecraft 26.1.x only).
### Bow Boost now works like Bow Boostin (new default, config bowBoostMode "auto")
Stand on the ground and press the key. The macro selects a bow, checks for arrows and ground, saves the camera, sprints, jumps (key tap),
waits for the top of the jump (vertical speed <= 0.05, at least 4 ticks, gives up after 15), looks up (bowBoostPitch, default now -78 like Bow Boostin),
draws bowBoostDrawTicks (3), releases, restores the camera and lets go of the sprint/jump keys. Cancels if a screen opens.
"manual" mode keeps the old behaviour (you jump and press at the top; uses bowBoostChargeTicks). New: BowBoost.java.
Only the sequence and numbers were reused: that mod uses 1.21.11 intermediary names, so none of its code runs on 26.2.
### Pearl catching
InstantPearlCatch is a SERVER-SIDE fix (the pearl/wind-charge tick order and a rule that a young pearl cannot hit other projectiles).
A client mod cannot change that on a server it does not own. Its two mixins are rewritten into this jar (ProjectileMixin, EntityTickListMixin,
pvpmacro.mixins.json, listed under "server", required=false, defaultRequire=0 so a target that moved in 26.2 only logs a warning).
They only act on a DEDICATED server that has this jar installed. The jar now declares environment "*" so a server will load it.
The client Pearl Catch macro is unchanged: it throws the pearl and the wind charge along the direction you are looking, so you can aim anywhere.
With that server fix, try pearlCatchDelayTicks = 0 for an instant catch. The original mod is built for 26.1.x only; the port is untested on 26.2.
### Whole mod: select the needed item if it is not already held
slot() no longer switches (or sends a packet) when the item you need is already in your hand, and when several hotbar items match,
the one in your hand is kept. Every macro still selects its own item first when it is not held.
### Files
New: BowBoost.java, mixin/ProjectileMixin.java, mixin/EntityTickListMixin.java (src/main/java/com/pvpmacro/mixin/), pvpmacro.mixins.json.
Changed: Steps, Config, Combos, AutoTotem, fabric.mod.json (environment "*", mixins, version 1.0.8).
### Untested
Everything. In particular: names reached by reflection (onGround, setSprinting, getDeltaMovement, keySprint, keyJump, xRotO),
and whether the two mixin targets exist unchanged in 26.2.

## 1.0.7 (not compiled or run in game by me)
- Bow boost angle default is now 75 degrees up (bowBoostPitch -75; a one-time config migration replaces the old saved -45).
- Xbow and Bow Boost now end with an action-bar line such as "rail > cart > fire > aim > shot > done" (or "... > STOPPED" / "ERROR ..."),
  so you can see where a failure happened without opening the log (config showProgress).
- Auto totem now tells you what it did: "no totem in your inventory", and a few ticks after a swap whether the offhand really holds a totem.
- Files: Config, Steps, MacroRunner, Combos, AutoTotem, fabric.mod.json (version).

## 1.0.6 (not compiled or run in game by me)
Bow boost, Xbow and auto totem all failed in game. I cannot see why without a log, but all three depend on game methods whose names
I could not confirm for 26.2 (camera angle, inventory click). Changes:
- Bow Boost: simplified as requested. ANY bow in the hotbar, whatever arrow the game picks, camera up 45 degrees (bowBoostPitch),
  draw bowBoostChargeTicks, release, camera back. Punch check, arrow check and their config keys are gone.
- Camera: the camera getters/setters are now found by signature (public float get/set with XRot/pitch or YRot/yaw in the name) instead of
  two fixed names. If none exists, Bow Boost cancels with a chat/action-bar message; Xbow carries on without aiming.
- Xbow: aiming is optional now. If the camera cannot be turned, it still places rail, cart, flint and steel (toward you) and fires the crossbow.
- Auto totem: the container-click method is found by its parameters (int, int, int, ClickType, Player) instead of a fixed name; if it cannot
  move an item you now get a message instead of silence.
- NEW Diagnostics.java: once per game start it logs the camera and click methods this version has ("[PvpMacro] diag: ...").
Files: new Diagnostics.java; changed Steps, Combos, Config, AutoTotem, PvpMacroMod, fabric.mod.json (version).

## 1.0.5 (not compiled or run in game by me)
### New
- Chaining: pressing a macro key while another macro runs queues it (config chainQueueSize, default 2, 0 = old behaviour).
  The hotbar is only restored after the last macro of the chain. Every macro still selects its own item first.
- Bow Boost (key K): Punch bow from the hotbar (bowBoostMinPunch, default 2), cancels if the first arrow the game would fire is a
  tipped arrow (allowTippedArrows), camera to bowBoostPitch (-45, up), draws bowBoostChargeTicks (5), releases, restores the camera.
  No arrow choosing, no offhand use.
- Auto Totem (toggle key "Toggle Auto Totem", unbound by default, off until toggled): moves a totem into an empty offhand
  (autoTotemReplaceOffhand = also replace other items, autoTotemDelayTicks = 2). Waits while a macro runs or a screen is open.
### Changed
- Xbow (expo): the fire used to be placed on the far side of the cart. It now goes between you and the cart (expoAheadBlocks blocks
  from the cart toward you), the camera aims at the cart for the shot (aimSyncTicks, default 2, lets the server get the angle),
  then the crossbow fires and the camera is restored. Lava option aims at the lava spot first.
- Pearl catch delay default 6 ticks. A one-time migration (configVersion) replaces an old saved value; edit it again afterwards if you like.
### Files
New: Reflect.java, AutoTotem.java. Changed: Config, MacroRunner, Steps, Combos, PvpMacroMod, lang en_us.json, fabric.mod.json (version).
### Untested / unconfirmed names for 26.2 (all done by reflection, so they fail at runtime with a log line, not at build time)
setXRot/setYRot/getXRot/getYRot/getEyeHeight (camera), Enchantments.PUNCH and ItemEnchantments.getLevel (Punch check),
handleInventoryMouseClick/handleContainerClick + ClickType.SWAP and the swap-hands packet (auto totem). Direct calls that are new:
Items.TIPPED_ARROW, Items.TOTEM_OF_UNDYING, ItemTags.ARROWS, Player.getItemInHand(OFF_HAND), Packet.
Nothing was compiled or run: no Minecraft jars in my environment, only a Java syntax check.
