export function formatIncidentContextEyebrow(version: number | null | undefined) {
  return version == null ? '정보 버전 확인 중' : `정보 버전 ${version}`;
}

export function formatMissingPersonIncidentTitle(displayName: string | null | undefined) {
  const normalizedDisplayName = displayName?.trim();

  return normalizedDisplayName ? `${normalizedDisplayName} 실종 사건` : '실종 사건';
}
