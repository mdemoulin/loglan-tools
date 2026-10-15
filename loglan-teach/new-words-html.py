#!/usr/bin/env python3
# new-words-html.py: render your coined Loglan words as a dictionary page.
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
# NOT ALL OF THIS FILE IS UNDER THE GPL.  The page template in it -- the HEAD
# and FOOT strings, taken from the Loglan-to-English dictionary's own pages --
# is Loglan material owned by The Loglan Center, Inc., which reserves all
# rights to it, and is used with its permission.  The GPL covers the rest.
# See COPYING-TLC.

"""new-words-html.py -- render coined words as a Loglan-to-English page.

The page looks like the Institute's Loglan-to-English dictionary pages (the
LOD's own styles and entry markup) but holds only words coined locally, so
they can be reviewed on their own or sent in.  It is meant for a selection
of words -- by default those not yet submitted to the Institute (no
"submitted" date), so the page is the next batch to send.  The entries are
a single list ordered by word, in a plain page that scrolls as a whole.

The words come from new-words.json in the data directory (~/.loglan, or
$LOGLAN_STATE_DIR); see the notes at the top of that file for its fields.

    new-words-html.py                      the words not yet submitted
    new-words-html.py --submitted DATE     the batch sent on DATE (YYYY-MM-DD)
    new-words-html.py --all                every word, submitted or not
    new-words-html.py -i FILE -o OUT       another word file; another page
"""

import argparse
import datetime
import html
import json
import os
import re

STATE = os.environ.get('LOGLAN_STATE_DIR') or os.path.expanduser('~/.loglan')
DATE = re.compile(r'^\d{4}-\d\d-\d\d$')

CASE_TAG = re.compile(r'\[([A-Z])-([A-Z]*)\]')
KEY = re.compile(r'\{([^{}]*)\}')

# The LOD separates the case tags with zero-width joiners so that "B-" does not
# break across a line; source definitions write a plain hyphen.
JOINED = r'[\1&zwj;-&zwj;\2]'


def render(text):
    """A source definition in LOD markup: {key} meanings marked, tags joined."""
    out = KEY.sub(r'<k>\1</k>', html.escape(text, quote=False))
    return CASE_TAG.sub(JOINED, out)


def l2e_block(word):
    lines = ['<w wid="%s"><wl>%s,</wl>' % (word['word'], word['word']),
             '<ms>', '\t<m>']
    tech = []
    if word.get('affix'):
        tech.append('<afx>%s</afx>' % word['affix'])
    if word.get('origin'):
        tech.append('<o>&lt;%s&gt;</o>' % html.escape(word['origin'], quote=False))
    tech.append('<tec>%s</tec>' % html.escape(word['tec'], quote=False))
    lines.append('\t\t<t>%s</t>' % ' '.join(tech))
    lines.append('\t\t<ds>')
    for d in word['defs']:
        lines.append('\t\t\t<d>%s</d>' % render(d))
    lines += ['\t\t</ds>', '\t</m>', '</ms>', '</w>']
    return lines


def entries(words):
    """All entries in one list, ordered by wid."""
    out = ['']
    for w in sorted(words, key=lambda w: w['word']):
        out += l2e_block(w)
    return '\n'.join(out + ['', ''])


