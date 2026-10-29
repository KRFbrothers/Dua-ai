"""Export the canonical preview as one offline HTML file (no dependencies)."""
import argparse
import base64
from pathlib import Path


def export(destination: Path) -> None:
    source = Path(__file__).resolve().parent
    html = (source / 'index.html').read_text()
    css = (source / 'preview.css').read_text()
    script = (source / 'preview.js').read_text()
    html = html.replace('<link rel="stylesheet" href="preview.css">', f'<style>{css}</style>')
    html = html.replace('<script src="preview.js" defer></script>', '')
    html = html.replace('</body>', f'<script>{script}</script>\n</body>')
    assets = {
        'dua-light.svg': 'image/svg+xml', 'dua-dark.svg': 'image/svg+xml',
        'inter-regular.ttf': 'font/ttf', 'Inter-OFL.txt': 'text/plain;charset=utf-8',
    }
    # JavaScript templates also contain the original relative image paths.
    for name, mime in assets.items():
        encoded = base64.b64encode((source / 'assets' / name).read_bytes()).decode('ascii')
        html = html.replace(f'assets/{name}', f'data:{mime};base64,{encoded}')
    html = html.replace('>Inter font license</a>', ' download="Inter-OFL.txt">Inter font license</a>')
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(html)
    print(f'Exported {destination} ({destination.stat().st_size:,} bytes)')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('destination', type=Path)
    export(parser.parse_args().destination)
