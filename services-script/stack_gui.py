#!/usr/bin/env python3
"""
stack_gui.py - Native desktop GUI to manage the POS microservices stack.

Built on GTK 3 via PyGObject (`gi`), which ships with most Linux desktops, so
there is no HTTP server and no browser involved: it opens a real desktop window.
It lets you:

  * Control tab   - buttons to bring phases / the whole stack / tooling up & down
                    (runs the same scripts in services-script/), with live output.
  * Logs tab      - logs of every saga-* container (docker logs).
  * Resources tab - live CPU / RAM usage per container with high-usage alerts.

All Docker interaction is identical to before; only the presentation layer
changed from a local web app to a native GTK window.

Usage:
    python3 services-script/stack_gui.py

Requires: python3, PyGObject (GTK 3), docker (+ docker compose) on PATH.
On Debian/Ubuntu PyGObject/GTK come from: apt install python3-gi gir1.2-gtk-3.0
"""

import json
import os
import subprocess
import threading

import gi

gi.require_version("Gtk", "3.0")
from gi.repository import Gtk, Gdk, GLib  # noqa: E402

# --- Paths -----------------------------------------------------------------
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(SCRIPT_DIR)

# --- Alert thresholds (percent) --------------------------------------------
CPU_ALERT = 85.0   # per-container CPU%
MEM_ALERT = 85.0   # per-container Mem%

# --- Whitelisted actions -> script + args ----------------------------------
# Only these actions can be triggered; nothing runs arbitrary commands.
ACTIONS = {
    "phase1-up":   ["phase1-up.sh"],
    "phase1-down": ["phase1-down.sh"],
    "phase2-up":   ["phase2-up.sh"],
    "phase2-down": ["phase2-down.sh"],
    "phase3-up":   ["phase3-up.sh"],
    "phase3-down": ["phase3-down.sh"],
    "up-all":      ["up-all.sh"],
    "down-all":    ["down-all.sh"],
    "jenkins-up":   ["jenkins-up.sh"],
    "jenkins-down": ["jenkins-down.sh"],
    "sonar-up":     ["sonar-up.sh"],
    "sonar-down":   ["sonar-down.sh"],
    "tooling-up":   ["tooling-up.sh"],
    "tooling-down": ["tooling-down.sh"],
}

# --- Compose services backing each Control card (container = "saga-<service>") --
# Used to show an up/down status message per card.
CARD_SERVICES = {
    "all":      ["postgres", "mongodb", "kafka", "redis",
                 "eureka-server", "api-gateway", "auth-service",
                 "stock-service", "venta-service", "despacho-service",
                 "pos-frontend", "ventas-mantenedor", "users-mantenedor"],
    "phase1":   ["postgres", "mongodb", "kafka", "redis"],
    "phase2":   ["eureka-server", "api-gateway", "auth-service",
                 "stock-service", "venta-service", "despacho-service"],
    "phase3":   ["pos-frontend", "ventas-mantenedor", "users-mantenedor"],
    "jenkins":  ["jenkins"],
    "sonar":    ["postgres-sonar", "sonarqube"],
}

# Serialize actions so two up/down runs don't clobber each other.
_action_lock = threading.Lock()


# --- Docker / action helpers (unchanged logic) -----------------------------
def _run(cmd, timeout=None):
    """Run a command, return (rc, combined_output)."""
    try:
        proc = subprocess.run(
            cmd, cwd=REPO_ROOT, capture_output=True, text=True, timeout=timeout
        )
        return proc.returncode, (proc.stdout or "") + (proc.stderr or "")
    except subprocess.TimeoutExpired:
        return 124, f"timeout after {timeout}s: {' '.join(cmd)}"
    except FileNotFoundError as exc:
        return 127, str(exc)


def _pct(s):
    """Parse '12.34%' -> 12.34 (float)."""
    try:
        return round(float(str(s).strip().rstrip("%")), 2)
    except (ValueError, AttributeError):
        return 0.0


