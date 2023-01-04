# Factorio Data Wrapper

Takes data from [Factorio](factorio.com)'s Lua files and outputs it in a desired format or makes it accessible to other Java programs.

Currently mainly developed for [Factorio Wiki Scripts](https://github.com/Bilka2/Wiki-scripts) and used by [Factorio Blueprint String Renderer](https://github.com/demodude4u/Factorio-FBSR) (aka Blueprint Bot) as a dependency.

Mod support is Work in Progress, it's tracked in [Issue #37](https://github.com/demodude4u/Java-Factorio-Data-Wrapper/issues/37).

## Installation and execution of the FactorioWikiMain app with Eclipse

Install [Eclipse for Java Developers](https://www.eclipse.org/downloads/packages/).

Clone this Java Factorio Data Wrapper repository via git. Commandline example: `git clone git@github.com:demodude4u/Java-Factorio-Data-Wrapper.git`

In Eclipse, open the directory you cloned the repo into: File -> Open Projects from File System... -> Directory... -> Open the directory -> Choose the FactorioDataWrapper directory -> Finish.

You may need to click "Workbench" or "Hide" in the top right of the "Welcome to Eclipse" page so see the GUI elements mentioned next.

In the "Project Explorer" window, expand FactorioDataWrapper, copy config.template.json, rename it to config.json and if needed adjust "factorio" to point to your Factorio install. It must point to the root Factorio directory which contains the `data` directory.

In the "Project Explorer" window, expand FactorioDataWrapper, expand src, expand com.factorio.demod.apps -> Right-click on FactorioWikiMain.java -> Run As -> Java Application.

The "Console" shows possible errors. If there are no errors, the output can be found in "FactorioDataWrapper/output/", the directory is opened automatically when the data wrapper finishes executing succesfully.

<!-- Tested with Eclipse Platform Version 2020-03 (4.15) on openSUSE Tumbleweed snapshot 20230103, installed Eclipse with `sudo zypper in eclipse-jdt` instead of link.

Tested with Eclipse IDE for Java Developers Version 2022-12 (4.26.0) on Windows 10.
-->
### Help, it's missing dependencies

The project is missing dependencies if it shows hundreds of errors and running it fails with this console message:
```
Error: Unable to initialize main class com.demod.factorio.apps.FactorioWikiMain
Caused by: java.lang.NoClassDefFoundError: JSONException
```
These should be set up by Maven, Eclipse for Java Developers should come with an integration for it by default. Check if the integration is installed: In Eclipse, open Help -> About Eclipse Platform/IDE -> Installation Details -> Installed Software. Check if "m2e Maven integration for Eclipse" is in the list.

If it is not in the list, install it:  
In eclipse -> Help -> Install New Software... -> Work with: --All Available Sites-- -> Search for maven -> Check the checkbox next to "m2e Maven integration for Eclipse" under General Purpose Tools -> Next > -> Next > -> Accept the license -> Finish -> Restart the application when it prompts for it.

## Config file

The value for "factorio" must point a Factorio install. It must point to the root Factorio directory which contains the `data` directory.

The value for "mod-exclude" can be empty. If it is filled with an array of mod names, mods by that name that are found in `data` on in the `mods` directory are ignored by the data wrapper and not loaded. By default, this exclude list is empty.

The value for "output" can be empty. If it is filled with a string, this is used as the directory name for the output directory of FactorioWikiMain. It is set to "output" by default.

### Example

```json
{
  "factorio": "C:\\Program Files (x86)\\Steam\\steamapps\\common\\Factorio",
  "mod-exclude": [
    "combat-tester",
    "test-maker",
    "trailer",
    "extreme-gui",
    "trailer-launch",
    "trailer-switch",
    "GIF_Tool"
  ]
}
```

### Mods directory

The `mods` directory must be placed in the FactorioDataWrapper directory. Mods inside it are loaded by the data wrapper.
