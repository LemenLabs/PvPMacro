# PvP Macro 1.1.0 - only the files to change (built on your latest 1.0.12 sources)

Copy these into your repo (same folders), overwrite when asked. Everything else stays as it is.
  src/client/java/com/pvpmacro/   Config, Steps, Combos, BowBoost, AutoTotem, PvpMacroMod (changed)
                                  Containers, Restock, Ui, MenuScreen (NEW)
  src/main/resources/             fabric.mod.json (version), assets/pvpmacro/lang/en_us.json (keys)
Not touched: WindChargeFlight.java (nothing uses it any more, you can delete it), the mixins, MacroRunner, Step, SlotSync, everything else.

Bow boost: shorter draw (4), sprint kept 20 ticks after the shot, picks the hotbar bow with the most Punch.
Auto totem: inventory totems via one inventory click (hotbar fallback kept). Restock: new, off by default. Menu: Right Shift.
Riptide Mace: removed. Config: new keys, existing ones kept (one-time bow boost draw migration).
Not compiled or run by me.
