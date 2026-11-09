import { Component, HostListener } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    RouterOutlet, FormsModule
  ],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  panelAbierto = false;
  filtro: 'ninguno' | 'deuteranopia' | 'protanopia' | 'tritanopia' | 'monocromatico' = 'ninguno';
  altoContraste = false;
  modoOscuro = false;
  narrador = false;
  escalaTexto = 100;
  espaciado = 0;

  constructor() {
    try {
      const p = JSON.parse(localStorage.getItem('accesibilidad') || '{}');
      this.filtro = p.filtro || 'ninguno';
      this.altoContraste = !!p.altoContraste;
      this.modoOscuro = !!p.modoOscuro;
      this.narrador = !!p.narrador;
      this.escalaTexto = Number(p.escalaTexto) || 100;
      this.espaciado = Number(p.espaciado) || 0;
      this.aplicarPreferencias();
    } catch { /* Preferencias inválidas: se usan valores predeterminados. */ }
  }

  alternarPanel(): void { this.panelAbierto = !this.panelAbierto; }

  guardar(): void {
    localStorage.setItem('accesibilidad', JSON.stringify({ filtro: this.filtro, altoContraste: this.altoContraste, modoOscuro: this.modoOscuro, narrador: this.narrador, escalaTexto: this.escalaTexto, espaciado: this.espaciado }));
    this.aplicarPreferencias();
  }

  restablecer(): void {
    this.filtro = 'ninguno'; this.altoContraste = false; this.modoOscuro = false; this.escalaTexto = 100; this.espaciado = 0; this.narrador = false;
    this.guardar();
    if ('speechSynthesis' in window) window.speechSynthesis.cancel();
  }

  private aplicarPreferencias(): void {
    const root = document.documentElement;
    root.dataset['filtroColor'] = this.filtro;
    root.classList.toggle('accesibilidad-alto-contraste', this.altoContraste);
    root.classList.toggle('tema-oscuro', this.modoOscuro);
    root.style.setProperty('--escala-texto', `${this.escalaTexto / 100}`);
    root.style.setProperty('--espaciado-letras', `${this.espaciado}px`);
    root.style.setProperty('--espaciado-palabras', `${this.espaciado * 1.5}px`);
    root.style.setProperty('--espaciado-lineas', `${1.5 + this.espaciado / 10}`);
  }

  @HostListener('document:focusin', ['$event'])
  alEnfocar(evento: FocusEvent): void {
    if (!this.narrador || !('speechSynthesis' in window)) return;
    const el = evento.target as HTMLElement | null;
    if (!el || !el.matches('button, a, input, select, textarea, [role="button"], [tabindex]')) return;
    const texto = el.getAttribute('aria-label') || el.getAttribute('title') || (el as HTMLInputElement).labels?.[0]?.innerText || el.innerText || (el as HTMLInputElement).value || el.getAttribute('placeholder');
    if (!texto?.trim()) return;
    window.speechSynthesis.cancel();
    const voz = new SpeechSynthesisUtterance(texto.trim());
    voz.lang = 'es-CO';
    window.speechSynthesis.speak(voz);
  }

  @HostListener('document:keydown.escape')
  cerrarConEscape(): void { this.panelAbierto = false; }
}
