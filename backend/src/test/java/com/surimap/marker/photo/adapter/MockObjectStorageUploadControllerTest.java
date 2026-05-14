package com.surimap.marker.photo.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MockObjectStorageUploadController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(MockObjectStorageAdapter.class)
@DisplayName("mock object storage upload endpoint")
class MockObjectStorageUploadControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private MockObjectStorageAdapter storage;

  @Test
  @DisplayName("PUT /mock-upload/** marks issued object as uploaded")
  void putMockUploadMarksIssuedObjectUploaded() throws Exception {
    String objectKey =
        "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001/4ca60e44-9cfe-410b-8794-2983baac0eac/photo.jpg";
    storage.generatePresignedUrl(objectKey, "image/jpeg", 3L, null, Duration.ofMinutes(15));

    mockMvc
        .perform(
            put("/mock-upload/{incidentId}/{markerId}/{fileName}",
                    "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001",
                    "4ca60e44-9cfe-410b-8794-2983baac0eac",
                    "photo.jpg")
                .contentType(MediaType.IMAGE_JPEG)
                .content(new byte[] {1, 2, 3}))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Mock-Object-Key", objectKey))
        .andExpect(header().string("X-Mock-Upload-Size", "3"));

    assertThat(storage.headObject(objectKey))
        .hasValueSatisfying(
            metadata -> {
              assertThat(metadata.contentType()).isEqualTo("image/jpeg");
              assertThat(metadata.sizeBytes()).isEqualTo(3L);
            });
  }
}
