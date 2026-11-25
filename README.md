# PolyPlus, Swylow edition

A fork of [PolyPlus](https://github.com/Polyfrost/PolyPlus) 2.0.11 (the OneClient companion mod) with a vanilla style main menu and a menu editor. Built for Minecraft 26.2 on Fabric. Download: https://swylow.simplemcnetwork.net

What's different:

- **Intro**: the OneClient logo rises while the screen opens like a curtain, then the menu slides in row by row (also into the vanilla and PolyPlus menus).
- **Vanilla menu presets**: Centered, Essential style (your skin left, buttons right, account top left) and OneClient style (Quickplay left, skin, account and buttons right).
- **Menu editor** for the lobby, the PolyPlus menu and the vanilla menu: "Edit main menu" asks which menu, then drag pieces anywhere. In the vanilla one, right click hides or shows a piece and you can switch presets and edit from there. Reset puts everything back.
- **Quickplay panel**: your last servers with icon and players online, folds open and closed.
- **Top right**: notifications bell and a settings dropdown (Lobby, PolyPlus menu, Notifications, Light/Dark theme, Edit main menu, Settings). Can be turned off to show them all in a row.
- **Dark theme for vanilla buttons**, switched together with the OneConfig theme.
- **Singleplayer**: worlds for your version and worlds from other versions in their own sections, plus a confirm screen before opening a world from another version.
- **Multiplayer**: Servers, Friends and Discover tabs. Servers adds a "Recent direct connects" section (last 3). Friends shows worlds your friends are hosting for you. Discover lists OneClient's server catalog to join or add.
- Option to use the vanilla style Singleplayer and Multiplayer screens from the lobby too.

All of it can be turned on and off in OneConfig: Main Menu (OneClient) > Vanilla Style.

Build: `./gradlew :26.2-fabric:build` (JDK 25), jar in `versions/26.2-fabric/build/libs/`.

---

# PolyPlus

[![Modrinth downloads](https://img.shields.io/modrinth/dt/Iw9mZi4a?style=flat&logo=modrinth&label=Modrinth&color=1bd96a)](https://modrinth.com/mod/polyplus)
[![CurseForge downloads](https://img.shields.io/curseforge/dt/1740831?style=flat&logo=curseforge&label=CurseForge&color=f16436)](https://www.curseforge.com/minecraft/mc-mods/polyplus)

PolyPlus is the companion mod for OneClient. It adds OneClient's online features to the game.

### Host a world

Pick a world from the main menu, or use the button that replaces "Open to LAN", and your friends can join without any port forwarding (they need PolyPlus too).

- Choose the game mode and whether cheats are allowed
- Invite single friends, a group or your party
- Share your resource pack with everyone who joins using `/polyplus sharepack`
- Works with Simple Voice Chat
- Regular LAN hosting is still there

### Friends and chat

Open the Social menu with `Shift+P`, or from the lobby.

- Add friends by username
- Direct messages and group chats
- Block players
- See your world invites and rejoin worlds you were invited to

### Cosmetics

Capes, wings, head and face items, gloves, feet, auras, back and shoulder items, plus pets on modern versions and emotes from an emote wheel.

- Try things on in the wardrobe and buy new ones from the marketplace
- Choose whose cosmetics you see for each type: everyone, friends only, just yourself or nobody
- A badge on nametags and in the tab list shows who else uses PolyPlus

### Parties and voice chat

Start a party with your friends and it follows you between worlds and servers.

- Voice chat for the whole party, with voice activity or push to talk (`V`)
- Proximity mode, where you hear party members from the direction they're standing in
- Play Together moves everyone to the leader's server or hosted world
- Your party sees your lobby scene, and you can share your own captures with them
- Ping blocks, players and dropped items with middle click, or hold it for a wheel of ping types

### The lobby

The main menu is a live 3D lobby made from places you've played. While you play, PolyPlus saves scenes from your worlds and servers, and the lobby rebuilds one behind the menu and puts your player in it.

- Pages for multiplayer, singleplayer, your wardrobe, the cosmetics marketplace, friends, stats and news from the Polyfrost blog
- Browse and watch your Replay Mod or Flashback recordings from the Replays page
- Pick how the scene reveals itself, what time of day it shows and which music plays
- Save the spot you're standing on as a scene, rename scenes and pin the one you like most
- Switch to the classic PolyPlus main menu (Quickplay, featured servers and a player preview) or back to vanilla

### Pause screen

The pause screen shows your player next to the menu and has shortcuts to the wardrobe, your party and your replay recording. There's a compact style too, or you can keep the vanilla one with PolyPlus's buttons beside it.

### Chat emoji

Type a shortcode like `:sob:` and it turns into an emoji. You get suggestions while you type, and there's a picker next to the chat box.

### Extras

- A first launch setup screen for toggle sprint, motion blur, GUI scale and other OneClient mods
- A branded loading screen
- A warning when your memory allocation is hurting performance
- A raw input switch in the controls screen (1.8.9)

## Terms of Service

The online features need you to accept the Polyfrost [Terms of Service](https://polyfrost.org/legal/terms) and [Privacy Policy](https://polyfrost.org/legal/privacy). If you decline, parties, voice, hosting, friends, chat and cosmetics stay off. You can change your answer under Privacy in the PolyPlus settings.

## Compatibility

PolyPlus doesn't work together with Auto Reauth or with wWaypoints older than 0.8.0.

## Supported versions

- Minecraft 1.8.9 on Ornithe.
- Minecraft 1.21.1-26.3 on Fabric.

PolyPlus needs [OneConfig](https://modrinth.com/mod/oneconfig), the Compose Multiplatform mod and Fabric Language Kotlin. The Fabric builds also need Fabric API.

## Support

- Report bugs and ask questions on the [Polyfrost Discord](https://polyfrost.org/discord).
- Check [Polyfrost Status](https://status.polyfrost.org/) if a Polyfrost service is down.
