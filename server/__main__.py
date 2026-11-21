"""CLI entrypoint: python3 -m server"""
from __future__ import annotations

import os
from aiohttp import web

from .app import create_app


def main() -> None:
    host = os.environ.get("HOST", "127.0.0.1")
    port = int(os.environ.get("PORT", "8787"))
    network = os.environ.get("IXH26_NETWORK", "mainnet")
    bitcoin_conf = os.environ.get("IXH26_BITCOIN_CONF")
    app = create_app(network=network, bitcoin_conf=bitcoin_conf)
    web.run_app(app, host=host, port=port)


if __name__ == "__main__":
    main()
