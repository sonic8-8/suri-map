package com.surimap.offlinepackage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.surimap.offlinepackage.repository.OfflinePackageMapper;
import com.surimap.offlinepackage.service.OfflinePackageRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@DisplayName("OfflinePackageRepository read-only query")
class OfflinePackageRepositoryReadOnlyQueryTest {

  @Test
  @DisplayName("byIncident reads installation statuses without seeding fixture manifests")
  void byIncidentReadsWithoutFixtureSeeding() {
    OfflinePackageMapper mapper = Mockito.mock(OfflinePackageMapper.class);
    OfflinePackageRepository repository = new OfflinePackageRepository(mapper);
    String incidentId = "10000000-0000-4000-8000-000000000001";
    when(mapper.findStatusesByIncident(incidentId)).thenReturn(List.of());

    assertThat(repository.byIncident(incidentId)).isEmpty();

    verify(mapper).findStatusesByIncident(incidentId);
    verifyNoMoreInteractions(mapper);
  }
}