def docker_stats():
    """Return a list of per-container stat dicts (saga-* only)."""
    rc, out = _run(
        ["docker", "stats", "--no-stream", "--format", "{{json .}}"], timeout=30
    )
    rows = []
    if rc != 0:
        return rows
    for line in out.splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            d = json.loads(line)
        except json.JSONDecodeError:
            continue
        name = d.get("Name", "")
        if not name.startswith("saga-"):
            continue
        cpu = _pct(d.get("CPUPerc", "0%"))
        mem = _pct(d.get("MemPerc", "0%"))
        rows.append({
            "name": name,
            "cpu": cpu,
            "mem": mem,
            "mem_usage": d.get("MemUsage", ""),
            "pids": d.get("PIDs", ""),
            "alert": cpu >= CPU_ALERT or mem >= MEM_ALERT,
        })
    rows.sort(key=lambda r: r["name"])
    return rows


def docker_ps():
    """Return list of {name,status,state} for saga-* containers."""
    rc, out = _run(
        ["docker", "ps", "-a", "--filter", "name=saga-",
         "--format", "{{.Names}}\t{{.Status}}\t{{.State}}"],
        timeout=20,
    )
    rows = []
    if rc != 0:
        return rows
    for line in out.splitlines():
        parts = line.split("\t")
        if len(parts) >= 3 and parts[0].startswith("saga-"):
            rows.append({"name": parts[0], "status": parts[1], "state": parts[2]})
    rows.sort(key=lambda r: r["name"])
    return rows


def docker_states():
    """Return {service_base_name: state} for all saga-* containers.

    The container name is "saga-<service>", so the key strips that prefix.
    'state' is docker's container state, e.g. 'running', 'exited', 'created'.
    Containers that don't exist simply won't appear in the dict.
    """
    states = {}
    for row in docker_ps():
        name = row["name"]
        base = name[5:] if name.startswith("saga-") else name
        states[base] = row["state"]
    return states


def docker_logs(container, tail=200):
    """Return recent logs for a single container."""
    if not container.startswith("saga-"):
        return "invalid container"
    rc, out = _run(
        ["docker", "logs", "--tail", str(tail), container], timeout=20
    )
    return out


def all_logs(tail=80):
    """Return {container: logs} for all saga-* containers."""
    result = {}
    for row in docker_ps():
        result[row["name"]] = docker_logs(row["name"], tail=tail)
    return result


def run_action(action):
    """Run a whitelisted script action; return (rc, output)."""
    spec = ACTIONS.get(action)
    if spec is None:
        return 2, f"unknown action: {action}"
    script = os.path.join(SCRIPT_DIR, spec[0])
    if not os.path.exists(script):
        return 2, f"script not found: {script}"
    cmd = ["bash", script] + spec[1:]
    with _action_lock:
        # up/down of the full stack can take minutes (image pulls, health waits).
        return _run(cmd, timeout=1800)


def run_action_streaming(action, on_line):
    """Run a whitelisted script action, streaming output line by line.

    `on_line(text)` is invoked for every chunk of output as it is produced
    (stdout and stderr merged), so the UI can show a live trace of what the
    containers are doing instead of one dump at the end.

    Returns the process return code (int). Validation failures (unknown action
    / missing script) are reported through `on_line` and return code 2.
    """
    spec = ACTIONS.get(action)
    if spec is None:
        on_line(f"unknown action: {action}\n")
        return 2
    script = os.path.join(SCRIPT_DIR, spec[0])
    if not os.path.exists(script):
        on_line(f"script not found: {script}\n")
        return 2
    cmd = ["bash", script] + spec[1:]
    with _action_lock:
        try:
            proc = subprocess.Popen(
                cmd, cwd=REPO_ROOT,
                stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                text=True, bufsize=1,  # line-buffered
            )
        except FileNotFoundError as exc:
            on_line(str(exc) + "\n")
            return 127
        # Read line by line as the script (and docker/compose under it) prints.
        assert proc.stdout is not None
        for line in proc.stdout:
            on_line(line)
        proc.stdout.close()
        return proc.wait()


def _bar_color(v, thr):
    """Return an (r, g, b) tuple for a usage bar given the value and threshold."""
    if v >= thr:
        return (0.94, 0.27, 0.27)      # red
    if v >= thr * 0.7:
        return (0.92, 0.70, 0.03)      # yellow
    return (0.13, 0.77, 0.37)          # green


