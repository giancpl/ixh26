#!/usr/bin/env python3
"""Collect a real-camera, same-scene DEMO in an explicitly started private guide.

Requires an authorized USB debug phone and operator access to the own local
Compose API container. Does not publish, mine, enroll, export keys or approve
calibration. Start 'demo series' in PhoTO first. Default is read-only inspection.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE = 'com.example.photoanchor'
TARGETS = {'enrollment': 30, 'development': 10, 'genuine': 10}
SQL = '''import json,sqlite3,sys
db=sqlite3.connect('file:/data/private-evidence/operations.sqlite3?mode=ro',uri=True,timeout=10)
db.row_factory=sqlite3.Row
r=db.execute("SELECT id,state,input,output,error FROM operations WHERE id=? AND kind='calibration'",(sys.argv[1],)).fetchone()
if r is None: raise SystemExit('Session not found')
d=dict(r)
for k in ('input','output'): d[k]=json.loads(d[k]) if d[k] else None
d['frames']=[dict(f) for f in db.execute('SELECT role,digest FROM frames WHERE session=? ORDER BY role,digest',(sys.argv[1],))]
print(json.dumps(d))'''


class Demo:
    def __init__(self, args):
        self.args = args
        self.deadline = time.monotonic() + args.timeout
        self.session = self.local_session()
        self.start = self.snapshot()

    def command(self, *argv, timeout=30):
        result = subprocess.run(argv, check=True, capture_output=True, timeout=timeout)
        return result.stdout

    def adb(self, *argv):
        return self.command('adb', '-s', self.args.serial, *argv)

    def local_session(self):
        value = json.loads(self.adb('exec-out', 'run-as', PACKAGE, 'cat', 'no_backup/photo-v3/device.json'))
        if value.get('calibration_plan') != 'photo/prnu-single-pixel/1' or value.get('calibration_demo') is not True:
            raise RuntimeError('Start an explicitly labelled demo series in PhoTO first.')
        identity = value.get('calibration_id', '')
        if not re.fullmatch('[0-9a-f]{32}', identity):
            raise RuntimeError('Invalid demo session identity.')
        return identity

    def snapshot(self):
        if time.monotonic() >= self.deadline:
            raise RuntimeError('Demo deadline exceeded; session and originals are retained.')
        value = json.loads(self.command('docker', 'exec', self.args.container, 'python', '-c', SQL, self.session))
        frames = value['frames']
        if len({f['digest'] for f in frames}) != len(frames):
            raise RuntimeError('Repeated digest in the private session.')
        counts = {role: sum(f['role'] == role for f in frames) for role in TARGETS}
        if any(counts[k] > TARGETS[k] for k in TARGETS) or len(frames) != sum(counts.values()):
            raise RuntimeError('Unexpected frame roles/counts; refusing to change this session.')
        value['counts'] = counts
        return value

    def window(self):
        if self.local_session() != self.session:
            raise RuntimeError('The active session changed; automation stopped.')
        self.adb('shell', 'uiautomator', 'dump', '/sdcard/photo-prnu-demo.xml')
        root = ET.fromstring(self.adb('exec-out', 'cat', '/sdcard/photo-prnu-demo.xml'))
        nodes = [n for n in root.iter('node') if n.get('package') == PACKAGE]
        if not nodes:
            raise RuntimeError('PhoTO must be unlocked and visible. No other app will be tapped.')
        for n in nodes:
            if n.get('text', '').startswith(('Frame not counted:', 'Local service:', 'Operation unavailable.')):
                raise RuntimeError(n.get('text'))
        visible = []
        for node in nodes:
            bounds = [int(v) for v in re.findall(r'\d+', node.get('bounds', ''))]
            if len(bounds) == 4 and bounds[2] > bounds[0] and bounds[3] > bounds[1]:
                visible.append(node)
        return root, visible

    def tap(self, root, node):
        parents = {c: p for p in root.iter() for c in p}
        while node.get('clickable') != 'true' and node in parents:
            node = parents[node]
        coords = [int(v) for v in re.findall(r'\d+', node.get('bounds', ''))]
        if node.get('clickable') != 'true' or len(coords) != 4:
            raise RuntimeError('Required PhoTO control is not enabled.')
        if node.get('enabled') != 'true':
            return False  # An in-flight capture/upload can still show its control.
        x1, y1, x2, y2 = coords
        if x2 <= x1 or y2 <= y1:
            raise RuntimeError('Required PhoTO control is off screen.')
        self.adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
        return True

    def collect(self):
        attempts = 0
        initial = sum(self.start['counts'].values())
        goal = min(50, initial + self.args.limit)
        previous = initial
        waiting = 0
        while True:
            view = self.snapshot(); current = sum(view['counts'].values())
            if current >= goal:
                return view
            if view['state'] != 'COLLECTING':
                raise RuntimeError('Session is not collecting.')
            if current != previous:
                print(json.dumps({'accepted': current, 'counts': view['counts'], 'purpose': 'DEMO_ONLY'}), flush=True)
                previous = current
            if attempts >= self.args.limit * 3 + 5:
                raise RuntimeError('Too many attempts without enough accepted frames; no checks were relaxed.')
            root, nodes = self.window()
            store = next((n for n in nodes if n.get('text', '').startswith('Store privately ·') or n.get('text') == 'Recover last stored frame'), None)
            capture = next((n for n in nodes if n.get('content-desc') == 'Capture photo'), None)
            if store is not None:
                if self.tap(root, store): attempts += 1
                waiting = 0
            elif capture is not None:
                self.tap(root, capture)
                waiting = 0
            else:
                waiting += 1
                if waiting > 20:
                    raise RuntimeError('Open Create with the active demo camera/capture before collection.')
            time.sleep(1)

    def evaluate(self):
        view = self.snapshot()
        if view['counts'] != TARGETS:
            raise RuntimeError('Evaluation requires the complete demo 30/10/10 collection.')
        if view['state'] == 'COLLECTING':
            root, nodes = self.window()
            profile = next((n for n in reversed(nodes) if n.get('text') == 'Profile'), None)
            if profile is None:
                raise RuntimeError('Profile navigation is unavailable.')
            self.tap(root, profile)
            for _ in range(8):
                root, nodes = self.window()
                button = next((n for n in nodes if n.get('text') == 'Evaluate'), None)
                if button is not None:
                    if self.tap(root, button): break
                    time.sleep(1)
                else:
                    self.adb('shell', 'input', 'swipe', '480', '1700', '480', '700', '450')
            else:
                raise RuntimeError('Evaluate is not visible in Profile.')
        last = None
        while True:
            view = self.snapshot()
            if view['state'] != last:
                print(json.dumps({'analysis_state': view['state']}), flush=True); last = view['state']
            if view['state'] == 'COMPLETE':
                return view
            if view['state'] in ('ERROR', 'UNAVAILABLE'):
                raise RuntimeError('Analysis failed/unavailable; original data retained and no threshold approved.')
            time.sleep(2)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--container', default='photoanchor-v3-api-1')
    parser.add_argument('--collect', action='store_true')
    parser.add_argument('--evaluate', action='store_true')
    parser.add_argument('--limit', type=int, default=50, help='Maximum additional accepted frames this run')
    parser.add_argument('--timeout', type=int, default=900)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    if not 1 <= args.limit <= 50 or not 1 <= args.timeout <= 1800:
        parser.error('Limit must be 1–50 and timeout 1–1800 seconds.')
    try:
        demo = Demo(args); view = demo.start
        print(json.dumps({'session': demo.session, 'state': view['state'], 'counts': view['counts'], 'purpose': 'DEMO_ONLY'}), flush=True)
        if args.collect: view = demo.collect()
        if args.evaluate: view = demo.evaluate()
        report = {'schema': 'photo/pixel-prnu-demo/1', 'purpose': 'same-scene automated DEMO; not independent calibration',
                  'approved_by_this_run': False, 'session': view}
        if args.output:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(json.dumps(report, indent=2))
        print(json.dumps({'state': view['state'], 'counts': view['counts'], 'approved_by_this_run': False}), flush=True)
        return 0
    except RuntimeError as exc:
        print(f'Demo stopped: {exc} Data retained; no approval or fallback performed.', flush=True)
        return 1
    except (ValueError, OSError, subprocess.SubprocessError, ET.ParseError):
        print('Demo stopped: inspect the private app/session state; data retained, no approval or fallback performed.', flush=True)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
