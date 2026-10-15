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

`install.sh` offers to run `loglan-fetch` and `mk-utility` for you.  In a
drill, type your answer and press Enter; a blank line shows the answer (and
counts as a miss), `:s` shows your progress, and `:q` saves and quits.  Any
one of a gloss's alternatives ("box/crate/carton") counts as right.  Open
`~/.loglan/cheatsheet.html` in a browser for the declension tables.

| Command | What it does |
|---|---|
| `loglan-fetch` | Downloads Lavinia Randall Holmes's current Loglan-to-English dictionary and the HTML edition of *Loglan 1*, and builds the `prims`, `affs` and `affsets` lists from the dictionary |
| `mk-utility` | Builds `cheatsheet.html` (the ethnic and animal declensions, and every primitive used in *Loglan 1*) and the `ethnic`, `animals`, `l1prims` and `derivs` lists |
| `loglan-teach LIST` | Drills a list: `--recognition` (Loglan to English, the default), `--recall`, or `--both`; `--status` shows how far along you are, and `loglan-teach --tables` lists every list with its size and whether progress is saved |

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
| `utts` | 383 sentences from the Institute's LoglanTeach and MacTeach programs, corrected to current Loglan grammar; a sentence drill (see below).  Shipped in `lists/`, not built, and copied into `~/.loglan` by `install.sh` |

Each line is tab-separated: `word`, `affixes` (or `-`), `gloss`, `notes`.
The gloss is the keywords of the word's first definition, and any one of
them is accepted as an answer; the notes are the rest of the definition,
with the word's own place as `-` and the others as `..`.  Any file of
`loglan<TAB>english` lines can be drilled too.

### Sentence files

A two-column file -- `loglan<TAB>english` -- is drilled as sentences, written
in the notation of the Institute's MacTeach program, so one line can accept
many wordings:

| Notation | Meaning |
|---|---|
| `a/b` | alternatives |
| `+`, `&` | close a set of alternatives, with and without a space: `is/s+a/the+brother-of` is *(is \| 's) (a \| the) brother of* |
| `=`, `:` | open one after fixed words, with and without a space: `there/to=X/it`, `grand:father/parent` |
| `-` | joins words into one group: `brother-of` |
| `(x)`, `(*x)` | optional; the starred kind is shown in the prompt |
| `\c` | the character `c`, literally |
| `\|note`, `[note]` | a note on the English, on the Loglan |

Answers are compared without regard to case, spacing, hyphens or
punctuation -- except Loglan commas, which are pauses and part of the
grammar.  The prompt shows the main wording, without the alternatives.

In any list, a line starting with `#` is skipped.

### Adding your own entries

A file named `<list>.added` beside a list -- `prims.added`,
`animals.added`, and so on -- holds entries of your own, in the list's
layout; blank lines and lines starting with `#` are skipped.  Whenever
`loglan-fetch` or `mk-utility` rebuilds the list, an added entry replaces
the built one for the same word, and the rest are added; nothing ever
writes to a `.added` file.  Additions to `prims` and `affs` also go into
`affsets`.

An animal the dictionary lacks can be started from its generic *-u* word
in `animals.added`: `mk-utility` forms the rest of the set (*-a*, *-o*,
*-i*, *-e*) by rule, and puts the whole set into `prims` and `affsets`.

    hamsteru	-	hamster	is any -, a rodent of the subfamily Cricetinae, of region/range..

## loglan-coin

Checks a new word before you propose it, and builds complexes from a
metaphor.  The word grammar is Randall Holmes's own PEG parser,
`omnibus.py`, which `loglan-fetch` downloads; the dictionary supplies the
rest.

    loglan-coin check kangydzoduo
    loglan-coin check --as borrowing hamsteru
    loglan-coin make sanpa djano

`check` says whether each word is a little word, a name, a borrowing, a
primitive or a complex, how a complex splits (`kangy + dzo + duo`) and what
its pieces stand for (`kangu + dzoru + durzo`), and whether it clashes with
the dictionary:

- the same word as another, or one differing only in its final vowel, or
  only by a pair of sibilants on adjacent corners of the square s-c, c-j,
  j-z, z-s (*Loglan 1* section 6.3; the diagonals s-j and c-z are fine)
- the same metaphor as a complex that already exists
- with `--def TEXT`, the same definition word for word

`--as complex` or `--as borrowing` says what the word is meant to be, which
catches the commonest mistakes: a complex that reads as a borrowing because
a *y* is missing (`tcidnatmia`), and a borrowing that falls apart into the
pieces of a complex (`hamsteru` = *ham + steru*).

`make` takes the pieces of a metaphor -- primitives, borrowings, little
words with affixes (`nu` gives *nur*), or affixes to force one form -- tries
each piece in each of its forms with the hyphens *y*, *r* and *n* where they
can go, keeps only what the parser accepts as a complex made of exactly
those pieces, and lists the results shortest first (`-n` for more).  If a
complex with that metaphor already exists, it says so first.

    $ loglan-coin make kangu dzoru durzo
    kaurdzoduo         kaur + dzo + duo             ok
    kangydzoduo        kangy + dzo + duo            ok
    ...

Words you have coined but that are not in the dictionary can be counted
too, from a JSON file of `{"words": [{"word", "origin", "tec", "defs"}]}`:
`loglan-coin --words mine.json check ...`.  Final-vowel sets among your own
words (*kavha*, *kavhe*, ... *kavhu*) are taken as deliberate, as *Loglan 1*
allows for *spana*/*spani*/*spano*.

The first run builds the parser's grammar and caches it in
`~/.loglan/cache`, which takes a few seconds; after that a check takes a
second or two.

## Sources and licensing

The code here is Copyright (C) 2026 Michael A. Demoulin, licensed under the
GNU General Public License, version 3 or (at your option) any later version;
see [COPYING](COPYING).

The dictionary, *Loglan 1* and the parser `omnibus.py` are not included.
Lavinia Randall Holmes publishes them at
<https://randall-holmes.github.io/Loglan/cefli.html>, and `loglan-fetch`
downloads your own copy and builds the word lists from it.

The sentences in `lists/utts` are included with the permission of The Loglan
Center (Lavinia Randall Holmes, October 2026).  **All rights to the Loglan
material are reserved by The Loglan Center, Inc.** (the Idaho corporation
which has superseded the Florida corporation).  That material is not covered
by the GPL.

## Requirements

- Perl 5.36 or later (core modules only)
- Python 3.8 or later, for `loglan-coin` (standard library only)
- HTTPS for `loglan-fetch`: Perl's `IO::Socket::SSL`, or `curl`
