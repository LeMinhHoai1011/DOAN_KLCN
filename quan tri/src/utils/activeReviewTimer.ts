/** Counts only visible time on a document, so an idle background tab is not reported as review effort. */
export interface ActiveReviewTimer { setVisible(visible: boolean, now: number): void; elapsed(now: number): number; reset(now: number, visible: boolean): void }

export const REVIEW_DECISIONS = ['APPROVE', 'REJECT', 'REQUEST_INFO'] as const

export function createActiveReviewTimer(now: number, visible: boolean): ActiveReviewTimer {
  let total = 0
  let since: number | null = visible ? now : null
  return {
    setVisible(isVisible, at) {
      if (isVisible && since === null) since = at
      if (!isVisible && since !== null) { total += Math.max(0, at - since); since = null }
    },
    elapsed(at) { return Math.round(total + (since === null ? 0 : Math.max(0, at - since))) },
    reset(at, isVisible) { total = 0; since = isVisible ? at : null },
  }
}

export const isReviewDecision = (action: string) => (REVIEW_DECISIONS as readonly string[]).includes(action)
