package org.aiknow.server.profile.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RevertProfileRequest(
    @NotBlank(message = "프로필 이름은 필수입니다.")
    @Size(max = 20, message = "프로필 이름은 20자 이하여야 합니다.")
    String name,

    @NotBlank(message = "이미지 주소는 필수입니다.")
    @Size(max = 20, message = "프로필 주소은 20자를 넘을 수 없습니다.")
    String imgUrl
) {

}
