# loglan-tools

Tools for learning and working with [Loglan](https://www.loglan.org/), the logical language
James Cooke Brown began in 1955.

> **Not everything here is under the GPL.**  The programs are free software
> (GPL version 3 or later; see [COPYING](COPYING)).  The Loglan material
> they ship and work from -- the dictionary, *Loglan 1* and `omnibus.py` in
> `sources/`, the lists in `lists/`, and the page template inside
> `new-words-html.py` -- is owned by **The Loglan Center, Inc.**, which
> reserves all rights to it, and is used with its permission.
> [COPYING-TLC](COPYING-TLC) says exactly which files are whose.

## loglan-teach

A flashcard drill for Loglan vocabulary, in the mold of the Loglan
Institute's old MacTeach and LoglanTeach programs: a ladder of rungs that a
word climbs as you answer it correctly, and an error box it must answer its
way out of when you miss.

    ./install.sh          # pick ~/.local/bin, ~/bin, /usr/local/bin or your own
    loglan-teach core     # drill

`install.sh` copies the Loglan sources that ship with the tools into
`~/.loglan` and builds the lists from them (see [Sources and
updates](#sources-and-updates)); nothing has to be downloaded.  In a
drill, type your answer and press Enter; a blank line shows the answer (and
counts as a miss), `:s` shows your progress, and `:q` saves and quits.  Any
one of a gloss's alternatives ("box/crate/carton") counts as right.  Open
`~/.loglan/cheatsheet.html` in a browser for the declension tables.

| Command | What it does |
|---|---|
| `loglan-update` | Brings the sources up to date -- from the copies shipped with the tools, and from the web when they have changed there -- and builds every list from them.  `loglan-teach` and `loglan-coin` run it in the background once a day |
| `mk-utility` | Run by `loglan-update`: builds `cheatsheet.html` (the ethnic and animal declensions, and every primitive used in *Loglan 1*) and the `l1prims` and `derivs` lists |
| `loglan-lookup WORD` | Looks a word up in the dictionary, either direction: `kliri` or `clear`; `-p` for every headword containing it.  The English side is made from the keywords the definitions mark; your auxiliary words are found too, and coined words with `--words FILE` |
| `new-words-html.py` | Makes a Loglan-to-English page of your coined words (see [Your own words](#your-own-words)): by default the ones not yet submitted, `--submitted DATE` for a batch already sent, `--all` for every one |
| `loglan-parse` | Randall Holmes's parser, `omnibus.py`, in its own interactive loop: type a Loglan utterance and see how it parses (`?` for its commands, `...` to leave).  It runs in `~/.loglan/parse`, where its scratch files and anything it saves land; `man loglan-parse` documents its commands, batch files and output |
| `loglan-teach LIST` | Drills a list: `--recognition` (Loglan to English, the default), `--recall`, or `--both`; `--status` shows how far along you are, and `loglan-teach --tables` lists every list with its size and whether progress is saved |

Everything lives in `~/.loglan` (or `$LOGLAN_STATE_DIR`): the sources
under `src/`, the lists, the auxiliary lists under `aux/`, and a
`<list>.state.json` with your progress on each.  `loglan-teach prims` finds
`~/.loglan/prims` (or `~/.loglan/aux/prims`) from any directory; a list of
your own can be given by path.  `loglan-fetch`, the old name of
`loglan-update`, still works.

### The lists

| List | Contents |
|---|---|
| `core` | the everyday primitives: composite (C-), derived (D-, the declensions), international (I-), Loglan (L-) and onomatopoeic (O-Prims) -- `prims` without the native (N-) and scientific (S-Prims) -- plus the set members formed by rule |
| `prims` | every primitive in the dictionary, plus every word of the auxiliary lists it does not already have |
| `affs` | every primitive or little word with affixes; drill with `--field affix` |
| `affsets` | `prims` and `affs` together, likewise |
| `ethnic` | auxiliary: the ethnic declension, *-a* language, *-e* territory, *-i* person, *-o* culture |
| `animals` | auxiliary: the animal declension, *-u* generic, *-a* female, *-o* male, *-i* young, *-e* -like |
| `coined` | your own words, from `~/.loglan/new-words.json` (see [Your own words](#your-own-words)); kept out of the other lists |
| `m1-text` | not a drill: the commentary of the Institute's MacTeach 1 / LoglanTeach program, the 34 short lessons it showed between groups of the `utts` sentences.  `loglan-teach utts` shows them as it goes (see below).  Shipped in `lists/` and put into `~/.loglan` like `utts` |
| `l1prims` | the primitives used in chapters 1-7 of *Loglan 1*, with the book's definitions |
| `derivs` | not a drill: the word origins `loglan-teach` shows after each answer |
| `utts` | 383 sentences from the Institute's LoglanTeach and MacTeach programs, corrected to current Loglan grammar; a sentence drill (see below).  Shipped in `lists/`, not built, and put into `~/.loglan` unless you already have one there |

The **auxiliary lists** are vocabulary the dictionary does not lay out as
a list of its own.  `ethnic` and `animals` ship with the tools, curated, in
`lists/aux/`: each set as the dictionary has it, with the members none of
the sources has formed by the declension rule and marked `(formed by
rule)`.  Every word in them also goes into `prims` and `affsets` (and into
`affs`, if it has affixes), and the cheat sheet's declension tables are
drawn from them.  A list of your own dropped into `~/.loglan/aux/` is
treated the same way.  (Whoever maintains the tools can work the two lists
out afresh from the dictionary with `mk-utility --aux lists/aux`.)

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

`utts` is taught as MacTeach 1 taught it, with its lessons: a line
`# {7}` in it marks where lesson 7 of `m1-text` comes.  Every sentence is
asked in its turn, from the top of the file, and each lesson is shown once,
before the sentences it goes with -- the first six, on pronunciation and the
kinds of word, before the very first.  A sentence you miss is asked again,
until you have it, before the next lesson begins; after the last sentence
it starts again from the top.  `:l` shows the last lesson again, `:s` says
where you are, and `--random` drills the list on the ladder instead,
without the lessons.  A sentence that comes twice with two Englishes (a
literal and an idiomatic one) is two cards, each with its own progress, and
one that has no translation -- ungrammatical, its English just `-` -- is
answered with a `-`.

In any list, a line starting with `#` is skipped.

### Adding your own entries

A file named `<list>.added` beside a list -- `prims.added`,
`animals.added`, and so on -- holds entries of your own, in the list's
layout; blank lines and lines starting with `#` are skipped.  Whenever
`loglan-update` rebuilds the list, an added entry replaces the built one
for the same word, and the rest are added; nothing ever writes to a
`.added` file.  Additions to `prims` and `affs` also go into `affsets`, and
additions to an auxiliary list go wherever its words go.

An animal the list lacks can be started from its generic *-u* word in
`animals.added`: `loglan-update` forms the rest of the set (*-a*, *-o*,
*-i*, *-e*) by rule, and puts the whole set into `prims` and `affsets`.
Check a new word with `loglan-coin` first.

    kavhu	-	guinea-pig/cavy	is any -, a rodent of the genus Cavia, of region/range..

### Your own words

Words you have coined live in `~/.loglan/new-words.json`, which the tools
never ship, and only `loglan-coin --add` writes (see below): `{"words": [...]}`, one object per word with
`word`, `origin`, `tec` (the type line, `2-Cpx MD 2026 7+`), `defs` (each
definition with its key meanings in braces and its case tags plainly,
`(2n) B is an {omelette} of ingredients S. [B-S]`), an optional `affix`,
an optional `set` (`animal` or `ethnic`, for a member of a declension set),
and `submitted`, the date it went to the Institute or `null`.  Notes at the
top of the file describe it in full.  With it there:

- `loglan-update` makes the `coined` drill list from it, and rebuilds the
  lists whenever the file changes; a word with a `set` goes into that
  auxiliary list as well (and so into `prims`, and `core` by its type),
  glossed by the declension, and any other primitive into `prims` (and
  `core`, if it is a C-, D-, I-, L- or O-Prim).  Complexes and borrowings
  stay in `coined`;
- `loglan-lookup` finds the words, either direction;
- `loglan-coin` checks new words against them as against the dictionary;
- `new-words-html.py` renders them as a page for submission.

`loglan-coin --add` puts a word in for you, once it has passed the checks
below, filling in a draft of what you have not given -- correct anything
in the file that is not right:

    loglan-coin --add --loglan=kaurhafbao --english='B is a doghouse for dog D'
    loglan-coin --add --loglan=brokolie --english=broccoli --origin='It. broccoli'
    loglan-coin --add --set=animal --loglan=hamsiteru --english=hamster --origin='Ger. Hamster'

A complex gets its origin worked out from its pieces
(`ka(ng)u+r+ha(s)f(a)+ba(ks)o=dog house box`) and its type (`3-Cpx`); a
borrowing gets `Bor.` and the `--origin` you give (else `?`).  `--english`
is either a word (`broccoli` becomes `(1n) B is a {broccoli}. [B-]`) or a
definition with its places, which gets the grammar code, braces and case
tags it lacks (`B is pink/rosy, a whitened red` becomes `(1a) B is
{pink}/{rosy}, a whitened red. [B-]`).  The tec line is the type, your
commonest author initials (`--author` to say otherwise) and the year.

`--set=animal` or `--set=ethnic` adds every member of the declension set
the word belongs to that is missing, each marked with `set`.  In an animal
set the `-u` word (any) is the borrowing (`Bor.`) the others derive from --
or an N-Prim, if it has a primitive's five letters -- and `-a` (female),
`-o` (male), `-i` (baby) and `-e` (-like) are D-Prims with it as their
origin; in an ethnic set the `-a` word (the language) is the N-Prim,
and `-e` (territory), `-i` (person) and `-o` (culture) are D-Prims sharing
its origin.

A word is not added, and the reasons are given, if it is already in the
dictionary or your file; if the dictionary already has a word for the
meaning you give (`gofru` for gopher) -- the commonest reason a coined word
is later withdrawn; if it clashes with another (as `check` finds); or if it
is not a complex or borrowing (a set's members must be borrowings or
primitives).  `--force` adds it anyway.

`~/.loglan/dropped-new-words.json`, in the same form, is a record of words
withdrawn because the dictionary already had them; no tool reads it.

## loglan-coin

Checks a new word before you propose it, and builds complexes from a
metaphor.  The word grammar is Randall Holmes's own PEG parser,
`omnibus.py`, one of the shipped sources; the dictionary supplies the
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

Your coined words in `~/.loglan/new-words.json` always count as well, and
another file of them can be added with `loglan-coin --words mine.json
check ...`.  Final-vowel sets among your own
words (*kavha*, *kavhe*, ... *kavhu*) are taken as deliberate, as *Loglan 1*
allows for *spana*/*spani*/*spano*.

The first run builds the parser's grammar and caches it in
`~/.loglan/cache`, which takes a few seconds; after that a check takes a
second or two.

## Sources and updates

The Loglan material the tools work from ships with them, exactly as
published, in `sources/`: Lavinia Randall Holmes's current
Loglan-to-English dictionary, the HTML edition of *Loglan 1*, and her
parser `omnibus.py`.  `sources/MANIFEST` gives each file's web address,
checksum and the date it was copied; `sources/FIXES` lists corrections to
make to them, which are made only in the working copies in
`~/.loglan/src` -- the originals stay as published.

`install.sh` copies `sources/` and `lists/` to `~/.loglan/dist`, and
`loglan-update` takes it from there.  It keeps the newest original of each
source in `~/.loglan/src/.orig` -- the shipped copy, or the one online if it
has changed since -- writes the corrected working copies, and rebuilds the
lists.  `loglan-teach` and `loglan-coin` start it in the background once a
day, so a new edition of the dictionary turns up in the lists without your
doing anything; the drill never waits for it, `~/.loglan/update.log` says
what it did, and the next drill tells you if anything changed.  Set
`LOGLAN_NO_UPDATE=1` to stop the background check, or run
`loglan-update --offline` to rebuild without looking online.

For maintainers: `loglan-update --refresh-repo .` downloads every source
and writes the changed ones into `sources/`, updating `MANIFEST`, ready to
commit.

## Sources and licensing

The code here is Copyright (C) 2026 Michael A. Demoulin, licensed under the
GNU General Public License, version 3 or (at your option) any later version;
see [COPYING](COPYING).

The dictionary, *Loglan 1* and the parser `omnibus.py` in `sources/`, the
sentences in `lists/utts`, the ethnic and animals lists in `lists/aux`
(drawn from the dictionary), and the page template inside
`new-words-html.py` are Loglan material, included with the permission of
The Loglan Center (Lavinia Randall Holmes, October 2026); [COPYING-TLC](COPYING-TLC)
lists them and gives the permission in full.  Lavinia Randall Holmes
publishes the originals at <https://randall-holmes.github.io/Loglan/cefli.html>.
**All rights to the Loglan material are reserved by The Loglan Center,
Inc.** (the Idaho corporation which has superseded the Florida
corporation).  That material is not covered by the GPL.

## Requirements

- Perl 5.36 or later (core modules only)
- Python 3.8 or later, for `loglan-coin`, `loglan-lookup`, `loglan-parse` and
  `new-words-html.py` (standard library only)
- for the online check (optional: the tools work from the shipped sources
  without it), HTTPS: Perl's `IO::Socket::SSL`, or `curl`
