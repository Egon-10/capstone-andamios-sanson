import { HttpErrorResponse } from '@angular/common/http';

import { aParametros } from './parametros';
import { mensajeDeError, mensajeDeErrorEnBlob } from './errores';

describe('aParametros', () => {
  it('omite los filtros vacíos, nulos e indefinidos', () => {
    const p = aParametros({ texto: '  ', rolId: null, estado: undefined, pagina: 0, desde: '2026-10-01' });
    expect(p.keys()).toEqual(['pagina', 'desde']);
    expect(p.get('pagina')).toBe('0');
  });

  it('recorta los textos', () => {
    expect(aParametros({ texto: ' luis ' }).get('texto')).toBe('luis');
  });
});

describe('mensajeDeError', () => {
  it('prefiere el error de campo que envía el servidor', () => {
    const e = new HttpErrorResponse({ status: 422, error: { estado: 422, mensaje: 'Datos inválidos', errores: { correo: 'Correo repetido' } } });
    expect(mensajeDeError(e, 'x')).toBe('Correo repetido');
  });

  it('usa el mensaje general si no hay errores de campo', () => {
    const e = new HttpErrorResponse({ status: 409, error: { estado: 409, mensaje: 'Ya existe' } });
    expect(mensajeDeError(e, 'x')).toBe('Ya existe');
  });

  it('explica la falta de conexión, el permiso y el exceso de solicitudes', () => {
    expect(mensajeDeError(new HttpErrorResponse({ status: 0 }), 'x')).toContain('conexión');
    expect(mensajeDeError(new HttpErrorResponse({ status: 403 }), 'x')).toContain('permiso');
    expect(mensajeDeError(new HttpErrorResponse({ status: 429 }), 'x')).toContain('Espere');
    expect(mensajeDeError(new HttpErrorResponse({ status: 500 }), 'Por omisión')).toBe('Por omisión');
  });

  it('lee el mensaje de un error que llega como Blob (descargas)', async () => {
    const cuerpo = new Blob([JSON.stringify({ estado: 422, mensaje: 'Fecha futura' })], { type: 'application/json' });
    const e = new HttpErrorResponse({ status: 422, error: cuerpo });
    expect(await mensajeDeErrorEnBlob(e, 'x')).toBe('Fecha futura');
  });

  it('si el Blob no es JSON usa el mensaje por código', async () => {
    const e = new HttpErrorResponse({ status: 403, error: new Blob(['<html>']) });
    expect(await mensajeDeErrorEnBlob(e, 'x')).toContain('permiso');
  });
});
