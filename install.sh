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
# GPL: it is owned by The Loglan Center, Inc., which reserves all rights to
# it, but allows and encourages noncommercial use of it by others; it is used
# here with its permission.  See COPYING-TLC.

# install.sh -- put the loglan-teach commands somewhere on your PATH, and the
# Android app, Loglan Teach, on the phone plugged in over USB.
#
#   ./install.sh                  install where they are now, or ask where
#   ./install.sh --ask            ask where, even if they are installed
#   ./install.sh DIR              install into DIR without asking
#   ./install.sh --no-app         the commands alone, not the app
#
# In Termux, on the phone itself, it installs the commands alone.
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
#
# The app is built here, from android/ (it needs a JDK, 17 or later, and the
# Android SDK), put on the phone with adb, and -- when ~/.loglan is a
# Subversion working copy, reached by svn+ssh:// -- set up to sync with the
# same repository: the phone's key goes into the server's authorized_keys,
# and the server, its address and its own key into the app.  The login it
# makes to the server for that is your own: the user and host this
# machine's svn reaches, or LOGLAN_SERVER_LOGIN (user@host).

set -eu

here=$(cd "$(dirname "$0")" && pwd)
tools=(loglan-teach loglan-update mk-utility loglan-coin loglan-lookup loglan-parse new-words-html.py loglan-fetch)

ask=
app=1
[ -n "${TERMUX_VERSION:-}" ] && app=
args=()
for a in "$@"; do
    case $a in
        --ask) ask=1 ;;
        --no-app) app= ;;
        -*) echo "install.sh: no such option: $a" >&2; exit 1 ;;
        *) args+=("$a") ;;
    esac
done
set -- ${args[@]+"${args[@]}"}

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
    # Termux, on Android, has no /usr/local or sudo; its own bin directory
    # is on PATH and yours to write, so it is the one offered first there.
    if [ -n "${TERMUX_VERSION:-}" ] && [ -d "${PREFIX:-}/bin" ]; then
        dirs=("$PREFIX/bin" "$HOME/.local/bin" "$HOME/bin")
        notes=("" "" "")
    else
        dirs=("$HOME/.local/bin" "$HOME/bin" /usr/local/bin)
        notes=("" "" "   (needs sudo)")
    fi
    echo "Install ${tools[*]} into:"
    for i in 0 1 2; do
        echo "  $((i + 1))) ${dirs[i]}${notes[i]}"
    done
    echo "  4) somewhere else"
    read -r -p "Choice [1]: " choice
    case ${choice:-1} in
        [123]) dest=${dirs[${choice:-1} - 1]} ;;
        4) read -r -p "Directory: " dest
           dest=${dest/#\~/$HOME} ;;
        *) echo "install.sh: no such choice: $choice" >&2; exit 1 ;;
    esac
fi

# sudo only where the directory -- or, if it is not there yet, the nearest
# one above it that is -- is not yours to write: a new ~/.local/bin needs none.
sudo=
top=$dest
while [ ! -d "$top" ]; do
    top=$(dirname "$top")
done
[ -w "$top" ] || sudo=sudo
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

   check and make only show you words; neither writes anything.  To add
   one to your own dictionary ($data/new-words.json), checked first:

       loglan-coin --add --loglan=kaurhafbao --english='B is a doghouse for dog D'
       loglan-coin --add --set=animal --loglan=hamsiteru --english=hamster

   The lists are rebuilt in the background, and the word is in the coined
   list (and, for --set, in animals or ethnic) the next time you drill.

Look a word up, either direction (needs Python 3):

       loglan-lookup kliri               # the Loglan word
       loglan-lookup clear               # the English word

Parse a sentence (needs Python 3):

       loglan-parse                      # type an utterance; ? for help, ... to leave
       man loglan-parse                  # its commands, batch files and output

