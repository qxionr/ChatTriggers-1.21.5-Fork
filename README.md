<div style="text-align:center;">
  <p>
    <a href="https://chattriggers.com">
      <img src="https://chattriggers.com/assets/images/logo-final.png" width="546" alt="ChatTriggers.js" />
    </a>
  </p>
  <p>
    <a href="https://discord.gg/scatha">
      <img src="https://discordapp.com/api/guilds/119493402902528000/embed.png" alt="Discord" />
    </a>
  </p>
</div>

### How To Download (build yourself)
1. Install JVM 21 and make sure the project is running it
2. Do .\gradlew.bat clean
3. Do .\gradlew.bat build
4. Look in folder and is there


### Examples

Want to hide an annoying server message that you see every time you join a world?

```js
register('chat', event => {
    cancel(event);
}).setCriteria("Check out our store at www.some-mc-server.com for 50% off!");
```

How about automating a series of common commands into one?

```js
register('command', () => {
    ChatLib.command('command1');
    ChatLib.command('command2');
    ChatLib.command('command3');
}).setName('commandgroup');
```

Or even something silly like a calculator command

```js
register('command', (...args) => {
    // Evaluate all args the user gives us...
    const result = new Function('return ' + args.join(' '))();

    // ...and show the result is a nice green color
    ChatLib.chat(`&aResult: ${result}`);
}).command('calc');
```

With CT's register system, you can listen to custom events that we emit and react to them. For example, we emit events when you switch worlds, click on a GUI, hit a block, hover over an item and see its tooltip, and much more! You can even provide custom events for other module authors to use.

### Getting Started

To begin, [download and install Fabric](https://fabricmc.net/wiki/install) for one of the supported versions, then head over to our [releases page](https://github.com/ChatTriggers/ctjs/releases) and download the latest version. The mod is installed like any mod; just drag it into your mods folder. Once installed, you can import modules in-game by typing `/ct import <moduleName>`, where `<moduleName>` is the name of the module. You can browse the available modules on [our website](https://www.chattriggers.com/modules).

### Writing Modules

If you can't find any modules on the website that do exactly what you want, which is quite likely, you'll have you write your own! Here are the steps you'll want to take:

1. Navigate to the `.minecraft/config/ChatTriggers/modules` folder. If you don't know where this is, you can also execute `/ct files`, which will open the correct directory automatically.
1. Create a new directory with the name of your module
1. Inside the directory, create a file called `metadata.json`, and inside of that, put the following text: 
    ```json
    {
        "name": "<module name>",
        "entry": "index.js"
    }
    ```
    This means that when our module first runs, CT will run the `index.js` file

    _Note that `<module name>` must match the name of your folder exactly!_
1. Create the `index.js` file, and put some code in there. What exactly you write will depend on what you want CT to do. To learn more about the available APIs, take a look at the [Slate](https://chattriggers.com/slate/#introduction).
    * Some things on the Slate may be outdated, we are working on improving this

### Documentation

- [Slate](https://chattriggers.com/slate/#introduction), a guided tutorial that covers the basics
- [Javadocs](https://chattriggers.com/javadocs/), a technical reference for all public APIs in the mod
- [MIGRATION.md](docs/MIGRATION.md), a guide for upgrading modules from CT 2.X to 3.0
- [CONTRIBUTING.md](CONTRIBUTING.md) (TODO)
