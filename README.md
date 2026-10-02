# loglan-tools

Tools for learning [Loglan](https://www.loglan.org/), the logical language
James Cooke Brown began in 1955.

## loglan-teach

A flashcard drill for Loglan vocabulary, in the mould of the Loglan
Institute's old MacTeach and LoglanTeach programs: a ladder of rungs that a
word climbs as you answer it correctly, and an error box it must answer its
way out of when you miss.

    ./install.sh          # pick ~/.local/bin, ~/bin, /usr/local/bin or your own
    loglan-fetch          # download the sources, build prims/affs/affsets
    mk-utility            # build the cheat sheet and the other drill lists
    loglan-teach prims    # drill

| Command | What it does |
|---|---|
| `loglan-fetch` | Downloads Randall Holmes's current Loglan-to-English dictionary and the HTML edition of *Loglan 1*, and builds the `prims`, `affs` and `affsets` lists from the dictionary |
| `mk-utility` | Builds `cheatsheet.html` (the ethnic and animal declensions, and every primitive used in *Loglan 1*) and the `ethnic`, `animals`, `l1prims` and `derivs` lists |
| `loglan-teach LIST` | Drills a list: `--recognition` (Loglan to English, the default), `--recall`, or `--both`; `--status` shows how far along you are |

Everything lives in `~/.loglan` (or `$LOGLAN_STATE_DIR`): the downloaded
sources under `src/`, the lists, and a `<list>.state.json` with your
progress on each.  `loglan-teach prims` finds `~/.loglan/prims` from any
directory; a list of your own can be given by path.

### The lists

| List | Contents |
|---|---|
| `prims` | every primitive in the dictionary, plus the members of the ethnic and animal sets that only the declension rule forms (added by `mk-utility`) |
| `affs` | every primitive or little word with affixes; drill with `--field affix` |
| `affsets` | `prims` and `affs` together, likewise |
| `ethnic` | the ethnic declension: *-a* language, *-e* territory, *-i* person, *-o* culture |
| `animals` | the animal declension: *-u* generic, *-a* female, *-o* male, *-i* young, *-e* -like |
| `l1prims` | the primitives used in chapters 1-7 of *Loglan 1*, with the book's definitions |
| `derivs` | not a drill: the word origins `loglan-teach` shows after each answer |

Each line is tab-separated: `word`, `affixes` (or `-`), `gloss`, `notes`.
The gloss is the keywords of the word's first definition, and any one of
them is accepted as an answer; the notes are the rest of the definition,
with the word's own place as `-` and the others as `..`.  Any file of
`loglan<TAB>english` lines can be drilled too.

## Sources and licensing

The code here is Copyright (C) 2026 Michael A. Demoulin, licensed under the
GNU General Public License, version 3 or (at your option) any later version;
see [COPYING](COPYING).

The word data is not included.  The dictionary and *Loglan 1* belong to The
Loglan Institute; Randall Holmes publishes them at
<https://randall-holmes.github.io/Loglan/cefli.html> for private study, and
other uses need the Institute's permission.  `loglan-fetch` downloads your
own copy.

## Requirements

- Perl 5.36 or later (core modules only)
- HTTPS for `loglan-fetch`: Perl's `IO::Socket::SSL`, or `curl`
