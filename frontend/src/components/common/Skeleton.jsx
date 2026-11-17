export function Skeleton({ className = '' }) {
  return <span aria-hidden="true" className={`block animate-pulse rounded bg-slate-200 dark:bg-slate-700 ${className}`} />;
}

export function CardSkeleton() {
  return (
    <div className="surface p-5">
      <Skeleton className="h-4 w-28" />
      <Skeleton className="mt-5 h-8 w-16" />
      <Skeleton className="mt-4 h-3 w-40" />
    </div>
  );
}
