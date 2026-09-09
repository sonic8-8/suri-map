package com.surimap.api.controller.photo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.client.storage.MockObjectStorageAdapter;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MockObjectStorageUploadController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(MockObjectStorageAdapter.class)
class MockObjectStorageUploadControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private MockObjectStorageAdapter storage;

  @ParameterizedTest
  @ValueSource(
      strings = {
        "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001/4ca60e44-9cfe-410b-8794-2983baac0eac/photo.jpg",
        "mock-112/missing-person/001.jpg"
      })
  @DisplayName("마커·실종자 사진을 업로드하면, 같은 파일 키로 원본을 조회할 수 있다")
  void uploadAndView_photoObjectKey_preservesOriginalBytes(String objectKey) throws Exception {
    // given: 마커 또는 실종자 사진의 업로드 주소를 발급했다.
    storage.clear();
    storage.generatePresignedUrl(objectKey, "image/jpeg", 3L, null, Duration.ofMinutes(15));

    // when: 발급받은 파일 키로 사진을 업로드한다.
    mockMvc
        .perform(
            put("/mock-upload/" + objectKey)
                .contentType(MediaType.IMAGE_JPEG)
                .content(new byte[] {1, 2, 3}))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Mock-Object-Key", objectKey))
        .andExpect(header().string("X-Mock-Upload-Size", "3"));

    // then: 파일 정보와 업로드한 원본 바이트를 같은 키로 조회할 수 있다.
    assertThat(storage.headObject(objectKey))
        .hasValueSatisfying(
            metadata -> {
              assertThat(metadata.contentType()).isEqualTo("image/jpeg");
              assertThat(metadata.sizeBytes()).isEqualTo(3L);
            });

    mockMvc
        .perform(get("/mock-upload/" + objectKey))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Mock-Object-Key", objectKey))
        .andExpect(content().bytes(new byte[] {1, 2, 3}));
  }
}