# --- Main window -----------------------------------------------------------
class StackWindow(Gtk.Window):
    def __init__(self):
        super().__init__(title="POS Stack GUI")
        self.set_border_width(0)

        # Size the window to fit the screen's work area (minus panels/taskbar)
        # so the bottom of the window and the console never fall off-screen.
        width, height = self._fit_to_screen(920, 720)
        self.set_default_size(width, height)
        self.set_position(Gtk.WindowPosition.CENTER)

        self._action_buttons = []
        self._status_labels = {}   # card key -> Gtk.Label showing up/down state
        self._containers_loaded = False

        notebook = Gtk.Notebook()
        self.add(notebook)

        notebook.append_page(self._build_control_tab(), Gtk.Label(label="Control"))
        notebook.append_page(self._build_logs_tab(), Gtk.Label(label="Logs"))
        notebook.append_page(self._build_resources_tab(), Gtk.Label(label="Recursos"))

        # Periodic refresh timers (in the GTK main loop).
        GLib.timeout_add_seconds(4, self._tick_resources)
        GLib.timeout_add_seconds(5, self._tick_logs)
        GLib.timeout_add_seconds(5, self._tick_status)

        # Initial async loads.
        self._refresh_stats()
        self._refresh_status()

    @staticmethod
    def _fit_to_screen(want_w, want_h):
        """Clamp a desired window size to the current monitor's work area.

        Leaves a small margin so the window (and its bottom console) stays
        fully on-screen above panels/taskbars. Falls back to the requested
        size if the geometry can't be determined.
        """
        try:
            display = Gdk.Display.get_default()
            monitor = (display.get_primary_monitor()
                       or display.get_monitor(0))
            area = monitor.get_workarea()  # excludes panels where the WM reports it
            max_w = max(640, area.width - 80)
            max_h = max(480, area.height - 80)
            return min(want_w, max_w), min(want_h, max_h)
        except Exception:
            return want_w, want_h

    # ---- Control tab ------------------------------------------------------
    def _build_control_tab(self):
        flow = Gtk.FlowBox()
        flow.set_valign(Gtk.Align.START)
        flow.set_max_children_per_line(3)
        flow.set_min_children_per_line(1)
        flow.set_selection_mode(Gtk.SelectionMode.NONE)
        flow.set_row_spacing(12)
        flow.set_column_spacing(12)

        cards = [
            ("all", "Todo el stack", "", [("Subir todo", "up-all", True),
                                          ("Bajar todo", "down-all", False)]),
            ("phase1", "Fase 1 · Infraestructura", "postgres, mongodb, kafka, redis",
             [("Subir", "phase1-up", True), ("Bajar", "phase1-down", False)]),
            ("phase2", "Fase 2 · Backend", "eureka, gateway, auth, stock, venta, despacho",
             [("Subir", "phase2-up", True), ("Bajar", "phase2-down", False)]),
            ("phase3", "Fase 3 · Frontends", "pos, ventas-mantenedor, users-mantenedor",
             [("Subir", "phase3-up", True), ("Bajar", "phase3-down", False)]),
            ("jenkins", "Jenkins", "CI (http://localhost:8888)",
             [("Subir", "jenkins-up", True), ("Bajar", "jenkins-down", False)]),
            ("sonar", "SonarQube", "calidad (http://localhost:9000)",
             [("Subir", "sonar-up", True), ("Bajar", "sonar-down", False)]),
        ]

        for key, title, subtitle, buttons in cards:
            flow.add(self._make_card(key, title, subtitle, buttons))

        # Cards live in their own scroller so they never push the console
        # off-screen; the whole tab is a vertical paned (cards over console).
        cards_scroll = Gtk.ScrolledWindow()
        cards_scroll.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.AUTOMATIC)
        cards_scroll.add(flow)

        console_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=4)
        con_label = Gtk.Label(label="Salida", xalign=0)
        console_box.pack_start(con_label, False, False, 0)

        self.console = Gtk.TextView()
        self.console.set_editable(False)
        self.console.set_cursor_visible(False)
        self.console.set_monospace(True)
        self.console.set_wrap_mode(Gtk.WrapMode.WORD_CHAR)
        self.console_buf = self.console.get_buffer()
        self.console_buf.set_text("Listo. Elige una acción arriba.")

        con_scroll = Gtk.ScrolledWindow()
        # Vertical scrollbar always available so long traces are scrollable;
        # horizontal only when needed (lines mostly wrap).
        con_scroll.set_policy(Gtk.PolicyType.AUTOMATIC, Gtk.PolicyType.ALWAYS)
        con_scroll.set_min_content_height(160)
        con_scroll.set_shadow_type(Gtk.ShadowType.IN)
        con_scroll.add(self.console)
        # Keep a handle so we can auto-scroll to the bottom on new output.
        self._con_scroll = con_scroll
        console_box.pack_start(con_scroll, True, True, 0)

        paned = Gtk.Paned(orientation=Gtk.Orientation.VERTICAL)
        paned.pack1(cards_scroll, resize=True, shrink=True)
        paned.pack2(console_box, resize=True, shrink=False)
        paned.set_position(360)  # initial split; user can drag it

        outer = Gtk.Box(orientation=Gtk.Orientation.VERTICAL)
        outer.set_border_width(12)
        outer.pack_start(paned, True, True, 0)
        return outer

    def _make_card(self, key, title, subtitle, buttons):
        frame = Gtk.Frame()
        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=8)
        box.set_border_width(12)

        t = Gtk.Label(xalign=0)
        t.set_markup(f"<b>{GLib.markup_escape_text(title)}</b>")
        box.pack_start(t, False, False, 0)

        if subtitle:
            s = Gtk.Label(label=subtitle, xalign=0)
            s.get_style_context().add_class("dim-label")
            s.set_line_wrap(True)
            box.pack_start(s, False, False, 0)

        # Status message (up/down). Filled in by _refresh_status().
        status = Gtk.Label(xalign=0)
        status.set_markup("<small>Estado: comprobando…</small>")
        self._status_labels[key] = status
        box.pack_start(status, False, False, 0)

        btnrow = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
        btnrow.set_homogeneous(True)
        for label, action, is_up in buttons:
            btn = Gtk.Button(label=label)
            style = btn.get_style_context()
            style.add_class("suggested-action" if is_up else "destructive-action")
            btn.connect("clicked", self._on_action_clicked, action)
            self._action_buttons.append(btn)
            btnrow.pack_start(btn, True, True, 0)
        box.pack_start(btnrow, False, False, 0)

        frame.add(box)
        return frame

    def _on_action_clicked(self, _btn, action):
        for b in self._action_buttons:
            b.set_sensitive(False)
        self._set_console(
            f"▶ Ejecutando: {action} ...\n"
            "(traza en vivo — esto puede tardar varios minutos)\n"
            + "─" * 60 + "\n"
        )

        def worker():
            # Stream each line to the console as the script/containers produce it.
            rc = run_action_streaming(
                action,
                lambda text: GLib.idle_add(self._append_console, text),
            )
            GLib.idle_add(self._action_done, action, rc)

        threading.Thread(target=worker, daemon=True).start()

    def _action_done(self, action, rc):
        tail = ("\n" + "─" * 60 + "\n"
                + ("✔ OK (rc=0)" if rc == 0 else f"✖ Terminó con rc={rc}") + "\n")
        self._append_console(tail)
        for b in self._action_buttons:
            b.set_sensitive(True)
        # An up/down just ran: refresh the per-card status messages.
        self._refresh_status()
        return False

    # ---- Per-card up/down status -----------------------------------------
    def _refresh_status(self):
        def worker():
            states = docker_states()
            GLib.idle_add(self._render_status, states)

        threading.Thread(target=worker, daemon=True).start()

    def _render_status(self, states):
        for key, label in self._status_labels.items():
            services = CARD_SERVICES.get(key, [])
            total = len(services)
            running = sum(1 for s in services if states.get(s) == "running")

            if total and running == total:
                color, icon, word = "#22c55e", "▲", "Arriba"
            elif running == 0:
                color, icon, word = "#ef4444", "▼", "Abajo"
            else:
                color, icon, word = "#eab308", "◼", "Parcial"

            label.set_markup(
                f"<small><span foreground='{color}'>{icon} {word}</span> "
                f"({running}/{total} servicios)</small>"
            )
        return False

    def _tick_status(self):
        self._refresh_status()
        return True  # keep the timer running

    def _set_console(self, text):
        self.console_buf.set_text(text)

    def _append_console(self, text):
        end = self.console_buf.get_end_iter()
        self.console_buf.insert(end, text)
        # Auto-scroll to the bottom so the newest trace line stays visible.
        # Reuse a single persistent mark instead of creating one per line.
        mark = self.console_buf.get_mark("scroll_bottom")
        if mark is None:
            mark = self.console_buf.create_mark(
                "scroll_bottom", self.console_buf.get_end_iter(), False
            )
        else:
            self.console_buf.move_mark(mark, self.console_buf.get_end_iter())
        self.console.scroll_to_mark(mark, 0.0, True, 0.0, 1.0)
        return False  # so it can be used directly as a GLib.idle_add callback

    # ---- Logs tab ---------------------------------------------------------
    def _build_logs_tab(self):
        outer = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=10)
        outer.set_border_width(16)

        selrow = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=10)

        selrow.pack_start(Gtk.Label(label="Contenedor:"), False, False, 0)
        self.log_container = Gtk.ComboBoxText()
        self.log_container.append("__all__", "Todos")
        self.log_container.set_active_id("__all__")
        self.log_container.connect("changed", lambda *_: self._refresh_logs())
        selrow.pack_start(self.log_container, False, False, 0)

        selrow.pack_start(Gtk.Label(label="Líneas:"), False, False, 0)
        self.log_tail = Gtk.ComboBoxText()
        for v in ("80", "200", "500"):
            self.log_tail.append(v, v)
        self.log_tail.set_active_id("200")
        self.log_tail.connect("changed", lambda *_: self._refresh_logs())
        selrow.pack_start(self.log_tail, False, False, 0)

        self.log_auto = Gtk.CheckButton(label="Auto-refrescar (5s)")
        self.log_auto.set_active(True)
        selrow.pack_start(self.log_auto, False, False, 0)

        refresh_btn = Gtk.Button(label="Refrescar")
        refresh_btn.connect("clicked", lambda *_: self._refresh_logs())
        selrow.pack_start(refresh_btn, False, False, 0)

        outer.pack_start(selrow, False, False, 0)

        self.logbox = Gtk.TextView()
        self.logbox.set_editable(False)
        self.logbox.set_cursor_visible(False)
        self.logbox.set_monospace(True)
        self.logbox.set_wrap_mode(Gtk.WrapMode.WORD_CHAR)
        self.logbox_buf = self.logbox.get_buffer()
        self.logbox_buf.set_text("Cargando...")

        scroll = Gtk.ScrolledWindow()
        scroll.set_policy(Gtk.PolicyType.AUTOMATIC, Gtk.PolicyType.AUTOMATIC)
        scroll.add(self.logbox)
        outer.pack_start(scroll, True, True, 0)

        return outer

    def _load_containers(self, on_done=None):
        def worker():
            rows = docker_ps()
            GLib.idle_add(self._populate_containers, rows, on_done)

        threading.Thread(target=worker, daemon=True).start()

    def _populate_containers(self, rows, on_done):
        current = self.log_container.get_active_id()
        self.log_container.remove_all()
        self.log_container.append("__all__", "Todos")
        for c in rows:
            short = c["name"][5:] if c["name"].startswith("saga-") else c["name"]
            self.log_container.append(c["name"], f"{short} ({c['state']})")
        self.log_container.set_active_id(current or "__all__")
        self._containers_loaded = True
        if on_done:
            on_done()
        return False

    def _refresh_logs(self):
        if not self._containers_loaded:
            self._load_containers(on_done=self._refresh_logs)
            return
        name = self.log_container.get_active_id() or "__all__"
        try:
            tail = int(self.log_tail.get_active_id() or "200")
        except ValueError:
            tail = 200

        def worker():
            if name == "__all__":
                logs = all_logs(tail=min(tail, 120))
                text = "\n".join(
                    f"===== {k} =====\n{v or '(sin logs)'}\n"
                    for k, v in logs.items()
                ) or "(sin contenedores)"
            else:
                text = docker_logs(name, tail=tail) or "(sin logs)"
            GLib.idle_add(self._set_logbox, text)

        threading.Thread(target=worker, daemon=True).start()

    def _set_logbox(self, text):
        self.logbox_buf.set_text(text)
        return False

    # ---- Resources tab ----------------------------------------------------
    def _build_resources_tab(self):
        outer = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=10)
        outer.set_border_width(16)

        self.alert_banner = Gtk.Label(xalign=0)
        self.alert_banner.set_no_show_all(True)
        self.alert_banner.get_style_context().add_class("error")
        outer.pack_start(self.alert_banner, False, False, 0)

        self.res_hint = Gtk.Label(xalign=0)
        self.res_hint.get_style_context().add_class("dim-label")
        self.res_hint.set_text(
            f"Umbral de alerta: CPU ≥ {CPU_ALERT}%  /  RAM ≥ {MEM_ALERT}%"
        )
        outer.pack_start(self.res_hint, False, False, 0)

        self.res_flow = Gtk.FlowBox()
        self.res_flow.set_valign(Gtk.Align.START)
        self.res_flow.set_max_children_per_line(3)
        self.res_flow.set_min_children_per_line(1)
        self.res_flow.set_selection_mode(Gtk.SelectionMode.NONE)
        self.res_flow.set_row_spacing(12)
        self.res_flow.set_column_spacing(12)

        scroll = Gtk.ScrolledWindow()
        scroll.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.AUTOMATIC)
        scroll.add(self.res_flow)
        outer.pack_start(scroll, True, True, 0)

        return outer

    def _refresh_stats(self):
        def worker():
            stats = docker_stats()
            GLib.idle_add(self._render_stats, stats)

        threading.Thread(target=worker, daemon=True).start()

    def _render_stats(self, stats):
        for child in self.res_flow.get_children():
            self.res_flow.remove(child)

        alerts = []
        if not stats:
            lbl = Gtk.Label(label="No hay contenedores en ejecución.")
            lbl.get_style_context().add_class("dim-label")
            self.res_flow.add(lbl)
        for s in stats:
            if s["alert"]:
                alerts.append(s["name"])
            self.res_flow.add(self._make_stat_card(s))

        if alerts:
            names = ", ".join(a[5:] if a.startswith("saga-") else a for a in alerts)
            self.alert_banner.set_text(f"⚠ Alto consumo de recursos en: {names}")
            self.alert_banner.show()
        else:
            self.alert_banner.hide()

        self.res_flow.show_all()
        return False

    def _make_stat_card(self, s):
        short = s["name"][5:] if s["name"].startswith("saga-") else s["name"]
        frame = Gtk.Frame()
        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=6)
        box.set_border_width(12)

        title = Gtk.Label(xalign=0)
        title.set_markup(f"<b>{GLib.markup_escape_text(short)}</b>")
        box.pack_start(title, False, False, 0)

        box.pack_start(self._metric_row("CPU", s["cpu"], CPU_ALERT), False, False, 0)
        box.pack_start(self._metric_row("RAM", s["mem"], MEM_ALERT), False, False, 0)

        foot = Gtk.Label(xalign=0)
        foot.get_style_context().add_class("dim-label")
        foot.set_text(f"{s['mem_usage']}   ·   PIDs: {s['pids']}")
        box.pack_start(foot, False, False, 0)

        frame.add(box)
        return frame

    def _metric_row(self, label, value, threshold):
        row = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=2)
        header = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL)
        header.pack_start(Gtk.Label(label=label, xalign=0), True, True, 0)
        val = Gtk.Label(label=f"{value:.1f}%", xalign=1)
        header.pack_start(val, False, False, 0)
        row.pack_start(header, False, False, 0)

        bar = Gtk.ProgressBar()
        bar.set_fraction(min(value, 100.0) / 100.0)
        row.pack_start(bar, False, False, 0)
        return row

    # ---- Timers -----------------------------------------------------------
    def _tick_resources(self):
        # Only refresh when the Resources tab is visible enough to matter;
        # refreshing unconditionally is cheap and keeps things simple.
        self._refresh_stats()
        return True  # keep the timer running

    def _tick_logs(self):
        if self.log_auto.get_active():
            self._refresh_logs()
        return True


def main():
    win = StackWindow()
    win.connect("destroy", Gtk.main_quit)
    win.show_all()
    # Alert banner starts hidden despite show_all() due to set_no_show_all(True).
    Gtk.main()


if __name__ == "__main__":
    main()
