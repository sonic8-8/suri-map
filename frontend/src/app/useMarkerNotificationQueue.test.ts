import { act, cleanup, renderHook } from '@testing-library/react';
import { StrictMode, useState } from 'react';
import { afterEach, describe, expect, it } from 'vitest';

import type { MarkerNotification } from '../shared/ui';
import { useMarkerNotificationQueue } from './useMarkerNotificationQueue';

const firstNotification: MarkerNotification = {
  id: '9fca892b-db02-3a96-acf1-571a51908778',
  title: '발견 마커 수신',
  markerType: '발견',
  reporter: '검증 업무폰',
  areaLabel: '검증 수색 차수',
  markerRecordedAtLabel: '13:00',
  coordinateLabel: '',
};
const secondNotification: MarkerNotification = {
  ...firstNotification,
  id: 'f2db2ba9-b8f5-381a-a2bb-2b661148fff4',
};

afterEach(cleanup);

describe('useMarkerNotificationQueue', () => {
  it('다른 화면 상태 갱신 중 새 알림을 받으면, 표시할 알림을 유지한다', () => {
    // given: App처럼 같은 컴포넌트가 화면 상태와 알림 대기열을 함께 관리한다.
    const { result } = renderHook(
      () => {
        const [, setViewRevision] = useState(0);
        return {
          ...useMarkerNotificationQueue(),
          refreshView: () => setViewRevision((current) => current + 1),
        };
      },
      { wrapper: StrictMode },
    );

    // when: 화면 갱신과 새 알림 처리가 한 번의 갱신에 겹친다.
    act(() => {
      result.current.refreshView();
      result.current.addMarkerNotification(firstNotification);
    });

    // then: 새 알림이 대기열과 현재 표시 위치에 남는다.
    expect(result.current).toMatchObject({
      markerNotifications: [firstNotification],
      markerNotificationIndex: 0,
    });
  });

  it('서로 다른 알림을 연이어 받으면, 모두 보관하고 마지막 알림을 표시한다', () => {
    // given: 실제 앱과 같은 StrictMode에서 대기열을 시작한다.
    const { result } = renderHook(() => useMarkerNotificationQueue(), { wrapper: StrictMode });

    // when: 화면이 갱신되기 전에 서로 다른 알림을 연이어 받는다.
    act(() => {
      result.current.addMarkerNotification(firstNotification);
      result.current.addMarkerNotification(secondNotification);
    });

    // then: 두 알림을 모두 보관하고 마지막 알림을 가리킨다.
    expect(result.current).toMatchObject({
      markerNotifications: [firstNotification, secondNotification],
      markerNotificationIndex: 1,
    });
  });

  it('이미 받은 알림을 다시 받으면, 대기열과 보고 있던 알림을 유지한다', () => {
    // given: 두 알림을 받은 뒤 첫 번째 알림으로 이동했다.
    const { result } = renderHook(() => useMarkerNotificationQueue(), { wrapper: StrictMode });
    act(() => {
      result.current.addMarkerNotification(firstNotification);
      result.current.addMarkerNotification(secondNotification);
    });
    act(() => result.current.moveMarkerNotification(0));

    // when: 두 번째 알림이 같은 ID로 다시 도착한다.
    act(() => result.current.addMarkerNotification(secondNotification));

    // then: 알림을 중복 추가하거나 표시 위치를 바꾸지 않는다.
    expect(result.current).toMatchObject({
      markerNotifications: [firstNotification, secondNotification],
      markerNotificationIndex: 0,
    });
  });

  it('알림을 닫은 뒤 다시 받으면, 닫았던 알림은 제외하고 새 알림만 표시한다', () => {
    // given: 알림 하나를 표시하고 있다.
    const { result } = renderHook(() => useMarkerNotificationQueue(), { wrapper: StrictMode });
    act(() => result.current.addMarkerNotification(firstNotification));

    // when: 알림을 닫는다.
    act(() => result.current.closeMarkerNotifications());

    // then: 표시할 알림은 없어지고 위치도 초기화한다.
    expect(result.current).toMatchObject({ markerNotifications: [], markerNotificationIndex: 0 });

    // when: 닫았던 알림과 새로운 알림이 연이어 도착한다.
    act(() => {
      result.current.addMarkerNotification(firstNotification);
      result.current.addMarkerNotification(secondNotification);
    });

    // then: 닫았던 알림을 다시 띄우지 않고 새 알림만 표시한다.
    expect(result.current).toMatchObject({
      markerNotifications: [secondNotification],
      markerNotificationIndex: 0,
    });
  });

  it('알림의 이전·다음 위치로 이동하면, 선택한 알림을 표시한다', () => {
    // given: 알림 두 개를 보관하고 마지막 알림을 표시하고 있다.
    const { result } = renderHook(() => useMarkerNotificationQueue(), { wrapper: StrictMode });
    act(() => {
      result.current.addMarkerNotification(firstNotification);
      result.current.addMarkerNotification(secondNotification);
    });

    // when: 이전 알림으로 이동한다.
    act(() => result.current.moveMarkerNotification(0));

    // then: 첫 번째 알림을 표시한다.
    expect(result.current.markerNotifications[result.current.markerNotificationIndex]).toEqual(firstNotification);

    // when: 다음 알림으로 돌아간다.
    act(() => result.current.moveMarkerNotification(1));

    // then: 두 번째 알림을 표시한다.
    expect(result.current.markerNotifications[result.current.markerNotificationIndex]).toEqual(secondNotification);
  });

  it.each([
    { nextIndex: -1, expectedIndex: 0, boundary: '첫 알림 앞으로' },
    { nextIndex: 2, expectedIndex: 1, boundary: '마지막 알림 뒤로' },
  ])('$boundary 이동을 요청하면, 대기열 안의 표시 위치를 유지한다', ({ nextIndex, expectedIndex }) => {
    // given: 알림 두 개를 보관하고 있다.
    const { result } = renderHook(() => useMarkerNotificationQueue(), { wrapper: StrictMode });
    act(() => {
      result.current.addMarkerNotification(firstNotification);
      result.current.addMarkerNotification(secondNotification);
    });

    // when: 대기열 범위를 벗어난 위치로 이동한다.
    act(() => result.current.moveMarkerNotification(nextIndex));

    // then: 처음 또는 마지막 알림의 유효한 위치를 유지한다.
    expect(result.current.markerNotificationIndex).toBe(expectedIndex);
  });

  it('알림이 없는 상태에서 이동을 요청하면, 표시 위치를 0으로 유지한다', () => {
    // given: 대기열이 비어 있다.
    const { result } = renderHook(() => useMarkerNotificationQueue(), { wrapper: StrictMode });

    // when: 다른 알림 위치로 이동하려 한다.
    act(() => result.current.moveMarkerNotification(1));

    // then: 음수 위치를 만들지 않고 빈 대기열을 유지한다.
    expect(result.current).toMatchObject({ markerNotifications: [], markerNotificationIndex: 0 });
  });
});