Everything is kept in $data: the lists, your progress
(<list>.state.json), the sources, and vocab-cheatsheet.html -- open that in a
browser.  To add words of your own, see "Adding your own entries"
in README.md.
EOF

[ -n "$app" ] || exit 0

# ---- The Android app ----

pkg=io.github.mdemoulin.loglanteach

cat <<EOF

The Android app
---------------
EOF

# What building it takes: a JDK, 17 or later, and the Android SDK, with adb.
sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
if [ -z "$sdk" ] && [ -f "$here/android/local.properties" ]; then
    sdk=$(sed -n 's/^sdk\.dir=//p' "$here/android/local.properties")
fi
sdk=${sdk:-$HOME/Android/Sdk}
missing=()
jv=$(java -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -n 1) || true
[ "${jv:-0}" -ge 17 ] || missing+=("a JDK, 17 or later${jv:+ (java here is $jv)}")
[ -d "$sdk/platforms" ] || missing+=("the Android SDK, in ~/Android/Sdk (or say where in ANDROID_HOME)")
adb=$(command -v adb || echo "$sdk/platform-tools/adb")
[ -x "$adb" ] || missing+=("adb, from the SDK's platform-tools")
if [ ${#missing[@]} -gt 0 ]; then
    echo "Building the app needs, and this machine has not got:"
    printf '  - %s\n' "${missing[@]}"
    echo "Install them and run ./install.sh again -- or ./install.sh --no-app for the commands alone."
    exit 1
fi

# The phone: plugged in, with USB debugging on, and this computer allowed.
said= said_auth=
while :; do
    ready=($("$adb" devices | awk 'NR > 1 && $2 == "device" { print $1 }'))
    [ ${#ready[@]} -gt 0 ] && break
    if [ -z "$said" ]; then
        cat <<EOF
Plug the phone in over USB, with USB debugging on.  If it has never been on:

  1. Settings > About phone: tap "Build number" seven times, for Developer options.
  2. Settings > System > Developer options: turn on "USB debugging".
  3. Plug the phone in, and on it allow USB debugging from this computer.

Waiting for the phone (Ctrl-C to stop; ./install.sh --no-app for the commands alone)...
EOF
        said=1
    fi
    if [ -z "$said_auth" ] && "$adb" devices | grep -q 'unauthorized$'; then
        echo "The phone is there, but has not allowed this computer yet: look for the prompt on it."
        said_auth=1
    fi
    sleep 2
done
if [ -n "${ANDROID_SERIAL:-}" ]; then
    serial=$ANDROID_SERIAL
elif [ ${#ready[@]} -eq 1 ]; then
    serial=${ready[0]}
else
    echo "Which phone?"
    for i in "${!ready[@]}"; do
        echo "  $((i + 1))) ${ready[i]}  $("$adb" -s "${ready[i]}" shell getprop ro.product.model | tr -d '\r')"
    done
    read -r -p "Choice [1]: " choice
    serial=${ready[${choice:-1} - 1]:-}
    [ -n "$serial" ] || { echo "install.sh: no such choice: $choice" >&2; exit 1; }
fi
export ANDROID_SERIAL=$serial
phone=$("$adb" shell getprop ro.product.model | tr -d '\r')

echo "Building the app..."
(cd "$here/android" && ANDROID_HOME=$sdk ./gradlew -q :app:assembleDebug >/dev/null)
apk=$here/android/app/build/outputs/apk/debug/app-debug.apk
if ! out=$("$adb" install -r "$apk" 2>&1); then
    echo "$out"
    case $out in
        *UPDATE_INCOMPATIBLE*|*SIGNATURES*)
            echo
            echo "The app on the phone was built on another computer, and signed with its key: it"
            echo "can only be updated from there.  Uninstalling it loses the progress it holds"
            echo "(adb uninstall $pkg), after which ./install.sh installs this one." ;;
    esac
    exit 1
fi
echo "installed Loglan Teach on the $phone"

# Its sync, with the repository this machine's progress is kept in.
sync_skip() {
    echo "$1"
    echo "The app's sync can be set up by hand, in its Settings > Sync."
}
set_up_sync() {
    local url rest at host user port ip login key reply root hostkey
    url=$(svn info --show-item url "$data" 2>/dev/null) || url=
    case $url in
        svn+ssh://*) ;;
        "") sync_skip "Progress here is not kept in Subversion, so there is nothing to sync the app with."
            return ;;
        *)  sync_skip "This machine reaches the repository as $url; the app needs svn+ssh://."
            return ;;
    esac
    rest=${url#svn+ssh://}
    at=${rest%%/*}
    local repo=/${rest#*/}
    # The host, user and port ssh makes of it here, ssh config and all.
    local conf
    conf=$(ssh -G "$at" 2>/dev/null)
    host=$(awk '$1 == "hostname" { print $2 }' <<< "$conf")
    user=$(awk '$1 == "user" { print $2 }' <<< "$conf")
    port=$(awk '$1 == "port" { print $2 }' <<< "$conf")
    # The phone needs an address: it cannot look up a .local name.
    if [[ $host =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
        ip=$host
    else
        ip=$(getent ahostsv4 "$host" | awk 'NR == 1 { print $1 }')
    fi
    if [ -z "$ip" ]; then
        sync_skip "$host cannot be looked up from here (is this machine on its network?)."
        return
    fi
    key=$("$adb" shell content read --uri "content://$pkg.sync/key" | tr -d '\r')
    case $key in
        ssh-ed25519\ *) ;;
        *) sync_skip "The app did not give its key: $key"; return ;;
    esac
    # On the server: the phone's key in authorized_keys, let do nothing but
    # svnserve, from the root the other machines' keys use; and the server's
    # own key, for the app to know it by.
    login=${LOGLAN_SERVER_LOGIN:-$user@$host}
    echo "Setting up the app's sync with $login..."
    if ! reply=$(printf '%s\n' "$key" | ssh -T -o ConnectTimeout=5 -p "$port" "$login" '
set -e
read -r key
umask 077
mkdir -p ~/.ssh
touch ~/.ssh/authorized_keys
root=$(sed -n "s/.*svnserve -t -r \([^\", ]*\).*/\1/p" ~/.ssh/authorized_keys | head -n 1)
root=${root:-/svn}
blob=$(echo "$key" | cut -d " " -f 2)
if grep -qF "$blob" ~/.ssh/authorized_keys; then
    echo had
else
    printf "command=\"svnserve -t -r %s\",restrict %s\n" "$root" "$key" >> ~/.ssh/authorized_keys
    echo added
fi
echo "root $root"
echo "hostkey $(cut -d " " -f 2 /etc/ssh/ssh_host_ed25519_key.pub)"'); then
        sync_skip "$login could not be reached (LOGLAN_SERVER_LOGIN=user@host to log in as someone else)."
        return
    fi
    root=$(awk '$1 == "root" { print $2 }' <<< "$reply")
    hostkey=$(awk '$1 == "hostkey" { print $2 }' <<< "$reply")
    case $reply in
        *had*) echo "the phone's key was already in $login's authorized_keys" ;;
        *) echo "added the phone's key to $login's authorized_keys (svnserve -r $root)" ;;
    esac
    "$adb" shell content call --uri "content://$pkg.sync" --method configure \
        --extra "host:s:$ip" --extra "port:i:$port" --extra "user:s:$user" \
        --extra "repo:s:$repo" ${hostkey:+--extra "hostkey:s:$hostkey"} >/dev/null
    echo "the app syncs with svn+ssh://$user@$ip$([ "$port" = 22 ] || echo ":$port")$repo"
}
set_up_sync

"$adb" shell am start -n "$pkg/io.github.mdemoulin.loglan.teach.MainActivity" >/dev/null
echo
echo "Loglan Teach is open on the phone.  ./install.sh again updates it; its progress stays."