# ---- The Loglan Center's material: not under the GPL ----------------------
# The page around the entries: the head and footer of the Loglan-to-English
# dictionary's LoglanToEnglish.html, less its search box, letter index and
# fixed bars.  All rights reserved by The Loglan Center, Inc. (the Idaho
# corporation which has superseded the Florida corporation); used with its
# permission.  See COPYING-TLC.
HEAD = r'''<!doctype html>
<html lang="en">
<head>
<!-- Required meta tags -->
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no">

<!-- Bootstrap CSS -->
<link rel="stylesheet" href="https://stackpath.bootstrapcdn.com/bootstrap/4.5.2/css/bootstrap.min.css" integrity="sha384-JcKb8q3iqJ61gNV9KGb8thSsNjpSL0n8PARn9HuZOnIxN0hoP+VmmDGMN5t9UJ0Z" crossorigin="anonymous">
<link href="https://fonts.googleapis.com/css2?family=Merriweather:ital,wght@0,400;0,700;1,400;1,700&display=swap" rel="stylesheet">
<link rel="icon"
      type="image/png"
      href="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAADJ0lEQVQ4T21TWUwTURQ9701bpmWAAq2tUlCQQhBRjPrhEomoARMVExdI1A+/MIS6JeKX30YSNaL4YeKHGrcS14AmKmpEMCYajUsEWdQWQaoVSwfaaWfmaYcl1vj+7rn3nHffPfcR/Ofs3XvDLMuRVYTQWeNpVWc08hfq6ysG/i0nfwPV1RedvKBvAUOO0Wob5a3JRjDg5/tuPQgDCOlUJLWisbHq4yRvSqCmxl2u59UWoWCUKmICTOlFsCyeB0WKovv8Lczc2YefzywIdgoKZFrW0LClNSaiCdTUuHN1Caxr2mofTSocQeSHAUPN+cipXA/GgJ5z1+HY0Q2doCDwKhX+tnQ1JLK8M2eqejWB3fuufEyaIzqnrRnSOvv1Mg1hTwEyy0u0eOBBOwyON0hZMKzFvns2BDuT3jUcqywidXXXHOGI7M3e9QmUV7SC2C2hvnw41paCECDQ9Rkjng7YN3zW8rLI4cvZbDCZ5ZLa2qtHTJmRAxmVnql5MBXov5AL29ISJDpskENheFsegimjSFs+iMRcEd6Ls1h40HCUuFzuGynzgxutq7/FOeRvs8KgW4G04vxxnDGI/T4MtD5F1o5e+GMDfZvcTFyuppspxYEK66rx92uuRygGmmYitWAJzAUTqzCR+/78FQI9PaC8hIiPbya1tVcOm7KiBzO2eogajRGzIQ1RJM22Y0bpMhCOgskKGGOgep0mMzb4A1/vd0RlMXyI7N9/OVNm1JNd/Qmhfh7B14XIKFsJquO0YskfgKf5EVRZhW1JMaThEQhZ0+G90y5RUPuEje4uwSnmRX8ZYXYuROpc5zh5eASe262wlHrBFA7B91bw0wMYfmEGU+nVkyc2V8UtktGWTrPWl4JQAiUkoa/pLiwrvRDyglPzCbw2w/8kXY2Gaf7p01t7pqzbs+fSPNCE+4mZNsFessg0+LgDBnsv0pb5NLIyxsHfZoHYJahKhFt36tSWuzE87jO5XG4r0XFHoLLtjCn65EIRRK9CGjIyyacjhEM3VZTy48e39U22FCcwCdbV3ZoxNhbZRQgrin1BxtgHk4lrrK/f1B+3LH+C3+k0TOAiD2BKAAAAAElFTkSuQmCC"
/>
<title>Loglan to English Dictionary: New Words</title>

<style>
	body {
		font-family: 'Merriweather', serif;
		font-size: 14px;
		margin-left: 20px;
	}
	nav.fixed-top {
		background-color: black;
		height: 50px;
		color: white;
	}
	nav.fixed-bottom {
		height: 20px;
		text-align: right;
	}

	.lt > span {
		font-size: 24px;
		font-weight: bold;
	}
	w {
		margin-top: 20px;
	}
	wl {
		font-weight: bold;
		display: block;
	}
	m, d, w, us {
		display: block;
	}
	ms > *:not(:first-child) {
		margin-top: 10px;
	}
	ms > * {
		margin-left: 10px;
	}

	afx {
		color: #007F00;
		font-style: italic;
	}
	o {
		color: #007F00;
		font-style: italic;
	}
	d {
		margin-left: 20px;
		margin-top: 5px;

		text-align: justify;
		max-width: 60%;
		min-width: 400px;
	}
	du {
		font-weight: bold;
	}
	us {
		margin-top: 5px;
		max-width: 40%;
		min-width: 400px;
	}
	k {
		color: #0000FF;
		font-style: italic;
	}
	l {
		color: #C70039;
		font-weight: bold;
	}
</style>
</head>
<body>

<nav class="navbar navbar-expand-lg navbar-light bg-light">
	<a class="navbar-brand" href="#top">New Words</a>
</nav>
<main id="content">
<a name="top"></a>
'''

