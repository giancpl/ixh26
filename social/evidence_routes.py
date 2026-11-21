"""Owner-authorized application API for enrollment and evidence preparation."""
import hashlib
import re
import sqlite3
from cryptography.exceptions import InvalidSignature
from io import BytesIO
import zipfile
from fastapi import Request
from fastapi.responses import JSONResponse, Response
from starlette.concurrency import run_in_threadpool
from fingerprinto.prnu import Geometry
from fingerprinto.receipt_v3 import json_object
from .evidence import EvidenceService, Unavailable
from .evidence_store import Conflict, ID, HASH
from .http_auth import authorize, AuthError
from upp_protocol import MAX_MEDIA_BYTES
from upp_protocol.protocol import evidence_commitment


def install(app,config,api_error,bounded_body,jpeg_dimensions):
    svc=EvidenceService(config);app.state.evidence=svc

    @app.exception_handler(Conflict)
    async def conflict(request,error):return JSONResponse({'error':{'code':'evidence_conflict','message':'Evidence conflicts with the saved operation.'}},status_code=409)
    @app.exception_handler(Unavailable)
    async def unavailable(request,error):return JSONResponse({'error':{'code':'evidence_unavailable','message':'Evidence service or current trust is unavailable.'}},status_code=503)

    async def auth(request,limit=256*1024):
        body=await bounded_body(request,limit)
        url=config.public_url+request.url.path+('?' + request.url.query if request.url.query else '')
        try:
            def check():
                with svc.store.connection() as db:
                    return authorize(request.headers.get('authorization'),url=url,method=request.method,body=body,db=db)
            owner=await run_in_threadpool(check)
            return body,owner
        except AuthError:raise api_error(401,'invalid_authorization','Fresh NIP-98 authorization is required.') from None
        except sqlite3.Error:raise api_error(503,'authorization_unavailable','Authorization storage is unavailable.') from None

    def document(raw,fields):
        try:
            obj=json_object(raw,limit=256*1024)
            if set(obj)!=set(fields):raise ValueError()
            if 'operation_id' in obj and not ID.fullmatch(obj['operation_id']):raise ValueError()
            return obj
        except (TypeError,ValueError):raise api_error(400,'invalid_evidence_request','Invalid evidence request.') from None

    def require(identity,owner,kind=None):
        row=svc.store.get(identity,owner)
        if row is None or (kind is not None and row['kind']!=kind):raise api_error(404,'evidence_not_found','Evidence was not found.')
        return row

    @app.post('/v1/enrollments/challenge')
    async def challenge(request:Request):
        raw,owner=await auth(request);obj=document(raw,['operation_id'])
        return await run_in_threadpool(svc.challenge,obj['operation_id'],owner)

    @app.post('/v1/enrollments')
    async def enrollment(request:Request):
        raw,owner=await auth(request);obj=document(raw,['operation_id','fields','possession'])
        try:await run_in_threadpool(svc.check_enrollment_input,obj,owner)
        except (ValueError,TypeError,KeyError):raise api_error(400,'invalid_enrollment','Invalid enrollment evidence.') from None
        created=await run_in_threadpool(svc.store.enqueue,obj['operation_id'],owner,'enrollment',obj)
        return JSONResponse(svc.enrollment_view(obj['operation_id'],owner),status_code=202 if created else 200)

    @app.get('/v1/enrollments/{identity}')
    async def enrollment_state(identity:str,request:Request):
        _,owner=await auth(request);require(identity,owner,'enrollment')
        return await run_in_threadpool(svc.enrollment_view,identity,owner)

    @app.post('/v1/calibrations')
    async def calibration(request:Request):
        raw,owner=await auth(request);obj=document(raw,['operation_id','enrollment_id','geometry'])
        try:
            geometry=Geometry(**obj['geometry'])
            if geometry.source_width is None or geometry.source_height is None:raise ValueError()
            from fingerprinto.prnu_image import DECODER
            if geometry.decoder!=DECODER and not re.fullmatch(r'pillow-13\.0\.0/rgb-green-stored-exif-[1-8]/1',geometry.decoder):raise ValueError()
        except (ValueError,TypeError):raise api_error(400,'invalid_geometry','Unsupported calibration geometry.') from None
        require(obj['enrollment_id'],owner,'enrollment')
        created=await run_in_threadpool(svc.store.enqueue,obj['operation_id'],owner,'calibration',obj)
        return JSONResponse(svc.store.public(obj['operation_id'],owner),status_code=202 if created else 200)

    @app.put('/v1/calibrations/{identity}/frames/{role}/{sha256}')
    async def frame(identity:str,role:str,sha256:str,request:Request):
        raw,owner=await auth(request,MAX_MEDIA_BYTES);session=require(identity,owner,'calibration')
        if hashlib.sha256(raw).hexdigest()!=sha256:raise api_error(400,'frame_digest_mismatch','Frame digest differs.')
        from PIL import Image
        from io import BytesIO
        try:
            with Image.open(BytesIO(raw),formats=['JPEG']) as image:
                geometry=session['input']['geometry']
                if image.size!=(geometry['source_width'],geometry['source_height']):raise ValueError()
        except (OSError,ValueError):raise api_error(400,'unsupported_frame_geometry','Frame dimensions differ from the fixed route.') from None
        await run_in_threadpool(jpeg_dimensions,raw)
        try:await run_in_threadpool(svc.store.add_frame,identity,owner,role,sha256,raw)
        except Conflict:raise
        except ValueError:raise api_error(400,'invalid_frame','Invalid frame role or digest.') from None
        return svc.store.public(identity,owner)

    @app.post('/v1/calibrations/{identity}/evaluate')
    async def evaluate(identity:str,request:Request):
        raw,owner=await auth(request);document(raw,[]);require(identity,owner,'calibration')
        await run_in_threadpool(svc.store.evaluate,identity,owner)
        return svc.store.public(identity,owner)

    @app.get('/v1/calibrations/{identity}')
    async def calibration_state(identity:str,request:Request):
        _,owner=await auth(request);require(identity,owner,'calibration')
        return svc.calibration_view(identity,owner)

    @app.post('/v1/evidence')
    async def prepare(request:Request):
        raw,owner=await auth(request)
        try:
            obj=json_object(raw)
            required={'operation_id','enrollment_id','media_sha256','time'}
            if set(obj)-required-{'reference_hash'} or not required<=set(obj) or not ID.fullmatch(obj['operation_id']) or type(obj['time']) is not int:raise ValueError()
            require(obj['enrollment_id'],owner,'enrollment')
            if not HASH.fullmatch(obj['media_sha256']):raise ValueError()
            await run_in_threadpool(svc.asset,obj['media_sha256'])
        except (ValueError,TypeError,KeyError):raise api_error(400,'invalid_evidence_request','Invalid evidence request.') from None
        created=await run_in_threadpool(svc.store.enqueue,obj['operation_id'],owner,'evidence',obj)
        return JSONResponse(svc.store.public(obj['operation_id'],owner),status_code=202 if created else 200)

    @app.get('/v1/evidence/{identity}')
    async def preparation(identity:str,request:Request):
        _,owner=await auth(request);require(identity,owner,'evidence')
        return svc.store.public(identity,owner)

    @app.post('/v1/evidence/{identity}/receipt')
    async def receipt(identity:str,request:Request):
        raw,owner=await auth(request);require(identity,owner,'evidence')
        try:return await run_in_threadpool(svc.receive_receipt,identity,owner,raw)
        except Conflict:raise
        except (ValueError,TypeError,InvalidSignature):raise api_error(400,'invalid_receipt','Receipt or current evidence is invalid.') from None

    def post_attachment(event_id):
        from .storage import Store
        row=Store(config).get(event_id) if HASH.fullmatch(event_id) else None
        if row is None:raise api_error(404,'post_not_found','Post was not found.')
        import json
        commitment=evidence_commitment(json.loads(row['event']))
        att=svc.store.attachment(commitment) if commitment else None
        if att is None or att['event']!=event_id:raise api_error(404,'evidence_not_found','No v3 receipt is attached.')
        return att

    @app.post('/v1/posts/{event_id}/evidence/verify')
    def verify(event_id:str):
        att=post_attachment(event_id)
        return svc.snapshot(att['commitment'],verify=True)

    @app.get('/v1/posts/{event_id}/evidence/proof')
    def proof(event_id:str):
        att=post_attachment(event_id);path=svc.store.receipts/att['operation']/'commitment.bin.ots'
        if not path.is_file():raise api_error(404,'proof_not_ready','No v3 proof is available yet.')
        return Response(path.read_bytes(),media_type='application/octet-stream')

    @app.get('/v1/posts/{event_id}/evidence/bundle')
    def bundle(event_id:str):
        att=post_attachment(event_id);files=svc.bundle(att).files();output=BytesIO()
        with zipfile.ZipFile(output,'w',compression=zipfile.ZIP_STORED) as archive:
            for name,raw in files.items():archive.writestr(name,raw)
        return Response(output.getvalue(),media_type='application/zip')
    return svc
