# icu4j-wikipedia-translit

A Java library that transliterates text between scripts using character maps defined in
the Lua data-module format used by Wikipedia transliteration modules.

The library is script-agnostic: it works with any source and target script (Cyrillic →
Latin, Arabic → Latin, Greek → Latin, etc.) as long as a compatible Lua data module
exists.  Character maps are loaded at runtime either from a live Wikipedia URL or from a
Lua string you supply directly — no bundled data, no manual JSON conversion.

Transliteration rules are compiled into ICU4J `Transliterator` instances at startup.
The rule engine handles context-sensitive mappings (lookbehind / lookahead), uppercase
source characters, and multi-character output sequences.

---

## Requirements

- Java 17 or later
- Maven (for building / dependency management)

---

## Adding the library to your project

Build and install the library into your local Maven repository:

```bash
mvn clean install -DskipTests
```

Then declare the core dependency in your `pom.xml`:

```xml
<dependency>
  <groupId>org.wikipedia.ro</groupId>
  <artifactId>icu4j-wikipedia-translit-core</artifactId>
  <version>1.0.0</version>
</dependency>
```

If you also need JSON loading support, add the optional JSON module:

```xml
<dependency>
  <groupId>org.wikipedia.ro</groupId>
  <artifactId>icu4j-wikipedia-translit-json</artifactId>
  <version>1.0.0</version>
</dependency>
```

The repository also includes a `samples` module with runnable examples for two common
use cases:

- loading a live Wikipedia Lua module
- loading bundled JSON maps from inside the application

Run them from the repository root:

```bash
mvn -pl samples -am -DskipTests install
cd samples
mvn exec:java -Dexec.mainClass=org.wikipedia.ro.translit.samples.WikipediaModuleSample
mvn exec:java -Dexec.mainClass=org.wikipedia.ro.translit.samples.InternalJsonSample
```

---

## Quick start

### Option 1 — Load from a Wikipedia URL

Fetch a Lua data module over HTTP at service-creation time.  The Lua content is parsed
as a data structure — no Lua code is executed.

```java
import org.wikipedia.ro.translit.IcuTransliterationService;

IcuTransliterationService service = IcuTransliterationService.fromLuaUrl(
    "https://ro.wikipedia.org/w/index.php" +
    "?title=Modul:Transliteration/langdata&action=raw");

// The language codes are the keys defined in that module.
String result = service.transliterate("Любовь", "ru");
```

Construct the service once (it is thread-safe after construction) and reuse it throughout
your application.

### Option 2 — Provide the Lua source directly

If you already have the Lua content as a string — retrieved, cached, or embedded — pass
it directly:

```java
import org.wikipedia.ro.translit.IcuTransliterationService;
import org.wikipedia.ro.translit.LuaTransliterationMapLoader;

// Fetch or read the module text however you like, then:
String luaContent = Files.readString(Path.of("my-langdata.lua"));
IcuTransliterationService service = IcuTransliterationService.fromLuaContent(luaContent);

String result = service.transliterate("Љубав", "sr");
```

You can also parse the Lua into maps first and inspect them before creating the service:

```java
import org.wikipedia.ro.translit.IcuTransliterationService;
import org.wikipedia.ro.translit.LuaTransliterationMapLoader;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

Map<String, Map<String, JsonNode>> maps =
        LuaTransliterationMapLoader.parseFromLuaContent(luaContent);

// Inspect, filter, or merge maps here, then:
IcuTransliterationService service = new IcuTransliterationService(maps);
```

### Option 3 — Load JSON maps from a local file or a string

If you already have transliteration maps in JSON form, use `TransliterationMapLoader`.
There is no bundled JSON configuration in this library; you load your own JSON file or
JSON text directly.

```java
import org.wikipedia.ro.translit.IcuTransliterationService;
import org.wikipedia.ro.translit.TransliterationMapLoader;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

String jsonContent = Files.readString(Path.of("my-custom-maps.json"));
Map<String, Map<String, JsonNode>> maps = TransliterationMapLoader.parseFromJson(jsonContent);
IcuTransliterationService service = new IcuTransliterationService(maps);
```

