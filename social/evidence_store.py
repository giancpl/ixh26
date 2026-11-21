"""Durable owner-scoped operations and private content; no authority secrets."""
from contextlib import contextmanager
import json
import os
import re
import sqlite3
import time
import uuid
from .storage import canonical, publish_bytes

ID = re.compile(r'[0-9a-f]{32}\Z')
HASH = re.compile(r'[0-9a-f]{64}\Z')


class Conflict(ValueError):
    pass


class EvidenceStore:
    def __init__(self, config):
        self.config = config
        self.root = config.data_dir / 'private-evidence'
        self.root.mkdir(mode=0o700, parents=True, exist_ok=True)
        os.chmod(self.root, 0o700)
        self.frames = self.root/'frames'; self.frames.mkdir(mode=0o700, exist_ok=True)
        self.references = self.root/'references'; self.references.mkdir(mode=0o700, exist_ok=True)
        self.receipts = self.root/'receipts'; self.receipts.mkdir(mode=0o700, exist_ok=True)
        self.db = self.root/'operations.sqlite3'
        with self.connection() as db:
            db.executescript('''PRAGMA journal_mode=WAL;
                CREATE TABLE IF NOT EXISTS operations(
                    id TEXT PRIMARY KEY,owner TEXT NOT NULL,kind TEXT NOT NULL,input TEXT NOT NULL,
                    state TEXT NOT NULL,output TEXT,error TEXT,due REAL NOT NULL,
                    lease TEXT,lease_until REAL,attempts INTEGER NOT NULL DEFAULT 0);
                CREATE TABLE IF NOT EXISTS challenges(
                    id TEXT PRIMARY KEY,owner TEXT NOT NULL,challenge TEXT NOT NULL,expires REAL NOT NULL,policy TEXT NOT NULL);
                CREATE TABLE IF NOT EXISTS http_auth_replay(id TEXT PRIMARY KEY,expires REAL NOT NULL);
                CREATE TABLE IF NOT EXISTS frames(
                    session TEXT NOT NULL,owner TEXT NOT NULL,role TEXT NOT NULL,digest TEXT NOT NULL,
                    PRIMARY KEY(session,digest));
                CREATE TABLE IF NOT EXISTS attachments(
                    commitment TEXT PRIMARY KEY,operation TEXT UNIQUE NOT NULL,owner TEXT NOT NULL,media TEXT NOT NULL,
                    event TEXT UNIQUE,settlement TEXT);
            ''')

    @contextmanager
    def connection(self):
        db = sqlite3.connect(self.db, timeout=15)
        db.row_factory = sqlite3.Row
        try:
            db.execute('PRAGMA synchronous=FULL')
            yield db
            db.commit()
        except BaseException:
            db.rollback(); raise
        finally:
            db.close()

    def enqueue(self, identity, owner, kind, data):
        if not isinstance(identity,str) or not ID.fullmatch(identity):
            raise ValueError('Invalid operation ID')
        raw=canonical(data)
        with self.connection() as db:
            db.execute('BEGIN IMMEDIATE')
            old=db.execute('SELECT owner,kind,input FROM operations WHERE id=?',(identity,)).fetchone()
            if old is not None:
                if tuple(old)!=(owner,kind,raw): raise Conflict('Operation inputs changed')
                return False
            db.execute('INSERT INTO operations(id,owner,kind,input,state,due) VALUES(?,?,?,?,?,?)',
                       (identity,owner,kind,raw,'COLLECTING' if kind=='calibration' else 'QUEUED',time.time()))
            return True

    def get(self, identity, owner=None):
        if not isinstance(identity,str) or not ID.fullmatch(identity): return None
        with self.connection() as db:
            row=db.execute('SELECT * FROM operations WHERE id=?',(identity,)).fetchone()
        if row is None or (owner is not None and owner!=row['owner']): return None
        out=dict(row)
        for key in ('input','output'):
            out[key]=json.loads(out[key]) if out[key] else None
        return out

    def public(self, identity, owner):
        row=self.get(identity,owner)
        if row is None: return None
        output={k:row[k] for k in ('id','kind','state','output','error')}
        if row['kind']=='calibration':
            output['frames']=[{k:r[k] for k in ('role','digest')} for r in self.frame_rows(identity)]
        return output

    def claim(self):
        with self.connection() as db:
            db.execute('BEGIN IMMEDIATE')
            now=time.time()
            row=db.execute("SELECT id FROM operations WHERE state IN ('QUEUED','PROCESSING','UNAVAILABLE') AND due<=? AND (lease_until IS NULL OR lease_until<?) ORDER BY due LIMIT 1",(now,now)).fetchone()
            if row is None:return None
            lease=uuid.uuid4().hex
            db.execute("UPDATE operations SET state='PROCESSING',lease=?,lease_until=?,attempts=attempts+1 WHERE id=?",(lease,now+self.config.lease_seconds,row['id']))
            return row['id'],lease

    def renew(self, identity, lease):
        with self.connection() as db:
            return db.execute('UPDATE operations SET lease_until=? WHERE id=? AND lease=?',
                              (time.time()+self.config.lease_seconds,identity,lease)).rowcount==1

    def finish(self, identity, lease, *, state, output=None, error=None, retry=False):
        with self.connection() as db:
            return db.execute('UPDATE operations SET state=?,output=?,error=?,due=?,lease=NULL,lease_until=NULL WHERE id=? AND lease=?',
                ('QUEUED' if retry else state,canonical(output) if output is not None else None,error,
                 time.time()+self.config.retry_seconds,identity,lease)).rowcount==1

    def frame_rows(self, identity):
        with self.connection() as db:
            return [dict(r) for r in db.execute('SELECT * FROM frames WHERE session=? ORDER BY role,digest',(identity,))]

    def add_frame(self, identity,owner,role,digest,data):
        if role not in ('enrollment','development','genuine','impostor') or not HASH.fullmatch(digest):raise ValueError('Invalid frame role/hash')
        with self.connection() as db:
            db.execute('BEGIN IMMEDIATE')
            session=db.execute("SELECT state FROM operations WHERE id=? AND owner=? AND kind='calibration'",(identity,owner)).fetchone()
            if session is None or session['state']!='COLLECTING':raise Conflict('Calibration is not collecting')
            old=db.execute('SELECT role FROM frames WHERE session=? AND digest=?',(identity,digest)).fetchone()
            if old is not None and old['role']!=role: raise Conflict('Calibration splits must be disjoint')
            count=db.execute('SELECT count(*) FROM frames WHERE session=?',(identity,)).fetchone()[0]
            if old is None and count>=128:raise Conflict('Calibration frame quota reached')
            publish_bytes(self.frames/(digest+'.jpg'),data)
            db.execute('INSERT OR IGNORE INTO frames VALUES(?,?,?,?)',(identity,owner,role,digest))

    def evaluate(self,identity,owner):
        with self.connection() as db:
            db.execute("UPDATE operations SET state='QUEUED',due=? WHERE id=? AND owner=? AND kind='calibration' AND state='COLLECTING'",(time.time(),identity,owner))

    def attach(self, commitment, owner, media, event, *, operation=None):
        with self.connection() as db:
            db.execute('BEGIN IMMEDIATE')
            row=db.execute('SELECT * FROM attachments WHERE commitment=?',(commitment,)).fetchone()
            if row is None or row['owner']!=owner or row['media']!=media or (row['event'] and row['event']!=event):
                raise Conflict('Evidence does not match this post')
            if operation is None:
                db.execute('UPDATE attachments SET event=? WHERE commitment=?',(event,commitment))
            return dict(row)

    def attachment(self, commitment):
        with self.connection() as db:
            row=db.execute('SELECT * FROM attachments WHERE commitment=?',(commitment,)).fetchone()
            return dict(row) if row else None
