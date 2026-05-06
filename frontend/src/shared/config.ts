// TODO: VWorld 키가 웹 번들에 노출되는 구조이므로 도메인 제한 또는 서버 프록시 적용 여부를 확인한다.
// VWorld 키는 클라이언트 번들에서 직접 읽을 수 있도록 Vite define으로 주입한다.
export function getVWorldApiKey() {
  return __V_WORLD_API_KEY__;
}

