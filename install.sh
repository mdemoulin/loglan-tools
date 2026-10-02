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

echo
echo "Next: loglan-fetch, then mk-utility, then e.g. 'loglan-teach prims'."
echo "Lists and progress are kept in \${LOGLAN_STATE_DIR:-~/.loglan}."
