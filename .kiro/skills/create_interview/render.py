#!/usr/bin/env python3
"""
render.py — Convierte un README_<TEMA>.md de entrevista en HTML (tema oscuro +
CSS de impresion) y PDF, con el mismo estilo que las entrevistas existentes en
interview-simulator/.

Uso:
    python3 render.py interview-simulator/README_SPRING_SECURITY.md
    python3 render.py interview-simulator/README_SPRING_SECURITY.md --no-pdf

Requiere:
    - python3 con el modulo `markdown`  (pip install markdown)
    - google-chrome / chromium para el PDF (opcional con --no-pdf)

Convenciones del Markdown de entrada:
    # Simulación de Entrevista Técnica — <Tema>
    > blockquote(s) de intro
    ## 0. Seccion ...
    **P.** pregunta
    **R.** respuesta (puede ocupar varias lineas / listas / bloques de codigo)
    *Follow-up:* seguimiento
"""
import argparse
import html as _html
import os
import re
import shutil
import subprocess
import sys
import tempfile

try:
    import markdown as md_lib
except ImportError:
    sys.exit(
        "Falta el modulo 'markdown'. Instalalo con: pip install markdown\n"
        "(o: python3 -m pip install --user markdown)"
    )

STYLE = """  :root {
    --bg:#0f1420; --panel:#161d2b; --panel2:#1d2636; --fg:#e6edf3;
    --muted:#9aa7b8; --accent:#3b82f6; --green:#22c55e; --yellow:#eab308;
    --red:#ef4444; --border:#2a3446; --code:#0a0e16; --codefg:#c9d4e0;
  }
  * { box-sizing:border-box; }
  html { scroll-behavior:smooth; }
  body {
    margin:0; background:var(--bg); color:var(--fg);
    font-family:system-ui,Segoe UI,Roboto,Helvetica,Arial,sans-serif;
    line-height:1.65; font-size:16px;
  }
  .wrap { max-width:900px; margin:0 auto; padding:40px 24px 80px; }
  h1 { font-size:30px; line-height:1.25; margin:0 0 8px; }
  h2 {
    font-size:22px; margin:44px 0 16px; padding-bottom:8px;
    border-bottom:2px solid var(--border);
  }
  .subtitle { color:var(--muted); font-size:14px; margin-bottom:24px; }
  blockquote {
    margin:0 0 28px; padding:16px 20px; background:var(--panel);
    border-left:4px solid var(--accent); border-radius:0 8px 8px 0;
    color:var(--fg);
  }
  blockquote p { margin:0 0 8px; }
  blockquote p:last-child { margin-bottom:0; }
  .q, .a, .followup {
    padding:14px 18px; border-radius:10px; margin:14px 0;
    border:1px solid var(--border);
  }
  .q { background:var(--panel2); border-left:4px solid var(--accent); }
  .a { background:var(--panel); border-left:4px solid var(--green); }
  .followup {
    background:rgba(234,179,8,.06); border-left:4px solid var(--yellow);
    font-size:15px;
  }
  .tag {
    display:inline-block; font-size:11px; font-weight:700; letter-spacing:.05em;
    text-transform:uppercase; padding:2px 8px; border-radius:20px; margin-right:8px;
    vertical-align:middle;
  }
  .tag-q { background:rgba(59,130,246,.18); color:#93c5fd; }
  .tag-a { background:rgba(34,197,94,.18); color:#86efac; }
  .tag-f { background:rgba(234,179,8,.18); color:#fde047; }
  code {
    background:var(--code); color:var(--codefg); padding:2px 6px;
    border-radius:5px; font-family:ui-monospace,Menlo,Consolas,monospace;
    font-size:13.5px;
  }
  pre {
    background:var(--code); color:var(--codefg); padding:14px 16px;
    border-radius:10px; overflow:auto; border:1px solid var(--border);
    font-family:ui-monospace,Menlo,Consolas,monospace; font-size:13.5px;
    line-height:1.5;
  }
  pre code { background:none; padding:0; }
  ul, ol { margin:10px 0; padding-left:24px; }
  li { margin:4px 0; }
  strong { color:#fff; }
  table {
    width:100%; border-collapse:collapse; margin:16px 0; font-size:14.5px;
    background:var(--panel); border-radius:10px; overflow:hidden;
  }
  th, td { padding:10px 14px; text-align:left; border-bottom:1px solid var(--border); }
  th { background:var(--panel2); color:#fff; font-weight:600; }
  tr:last-child td { border-bottom:none; }
  td code, th code { font-size:13px; }
  hr { border:none; border-top:1px solid var(--border); margin:36px 0; }
  .toc {
    background:var(--panel); border:1px solid var(--border); border-radius:10px;
    padding:16px 20px; margin-bottom:32px;
  }
  .toc h3 { margin:0 0 10px; font-size:14px; color:var(--muted);
            text-transform:uppercase; letter-spacing:.05em; }
  .toc ol { margin:0; padding-left:20px; }
  .toc a { color:#93c5fd; text-decoration:none; }
  .toc a:hover { text-decoration:underline; }
  @media print {
    :root { --bg:#fff; --panel:#f5f6f8; --panel2:#eef0f3; --fg:#1a1a1a;
            --muted:#555; --border:#bbb; --code:#f0f0f0; --codefg:#222; }
    @page { size:A4; margin:16mm 15mm; }
    html, body { background:#fff; }
    body { font-size:10.5pt; line-height:1.5; }
    .wrap { max-width:none; padding:0; }
    * { -webkit-print-color-adjust:exact; print-color-adjust:exact; }
    a { color:#1d4ed8; text-decoration:none; }
    strong, h1, h2, th { color:#111 !important; }
    code { color:#222 !important; }
    .tag-q { background:#dbeafe !important; color:#1e40af !important; }
    .tag-a { background:#dcfce7 !important; color:#166534 !important; }
    .tag-f { background:#fef9c3 !important; color:#854d0e !important; }
    h1 { font-size:20pt; }
    h2 { font-size:14pt; margin-top:18pt; break-after:avoid; page-break-after:avoid; }
    .q, .a, .followup, blockquote, table, pre { break-inside:avoid; page-break-inside:avoid; }
    .q { break-after:avoid; page-break-after:avoid; }
    .toc { display:none; }
    tr, li { break-inside:avoid; }
  }"""


