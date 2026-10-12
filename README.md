# PolyPlus

[![Modrinth downloads](https://img.shields.io/modrinth/dt/Iw9mZi4a?style=flat&logo=modrinth&label=Modrinth&color=1bd96a)](https://modrinth.com/mod/polyplus)
[![CurseForge downloads](https://img.shields.io/curseforge/dt/1740831?style=flat&logo=curseforge&label=CurseForge&color=f16436)](https://www.curseforge.com/minecraft/mc-mods/polyplus)

PolyPlus is the client mod of the OneClient modpack. It lets you host a singleplayer world for your friends, adds a friends list with chat, and shows cosmetics, emoji and a new main menu. The online features run on a Polyfrost service, so read "Online services and your data" before you install it.

## Features

### Host a world for friends

Pick a world on the main menu, or press the button that replaces "Open to LAN" in the pause menu. PolyPlus hosts the world over Epic Online Services peer-to-peer networking, so you do not open a port. Your friends need PolyPlus to join.

- Choose the game mode and whether cheats are allowed.
- Invite single friends or a whole group. Invites show up in their Social menu.
- "Private Relay" sends all traffic through Epic's relay servers. It starts on.
- Run `/polyplus sharepack` to offer your equipped resource pack to everyone who joins.
- If Simple Voice Chat is installed, PolyPlus routes its voice traffic over the same connection.
- You can still host on LAN from the same screen.

### Friends and chat

Press `Shift+P` to open the Social menu. You can rebind the key.

- Send a friend request by username, and accept, decline or cancel requests.
- Block and unblock players.
- Message a friend directly or create a group. You can rename, mute and leave groups.
- See world invites and rejoin a world you were invited to.

### Cosmetics

PolyPlus draws capes, wings, hats, glasses, gloves, boots, auras and back and shoulder items on players who own them. On Minecraft 1.21.1 and newer it also shows pets. Emotes play from an emote wheel. The wheel has no key until you bind one.

- The Cosmetics screen lists what you own and what the store sells. Checkout opens in your browser.
- Eight dropdowns set whose capes, wings, gloves, hats, boots, back items, shoulder items and auras you see: everyone, friends only, yourself or no one.
- Boots cosmetics hide while you wear boots. A separate option does the same for hats and helmets, and it starts off.
- A badge marks other PolyPlus users on nametags and in the tab list. You can turn it off.
- `/polyplus cosmetics` lists, equips and clears cosmetics and plays emotes from chat.

### Chat emoji

Type a shortcode such as `:sob:` and PolyPlus draws the Twemoji image in chat. It suggests shortcodes while you type, and a picker button sits next to the chat box.

### Main menu

PolyPlus replaces the title screen. The new menu has a Quickplay panel with your last 3 servers, a 3D preview of your player, an account switcher, featured servers and buttons for Social, Cosmetics and Host World.

- Hide each part of the menu with its own switch, or turn on "Vanilla Main Menu" to get the normal title screen back.
- Set the menu frame cap to 60 FPS, to your monitor refresh rate plus 60, or to a value from 15 to 260.
- Keep the panorama behind every menu. On 1.21.11 and newer you can use the PolyPlus panorama.

### First launch

The first launch opens a setup screen. It asks about toggle sprint, motion blur, GUI scale and the settings of other OneClient mods that are installed, such as Wavey Capes, Mount Opacity and Custom Block Highlight.

PolyPlus also changes settings outside its own menu on first launch. It applies default Minecraft options, sets defaults for mods such as Mod Menu, Bobby and Controlify, and removes the default keys of several mods so they do not clash.

### Smaller additions

- A notification suggests a different memory allocation when the current one hurts performance. It appears at most once every two days and you can turn it off.
- On 1.8.9 on Windows, a "Convert Sensitivity..." button in the controls screen converts a sensitivity value for your Windows pointer speed.
- The narrator is skipped while master or voice volume is at 0%.
- PolyPlus refreshes your Microsoft session when it expires.
- PolyPlus includes Polydium, the Polyfrost performance mod, so you do not install it separately.

## Terms of Service

PolyPlus needs you to accept the Polyfrost [Terms of Service](https://polyfrost.org/legal/terms) and [Privacy Policy](https://polyfrost.org/legal/privacy). If you launch through OneClient, the launcher's answer counts. Otherwise PolyPlus asks on first launch. If you decline, hosting, friends, chat, cosmetics, featured servers and crash reporting stay off. You can change your answer under Privacy in the PolyPlus settings.

## Compatibility

- PolyPlus does not load alongside Auto Reauth.
- PolyPlus does not load with WWaypoints older than 0.8.0.

## Supported versions

- Minecraft 1.8.9 on Ornithe.
- Minecraft 1.21.1, 1.21.4, 1.21.5, 1.21.8, 1.21.10, 1.21.11, 26.1, 26.2 and 26.3 on Fabric.

PolyPlus needs [OneConfig](https://modrinth.com/mod/oneconfig), the Compose Multiplatform mod and Fabric Language Kotlin. The Fabric builds also need Fabric API.

## Support

- Report bugs and ask questions on the [Polyfrost Discord](https://polyfrost.org/discord).
- Check [Polyfrost Status](https://status.polyfrost.org/) if a Polyfrost service is down.

## Building

```sh
./gradlew ":26.3-fabric:build"
```

Replace `26.3` with any supported Fabric version. The 1.8.9 project is `:1.8.9-ornithe`. The build needs JDK 21 and JDK 25 installed.
