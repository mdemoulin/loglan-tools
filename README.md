# loglan-tools

Tools for learning and working with [Loglan](https://www.loglan.org/), the logical language
James Cooke Brown began in 1955.

> **Not everything here is under the GPL.**  The programs are free software
> (GPL version 3 or later; see [COPYING](COPYING)) -- all except the parser,
> `omnibus.py`, which is Lavinia Randall Holmes's and belongs to The Loglan
> Center.  It and the rest of the Loglan material the programs ship and work
> from -- the dictionary and *Loglan 1* in `sources/`, the lists in
> `lists/`, and the page template inside `new-words-html.py` -- are owned by
> **The Loglan Center, Inc.**, which reserves all rights to them, but allows
> and encourages noncommercial use of them by others; they are used here
> with its permission.
> [COPYING-TLC](COPYING-TLC) says exactly which files are whose.

## loglan-teach

A flashcard drill for Loglan vocabulary, in the mold of the Loglan
Institute's old MacTeach and LoglanTeach programs: a ladder of rungs that a
word climbs as you answer it correctly, and an error box it must answer its
way out of when you miss.

    ./install.sh          # pick ~/.local/bin, ~/bin, /usr/local/bin or your own;
                          # later runs reinstall there (--ask to pick again)
    loglan-teach core     # drill

`install.sh` copies the Loglan sources that ship with the tools into
`~/.loglan` and builds the lists from them (see [Sources and
updates](#sources-and-updates)); nothing has to be downloaded.  In a
drill, type your answer and press Enter; a blank line shows the answer (and
counts as a miss), `:s` shows your progress, `:r` asks the card before
again (graded, then back to this one; `:r ANSWER` grades it on ANSWER
without asking), `:t` puts the card before
on your `troublesome` list (an animal or a people with its whole set),
`:h` lists the commands, and `:q` saves and quits.  Any
one of a gloss's alternatives ("box/crate/carton") counts as right, and
an "X-like" -- the *-e* of an animal -- takes "Xish" as well (*kange*,
"dog-like", takes "doggish").  On
Android, see [On Android](#on-android).  Open
`~/.loglan/vocab-cheatsheet.html` in a browser for the declension tables.

| Command | What it does |
|---|---|
| `loglan-update` | Brings the sources up to date -- from the copies shipped with the tools, and from the web when they have changed there -- and builds every list from them.  `loglan-teach` and `loglan-coin` run it in the background once a day |
| `mk-utility` | Run by `loglan-update`: builds `vocab-cheatsheet.html` (the ethnic and animal declensions, and every primitive used in *Loglan 1*) and the `l1prims` and `derivs` lists |
| `loglan-lookup WORD` | Looks a word up in the dictionary, either direction: `kliri` or `clear`; `-p` for every headword containing it.  The English side is made from the keywords the definitions mark; your auxiliary words are found too, and coined words with `--words FILE` |
| `new-words-html.py` | Makes a Loglan-to-English page of your coined words (see [Your own words](#your-own-words)): by default the ones not yet submitted, `--submitted DATE` for a batch already sent, `--all` for every one |
| `loglan-parse` | Lavinia Randall Holmes's parser, `omnibus.py`, in its own interactive loop: type a Loglan utterance and see how it parses (`?` for its commands, `...` to leave).  It runs in `~/.loglan/parse`, where its scratch files and anything it saves land; `man loglan-parse` documents its commands, batch files and output |
| `loglan-teach LIST` | Drills a list: `--recognition` (Loglan to English, the default), `--recall`, or `--both`; `--status` shows how far along you are, and `loglan-teach --tables` lists every list with its size and whether progress is saved |

Everything lives in `~/.loglan` (or `$LOGLAN_STATE_DIR`): the sources
under `src/`, the lists, the auxiliary lists under `aux/`, and a
`<list>.state.json` with your progress on each.  `loglan-teach prims` finds
`~/.loglan/prims` (or `~/.loglan/aux/prims`) from any directory; a list of
your own can be given by path.  `loglan-fetch`, the old name of
`loglan-update`, still works.

### Progress on more than one machine

If `~/.loglan` is a Subversion working copy -- has a `.svn` directory of its
own, checked out from a repository of yours, with the built files in
`svn:ignore` -- `loglan-teach` keeps the list's progress in step: it runs `svn update` on the state file before the
drill and commits it when you quit.  Progress made on two machines before
either was synced is merged word by word, each word as the copy that has
seen it more often has it.  When the repository cannot be reached it says
so once, drills from the copy here, and commits it the next time.  ssh is
never asked for a passphrase, so the key must be in your agent, or have
none (see below).
A list of your own kept there -- committed from one machine, say -- is
brought up to date too, before it is read, and fetched if it is not here
yet; one you have changed here and not committed is left as it is.
What `:t` adds to `troublesome` is committed when the drill ends, and
merged with whatever another machine has added to it meanwhile.
`--no-sync` or `LOGLAN_NO_SYNC=1` turns this off.  Your other files there
(`new-words.json`, `*.added`, a new list) are left for you to commit.

To set it up, on the machine that keeps the repository (here `server`,
with the repository in `/svn/loglan-state`):

    svnadmin create /svn/loglan-state
    svn mkdir -m 'loglan progress' file:///svn/loglan-state/trunk
    svn checkout file:///svn/loglan-state/trunk ~/.loglan

and on each machine, check `~/.loglan` out *before* running `install.sh`,
which builds the lists into it (run it again if `~/.loglan` came after).
On the server the checkout is by `file://`, as above, and needs no ssh;
elsewhere it is by `svn+ssh://`.  The two can be mixed, as long as every
commit is made as the same user, or by users of one group that can write
the repository.  Have svn ignore what `install.sh` and `loglan-update`
build, so that only your own files are kept:

    cd ~/.loglan
    printf '%s\n' src dist cache aux core prims affs affsets animals ethnic \
        l1prims derivs utts parse coined m1-text vocab-cheatsheet.html \
        update.log UPDATED .update.lock > /tmp/ignore
    svn propset svn:ignore -F /tmp/ignore . && svn commit -m 'ignore built files' .

A built file that was committed before it was ignored comes out with
`svn rm --keep-local FILE` and a commit.  On every other machine, move that
file aside before the next `svn update` and back after it, or the update
ends in a tree conflict.

For the ssh, give each machine a key of its own with no passphrase, so the
drill never waits on an agent, and let that key do nothing but svn.  On
the machine:

    ssh-keygen -t ed25519 -N '' -f ~/.ssh/loglan_sync -C "loglan-sync $(hostname)"

and in its `~/.ssh/config`:

    Host server-loglan
        HostName server.local
        User you
        IdentityFile ~/.ssh/loglan_sync
        IdentitiesOnly yes

On the server, add `~/.ssh/loglan_sync.pub` to `~/.ssh/authorized_keys`
with a restriction in front of it:

    command="svnserve -t -r /svn",restrict ssh-ed25519 AAAA... loglan-sync laptop

`-r /svn` makes `/svn` the root, so it drops out of the URL:

    svn checkout svn+ssh://server-loglan/loglan-state/trunk ~/.loglan

A working copy checked out by another URL is moved with `svn relocate
NEW-URL ~/.loglan`.

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
treated the same way.  The two lists were first worked out from the
dictionary, and are edited by hand since.

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
it starts again from the top.  `:l` shows the lesson the card goes with
again, `:s` says
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

An animal in `animals.added` goes in as a whole set -- *-u*, *-a*, *-o*,
*-i* and *-e*, a line each -- and the whole set goes into `prims` and
`affsets`.  Nothing is formed by rule: a set left short of a member is
reported by `loglan-update`.  A word you are coining belongs in
`new-words.json` instead, by `loglan-coin --add --set=animal`.

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
  glossed by its key meanings as written (`{female hamster}/{she-hamster}`
  is `female-hamster/she-hamster`), and any other primitive into `prims`
  (and `core`, if it is a C-, D-, I-, L- or O-Prim).  Complexes and
  borrowings stay in `coined`;
- `loglan-lookup` finds the words, either direction;
- `loglan-coin` checks new words against them as against the dictionary;
- `new-words-html.py` renders them as a page for submission.

Add words with `loglan-coin --add` rather than by hand.  It puts a word in
once it has passed the checks below, filling in a draft of what you have
not given -- correct anything in the file that is not right -- and with
`--set` it puts in every member of an animal or ethnic set; a set left
short of a member is reported by `loglan-update`, and nothing fills it in:

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
metaphor.  The word grammar is Lavinia Randall Holmes's own PEG parser,
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

The code here, except the parser `omnibus.py`, is Copyright (C) 2026
Michael A. Demoulin, licensed under the GNU General Public License, version
3 or (at your option) any later version; see [COPYING](COPYING).

The dictionary, *Loglan 1* and the parser `omnibus.py` in `sources/`, the
sentences in `lists/utts`, the ethnic and animals lists in `lists/aux`
(drawn from the dictionary), and the page template inside
`new-words-html.py` are Loglan material, included with the permission of
The Loglan Center (Lavinia Randall Holmes, October 2026); [COPYING-TLC](COPYING-TLC)
lists them and gives the permission in full.  Lavinia Randall Holmes
publishes the originals at <https://randall-holmes.github.io/Loglan/cefli.html>.
**All rights to the Loglan material are reserved by The Loglan Center,
Inc.** (the Idaho corporation which has superseded the Florida
corporation), which allows and encourages noncommercial use of it by
others.  That material is not covered by the GPL.

## Requirements

- Perl 5.36 or later (core modules only)
- Python 3.8 or later, for `loglan-coin`, `loglan-lookup`, `loglan-parse` and
  `new-words-html.py` (standard library only)
- for the online check (optional: the tools work from the shipped sources
  without it), HTTPS: Perl's `IO::Socket::SSL`, or `curl`
- for progress on more than one machine (optional), Subversion, and ssh
  to reach the repository

### On Android

The tools run in [Termux](https://termux.dev), a Linux terminal for
Android (install it from F-Droid, as Termux recommends):

    pkg install perl python git curl subversion openssh
    git clone https://github.com/mdemoulin/loglan-tools
    cd loglan-tools && ./install.sh
    loglan-teach core

There `install.sh` offers Termux's own bin directory (`$PREFIX/bin`) first;
it is already on your PATH, and needs no sudo.  For the manual page,
`pkg install man`.  (`subversion` and `openssh` are only for [progress on
more than one machine](#progress-on-more-than-one-machine); for that,
check `~/.loglan` out before running `install.sh`.)

Termux cannot look up `.local` names, so in its `~/.ssh/config` the
server's `HostName` is its address, `192.168.0.245` say.  Give the server
a reserved address on your router (its DHCP reservation, by the server's
MAC address), or a static one, so that the address stays the same.  Away
from that network the drill says it cannot reach the repository, and
syncs the next time it can.

To work in Termux from your computer, over the USB cable: in Termux, run
`sshd` (port 8022; it runs until Termux is stopped, or `pkill sshd`), and
put your computer's public key in Termux's `~/.ssh/authorized_keys`.  Then,
on the computer, with USB debugging on:

    adb forward tcp:8022 tcp:8022
    ssh -p 8022 localhost

Termux ignores the user name.  `adb shell` itself is no use for this: it
runs as Android's `shell` user, which cannot get into Termux's files.

### The Android app

`android/` holds an Android app of the drill, *Loglan Teach*, which needs
neither Termux nor a keyboard: tap a list, say which way to be asked, and
type the answers.  It drills as `loglan-teach` does -- the same rungs and
error box, the same answers taken, `utts` in order with its lessons, `:r`
and `:t` as buttons (the commands can be typed too) -- and keeps each
list's progress in a `<list>.state.json` of the same form.  The lists are
built by `loglan-update` when the app is built, and go with it; progress
stays on the phone (there is no syncing yet).

To build it you need a JDK (17 or later) and the Android SDK; with the SDK
in `~/Android/Sdk` (or `sdk.dir` in `android/local.properties`):

    cd android
    ./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
    adb install -r app/build/outputs/apk/debug/app-debug.apk

`./gradlew :drill:test` tests the drill against `loglan-teach` itself:
`drill/golden/make-golden` runs the drill's own subs on every list, and
the Kotlin drill must read each list, take each answer, and compile each
sentence the same way.