def slugify_anchor(text, idx):
    m = re.match(r"\s*(\d+)\.", text)
    if m:
        return "s" + m.group(1)
    if "apénd" in text.lower() or "apend" in text.lower() or "chuleta" in text.lower():
        return "apx"
    return "sec" + str(idx)


def md_inline(text):
    """Lightweight inline markdown (code, bold, italic, links) -> HTML.

    Does NOT run block-level parsing, so a heading like "0. Warm-up" is not
    turned into an <ol> list.
    """
    # Extract inline code spans first so their content is not further processed.
    spans = []

    def _stash(m):
        spans.append(m.group(1))
        return f"\x00{len(spans) - 1}\x00"

    text = re.sub(r"`([^`]+)`", _stash, text)
    text = _html.escape(text)
    text = re.sub(r"\[([^\]]+)\]\(([^)]+)\)", r'<a href="\2">\1</a>', text)
    text = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", text)
    text = re.sub(r"(?<!\*)\*(?!\*)([^*]+)\*(?!\*)", r"<em>\1</em>", text)

    def _unstash(m):
        return "<code>" + _html.escape(spans[int(m.group(1))]) + "</code>"

    text = re.sub(r"\x00(\d+)\x00", _unstash, text)
    return text.strip()


def strip_single_p(inner):
    """If HTML is a single <p>...</p>, unwrap it (matches existing files)."""
    s = inner.strip()
    if s.startswith("<p>") and s.endswith("</p>") and s.count("<p>") == 1:
        return s[3:-4].strip()
    return inner


def parse(md_text):
    lines = md_text.splitlines()
    title = ""
    subtitle_lines = []
    blocks = []  # list of dicts
    sections = []  # (anchor, label)

    i = 0
    n = len(lines)

    # Title
    while i < n:
        if lines[i].startswith("# "):
            title = lines[i][2:].strip()
            i += 1
            break
        i += 1

    # Subtitle: consume immediately-following non-empty non-blockquote lines
    while i < n and lines[i].strip() and not lines[i].startswith(">") \
            and not lines[i].startswith("#"):
        subtitle_lines.append(lines[i].strip())
        i += 1

    def flush_para(buf):
        text = "\n".join(buf).strip()
        if text:
            blocks.append({"type": "html", "html": md_lib.markdown(
                text, extensions=["fenced_code", "tables"])})

    para = []
    sec_idx = 0
    while i < n:
        line = lines[i]
        stripped = line.strip()

        # Blockquote group
        if stripped.startswith(">"):
            flush_para(para); para = []
            bq = []
            while i < n and lines[i].strip().startswith(">"):
                bq.append(lines[i].strip()[1:].lstrip())
                i += 1
            inner = md_lib.markdown("\n".join(bq), extensions=["fenced_code", "tables"])
            blocks.append({"type": "blockquote", "html": inner})
            continue

        # Heading (section)
        if stripped.startswith("## "):
            flush_para(para); para = []
            sec_idx += 1
            label = stripped[3:].strip()
            anchor = slugify_anchor(label, sec_idx)
            sections.append((anchor, label))
            blocks.append({"type": "h2", "anchor": anchor,
                           "html": md_inline(label)})
            i += 1
            continue

        # Q / A / Follow-up markers
        m_q = re.match(r"\*\*P\d*\.\*\*\s*(.*)", stripped)
        m_a = re.match(r"\*\*R\.\*\*\s*(.*)", stripped)
        m_f = re.match(r"\*Follow-?up:\*\s*(.*)", stripped)
        if m_q or m_a or m_f:
            flush_para(para); para = []
            kind = "q" if m_q else ("a" if m_a else "followup")
            first = (m_q or m_a or m_f).group(1)
            body = [first] if first else []
            i += 1
            # gather continuation lines until blank + next marker/heading
            while i < n:
                nxt = lines[i]
                s = nxt.strip()
                if s.startswith("## ") or s.startswith("> "):
                    break
                if re.match(r"\*\*P\d*\.\*\*", s) or re.match(r"\*\*R\.\*\*", s) \
                        or re.match(r"\*Follow-?up:\*", s):
                    break
                if s == "" and i + 1 < n:
                    nn = lines[i + 1].strip()
                    if re.match(r"\*\*P\d*\.\*\*", nn) or re.match(r"\*\*R\.\*\*", nn) \
                            or re.match(r"\*Follow-?up:\*", nn) or nn.startswith("## ") \
                            or nn.startswith("> "):
                        i += 1
                        break
                body.append(nxt)
                i += 1
            inner = md_lib.markdown("\n".join(body).strip(),
                                    extensions=["fenced_code", "tables"])
            blocks.append({"type": kind, "html": strip_single_p(inner)})
            continue

        if stripped == "---":
            flush_para(para); para = []
            blocks.append({"type": "hr"})
            i += 1
            continue

        para.append(line)
        i += 1

    flush_para(para)
    return title, " ".join(subtitle_lines), blocks, sections


