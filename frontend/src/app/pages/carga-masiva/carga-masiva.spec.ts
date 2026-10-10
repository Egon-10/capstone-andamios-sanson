import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';

import { CargaMasivaComponent } from './carga-masiva';
import { ProductoService } from '../../services/producto.service';

/** CP-20: pantalla de carga masiva (HU-19). */
describe('CargaMasivaComponent', () => {
  let productos: jasmine.SpyObj<ProductoService>;
  let c: CargaMasivaComponent;
  let fixture: ComponentFixture<CargaMasivaComponent>;
  const archivo = new File(['sku;nombre'], 'productos.csv', { type: 'text/csv' });

  function evento(archivos: File[] | null): Event {
    const lista = archivos
      ? Object.assign([...archivos], { item: (i: number) => archivos[i] })
      : null;
    return { target: { files: lista } } as unknown as Event;
  }

  beforeEach(() => {
    productos = jasmine.createSpyObj('ProductoService', ['cargaMasiva']);
    TestBed.configureTestingModule({
      imports: [CargaMasivaComponent],
      providers: [{ provide: ProductoService, useValue: productos }]
    });
    fixture = TestBed.createComponent(CargaMasivaComponent);
    c = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('toma el archivo elegido y limpia el informe anterior', () => {
    c.informe = { errores: [], skusCargados: [] };
    c.seleccionar(evento([archivo]));
    expect(c.archivo).toBe(archivo);
    expect(c.nombreArchivo).toBe('productos.csv');
    expect(c.informe).toBeUndefined();
  });

  it('olvida el archivo si se cancela la seleccion', () => {
    c.seleccionar(evento([archivo]));
    c.seleccionar(evento([]));
    expect(c.archivo).toBeUndefined();
    c.seleccionar(evento(null));
    expect(c.nombreArchivo).toBe('');
  });

  it('pide el archivo antes de enviar', () => {
    c.simular();
    expect(c.mensajeError).toContain('Seleccione');
    expect(productos.cargaMasiva).not.toHaveBeenCalled();
  });

  it('simula sin guardar con el modo elegido', () => {
    productos.cargaMasiva.and.returnValue(of({ simulacion: true, errores: [], skusCargados: [] }));
    c.seleccionar(evento([archivo]));
    c.modo = 'PARCIAL';

    c.simular();

    expect(productos.cargaMasiva).toHaveBeenCalledWith(archivo, 'PARCIAL', true);
    expect(c.informe?.simulacion).toBeTrue();
    expect(c.procesando).toBeFalse();
  });

  it('carga de verdad', () => {
    productos.cargaMasiva.and.returnValue(of({ seGuardo: true, errores: [], skusCargados: ['AND-1'] }));
    c.seleccionar(evento([archivo]));
    c.cargar();
    expect(productos.cargaMasiva).toHaveBeenCalledWith(archivo, 'TODO_O_NADA', false);
    expect(c.informe?.skusCargados).toEqual(['AND-1']);
  });

  it('muestra el error del servidor, por ejemplo una cabecera incompleta', () => {
    productos.cargaMasiva.and.returnValue(throwError(() =>
      new HttpErrorResponse({ status: 422, error: { mensaje: 'Faltan columnas' } })));
    c.seleccionar(evento([archivo]));
    c.cargar();
    expect(c.mensajeError).toBe('Faltan columnas');
    expect(c.informe).toBeUndefined();
  });

  it('usa un mensaje por omision si el servidor no envia uno', () => {
    productos.cargaMasiva.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    c.seleccionar(evento([archivo]));
    c.cargar();
    expect(c.mensajeError).toBe('No se pudo procesar el archivo');
  });

  it('HU-40: si el servidor falla muestra el error y reintentar repite la misma simulacion', () => {
    productos.cargaMasiva.and.returnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
    c.seleccionar(evento([archivo]));
    c.simular();
    fixture.detectChanges();

    expect(c.estadoVista).toBe('error');
    const alerta: HTMLElement = fixture.nativeElement.querySelector('app-estado-vista [role="alert"]');
    expect(alerta.textContent).toContain('No se pudo procesar el archivo');

    productos.cargaMasiva.and.returnValue(of({ simulacion: true, filasLeidas: 1, aceptadas: 1, errores: [], skusCargados: [] }));
    alerta.querySelector('button')!.click();
    fixture.detectChanges();

    expect(productos.cargaMasiva.calls.mostRecent().args).toEqual([archivo, 'TODO_O_NADA', true]);
    expect(c.estadoVista).toBe('listo');
    expect(fixture.nativeElement.textContent).toContain('Resultado de la simulación');
  });

  it('HU-40: un archivo sin filas se informa como vacio y la falta de archivo no ofrece reintentar', () => {
    c.simular();
    fixture.detectChanges();
    expect(c.estadoVista).toBe('listo');
    expect(fixture.nativeElement.querySelector('app-estado-vista [role="alert"]')).toBeNull();

    productos.cargaMasiva.and.returnValue(of({ simulacion: true, filasLeidas: 0, errores: [], skusCargados: [] }));
    c.seleccionar(evento([archivo]));
    c.simular();
    fixture.detectChanges();

    expect(c.estadoVista).toBe('vacio');
    expect(fixture.nativeElement.textContent).toContain('El archivo no tiene filas de datos');
  });

  it('descarga una plantilla con las columnas que espera el servidor', async () => {
    let contenido: Blob | undefined;
    spyOn(URL, 'createObjectURL').and.callFake((b: Blob | MediaSource) => {
      contenido = b as Blob;
      return 'blob:plantilla';
    });
    spyOn(URL, 'revokeObjectURL');
    spyOn(HTMLAnchorElement.prototype, 'click');

    c.descargarPlantilla();

    const texto = await contenido!.text();
    expect(texto).toContain('sku;nombre;descripcion;precio;stock');
    expect(texto).toContain('categoria;proveedor');
  });

  it('limpiar deja la pantalla como al entrar', () => {
    c.seleccionar(evento([archivo]));
    c.mensajeError = 'algo';
    c.limpiar();
    expect(c.archivo).toBeUndefined();
    expect(c.nombreArchivo).toBe('');
    expect(c.mensajeError).toBe('');
  });
});
