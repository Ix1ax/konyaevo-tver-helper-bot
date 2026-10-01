import { Component, type ErrorInfo, type ReactNode } from 'react';

export class ViewErrorBoundary extends Component<{ children: ReactNode; onBack: () => void }, { failed: boolean }> {
  state = { failed: false };
  static getDerivedStateFromError() { return { failed: true }; }
  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Не удалось открыть раздел приложения', error, info.componentStack);
  }
  render() {
    if (!this.state.failed) return this.props.children;
    return <div className="page">
      <p className="notice error-notice" role="alert">Не удалось открыть раздел. Обновите приложение и попробуйте ещё раз.</p>
      <button className="button-primary w-full mt-4" onClick={() => window.location.reload()}>Обновить приложение</button>
      <button className="button-quiet w-full mt-2" onClick={this.props.onBack}>К профилю</button>
    </div>;
  }
}
