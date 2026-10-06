import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';

import { ValorizacionComponent } from './valorizacion';
import { InventarioService } from '../../services/inventario.service';
import { Valorizacion } from '../../models/valorizacion';

/** CP-18: pantalla de valorización (HU-17) y reposición (HU-20). */
describe('ValorizacionComponent', () => {
  let inventario: jasmine.SpyObj<InventarioService>;

  const valorizacion: Valorizacion = {
    valorTotal: 41840, unidadesTotales: 170, productosContados: 2,
    porCategoria: [{ categoria: 'Andamios', productos: 1, unidades: 150, valor: 40000, participacion: 95.6 }],
    detalle: [
      { productoId: 1, sku: 'AND-1', producto: 'Marco "doble"', categoria: 'Andamios',
        stock: 150, costoPromedio: 266.67, valor: 40000, necesitaReposicion: false },
      { productoId: 2, sku: 'RUE-1', producto: 'Rueda', categoria: 'Ruedas',
        stock: 20, costoPromedio: 92, valor: 1840, necesitaReposicion: true }
    ]
  };

  function crear(): ValorizacionComponent {
    const fixture = TestBed.createComponent(ValorizacionComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  beforeEach(() => {
    inventario = jasmine.createSpyObj('InventarioService', ['valorizacion', 'porReponer']);
    inventario.valorizacion.and.returnValue(of(valorizacion));
    inventario.porReponer.and.returnValue(of([valorizacion.detalle[1]]));
    TestBed.configureTestingModule({
      imports: [ValorizacionComponent],
      providers: [{ provide: InventarioService, useValue: inventario }]
    });
  });

  it('carga la valorizacion y la lista de reposicion', () => {
    const c = crear();
    expect(c.valorizacion).toEqual(valorizacion);
    expect(c.porReponer.length).toBe(1);
    expect(c.cargando).toBeFalse();
  });

  it('muestra el error del servidor, por ejemplo al encargado sin permiso', () => {
    inventario.valorizacion.and.returnValue(throwError(() =>
      new HttpErrorResponse({ status: 403, error: { mensaje: 'No tiene permisos' } })));
    inventario.porReponer.and.returnValue(throwError(() => new HttpErrorResponse({ status: 403 })));

    const c = crear();

    expect(c.mensajeError).toBe('No tiene permisos');
    expect(c.porReponer).toEqual([]);
  });

  it('usa un mensaje por omision si el servidor no envia uno', () => {
    inventario.valorizacion.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const c = crear();
    expect(c.mensajeError).toContain('valorizacion');
  });

  it('filtra el detalle a lo que necesita reposicion', () => {
    const c = crear();
    expect(c.detalle.length).toBe(2);
    c.soloReposicionEnDetalle = true;
    expect(c.detalle.map(p => p.sku)).toEqual(['RUE-1']);
  });

  it('el detalle esta vacio mientras no hay valorizacion', () => {
    const c = crear();
    c.valorizacion = undefined;
    expect(c.detalle).toEqual([]);
  });

  it('suma el valor de lo que hay que reponer', () => {
    const c = crear();
    expect(c.valorPorReponer).toBe(1840);
  });

  it('exporta el detalle a CSV escapando las comillas', async () => {
    const c = crear();
    let contenido: Blob | undefined;
    spyOn(URL, 'createObjectURL').and.callFake((b: Blob | MediaSource) => {
      contenido = b as Blob;
      return 'blob:prueba';
    });
    spyOn(URL, 'revokeObjectURL');
    const clic = spyOn(HTMLAnchorElement.prototype, 'click');

    c.descargarDetalle();

    expect(clic).toHaveBeenCalled();
    const texto = await contenido!.text();
    expect(texto).toContain('"SKU";"Producto"');
    expect(texto).toContain('"Marco ""doble"""');
    expect(texto).toContain('"Si"');
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:prueba');
  });

  it('no exporta nada si no hay detalle', () => {
    const c = crear();
    c.valorizacion = { ...valorizacion, detalle: [] };
    const crearUrl = spyOn(URL, 'createObjectURL');
    c.descargarDetalle();
    expect(crearUrl).not.toHaveBeenCalled();
  });
});
