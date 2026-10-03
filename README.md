
<h1 align="center">Cat Client 2</h1>
<p align="center">A personal fork of Meteor Client with cat-themed ClickGUI styles.</p>

> **This is an unofficial, personal fork of [Meteor Client](https://github.com/MeteorDevelopment/meteor-client)** by MeteorDevelopment (MineGame159, squidoodly, seasnail). All modules and the core framework are their work; see [CREDITS.md](CREDITS.md) for details. This fork only adds two extra ClickGUI themes ("Pixel Cat's End" and "Classic") skinned with custom texture packs, selectable in-game via the GUI theme dropdown.

## Usage

### Building
- Clone this repository
- Run `./gradlew build`

### Friend system (launcher only)

Everything about friends and joining happens in the launcher, never in Minecraft. Two pieces:

- `friends-server` - a small service that brokers the data (friendships, join requests, which modpack a running game needs). It only stores and hands out small descriptions; mod jars are downloaded directly from Modrinth by each launcher.
- `launcher` - the client side: friends screen, request handling, modpack publishing and installation.

Build and run the service once, anywhere with Java 21+:

```
./gradlew :friends-server:shadowJar
java -jar friends-server/build/libs/cat-friends-server.jar --port=8765 --data=friends-data.json
```

Then in the launcher open **Friends**, put the service URL into "Friend service" (default `http://localhost:8765`) and press **Connect**.

How it works:

1. Everyone logs in with their Minecraft account. The launcher sends its Minecraft access token to the service, which validates it against Mojang, so nobody can act as somebody else. Only friends can see each other's sessions.
2. **Add friend** sends a request by Minecraft name. Asking a friend to join your game is the same request with kind `JOIN`.
3. Start Minecraft. While the game runs the launcher publishes a session: Minecraft version, mod loader + version, the mod list with each mod's version, and the address friends should connect to. The address is detected from the game log when a world is opened to LAN, and can be corrected by hand in the friends screen.
4. A friend presses **Ask to join**. As soon as you accept, their launcher installs everything that is missing into a dedicated instance for you (`.../CatClient2/instances/friend-<name>-<uuid>`) and starts the game with `--quickPlayMultiplayer`, so it lands directly in your world.

Mod versions are never guessed: the host resolves each mod it has installed against Modrinth for its own Minecraft version and loader, the friend downloads that exact file and the SHA-1 is verified. If a mod has no build for the friend's Minecraft version, or the exact version is gone, the join screen says so instead of installing something that cannot load. Mods that are not on Modrinth have to be copied into the friend's instance by hand - the launch screen lists them.

A session is only visible for two minutes after the host's last heartbeat, so "in game" cannot be faked by a launcher that stopped talking to the service.

### Installation
Follow the [guide](https://meteorclient.com/faq/installation) on the wiki.

## Contributions
We will review and help with all reasonable pull requests as long as the guidelines below are met.

- The license header must be applied to all java source code files.
- IDE or system-related files should be added to the `.gitignore`, never committed in pull requests.
- In general, check existing code to make sure your code matches relatively close to the code already in the project.
- Favour readability over compactness.
- If you need help, check out the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html) for a reference.

## Bugs and Suggestions
Bug reports and suggestions should be made in this repo's [issue tracker](https://github.com/MeteorDevelopment/meteor-client/issues) using the templates provided.  
Please provide as much information as you can to best help us understand your issue and give a better chance of it being resolved.

## Donations
All of our work is completely free and non-profit (donations pay only for hosting costs), therefore we are very grateful for all donations made to support us in running our community.  
Donations can be made via our [website](https://meteorclient.com/donate) and the minimum amount to get donor benefits is €5.  
You will be rewarded with a role on our Discord server and a customisable in-game cape.  
⚠️ _Make sure to create a Meteor account and link your Discord and Minecraft accounts to fully experience your rewards._ ⚠️

## Credits
See [CREDITS.md](CREDITS.md) for full upstream and asset credits.

[Cabaletta](https://github.com/cabaletta) and [WagYourTail](https://github.com/wagyourtail) for [Baritone](https://github.com/cabaletta/baritone)  
The [Fabric Team](https://github.com/FabricMC) for [Fabric](https://github.com/FabricMC/fabric-loader) and [Yarn](https://github.com/FabricMC/yarn)

## Licensing
This project is licensed under the [GNU General Public License v3.0](https://www.gnu.org/licenses/gpl-3.0.en.html). 

If you use **ANY** code from the source:
- You must disclose the source code of your modified work and the source code you took from this project. This means you are not allowed to use code from this project (even partially) in a closed-source and/or obfuscated application.
- You must state clearly and obviously to all end users that you are using code from this project.
- Your application must also be licensed under the same license.

*If you have any other questions, check our [FAQ](https://meteorclient.com/faq) or ask in our [Discord](https://meteorclient.com/discord) server.*
