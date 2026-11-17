import { useEffect } from 'react';

export function usePageTitle(title) {
  useEffect(() => {
    document.title = title ? `${title} — TRUSTLAYER` : 'TRUSTLAYER — Financial fraud defense';
  }, [title]);
}
