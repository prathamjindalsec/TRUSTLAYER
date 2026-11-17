import { Component } from 'react';
import { RotateCcw, TriangleAlert } from 'lucide-react';

export class AppErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { error: null };
  }

  static getDerivedStateFromError(error) {
    return { error };
  }

  componentDidCatch(error, info) {
    // Do not send client-side error details to an undeclared analytics service. Production error
    // monitoring will be wired to an approved provider in the hardening phase.
    console.error('Uncaught TRUSTLAYER frontend error', error, info);
  }

  render() {
    if (!this.state.error) return this.props.children;
    return <main className="flex min-h-screen items-center justify-center bg-slate-50 px-4 dark:bg-ink-950"><section className="surface max-w-md p-7 text-center"><TriangleAlert className="mx-auto h-8 w-8 text-alert-red" aria-hidden="true" /><h1 className="mt-4 text-xl font-bold text-ink-950 dark:text-white">The interface hit an unexpected error.</h1><p className="mt-2 text-sm leading-6 text-slate-600 dark:text-slate-300">No action was sent automatically. Reload the page to restore the workspace.</p><button type="button" onClick={() => window.location.reload()} className="btn-primary mt-6"><RotateCcw className="h-4 w-4" aria-hidden="true" />Reload TRUSTLAYER</button></section></main>;
  }
}
