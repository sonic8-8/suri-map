-- split 시 parent.areaLevel()을 그대로 상속하던 버그로 인해 잘못 저장된 area_level 수정
-- OVERALL parent의 split children: OVERALL -> UNIT
-- UNIT parent의 split children: UNIT -> TEAM (단, parentAreaId IS NOT NULL이고 parent가 UNIT인 경우)

UPDATE search_area child
SET area_level = 'UNIT'
WHERE child.area_level = 'OVERALL'
  AND child.parent_search_area_id IS NOT NULL
  AND EXISTS (
    SELECT 1 FROM search_area parent
    WHERE parent.id = child.parent_search_area_id
      AND parent.area_level = 'OVERALL'
  );

UPDATE search_area child
SET area_level = 'TEAM'
WHERE child.area_level = 'UNIT'
  AND child.parent_search_area_id IS NOT NULL
  AND EXISTS (
    SELECT 1 FROM search_area parent
    WHERE parent.id = child.parent_search_area_id
      AND parent.area_level = 'UNIT'
  );
