#!/usr/bin/env bash
# install.sh: install the loglan-tools commands.
# Part of loglan-tools <https://github.com/mdemoulin/loglan-tools>.
# Copyright (C) 2026  Michael A. Demoulin <35350967+mdemoulin@users.noreply.github.com>
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.
#
# The Loglan material this program works from -- the dictionary, Loglan 1,
# omnibus.py, and the word lists drawn from them -- is not covered by the
# GPL: it is owned by
# The Loglan Center, Inc., which reserves all rights to it, and is used with
# its permission.  See COPYING-TLC.

# install.sh -- put the loglan-teach commands somewhere on your PATH.
#
#   ./install.sh                  install where they are now, or ask where
#   ./install.sh --ask            ask where, even if they are installed
#   ./install.sh DIR              install into DIR without asking
#
# Installs loglan-teach (the drill), loglan-update (keeps the Loglan sources
# current and builds the lists), mk-utility (which loglan-update runs for the
# cheat sheet and the l1prims and derivs lists), loglan-coin (checks new words
# and builds complexes), loglan-lookup (looks words up, either direction),
# loglan-parse (Holmes's parser, interactively), new-words-html.py (a page of
# your coined words, for submission), and loglan-fetch (the old name of
# loglan-update).
#
# The sources -- the dictionary, Loglan 1, the parser -- and the shipped lists
# (utts, and the auxiliary ethnic and animals) go into ~/.loglan/dist, and the
# lists are built from them.  Run by hand, it then also looks online for newer
# sources; given DIR, it does not touch the network.

set -eu

here=$(cd "$(dirname "$0")" && pwd)
tools=(loglan-teach loglan-update mk-utility loglan-coin loglan-lookup loglan-parse new-words-html.py loglan-fetch)

ask=
if [ "${1:-}" = --ask ]; then
    ask=1
    shift
fi

# Already installed?  Then the same place again, unless --ask.
prev=
if [ -z "$ask" ] && p=$(command -v loglan-teach 2>/dev/null); then
    prev=$(cd "$(dirname "$p")" && pwd)
    [ "$prev" = "$here/loglan-teach" ] && prev=
fi

if [ $# -gt 0 ]; then
    dest=$1
elif [ -n "$prev" ]; then
    dest=$prev
    echo "Installing into $dest, where they are now (./install.sh --ask to choose)."
else
    echo "Install ${tools[*]} into:"
    echo "  1) $HOME/.local/bin"
    echo "  2) $HOME/bin"
    echo "  3) /usr/local/bin   (needs sudo)"
    echo "  4) somewhere else"
    read -r -p "Choice [1]: " choice
    case ${choice:-1} in
        1) dest=$HOME/.local/bin ;;
        2) dest=$HOME/bin ;;
        3) dest=/usr/local/bin ;;
        4) read -r -p "Directory: " dest
           dest=${dest/#\~/$HOME} ;;
        *) echo "install.sh: no such choice: $choice" >&2; exit 1 ;;
    esac
fi

sudo=
if [ -d "$dest" ] && [ ! -w "$dest" ] || [ ! -d "$dest" ] && [ ! -w "$(dirname "$dest")" ]; then
    sudo=sudo
fi
$sudo mkdir -p "$dest"
for t in "${tools[@]}"; do
    $sudo install -m 755 "$here/loglan-teach/$t" "$dest/$t"
    echo "installed $dest/$t"
done

case ":$PATH:" in
    *":$dest:"*) ;;
    *) echo
       echo "Note: $dest is not on your PATH.  Add it, e.g. in ~/.bashrc:"
       echo "    export PATH=\"$dest:\$PATH\"" ;;
esac

# The manual pages: in ~/.local/share/man for an install in your home
# directory, else in the share/man beside the bin directory
# (/usr/local/share/man for /usr/local/bin).  man finds them through PATH:
# it looks in X/share/man for each X/bin there.
case $dest in
    "$HOME"/*) mandir=$HOME/.local/share/man/man1; msudo= ;;
    *)         mandir=$(dirname "$dest")/share/man/man1; msudo=$sudo ;;
esac
$msudo mkdir -p "$mandir"
for m in "$here"/man/*.1; do
    $msudo install -m 644 "$m" "$mandir/"
    echo "installed $mandir/$(basename "$m")"
done
if [ "$mandir" = "$HOME/.local/share/man/man1" ]; then
    case ":$PATH:" in
        *":$HOME/.local/bin:"*) ;;
        *) echo "Note: man finds pages in ~/.local/share/man only while ~/.local/bin is on"
           echo "your PATH; otherwise add to ~/.bashrc:  export MANPATH=\"\$HOME/.local/share/man:\""
           ;;
    esac
fi

data=${LOGLAN_STATE_DIR:-$HOME/.loglan}

# What ships with the tools -- the sources (used with The Loglan Center's
# permission; see sources/NOTICE) and the lists -- is copied whole into
# $data/dist, replacing the last install's copy; loglan-update works from it.
# Your lists, progress and .added files are elsewhere in $data and untouched.
mkdir -p "$data"
rm -rf "$data/dist.new"
mkdir "$data/dist.new"
cp -Rp "$here/sources" "$here/lists" "$data/dist.new/"
rm -rf "$data/dist"
mv "$data/dist.new" "$data/dist"
echo "installed the sources and shipped lists in $data/dist"

echo
if [ $# -eq 0 ]; then
    "$dest/loglan-update" && built=1
else
    "$dest/loglan-update" --offline && built=1
fi

cat <<EOF

Getting started
---------------
EOF
if [ -z "${built:-}" ]; then
    cat <<EOF
1. Build the lists (the sources ship with the tools; no download needed):

       loglan-update

EOF
fi
cat <<EOF
Drill a list:

       loglan-teach core           # Loglan word shown; type the English
       loglan-teach --recall core  # English shown; type the Loglan word
       loglan-teach --tables       # every list, its size, and your progress

   In a drill, type your answer and press Enter.  A blank line shows the
   answer (and counts as a miss); :s shows your progress; :q saves and quits.
   Any one of a gloss's alternatives ("box/crate/carton") counts as right.
   A word climbs a rung each time you get it right and drops into the error
   box when you miss; progress is saved for next time.

Lists: core (the everyday primitives: no native or scientific ones), prims
(all primitives), affs (words with affixes; try --field affix),
affsets, l1prims (the primitives used in Loglan 1), utts (whole sentences),
the auxiliary lists animals and ethnic, whose words are in prims too, and
coined (your own words, from $data/new-words.json, if you keep one).

Once a day loglan-teach and loglan-coin quietly look online for newer
sources and rebuild the lists if there are any (LOGLAN_NO_UPDATE=1 stops
it); loglan-update does the same on demand.

Coin a word (needs Python 3):

       loglan-coin check kangydzoduo     # a word?  of what kind?  clashes?
       loglan-coin make kangu dzoru durzo # every complex from a metaphor

Look a word up, either direction (needs Python 3):

       loglan-lookup kliri               # the Loglan word
       loglan-lookup clear               # the English word

Parse a sentence (needs Python 3):

       loglan-parse                      # type an utterance; ? for help, ... to leave
       man loglan-parse                  # its commands, batch files and output

Everything is kept in $data: the lists, your progress
(<list>.state.json), the sources, and cheatsheet.html -- open that in a
browser.  To add words of your own, see "Adding your own entries"
in README.md.
EOF