Or load directly from a local JSON file:

```java
Map<String, Map<String, JsonNode>> maps =
        TransliterationMapLoader.loadFromFile(Path.of("my-custom-maps.json"));
IcuTransliterationService service = new IcuTransliterationService(maps);
```

---

## Finding a suitable Lua module

Any Wikipedia that uses a Lua-based transliteration module is a potential source.
The raw-content URL follows the pattern:

```
https://<wiki>.wikipedia.org/w/index.php?title=Module:<ModuleName>&action=raw
```

The structure of the returned file must be a `local map` table whose keys are language
codes and whose values are character maps.  See the [Map format](#map-format) section
below.

---

## API reference

| Method | Description |
|--------|-------------|
| `IcuTransliterationService.fromLuaUrl(String url)` | Fetch a Lua module from a URL and compile the maps. Throws `IOException`. |
| `IcuTransliterationService.fromLuaContent(String lua)` | Parse an already-fetched Lua string and compile the maps. |
| `new IcuTransliterationService(Map<…> maps)` | Construct from pre-parsed maps (advanced use). |
| `service.transliterate(String text, String langCode)` | Transliterate `text` using the map for `langCode`. Returns `null` for `null` input. Throws `IllegalArgumentException` for unknown `langCode`. |
| `service.isTransliterationSupported(String langCode)` | Returns `true` if `langCode` is present in the loaded maps. |
| `LuaTransliterationMapLoader.parseFromLuaContent(String lua)` | Parse Lua source into maps without creating a service. |
| `LuaTransliterationMapLoader.loadFromUrl(String url)` | Fetch and parse a Lua module URL into maps. |
| `TransliterationMapLoader.parseFromJson(String json)` | Parse transliteration maps from a JSON string (optional JSON module). |
| `TransliterationMapLoader.loadFromFile(Path path)` | Load transliteration maps from a local JSON file (optional JSON module). |

---

## Map format

A Lua data module is a `local map` table where each top-level key is a language code and
each value is a character map:

```lua
local map = {
  ['sr'] = {
    ['а'] = 'a',           -- unconditional mapping
    ['б'] = 'b',
    ['я'] = {              -- context-sensitive mapping
      def = 'ea',          -- default output when no context rule matches
      bh  = {              -- lookbehind rules (key = preceding character)
        ['']  = 'ia',      -- '' = start of word
        [' '] = 'ia',      -- ' ' = preceded by space
        ['а'] = 'ia',      -- preceded by the character 'а'
      },
      ah  = {              -- lookahead rules (key = following character)
        ['е'] = 'ye',
      }
    }
  }
}
map['sr-Cyrl'] = map['sr']  -- alias
return map
```

| Field | Meaning |
|-------|---------|
| `def` | Output when no lookbehind or lookahead key matches |
| `bh`  | Lookbehind rules; keys are characters (or `''` for word start) in the **source** script |
| `ah`  | Lookahead rules; keys are characters in the **source** script |

---

## Architecture

| Class | Role |
|-------|------|
| `IcuTransliterationService` | Main entry point. Compiles one ICU4J `Transliterator` per language at construction time and exposes `transliterate(text, langCode)`. |
| `IcuRuleBuilder` | Converts a character map (`Map<String, TransliterationValue>`) into an ICU4J rule string, handling context rules, case expansion, and rule deduplication. |
| `LuaTransliterationMapLoader` | Fetches and parses a Wikipedia-style Lua data module using a custom recursive-descent parser. No Lua code is executed. |
| `TransliterationMapLoader` | Loads transliteration maps from JSON strings, classpath resources, or local files. This class lives in the optional JSON module so Jackson is not required for core Lua-based usage. |

---

## Thread safety

`IcuTransliterationService` is **thread-safe after construction**. Create one instance
and share it across threads.

---

## Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| [ICU4J](https://icu.unicode.org/) | 75.1 | Transliterator rule engine |
| [Jackson Databind](https://github.com/FasterXML/jackson-databind) | 2.17.2 | Optional JSON parsing support in the separate JSON module |

