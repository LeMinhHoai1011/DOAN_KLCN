export type CategoryLoadState = 'idle' | 'loading' | 'success' | 'empty' | 'unauthenticated' | 'forbidden' | 'error';
export function categoryFailureState(status?: number): CategoryLoadState {
 return status === 401 ? 'unauthenticated' : status === 403 ? 'forbidden' : 'error';
}
export function categoryCountLabel(state: CategoryLoadState, count: number): string {
 return state === 'success' || state === 'empty' ? String(count) : '—';
}
export function categoryTypeLabel(type?: string | null): string {
 return type === 'INCOME' ? 'Thu' : type === 'EXPENSE' ? 'Chi' : 'Chưa xác định';
}
