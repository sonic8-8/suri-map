import { useEffect, useState } from 'react';
import { getAreaEditBoard, type AreaEditBoardResponseDto } from '../../data/getAreaEditBoard';
import { getAreaEditIncidentDetail, type AreaEditIncidentDetailDto } from '../../data/getAreaEditIncidentDetail';

type UseAreaEditDataResult = {
  incidentDetail: AreaEditIncidentDetailDto | null;
  board: AreaEditBoardResponseDto | null;
  setBoard: (board: AreaEditBoardResponseDto | null) => void;
  reloadBoard: () => Promise<void>;
};

export function useAreaEditData(incidentId: string): UseAreaEditDataResult {
  const [incidentDetail, setIncidentDetail] = useState<AreaEditIncidentDetailDto | null>(null);
  const [board, setBoard] = useState<AreaEditBoardResponseDto | null>(null);

  useEffect(() => {
    let isActive = true;

    setIncidentDetail(null);
    void getAreaEditIncidentDetail(incidentId)
      .then((detail) => {
        if (isActive) setIncidentDetail(detail);
      })
      .catch(() => {
        if (isActive) setIncidentDetail(null);
      });

    return () => {
      isActive = false;
    };
  }, [incidentId]);

  useEffect(() => {
    let isActive = true;

    setBoard(null);
    void getAreaEditBoard(incidentId)
      .then((response) => {
        if (isActive) setBoard(response);
      })
      .catch(() => {
        if (isActive) setBoard(null);
      });

    return () => {
      isActive = false;
    };
  }, [incidentId]);

  const reloadBoard = async () => {
    const refreshed = await getAreaEditBoard(incidentId).catch(() => null);
    if (refreshed) setBoard(refreshed);
  };

  return { incidentDetail, board, setBoard, reloadBoard };
}
