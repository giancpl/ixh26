"""Render the two Mermaid blocks in docs/crypto-flow.md to local PNG files.

Optional documentation tooling only: requires Playwright, an existing Chromium
browser and the standalone Mermaid bundle. Does not install/download a browser,
contact a rendering service, import the crypto module or run its tests.
"""

import argparse
import math
from pathlib import Path
import re
import shutil


ROOT = Path(__file__).resolve().parents[1]
MERMAID_VERSION = "12.3.0"
OUTPUTS = (
    ("prnu-flow.png", "PRNU: reference e confronto", "Il confronto produce evidenze statistiche; non prova la verità della scena."),
    ("crypto-hash-flow.png", "Profilo v3: hash, firma e timestamp", "La firma lega i byte e le dichiarazioni; il timestamp non certifica il momento dello scatto."),
)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mermaid-bundle", type=Path, required=True,
                        help=f"Path to mermaid@{MERMAID_VERSION}/dist/mermaid.min.js")
    parser.add_argument("--browser", type=Path, help="Existing Chrome/Chromium/Edge executable")
    parser.add_argument("--scale", type=float, default=2)
    args = parser.parse_args()
    if not args.mermaid_bundle.is_file() or not math.isfinite(args.scale) or not 1 <= args.scale <= 3:
        parser.error("Existing Mermaid bundle and scale in [1,3] required")
    browser = args.browser
    if browser is None:
        candidates = [shutil.which(name) for name in ("chromium", "chromium-browser", "google-chrome")]
        candidates += [r"C:\Program Files\Google\Chrome\Application\chrome.exe",
                       r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"]
        browser = next((Path(p) for p in candidates if p and Path(p).is_file()), None)
    if browser is None or not browser.is_file():
        parser.error("Select an existing Chromium browser with --browser")
    blocks = re.findall(r"^```mermaid[^\S\n]*\n(.*?)^```[^\S\n]*(?:\n|\Z)",
                        (ROOT / "docs/crypto-flow.md").read_text(encoding="utf-8"), re.S | re.M)
    if len(blocks) != len(OUTPUTS):
        parser.error("Expected exactly two Mermaid diagrams in crypto-flow.md")
    from playwright.sync_api import sync_playwright
    destination = ROOT / "docs/diagrams"
    destination.mkdir(exist_ok=True)
    with sync_playwright() as p:
        instance = p.chromium.launch(executable_path=str(browser), headless=True,
                                     args=["--disable-background-networking", "--disable-component-update"])
        try:
            page = instance.new_page(viewport={"width": 2000, "height": 1200}, device_scale_factor=args.scale)
            page.route("**/*", lambda route: route.abort())
            page.set_content("""<!doctype html><html lang="it"><meta charset="utf-8">
                <style>body{margin:0;background:white;color:#183049;font-family:Arial,sans-serif}
                #sheet{display:inline-block;padding:28px}#title{font-size:26px;margin:0 0 24px}
                #caption{font-size:15px;margin:20px 0 0;max-width:950px;line-height:1.5}
                svg{display:block;max-width:none!important}</style>
                <main id="sheet"><h1 id="title"></h1><div id="diagram"></div><p id="caption"></p></main></html>""")
            page.add_script_tag(path=str(args.mermaid_bundle.resolve()))
            for block, (name, title, caption) in zip(blocks, OUTPUTS):
                page.set_viewport_size({"width": 2000, "height": 1200})
                page.locator("#title").evaluate("(node, value) => node.textContent = value", title)
                page.locator("#caption").evaluate("(node, value) => node.textContent = value", caption)
                page.evaluate("""async (source) => {
                    mermaid.initialize({startOnLoad:false,securityLevel:'strict',theme:'base',
                        fontFamily:'Arial, sans-serif',
                        themeVariables:{fontSize:'17px',primaryColor:'#eef4fb',primaryTextColor:'#183049',
                            primaryBorderColor:'#51769a',lineColor:'#526c85'},
                        flowchart:{useMaxWidth:false,htmlLabels:true,curve:'linear',nodeSpacing:32,rankSpacing:48}});
                    const {svg} = await mermaid.render('cryptoDiagram', source);
                    document.getElementById('diagram').innerHTML = svg;
                    const node = document.querySelector('#diagram svg');
                    const box = node.viewBox.baseVal;
                    node.setAttribute('width', box.width);
                    node.setAttribute('height', box.height);
                    await document.fonts.ready;
                }""", block)
                bounds = page.locator("#sheet").bounding_box()
                if not bounds or bounds["width"] > 8000 or bounds["height"] > 12000:
                    raise ValueError("Unexpected diagram bounds")
                page.set_viewport_size({"width": math.ceil(bounds["width"]), "height": math.ceil(bounds["height"])})
                target = destination / name
                temporary = destination / (name + ".tmp.png")
                page.locator("#sheet").screenshot(path=str(temporary), animations="disabled")
                temporary.replace(target)
                print(f"Rendered {target.relative_to(ROOT)} ({bounds['width']:.0f} x {bounds['height']:.0f} CSS px, scale {args.scale:g})")
        finally:
            instance.close()


if __name__ == "__main__":
    main()
