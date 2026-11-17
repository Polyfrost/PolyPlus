## Unreleased changes
- Added generic names for mods in the OneConfig mod list, such as Motion Blur for PolyBlur and Toggle Sprint for PolySprint
- Vignette Tweaks now starts disabled on new installs

## 2.1.0
Requires OneConfig 1.8.0.

The settings, resource packs, Create World, Edit World, connecting, loading and Realms screens use the lobby's style now. You can switch each one back to vanilla in settings or during onboarding.

### New screens
- Video settings has tabs for Sodium, Iris and Argentum, and Sodium add-ons can add their own pages.
- Controls shows a map of your keyboard, and OneConfig shortcuts can use key combos like Ctrl+K.
- Resource packs can be sorted into folders, dragged around and saved as profiles. Packed Packs works with it.

### Lobby
- You can pin servers to the home page and group them into folders.
- You can customize the lobby and give each server its own scene. The main menu toggles moved here.
- The wardrobe was redesigned.
- Realms show up on the Multiplayer page.
- The dock has a music button, and the top right has a Discord button.
- The Quit options are easier to find, and you can set Quit to need a second click.

### Pause screen
- Added a Classic style. It has the OneClient look with vanilla's button layout, and a row for Social, Wardrobe, Host, Mods and More below it.
- You can pick a separate style for when the game pauses because you clicked out of the window.
- Mods opens OneConfig, and Mod Menu is under More.
- "Classic pause menu" under More is renamed to "Vanilla pause menu".
- Spotlight can keep panning the camera after its animation ends.

### Other changes
- The account manager was rebuilt. You can link Discord from it, and it opens our server invite after you link.
- Added a clock widget that follows the time of day.
- Lobby and pause widgets were reworked.
- The main menu can use the vanilla panorama.
- An optional notification tells you how long the game took to start.
- Play Together tells you when a friend is on another version.
- Player previews follow your skin layer, cape and main hand settings.
- When Polyfrost's services have problems, you get a notification and the lobby shows a button to status.polyfrost.org.
- On 1.8.9 you can switch the Options screen back to vanilla's centered layout.
- Reloading resources from OneClient's screens shows the OneClient loading screen.
- PolyPlus turns off Remove Reloading Screen (RRLS) if it's installed, where it used to refuse to launch.

### Fixes
- Fixed the lobby being upside down with VulkanMod.
- Fixed skins not refreshing after switching accounts.
- Fixed your hand showing in the lobby with some mods and resource packs.
- Fixed formatting codes in pause menu text.
- Fixed Enter not opening Social after closing a popup.
- Fixed widgets moving when you toggle one.
- Fixed sign-ins being retried after Mojang rejected them.
- Fixed sent friend requests showing under "Waiting on you".
- Fixed startup sounds with OneConfig's external sounds.