def render_html(title, subtitle, blocks, sections):
    tags = {"q": ("tag-q", "P"), "a": ("tag-a", "R"),
            "followup": ("tag-f", "Follow-up")}
    parts = []
    for b in blocks:
        t = b["type"]
        if t == "h2":
            parts.append(f'  <h2 id="{b["anchor"]}">{b["html"]}</h2>')
        elif t == "blockquote":
            parts.append(f'  <blockquote>{b["html"]}</blockquote>')
        elif t == "hr":
            parts.append("  <hr>")
        elif t in tags:
            cls, label = tags[t]
            div = "followup" if t == "followup" else t
            tagcls = cls
            parts.append(
                f'  <div class="{div}"><span class="tag {tagcls}">{label}</span> '
                f'{b["html"]}</div>')
        else:
            parts.append(f'  {b["html"]}')

    toc = ""
    if sections:
        items = "\n".join(
            f'      <li><a href="#{a}">{_html.escape(l)}</a></li>'
            for a, l in sections)
        toc = ('  <nav class="toc">\n    <h3>Contenido</h3>\n    <ol>\n'
               f'{items}\n    </ol>\n  </nav>\n')

    sub = f'  <p class="subtitle">{_html.escape(subtitle)}</p>\n' if subtitle else ""
    body = "\n".join(parts)
    page_title = f"{title} (POS Microservices)" if title else "Simulación de Entrevista Técnica"
    return f"""<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{_html.escape(page_title)}</title>
<style>
{STYLE}
</style>
</head>
<body>
<div class="wrap">

  <h1>{_html.escape(title)}</h1>
{sub}{toc}
{body}

</div>
</body>
</html>
"""


def find_chrome():
    for name in ("google-chrome", "google-chrome-stable", "chromium",
                 "chromium-browser", "chrome"):
        p = shutil.which(name)
        if p:
            return p
    return None


def html_to_pdf(html_path, pdf_path):
    chrome = find_chrome()
    if not chrome:
        print("  ! No se encontro Chrome/Chromium; se omite el PDF.",
              file=sys.stderr)
        return False
    with tempfile.TemporaryDirectory() as tmp:
        cmd = [
            chrome, "--headless=new", "--disable-gpu", "--no-sandbox",
            f"--user-data-dir={tmp}",
            "--no-pdf-header-footer",
            f"--print-to-pdf={os.path.abspath(pdf_path)}",
            "file://" + os.path.abspath(html_path),
        ]
        r = subprocess.run(cmd, capture_output=True, text=True)
        if r.returncode != 0 or not os.path.exists(pdf_path):
            # retry with legacy headless flag
            cmd[1] = "--headless"
            r = subprocess.run(cmd, capture_output=True, text=True)
        if r.returncode != 0 or not os.path.exists(pdf_path):
            print("  ! Fallo al generar el PDF:\n" + r.stderr[-500:],
                  file=sys.stderr)
            return False
    return True


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("md", help="ruta al README_<TEMA>.md")
    ap.add_argument("--no-pdf", action="store_true", help="no generar PDF")
    args = ap.parse_args()

    md_path = args.md
    if not os.path.exists(md_path):
        sys.exit(f"No existe: {md_path}")
    base, _ = os.path.splitext(md_path)
    html_path = base + ".html"
    pdf_path = base + ".pdf"

    with open(md_path, encoding="utf-8") as f:
        md_text = f.read()

    title, subtitle, blocks, sections = parse(md_text)
    html_out = render_html(title, subtitle, blocks, sections)
    with open(html_path, "w", encoding="utf-8") as f:
        f.write(html_out)
    print(f"  HTML -> {html_path}  ({len(sections)} secciones)")

    if not args.no_pdf:
        if html_to_pdf(html_path, pdf_path):
            size = os.path.getsize(pdf_path)
            print(f"  PDF  -> {pdf_path}  ({size} bytes)")


if __name__ == "__main__":
    main()
