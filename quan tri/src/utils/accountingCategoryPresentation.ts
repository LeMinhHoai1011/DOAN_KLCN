/** Mirrors the backend rule; blank means no account. */
export const accountingAccountError = (value: string) => /^\s*(?:[1-9][0-9]{2,9}\s*)?$/.test(value) ? null : 'Tài khoản kế toán phải gồm 3 đến 10 chữ số, không bắt đầu bằng 0.';
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
