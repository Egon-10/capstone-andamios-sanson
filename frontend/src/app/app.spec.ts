import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';

/** Panel de accesibilidad: preferencias visuales y narrador por voz. */
describe('App', () => {
  const raiz = document.documentElement;

  function crear() {
    const f = TestBed.createComponent(App);
    f.detectChanges();
    return f;
  }

  beforeEach(async () => {
    localStorage.removeItem('accesibilidad');
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([])]
    }).compileComponents();
  });

  afterEach(() => {
    localStorage.removeItem('accesibilidad');
    raiz.classList.remove('accesibilidad-alto-contraste');
    delete raiz.dataset['filtroColor'];
    ['--escala-texto', '--espaciado-letras', '--espaciado-palabras', '--espaciado-lineas'].forEach(v => raiz.style.removeProperty(v));
  });

  it('crea la aplicación con los valores predeterminados', () => {
    const c = crear().componentInstance;
    expect(c).toBeTruthy();
    expect(c.filtro).toBe('ninguno');
    expect(c.escalaTexto).toBe(100);
    expect(raiz.dataset['filtroColor']).toBe('ninguno');
  });

  it('abre y cierra el panel con el botón y con Escape', () => {
    const f = crear();
    const el: HTMLElement = f.nativeElement;
    const boton = el.querySelector('button.accesibilidad-flotante') as HTMLButtonElement;
    expect(boton.getAttribute('aria-expanded')).toBe('false');

    boton.click();
    f.detectChanges();
    expect(el.querySelector('#panel-accesibilidad')).not.toBeNull();
    expect(boton.getAttribute('aria-expanded')).toBe('true');

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    f.detectChanges();
    expect(el.querySelector('#panel-accesibilidad')).toBeNull();
  });

  it('aplica y guarda las preferencias elegidas', () => {
    const c = crear().componentInstance;
    c.filtro = 'protanopia';
    c.altoContraste = true;
    c.escalaTexto = 130;
    c.espaciado = 2;
    c.guardar();

    expect(raiz.dataset['filtroColor']).toBe('protanopia');
    expect(raiz.classList.contains('accesibilidad-alto-contraste')).toBeTrue();
    expect(raiz.style.getPropertyValue('--escala-texto')).toBe('1.3');
    expect(raiz.style.getPropertyValue('--espaciado-letras')).toBe('2px');
    expect(raiz.style.getPropertyValue('--espaciado-palabras')).toBe('3px');
    expect(JSON.parse(localStorage.getItem('accesibilidad')!)).toEqual(
      jasmine.objectContaining({ filtro: 'protanopia', altoContraste: true, escalaTexto: 130, espaciado: 2 }));
  });

  it('recupera las preferencias guardadas al abrir la aplicación', () => {
    localStorage.setItem('accesibilidad', JSON.stringify({ filtro: 'monocromatico', altoContraste: true, narrador: true, escalaTexto: 120, espaciado: 3 }));
    const c = crear().componentInstance;
    expect(c.filtro).toBe('monocromatico');
    expect(c.altoContraste).toBeTrue();
    expect(c.narrador).toBeTrue();
    expect(c.escalaTexto).toBe(120);
    expect(c.espaciado).toBe(3);
    expect(raiz.dataset['filtroColor']).toBe('monocromatico');
  });

  it('ignora preferencias guardadas que no son válidas', () => {
    localStorage.setItem('accesibilidad', '{no es json');
    const c = crear().componentInstance;
    expect(c.filtro).toBe('ninguno');
    expect(c.escalaTexto).toBe(100);
  });

  it('restablece los valores y detiene la voz', () => {
    const cancelar = spyOn(window.speechSynthesis, 'cancel');
    const c = crear().componentInstance;
    c.filtro = 'tritanopia';
    c.altoContraste = true;
    c.narrador = true;
    c.escalaTexto = 150;
    c.espaciado = 4;
    c.restablecer();

    expect(c.filtro).toBe('ninguno');
    expect(c.altoContraste).toBeFalse();
    expect(c.narrador).toBeFalse();
    expect(c.escalaTexto).toBe(100);
    expect(c.espaciado).toBe(0);
    expect(cancelar).toHaveBeenCalled();
  });

  describe('narrador', () => {
    let hablar: jasmine.Spy;

    beforeEach(() => {
      hablar = spyOn(window.speechSynthesis, 'speak');
      spyOn(window.speechSynthesis, 'cancel');
    });

    function enfocar(c: App, el: HTMLElement): void {
      c.alEnfocar({ target: el } as unknown as FocusEvent);
    }

    it('lee el nombre accesible del control enfocado', () => {
      const c = crear().componentInstance;
      c.narrador = true;
      const boton = document.createElement('button');
      boton.setAttribute('aria-label', 'Guardar producto');
      enfocar(c, boton);
      expect(hablar).toHaveBeenCalledTimes(1);
      const voz = hablar.calls.mostRecent().args[0] as SpeechSynthesisUtterance;
      expect(voz.text).toBe('Guardar producto');
      expect(voz.lang).toBe('es-CO');
    });

    it('usa el texto visible o el marcador cuando no hay etiqueta', () => {
      const c = crear().componentInstance;
      c.narrador = true;
      const campo = document.createElement('input');
      campo.setAttribute('placeholder', 'Buscar por nombre');
      enfocar(c, campo);
      expect((hablar.calls.mostRecent().args[0] as SpeechSynthesisUtterance).text).toBe('Buscar por nombre');
    });

    it('no habla si está desactivado, si el elemento no es un control o si no tiene texto', () => {
      const c = crear().componentInstance;
      const boton = document.createElement('button');
      boton.setAttribute('aria-label', 'Ingresar');
      enfocar(c, boton);

      c.narrador = true;
      enfocar(c, document.createElement('div'));
      enfocar(c, document.createElement('button'));
      expect(hablar).not.toHaveBeenCalled();
    });
  });
});
