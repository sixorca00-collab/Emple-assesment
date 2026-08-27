// forma comun de paginacion keyset del backend: los items y el cursor para pedir la pagina siguiente
export interface PageResponse<T> {
  items: T[];
  nextCursor: string | null;
}
