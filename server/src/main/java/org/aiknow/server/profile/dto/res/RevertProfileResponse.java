package org.aiknow.server.profile.dto.res;

import org.aiknow.server.profile.domain.ProfileImg;

public record RevertProfileResponse(
    Long id,
    String name,
    String imgUrl
) {

    public static RevertProfileResponse from(ProfileImg profileImg) {
        return new RevertProfileResponse(profileImg.getId(), profileImg.getName(), profileImg.getImgUrl());
    }

}
