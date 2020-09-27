This is a Minecraft (Bukkit) plugin that sends push notifications from your Minecraft server via [Pushover](http://www.pushover.net/).

This is a fork of [jobukkit's](https://github.com/jobukkit) orignal [MinecraftPush](https://github.com/jobukkit/MinecraftPush) version.

I forked it in order to add tracking of how long a player was on when they leave. As a Dad running a minecraft server,
this is good information.

Along the way, I also added a gradle build environment, removed calls to a flaky phone-home plugin tracking
service, and specified an API version in the plugin so bukkit doesn't warn about legacy plugins.


Installation
-----
1. Put the plugin in your plugins folder.
2. Reload or restart your server.

Usage
----
Join and type the command: `/pushover enable <pushover user key>`. Get your Pushover user key by logging in at [Pushover's website](http://www.pushover.net/), copying it, and then pasting it in the chat using <kbd>Ctrl</kbd>+<kbd>V</kbd> or <kbd>⌘</kbd>+<kbd>V</kbd>. Your players can (and should 😀) do this too! Enjoy! Only offline players receive notifications.

You can disable notifications again at any time using `/pushover disable`.

Features
-----

- Get a notification when someone joins or leaves. 📲
   - Custom join/leave messages supported!
   - Includes how long player was connected when player leaves
- Broadcast to all online players and all offline players with notifications enabled with `/broadcast <message>`. 📢
   - Permission is `minecraftpush.broadcast`; by default all ops can do it.
- [Name changing](https://mojang.com/2014/04/minecraft-1-7-6-pre-release/) ready! 📛
- More features coming! _(probably not unless I rewrite it)_

---------

### Notes for users of very old Minecraft versions (for example Tekkit Classic)
##### On a fairly recent Minecraft version? Don't read this. It doesn't contain anything even remotely useful for you. Otherwise:

- The plugin will crash by default on very old versions, due to Plugin Metrics being incompatible. Change `opt-out` to `true` in config.yml in the Plugin Metrics folder in the plugins folder to fix this.
- When resetting your world, everyone will need to re-enter their Pushover user keys. This is because of the way old Bukkit versions handle player UUID's.

-----

This plugin is powered by [pushover4j](https://github.com/sps/pushover4j).
