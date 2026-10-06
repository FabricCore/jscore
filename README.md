[**Forgejo** (source)](https://git.siri.ws/jsc/mod/) | [**Github** (mirror)](https://git.siri.ws/jsc/mod/)

# Overview

Module system for running scripting languages in Minecraft.

- Each script file is a module
- When the game starts, a file called the **entry point** is ran, and from there
- Each module can call `import` to load another module and `unimport` to undo the
  import
- When all importers have unimported a module, the module is automatically
  unloaded
  
## Features

- #### One entry point for each of client and server
  |Entry point|Start|Stop|
  |---|---|---|
  |Client|Client started|Client stoping|
  |Server|Server started|Server stopping|
  
  Note: the server entry point is also ran for integrated servers (i.e.
  singleplayer world)
  
- #### Prelude items
  Supports custom prelude - a way to insert arbitrary code before the first line
  of a script file, so language features (e.g. CommonJS require) can be added.
  
- #### Thread safety
  Module import/unimports are atomic operations and thread safe.

  TLDR: no unexpected behaviour from calling import/unimports in multiple threads at the same time.
  
- #### Multi-language support
  Supports any [GraalVM languages](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/Languages/),
  allows importing items from a module written in a different language.
  
  Note: you need a runtime for the language (e.g. [jsc/js-runtime](https://git.siri.ws/jsc/js-runtime))
  to run a script file, language is recognised by its file extension.
