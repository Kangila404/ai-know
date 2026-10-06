package org.aiknow.server.profile.dto.res;

import org.aiknow.server.profile.domain.ProfileImg;

public record ProfileResponse(
    Long id,
    String name,
    String imgUrl
) {

    public static ProfileResponse from(ProfileImg profileImg) {
        return new ProfileResponse(
            profileImg.getId(),
            profileImg.getName(),
            profileImg.getImgUrl()
        );
    }

}
