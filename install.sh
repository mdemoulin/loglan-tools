#!/usr/bin/env bash
# install.sh -- put the loglan-teach commands somewhere on your PATH.
#
#   ./install.sh                  ask where
#   ./install.sh DIR              install into DIR without asking
#
# Installs loglan-teach (the drill), loglan-fetch (downloads the dictionary
# and Loglan 1 and builds the word lists) and mk-utility (the cheat sheet and
# the ethnic, animals, l1prims and derivs lists).  Nothing is downloaded
# until you run loglan-fetch.

set -eu

here=$(cd "$(dirname "$0")" && pwd)
tools=(loglan-teach loglan-fetch mk-utility)

if [ $# -gt 0 ]; then
    dest=$1
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

data=${LOGLAN_STATE_DIR:-$HOME/.loglan}

# The word lists are built, not shipped: offer to build them now.  Asked only
# when the installer was asked where to install, i.e. run by hand.
built=
if [ $# -eq 0 ]; then
    echo
    echo "The word lists come from Randall Holmes's Loglan dictionary and Loglan 1,"
    echo "downloaded from randall-holmes.github.io (about 5 MB) into $data/src."
    read -r -p "Download them and build the lists now? [Y/n] " yn
    case ${yn:-y} in
        [Yy]*) "$dest/loglan-fetch" && "$dest/mk-utility" && built=1 ;;
    esac
fi

cat <<EOF

Getting started
---------------
EOF
if [ -z "$built" ]; then
    cat <<EOF
1. Build the word lists (needs the network the first time):

       loglan-fetch      # downloads the dictionary and Loglan 1, builds
                         #   prims, affs and affsets
       mk-utility        # builds ethnic, animals, l1prims, derivs and
                         #   cheatsheet.html

EOF
fi
cat <<EOF
Drill a list:

       loglan-teach prims          # Loglan word shown; type the English
       loglan-teach --recall prims # English shown; type the Loglan word
       loglan-teach --tables       # every list, its size, and your progress

   In a drill, type your answer and press Enter.  A blank line shows the
   answer (and counts as a miss); :s shows your progress; :q saves and quits.
   Any one of a gloss's alternatives ("box/crate/carton") counts as right.
   A word climbs a rung each time you get it right and drops into the error
   box when you miss; progress is saved for next time.

Lists: prims (all primitives), affs (words with affixes; try --field affix),
affsets, animals, ethnic, l1prims (the primitives used in Loglan 1).

Everything is kept in $data: the lists, your progress
(<list>.state.json), the downloaded sources, and cheatsheet.html -- open
that in a browser.  To add words of your own, see "Adding your own entries"
in README.md.
EOF
