import { Component, Suspense, type ErrorInfo, type ReactNode } from 'react';

type LazyRouteProps = {
  children: ReactNode;
};

export function LazyRoute({ children }: LazyRouteProps) {
  return <Suspense fallback={<RouteLoadingScreen />}>{children}</Suspense>;
}

type ProtectedRouteProps<T> = {
  value: T | null | undefined;
  children: (value: T) => ReactNode;
  fallback: ReactNode;
  lazy?: boolean;
};

export function ProtectedRoute<T>({ value, children, fallback, lazy = false }: ProtectedRouteProps<T>) {
  if (value == null) {
    return fallback;
  }

  const routeElement = children(value);
  return lazy ? <LazyRoute>{routeElement}</LazyRoute> : routeElement;
}

function RouteLoadingScreen() {
  return (
    <main className="situation-board-page situation-board-page-loading" aria-busy="true">
      <section className="situation-board-loading-screen" role="status" aria-live="polite" aria-label="Loading page">
        <span className="situation-board-loading-spinner" aria-hidden="true" />
      </section>
    </main>
  );
}

type RouteErrorBoundaryProps = {
  children: ReactNode;
  resetKey: string;
  onOpenIncidentList: () => void;
};

type RouteErrorBoundaryState = {
  error: Error | null;
};

export class RouteErrorBoundary extends Component<RouteErrorBoundaryProps, RouteErrorBoundaryState> {
  state: RouteErrorBoundaryState = { error: null };

  static getDerivedStateFromError(error: Error) {
    return { error };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('Route render error', {
      error,
      componentStack: errorInfo.componentStack,
      route: this.props.resetKey,
    });
  }

  componentDidUpdate(previousProps: RouteErrorBoundaryProps) {
    if (previousProps.resetKey !== this.props.resetKey && this.state.error) {
      this.setState({ error: null });
    }
  }

  render() {
    if (!this.state.error) {
      return this.props.children;
    }

    return (
      <main className="situation-board-page situation-board-page-loading" role="alert">
        <section className="situation-board-loading-screen" aria-label="Page error">
          <strong>페이지를 불러오지 못했습니다</strong>
          <span>일시적인 오류가 발생했습니다. 사건 목록으로 이동한 뒤 다시 시도하십시오.</span>
          <button type="button" onClick={this.props.onOpenIncidentList}>
            사건 목록으로 이동
          </button>
        </section>
      </main>
    );
  }
}