FOOT = r'''
</main>
<nav class="navbar navbar-expand-lg bg-light">
	<ul class="navbar-nav ml-auto">
		<li class="nav-item">
			<div class="container-fluent" style="font-size: 10px; margin-right: 10px;">%(stamp)s</div>
		</li>
	</ul>
</nav>
<!-- Optional JavaScript -->
<!-- jQuery first, then Popper.js, then Bootstrap JS -->
<script src="https://code.jquery.com/jquery-3.5.1.slim.min.js" integrity="sha384-DfXdz2htPH0lsSSs5nCTpuj/zy4C+OGpamoFVy38MVBnE+IbbVYUew+OrCXaRkfj" crossorigin="anonymous"></script>
<script src="https://cdn.jsdelivr.net/npm/popper.js@1.16.1/dist/umd/popper.min.js" integrity="sha384-9/reFTGAW83EW2RDu2S0VKaIzap3H66lZH81PoYlFhbGU+6BZp6G7niu735Sk7lN" crossorigin="anonymous"></script>
<script src="https://stackpath.bootstrapcdn.com/bootstrap/4.5.2/js/bootstrap.min.js" integrity="sha384-B4gt1jrGC7Jh4AgTPSdUtOBvfO8shuf57BaghqFfPlYxofvL8/KUEfYiJOMMV+rV" crossorigin="anonymous"></script>
</body>
</html>'''
# ---- end of The Loglan Center's material --------------------------------


def main():
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('-i', '--input', default=os.path.join(STATE, 'new-words.json'),
                    help='words to render (default: %(default)s)')
    ap.add_argument('-o', '--output', default='new-words.html',
                    help='output file (default: %(default)s)')
    pick = ap.add_mutually_exclusive_group()
    pick.add_argument('--submitted', metavar='DATE',
                      help='only the words submitted on DATE (YYYY-MM-DD) '
                           'instead of those not yet submitted')
    pick.add_argument('--all', action='store_true',
                      help='every word, submitted or not')
    args = ap.parse_args()
    if args.submitted and not DATE.match(args.submitted):
        ap.error('--submitted takes a date, YYYY-MM-DD')

    try:
        words = json.load(open(args.input, encoding='utf-8'))['words']
    except OSError as e:
        raise SystemExit('new-words-html.py: %s' % e)
    except json.JSONDecodeError as e:
        raise SystemExit('%s is not valid JSON: %s' % (args.input, e))

    seen = set()
    for w in words:
        if w['word'] in seen:
            raise SystemExit('duplicate word in %s: %s' % (args.input, w['word']))
        seen.add(w['word'])
        if w.get('submitted') is not None and not DATE.match(str(w['submitted'])):
            raise SystemExit('%s: %s: "submitted" must be YYYY-MM-DD or null, not %r'
                             % (args.input, w['word'], w['submitted']))

    if not args.all:
        words = [w for w in words if w.get('submitted') == args.submitted]
        if not words:
            raise SystemExit('no words %s in %s' % (
                'submitted on %s' % args.submitted if args.submitted
                else 'awaiting submission', args.input))

    stamp = 'Generated - %s; %d new words from %s; not part of the LOD database export.' % (
        datetime.date.today().strftime('%d.%m.%Y'), len(words),
        os.path.basename(args.input))
    with open(args.output, 'w', encoding='utf-8') as f:
        f.write(HEAD + entries(words) + FOOT % {'stamp': html.escape(stamp, quote=False)})
    print('wrote %s: %d words' % (args.output, len(words)))


if __name__ == '__main__':
    main()
