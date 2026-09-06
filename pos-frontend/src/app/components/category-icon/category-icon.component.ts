import { Component, Input, HostBinding, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * Animated SVG icon for product categories.
 * Each category has a unique gradient + animation personality:
 *  - laptop: floating with screen glow pulse
 *  - monitor: pixel pulse waves
 *  - mouse: click ripple
 *  - teclado: key bounce
 *  - auricular: music wave
 *  - gpu: fan spin
 *  - ssd: data flow shimmer
 *  - ram: electric pulse
 *  - tablet: tap ripple
 *  - cable: connector wiggle
 *  - silla: rocking
 *  - camara: shutter flash
 *  - default: gentle float
 */
@Component({
  selector: 'app-category-icon',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="cat-icon-wrapper" [ngClass]="'cat-' + category" [attr.aria-label]="category">
      <svg viewBox="0 0 64 64" class="cat-svg" xmlns="http://www.w3.org/2000/svg">
        <defs>
          <linearGradient [attr.id]="'g-' + uid + '-' + category" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" [attr.stop-color]="gradientStart"/>
            <stop offset="100%" [attr.stop-color]="gradientEnd"/>
          </linearGradient>
          <filter [attr.id]="'glow-' + uid + '-' + category" x="-50%" y="-50%" width="200%" height="200%">
            <feGaussianBlur stdDeviation="1.5" result="blur"/>
            <feMerge>
              <feMergeNode in="blur"/>
              <feMergeNode in="SourceGraphic"/>
            </feMerge>
          </filter>
        </defs>
        <g [attr.filter]="'url(#glow-' + uid + '-' + category + ')'" [attr.fill]="'url(#g-' + uid + '-' + category + ')'">
          @switch (category) {
            @case ('laptop') {
              <!-- Laptop with pulsing screen -->
              <g class="laptop-group">
                <rect class="laptop-base" x="8" y="42" width="48" height="6" rx="2"/>
                <rect class="laptop-screen" x="12" y="14" width="40" height="28" rx="3"/>
                <rect class="laptop-screen-inner" x="15" y="17" width="34" height="22" rx="1" fill="rgba(255,255,255,0.15)"/>
                <circle class="laptop-dot" cx="32" cy="46" r="1" fill="rgba(255,255,255,0.6)"/>
              </g>
            }
            @case ('monitor') {
              <!-- Monitor with scan lines -->
              <g class="monitor-group">
                <rect class="monitor-frame" x="8" y="10" width="48" height="32" rx="3"/>
                <rect class="monitor-screen" x="11" y="13" width="42" height="26" rx="1" fill="rgba(255,255,255,0.12)"/>
                <rect class="monitor-stand" x="28" y="42" width="8" height="6"/>
                <rect class="monitor-base" x="20" y="48" width="24" height="3" rx="1"/>
                <line class="scan-line" x1="11" y1="13" x2="53" y2="13" stroke="rgba(255,255,255,0.4)" stroke-width="1"/>
              </g>
            }
            @case ('mouse') {
              <!-- Mouse with scroll wheel -->
              <g class="mouse-group">
                <ellipse class="mouse-body" cx="32" cy="34" rx="14" ry="20"/>
                <line class="mouse-divider" x1="32" y1="18" x2="32" y2="32" stroke="rgba(0,0,0,0.25)" stroke-width="1.5"/>
                <rect class="mouse-wheel" x="30" y="20" width="4" height="6" rx="1.5" fill="rgba(255,255,255,0.7)"/>
                <circle class="mouse-click-ripple" cx="32" cy="42" r="2" fill="none" stroke="rgba(255,255,255,0.5)" stroke-width="1"/>
                <circle class="mouse-click-ripple mouse-click-ripple-2" cx="32" cy="42" r="2" fill="none" stroke="rgba(255,255,255,0.5)" stroke-width="1"/>
              </g>
            }
            @case ('teclado') {
              <!-- Keyboard with bouncing keys -->
              <g class="keyboard-group">
                <rect class="kb-base" x="6" y="22" width="52" height="22" rx="3"/>
                @for (i of [0,1,2,3,4,5,6,7,8,9,10,11]; track i) {
                  <rect class="kb-key" [attr.x]="8 + (i % 6) * 8" [attr.y]="26 + Math.floor(i / 6) * 6" width="6" height="4" rx="1" fill="rgba(255,255,255,0.4)"/>
                }
              </g>
            }
            @case ('auricular') {
              <!-- Headphones with sound waves -->
              <g class="headphones-group">
                <path class="hp-band" d="M 14 36 Q 14 14 32 14 Q 50 14 50 36" fill="none" stroke-width="4" stroke-linecap="round"/>
                <rect class="hp-ear hp-ear-l" x="10" y="32" width="10" height="16" rx="3"/>
                <rect class="hp-ear hp-ear-r" x="44" y="32" width="10" height="16" rx="3"/>
                <path class="sound-wave sound-wave-1" d="M 26 40 Q 28 44 26 48" fill="none" stroke="rgba(255,255,255,0.6)" stroke-width="1.5"/>
                <path class="sound-wave sound-wave-2" d="M 32 40 Q 34 44 32 48" fill="none" stroke="rgba(255,255,255,0.6)" stroke-width="1.5"/>
                <path class="sound-wave sound-wave-3" d="M 38 40 Q 40 44 38 48" fill="none" stroke="rgba(255,255,255,0.6)" stroke-width="1.5"/>
              </g>
            }
            @case ('gpu') {
              <!-- GPU with spinning fan -->
              <g class="gpu-group">
                <rect class="gpu-body" x="6" y="20" width="52" height="24" rx="3"/>
                <circle class="gpu-fan" cx="32" cy="32" r="9" fill="rgba(255,255,255,0.15)"/>
                <g class="gpu-fan-blades">
                  <ellipse cx="32" cy="32" rx="2" ry="7" fill="rgba(255,255,255,0.7)"/>
                  <ellipse cx="32" cy="32" rx="2" ry="7" fill="rgba(255,255,255,0.7)" transform="rotate(120 32 32)"/>
                  <ellipse cx="32" cy="32" rx="2" ry="7" fill="rgba(255,255,255,0.7)" transform="rotate(240 32 32)"/>
                </g>
                <circle class="gpu-hub" cx="32" cy="32" r="2" fill="rgba(255,255,255,0.9)"/>
                <rect class="gpu-pin" x="10" y="26" width="3" height="3" fill="rgba(255,255,255,0.5)"/>
                <rect class="gpu-pin" x="10" y="32" width="3" height="3" fill="rgba(255,255,255,0.5)"/>
              </g>
            }
            @case ('ssd') {
              <!-- SSD with data flow -->
              <g class="ssd-group">
                <rect class="ssd-body" x="8" y="20" width="48" height="24" rx="3"/>
                <rect class="ssd-label" x="12" y="24" width="20" height="16" rx="1" fill="rgba(255,255,255,0.25)"/>
                <circle class="ssd-led ssd-led-1" cx="46" cy="28" r="1.5" fill="#10b981"/>
                <circle class="ssd-led ssd-led-2" cx="46" cy="34" r="1.5" fill="#10b981"/>
                <path class="data-flow" d="M 36 32 L 44 32" stroke="rgba(255,255,255,0.8)" stroke-width="1.5" stroke-linecap="round"/>
                <path class="data-flow data-flow-2" d="M 36 32 L 44 32" stroke="rgba(255,255,255,0.8)" stroke-width="1.5" stroke-linecap="round"/>
              </g>
            }
            @case ('ram') {
              <!-- RAM stick with electric pulse -->
              <g class="ram-group">
                <rect class="ram-body" x="14" y="10" width="36" height="44" rx="2"/>
                <rect class="ram-strip" x="18" y="14" width="28" height="6" fill="rgba(255,255,255,0.4)"/>
                <rect class="ram-pins" x="20" y="46" width="2" height="6"/>
                <rect class="ram-pins" x="24" y="46" width="2" height="6"/>
                <rect class="ram-pins" x="28" y="46" width="2" height="6"/>
                <rect class="ram-pins" x="32" y="46" width="2" height="6"/>
                <rect class="ram-pins" x="36" y="46" width="2" height="6"/>
                <rect class="ram-pins" x="40" y="46" width="2" height="6"/>
                <path class="electric-pulse" d="M 22 30 L 26 30 L 28 26 L 30 34 L 32 28 L 34 32 L 38 32 L 42 32" fill="none" stroke="rgba(255,255,255,0.9)" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
              </g>
            }
            @case ('tablet') {
              <!-- Tablet with tap ripple -->
              <g class="tablet-group">
                <rect class="tablet-body" x="14" y="8" width="36" height="48" rx="4"/>
                <rect class="tablet-screen" x="17" y="12" width="30" height="38" rx="1" fill="rgba(255,255,255,0.15)"/>
                <circle class="tablet-btn" cx="32" cy="53" r="1.5" fill="rgba(255,255,255,0.5)"/>
                <circle class="tap-ripple" cx="32" cy="30" r="6" fill="none" stroke="rgba(255,255,255,0.6)" stroke-width="1"/>
                <circle class="tap-ripple tap-ripple-2" cx="32" cy="30" r="6" fill="none" stroke="rgba(255,255,255,0.6)" stroke-width="1"/>
              </g>
            }
            @case ('cable') {
              <!-- Cable with connector wiggle -->
              <g class="cable-group">
                <path class="cable-wire" d="M 12 18 Q 24 18 24 30 Q 24 42 36 42 Q 48 42 48 50" fill="none" stroke-width="4" stroke-linecap="round"/>
                <rect class="cable-plug cable-plug-1" x="8" y="14" width="8" height="8" rx="2"/>
                <rect class="cable-plug cable-plug-2" x="48" y="46" width="8" height="8" rx="2"/>
                <circle class="cable-spark" cx="36" cy="42" r="1.5" fill="rgba(255,255,255,0.9)"/>
              </g>
            }
            @case ('silla') {
              <!-- Office chair with rocking motion -->
              <g class="chair-group">
                <rect class="chair-back" x="22" y="10" width="20" height="20" rx="4"/>
                <rect class="chair-seat" x="18" y="28" width="28" height="8" rx="2"/>
                <rect class="chair-pole" x="30" y="36" width="4" height="10"/>
                <line class="chair-base chair-base-1" x1="22" y1="50" x2="42" y2="50" stroke-width="3" stroke-linecap="round"/>
                <circle class="chair-wheel chair-wheel-1" cx="22" cy="52" r="2"/>
                <circle class="chair-wheel chair-wheel-2" cx="32" cy="52" r="2"/>
                <circle class="chair-wheel chair-wheel-3" cx="42" cy="52" r="2"/>
              </g>
            }
            @case ('camara') {
              <!-- Camera with shutter flash -->
              <g class="camera-group">
                <rect class="cam-body" x="8" y="20" width="48" height="28" rx="3"/>
                <rect class="cam-top" x="22" y="14" width="20" height="8" rx="2"/>
                <circle class="cam-lens" cx="32" cy="34" r="10" fill="rgba(255,255,255,0.2)"/>
                <circle class="cam-lens-inner" cx="32" cy="34" r="5" fill="rgba(255,255,255,0.5)"/>
                <circle class="cam-flash" cx="48" cy="26" r="2" fill="#fef3c7"/>
                <circle class="cam-flash-burst" cx="48" cy="26" r="6" fill="none" stroke="#fef3c7" stroke-width="1"/>
              </g>
            }
            @case ('micro') {
              <!-- Microphone with sound waves -->
              <g class="mic-group">
                <rect class="mic-capsule" x="26" y="12" width="12" height="22" rx="6"/>
                <path class="mic-arc" d="M 18 30 Q 18 44 32 44 Q 46 44 46 30" fill="none" stroke-width="3" stroke-linecap="round"/>
                <line class="mic-stand" x1="32" y1="44" x2="32" y2="52" stroke-width="3" stroke-linecap="round"/>
                <line class="mic-base" x1="24" y1="52" x2="40" y2="52" stroke-width="3" stroke-linecap="round"/>
                <line class="mic-wave mic-wave-1" x1="14" y1="26" x2="14" y2="34" stroke-width="1.5" stroke-linecap="round"/>
                <line class="mic-wave mic-wave-2" x1="10" y1="24" x2="10" y2="36" stroke-width="1.5" stroke-linecap="round"/>
                <line class="mic-wave mic-wave-3" x1="50" y1="24" x2="50" y2="36" stroke-width="1.5" stroke-linecap="round"/>
                <line class="mic-wave mic-wave-4" x1="54" y1="26" x2="54" y2="34" stroke-width="1.5" stroke-linecap="round"/>
              </g>
            }
            @case ('pendrive') {
              <!-- USB stick with blink -->
              <g class="usb-group">
                <rect class="usb-body" x="20" y="20" width="24" height="32" rx="3"/>
                <rect class="usb-connector" x="24" y="48" width="16" height="8"/>
                <rect class="usb-connector" x="26" y="52" width="4" height="4"/>
                <rect class="usb-connector" x="34" y="52" width="4" height="4"/>
                <circle class="usb-led" cx="32" cy="30" r="2" fill="#10b981"/>
                <line class="usb-line" x1="24" y1="38" x2="40" y2="38" stroke="rgba(255,255,255,0.3)" stroke-width="0.8"/>
                <line class="usb-line" x1="24" y1="42" x2="40" y2="42" stroke="rgba(255,255,255,0.3)" stroke-width="0.8"/>
              </g>
            }
            @case ('impresora') {
              <!-- Printer with paper feed -->
              <g class="printer-group">
                <rect class="printer-top" x="10" y="20" width="44" height="14" rx="2"/>
                <rect class="printer-base" x="14" y="34" width="36" height="14" rx="2"/>
                <rect class="printer-paper" x="20" y="12" width="24" height="12" rx="1" fill="rgba(255,255,255,0.3)"/>
                <line class="printer-line" x1="22" y1="16" x2="42" y2="16" stroke="rgba(0,0,0,0.3)" stroke-width="0.6"/>
                <line class="printer-line" x1="22" y1="19" x2="42" y2="19" stroke="rgba(0,0,0,0.3)" stroke-width="0.6"/>
                <circle class="printer-led" cx="44" cy="27" r="1.5" fill="#10b981"/>
                <rect class="printer-tray" x="18" y="48" width="28" height="6" rx="1"/>
              </g>
            }
            @case ('dock') {
              <!-- Docking station -->
              <g class="dock-group">
                <rect class="dock-body" x="8" y="22" width="48" height="20" rx="3"/>
                <rect class="dock-port" x="12" y="28" width="6" height="8" rx="1" fill="rgba(0,0,0,0.3)"/>
                <rect class="dock-port" x="22" y="28" width="6" height="8" rx="1" fill="rgba(0,0,0,0.3)"/>
                <rect class="dock-port" x="32" y="28" width="6" height="8" rx="1" fill="rgba(0,0,0,0.3)"/>
                <rect class="dock-port" x="42" y="28" width="10" height="8" rx="1" fill="rgba(0,0,0,0.3)"/>
                <circle class="dock-led" cx="18" cy="36" r="1" fill="#10b981"/>
                <circle class="dock-led" cx="28" cy="36" r="1" fill="#10b981"/>
              </g>
            }
            @case ('router') {
              <!-- Router with signal waves -->
              <g class="router-group">
                <rect class="router-body" x="8" y="32" width="48" height="14" rx="3"/>
                <rect class="router-ant" x="14" y="14" width="2" height="18" rx="1"/>
                <rect class="router-ant" x="48" y="14" width="2" height="18" rx="1"/>
                <path class="signal-wave signal-wave-1" d="M 18 14 Q 15 10 15 6" fill="none" stroke-width="1.5" stroke-linecap="round"/>
                <path class="signal-wave signal-wave-2" d="M 22 14 Q 19 8 19 4" fill="none" stroke-width="1.5" stroke-linecap="round"/>
                <path class="signal-wave signal-wave-1" d="M 46 14 Q 49 10 49 6" fill="none" stroke-width="1.5" stroke-linecap="round"/>
                <path class="signal-wave signal-wave-2" d="M 42 14 Q 45 8 45 4" fill="none" stroke-width="1.5" stroke-linecap="round"/>
                <circle class="router-led" cx="14" cy="40" r="1.2" fill="#10b981"/>
                <circle class="router-led" cx="20" cy="40" r="1.2" fill="#10b981"/>
                <circle class="router-led" cx="26" cy="40" r="1.2" fill="#10b981"/>
              </g>
            }
            @case ('switch') {
              <!-- Network switch with blinking ports -->
              <g class="switch-group">
                <rect class="switch-body" x="6" y="24" width="52" height="16" rx="2"/>
                @for (i of [0,1,2,3,4,5,6,7,8,9,10,11]; track i) {
                  <rect class="switch-port" [attr.x]="10 + i * 4" y="30" width="3" height="4" rx="0.5" fill="rgba(0,0,0,0.4)"/>
                  <circle class="switch-led" [attr.cx]="11.5 + i * 4" cy="36" r="0.7" fill="#10b981"/>
                }
              </g>
            }
            @case ('nas') {
              <!-- NAS storage with drive bays -->
              <g class="nas-group">
                <rect class="nas-body" x="8" y="14" width="48" height="36" rx="3"/>
                <rect class="nas-bay" x="12" y="18" width="40" height="8" rx="1" fill="rgba(255,255,255,0.15)"/>
                <rect class="nas-bay" x="12" y="28" width="40" height="8" rx="1" fill="rgba(255,255,255,0.15)"/>
                <rect class="nas-bay" x="12" y="38" width="40" height="8" rx="1" fill="rgba(255,255,255,0.15)"/>
                <circle class="nas-led nas-led-1" cx="48" cy="22" r="1" fill="#10b981"/>
                <circle class="nas-led nas-led-2" cx="48" cy="32" r="1" fill="#10b981"/>
                <circle class="nas-led nas-led-3" cx="48" cy="42" r="1" fill="#10b981"/>
              </g>
            }
            @case ('ups') {
              <!-- UPS battery with charging -->
              <g class="ups-group">
                <rect class="ups-body" x="8" y="20" width="48" height="28" rx="3"/>
                <rect class="ups-bolt" x="28" y="26" width="8" height="16" fill="rgba(255,255,255,0.3)"/>
                <path class="ups-bolt-shape" d="M 32 26 L 28 34 L 31 34 L 30 42 L 36 32 L 33 32 L 34 26 Z" fill="rgba(255,255,255,0.9)"/>
                <circle class="ups-led" cx="48" cy="26" r="1.2" fill="#10b981"/>
                <rect class="ups-level" x="12" y="44" width="40" height="2" rx="1" fill="rgba(0,0,0,0.3)"/>
                <rect class="ups-level-fill" x="12" y="44" width="28" height="2" rx="1" fill="#10b981"/>
              </g>
            }
            @case ('webcam') {
              <!-- Webcam with recording -->
              <g class="webcam-group">
                <circle class="webcam-body" cx="32" cy="32" r="14"/>
                <circle class="webcam-lens" cx="32" cy="32" r="8" fill="rgba(0,0,0,0.3)"/>
                <circle class="webcam-lens-inner" cx="32" cy="32" r="4" fill="rgba(255,255,255,0.4)"/>
                <circle class="webcam-rec" cx="44" cy="22" r="2" fill="#ef4444"/>
                <circle class="webcam-rec-pulse" cx="44" cy="22" r="2" fill="none" stroke="#ef4444" stroke-width="0.8"/>
                <rect class="webcam-clip" x="20" y="46" width="24" height="4" rx="1"/>
              </g>
            }
            @case ('cargador') {
              <!-- Charger with lightning -->
              <g class="charger-group">
                <rect class="charger-body" x="18" y="14" width="28" height="36" rx="4"/>
                <rect class="charger-screen" x="22" y="18" width="20" height="8" rx="1" fill="rgba(255,255,255,0.2)"/>
                <path class="charger-bolt" d="M 32 28 L 26 38 L 30 38 L 28 46 L 38 34 L 34 34 L 36 28 Z" fill="#fef3c7"/>
                <circle class="charger-port" cx="32" cy="54" r="2.5" fill="rgba(0,0,0,0.3)"/>
              </g>
            }
            @case ('escritorio') {
              <!-- Desk -->
              <g class="desk-group">
                <rect class="desk-top" x="6" y="22" width="52" height="6" rx="1"/>
                <rect class="desk-leg" x="10" y="28" width="3" height="22"/>
                <rect class="desk-leg" x="51" y="28" width="3" height="22"/>
                <rect class="desk-drawer" x="14" y="32" width="14" height="14" rx="1" fill="rgba(255,255,255,0.2)"/>
                <line class="desk-handle" x1="18" y1="39" x2="24" y2="39" stroke="rgba(255,255,255,0.6)" stroke-width="1"/>
              </g>
            }
            @default {
              <!-- Default box icon -->
              <g class="default-group">
                <path class="default-box" d="M 32 10 L 54 22 L 54 42 L 32 54 L 10 42 L 10 22 Z"/>
                <path class="default-fold" d="M 10 22 L 32 34 L 54 22" fill="none" stroke="rgba(255,255,255,0.4)" stroke-width="1.2"/>
                <line class="default-fold" x1="32" y1="34" x2="32" y2="54" stroke="rgba(255,255,255,0.4)" stroke-width="1.2"/>
              </g>
            }
          }
        </g>
      </svg>
    </div>
  `,
  styles: [`
    :host { display: inline-block; }

    .cat-icon-wrapper {
      width: 100%;
      height: 100%;
      display: flex;
      align-items: center;
      justify-content: center;
      position: relative;
    }

    .cat-svg {
      width: 100%;
      height: 100%;
      overflow: visible;
      filter: drop-shadow(0 2px 4px rgba(0,0,0,0.25));
    }

    /* === Base floating animation (all icons) === */
    .cat-svg {
      animation: iconFloat 4s ease-in-out infinite;
    }

    @keyframes iconFloat {
      0%, 100% { transform: translateY(0) rotate(0deg); }
      50% { transform: translateY(-3px) rotate(0deg); }
    }

    /* === LAPTOP === */
    .cat-laptop .laptop-screen-inner {
      animation: laptopGlow 2s ease-in-out infinite;
    }
    .cat-laptop .laptop-dot {
      animation: laptopBlink 1.5s ease-in-out infinite;
    }

    @keyframes laptopGlow {
      0%, 100% { fill: rgba(255,255,255,0.15); }
      50% { fill: rgba(255,255,255,0.4); }
    }
    @keyframes laptopBlink {
      0%, 90%, 100% { opacity: 0.6; }
      95% { opacity: 1; transform: scale(1.4); }
    }

    /* === MONITOR === */
    .cat-monitor .scan-line {
      animation: monitorScan 2.5s linear infinite;
    }

    @keyframes monitorScan {
      0% { transform: translateY(0); opacity: 0; }
      10% { opacity: 0.8; }
      90% { opacity: 0.8; }
      100% { transform: translateY(26px); opacity: 0; }
    }

    /* === MOUSE === */
    .cat-mouse .mouse-click-ripple {
      transform-origin: 32px 42px;
      animation: mouseClick 1.6s ease-out infinite;
    }
    .cat-mouse .mouse-click-ripple-2 {
      animation-delay: 0.8s;
    }

    @keyframes mouseClick {
      0% { transform: scale(0.5); opacity: 0.8; }
      100% { transform: scale(3); opacity: 0; }
    }

    /* === KEYBOARD === */
    .cat-teclado .kb-key {
      animation: kbBounce 1.4s ease-in-out infinite;
    }
    .cat-teclado .kb-key:nth-child(odd) { animation-delay: 0.2s; }
    .cat-teclado .kb-key:nth-child(3n) { animation-delay: 0.4s; }
    .cat-teclado .kb-key:nth-child(4n) { animation-delay: 0.6s; }

    @keyframes kbBounce {
      0%, 90%, 100% { transform: translateY(0); }
      45% { transform: translateY(-1.5px); }
    }

    /* === HEADPHONES === */
    .cat-auricular .hp-band {
      transform-origin: 32px 36px;
      animation: hpPulse 2s ease-in-out infinite;
    }
    .cat-auricular .sound-wave {
      transform-origin: center;
      animation: soundWave 1.5s ease-in-out infinite;
    }
    .cat-auricular .sound-wave-1 { animation-delay: 0s; }
    .cat-auricular .sound-wave-2 { animation-delay: 0.2s; }
    .cat-auricular .sound-wave-3 { animation-delay: 0.4s; }

    @keyframes hpPulse {
      0%, 100% { stroke-width: 4; }
      50% { stroke-width: 4.5; }
    }
    @keyframes soundWave {
      0%, 100% { opacity: 0.3; transform: scaleY(0.5); }
      50% { opacity: 1; transform: scaleY(1.3); }
    }

    /* === GPU === */
    .cat-gpu .gpu-fan-blades {
      transform-origin: 32px 32px;
      animation: gpuSpin 1.2s linear infinite;
    }

    @keyframes gpuSpin {
      from { transform: rotate(0deg); }
      to { transform: rotate(360deg); }
    }

    /* === SSD === */
    .cat-ssd .ssd-led {
      animation: ssdBlink 1s ease-in-out infinite;
    }
    .cat-ssd .ssd-led-2 { animation-delay: 0.5s; }
    .cat-ssd .data-flow {
      stroke-dasharray: 8;
      animation: dataFlow 1.5s linear infinite;
    }
    .cat-ssd .data-flow-2 { animation-delay: 0.75s; }

    @keyframes ssdBlink {
      0%, 100% { opacity: 0.4; }
      50% { opacity: 1; }
    }
    @keyframes dataFlow {
      0% { stroke-dashoffset: 16; opacity: 0; }
      50% { opacity: 1; }
      100% { stroke-dashoffset: 0; opacity: 0; }
    }

    /* === RAM === */
    .cat-ram .electric-pulse {
      stroke-dasharray: 50;
      animation: electricFlow 1.8s ease-in-out infinite;
      filter: drop-shadow(0 0 2px currentColor);
    }

    @keyframes electricFlow {
      0% { stroke-dashoffset: 50; opacity: 0; }
      20% { opacity: 1; }
      80% { opacity: 1; }
      100% { stroke-dashoffset: 0; opacity: 0; }
    }

    /* === TABLET === */
    .cat-tablet .tap-ripple {
      transform-origin: 32px 30px;
      animation: tabletTap 2s ease-out infinite;
    }
    .cat-tablet .tap-ripple-2 { animation-delay: 1s; }

    @keyframes tabletTap {
      0% { transform: scale(0.3); opacity: 1; }
      100% { transform: scale(2); opacity: 0; }
    }

    /* === CABLE === */
    .cat-cable .cable-wire {
      animation: cableSway 3s ease-in-out infinite;
      stroke-dasharray: 100;
    }
    .cat-cable .cable-spark {
      animation: cableSpark 1.5s ease-in-out infinite;
    }

    @keyframes cableSway {
      0%, 100% { transform: translateX(0); }
      50% { transform: translateX(2px); }
    }
    @keyframes cableSpark {
      0%, 100% { opacity: 0.3; transform: scale(1); }
      50% { opacity: 1; transform: scale(1.8); }
    }

    /* === CHAIR === */
    .cat-silla .chair-group {
      transform-origin: 32px 50px;
      animation: chairRock 3s ease-in-out infinite;
    }

    @keyframes chairRock {
      0%, 100% { transform: rotate(-2deg); }
      50% { transform: rotate(2deg); }
    }

    /* === CAMERA === */
    .cat-camara .cam-flash {
      animation: camFlash 4s ease-in-out infinite;
    }
    .cat-camara .cam-flash-burst {
      transform-origin: 48px 26px;
      animation: camFlashBurst 4s ease-out infinite;
    }

    @keyframes camFlash {
      0%, 90%, 100% { fill: #fef3c7; opacity: 0.6; }
      92% { fill: #ffffff; opacity: 1; transform: scale(1.5); }
    }
    @keyframes camFlashBurst {
      0%, 90%, 100% { transform: scale(0.5); opacity: 0; }
      92% { transform: scale(2); opacity: 0.8; }
      95% { transform: scale(3); opacity: 0; }
    }

    /* === MIC === */
    .cat-micro .mic-wave {
      transform-origin: center;
      animation: micPulse 1.2s ease-in-out infinite;
    }
    .cat-micro .mic-wave-1, .cat-micro .mic-wave-4 { animation-delay: 0s; }
    .cat-micro .mic-wave-2, .cat-micro .mic-wave-3 { animation-delay: 0.3s; }

    @keyframes micPulse {
      0%, 100% { opacity: 0.3; transform: scaleY(0.6); }
      50% { opacity: 1; transform: scaleY(1.2); }
    }

    /* === USB === */
    .cat-pendrive .usb-led {
      animation: usbBlink 0.8s ease-in-out infinite;
    }

    @keyframes usbBlink {
      0%, 100% { opacity: 1; }
      50% { opacity: 0.3; }
    }

    /* === PRINTER === */
    .cat-impresora .printer-paper {
      transform-origin: 32px 18px;
      animation: paperFeed 3s ease-in-out infinite;
    }
    .cat-impresora .printer-led {
      animation: ssdBlink 1.2s ease-in-out infinite;
    }

    @keyframes paperFeed {
      0%, 100% { transform: translateY(0); }
      50% { transform: translateY(2px); }
    }

    /* === ROUTER === */
    .cat-router .signal-wave {
      animation: signalPulse 1.8s ease-out infinite;
    }
    .cat-router .signal-wave-1 { animation-delay: 0s; }
    .cat-router .signal-wave-2 { animation-delay: 0.4s; }
    .cat-router .router-led {
      animation: ssdBlink 1.5s ease-in-out infinite;
    }
    .cat-router .router-led:nth-child(odd) { animation-delay: 0.5s; }

    @keyframes signalPulse {
      0% { opacity: 1; transform: scale(0.7); }
      100% { opacity: 0; transform: scale(1.5); }
    }

    /* === SWITCH === */
    .cat-switch .switch-led {
      animation: ssdBlink 0.8s ease-in-out infinite;
    }
    .cat-switch .switch-led:nth-child(even) { animation-delay: 0.4s; }

    /* === NAS === */
    .cat-nas .nas-led {
      animation: ssdBlink 1.4s ease-in-out infinite;
    }
    .cat-nas .nas-led-2 { animation-delay: 0.5s; }
    .cat-nas .nas-led-3 { animation-delay: 1s; }

    /* === UPS === */
    .cat-ups .ups-level-fill {
      animation: upsCharge 2.5s ease-in-out infinite;
      transform-origin: 12px 45px;
    }
    .cat-ups .ups-bolt-shape {
      animation: boltFlicker 1.5s ease-in-out infinite;
    }

    @keyframes upsCharge {
      0%, 100% { transform: scaleX(0.6); }
      50% { transform: scaleX(1); }
    }
    @keyframes boltFlicker {
      0%, 100% { opacity: 0.9; }
      50% { opacity: 1; filter: drop-shadow(0 0 4px #fef3c7); }
    }

    /* === WEBCAM === */
    .cat-webcam .webcam-rec {
      animation: ssdBlink 1s ease-in-out infinite;
    }
    .cat-webcam .webcam-rec-pulse {
      transform-origin: 44px 22px;
      animation: camFlashBurst 1.5s ease-out infinite;
    }
    .cat-webcam .webcam-lens-inner {
      animation: lensFocus 3s ease-in-out infinite;
      transform-origin: 32px 32px;
    }

    @keyframes lensFocus {
      0%, 100% { transform: scale(1); }
      50% { transform: scale(1.2); }
    }

    /* === CHARGER === */
    .cat-cargador .charger-bolt {
      transform-origin: 32px 36px;
      animation: boltCharge 1.5s ease-in-out infinite;
      filter: drop-shadow(0 0 3px #fef3c7);
    }

    @keyframes boltCharge {
      0%, 100% { transform: scale(1); opacity: 0.85; }
      50% { transform: scale(1.15); opacity: 1; }
    }

    /* === DEFAULT (box) === */
    .cat-default .default-box {
      transform-origin: 32px 32px;
      animation: boxSpin 8s linear infinite;
    }

    @keyframes boxSpin {
      0%, 70%, 100% { transform: rotateY(0deg); }
      85% { transform: rotateY(180deg); }
    }
  `]
})
/**
 * Componente de presentación (standalone, OnPush) que dibuja el icono SVG
 * animado de una categoría de producto.
 *
 * Selecciona el gráfico según la categoría recibida por `@Input()`, aplica un
 * gradiente propio por categoría y un filtro de brillo, y ajusta el tamaño del
 * host. Genera un id único por instancia para aislar los `<defs>` del SVG. Se
 * usa en el catálogo y el carrito del POS para dar identidad visual a cada
 * tipo de producto. No tiene lógica de negocio.
 */
export class CategoryIconComponent {
  @Input() category: string = 'default';
  @Input() size: number = 48;

  // Apply the requested size to the host element so the SVG (width/height:100%)
  // is constrained instead of expanding to its intrinsic size.
  @HostBinding('style.width.px') get hostWidth(): number { return this.size; }
  @HostBinding('style.height.px') get hostHeight(): number { return this.size; }

  // Stable unique id per instance to keep SVG <defs> isolated
  readonly uid = Math.random().toString(36).slice(2, 9);
  readonly Math = Math;

  /**
   * Devuelve el color inicial del gradiente correspondiente a la categoría
   * actual, recurriendo al gradiente `default` si la categoría no está mapeada.
   * @returns color de inicio del gradiente en formato hex.
   */
  get gradientStart(): string {
    const map: Record<string, [string, string]> = {
      laptop:    ['#818cf8', '#4f46e5'],
      monitor:   ['#22d3ee', '#0891b2'],
      mouse:     ['#fbbf24', '#d97706'],
      teclado:   ['#f472b6', '#db2777'],
      auricular: ['#c084fc', '#9333ea'],
      gpu:       ['#f87171', '#dc2626'],
      ssd:       ['#4ade80', '#16a34a'],
      ram:       ['#fb923c', '#ea580c'],
      tablet:    ['#22d3ee', '#0e7490'],
      cable:     ['#a3e635', '#65a30d'],
      silla:     ['#f472b6', '#be185d'],
      camara:    ['#fb7185', '#e11d48'],
      micro:     ['#e879f9', '#a21caf'],
      pendrive:  ['#4ade80', '#15803d'],
      impresora: ['#94a3b8', '#475569'],
      dock:      ['#818cf8', '#4338ca'],
      router:    ['#a5b4fc', '#4f46e5'],
      switch:    ['#7dd3fc', '#0369a1'],
      nas:       ['#34d399', '#047857'],
      ups:       ['#fbbf24', '#b45309'],
      webcam:    ['#fb7185', '#9f1239'],
      cargador:  ['#facc15', '#a16207'],
      escritorio:['#2dd4bf', '#0f766e'],
      default:   ['#94a3b8', '#475569'],
    };
    return (map[this.category] ?? map['default'])[0];
  }

  /**
   * Devuelve el color final del gradiente correspondiente a la categoría
   * actual, recurriendo al gradiente `default` si la categoría no está mapeada.
   * @returns color de fin del gradiente en formato hex.
   */
  get gradientEnd(): string {
    const map: Record<string, [string, string]> = {
      laptop:    ['#818cf8', '#4f46e5'],
      monitor:   ['#22d3ee', '#0891b2'],
      mouse:     ['#fbbf24', '#d97706'],
      teclado:   ['#f472b6', '#db2777'],
      auricular: ['#c084fc', '#9333ea'],
      gpu:       ['#f87171', '#dc2626'],
      ssd:       ['#4ade80', '#16a34a'],
      ram:       ['#fb923c', '#ea580c'],
      tablet:    ['#22d3ee', '#0e7490'],
      cable:     ['#a3e635', '#65a30d'],
      silla:     ['#f472b6', '#be185d'],
      camara:    ['#fb7185', '#e11d48'],
      micro:     ['#e879f9', '#a21caf'],
      pendrive:  ['#4ade80', '#15803d'],
      impresora: ['#94a3b8', '#475569'],
      dock:      ['#818cf8', '#4338ca'],
      router:    ['#a5b4fc', '#4f46e5'],
      switch:    ['#7dd3fc', '#0369a1'],
      nas:       ['#34d399', '#047857'],
      ups:       ['#fbbf24', '#b45309'],
      webcam:    ['#fb7185', '#9f1239'],
      cargador:  ['#facc15', '#a16207'],
      escritorio:['#2dd4bf', '#0f766e'],
      default:   ['#94a3b8', '#475569'],
    };
    return (map[this.category] ?? map['default'])[1];
  }
}
