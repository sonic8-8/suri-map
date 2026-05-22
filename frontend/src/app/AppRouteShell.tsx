import { Component, Suspense, type ErrorInfo, type ReactNode } from 'react';

type LazyRouteProps = {
  children: ReactNode;
};

export function LazyRoute({ children }: LazyRouteProps) {
  return <Suspense fallback={<RouteLoadingScreen />}>{children}</Suspense>;
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
          <strong>?섏씠吏瑜??쒖떆?섏? 紐삵뻽?듬땲??</strong>
          <span>?쇱떆?곸씤 ?붾㈃ ?ㅻ쪟媛 諛쒖깮?덉뒿?덈떎. ?ш굔 紐⑸줉?쇰줈 ?뚯븘媛????ㅼ떆 ?댁뼱二쇱꽭??</span>
          <button type="button" onClick={this.props.onOpenIncidentList}>
            ?ш굔 紐⑸줉?쇰줈 ?뚯븘媛湲?          </button>
        </section>
      </main>
    );
  }
}
