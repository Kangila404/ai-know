package org.aiknow.server.user.dto.res;

import org.aiknow.server.profile.domain.ProfileImg;

public record UpdateProfileResponse(
    Long id,
    String name,
    String imgUrl
) {

    public static UpdateProfileResponse from(ProfileImg profileImg){
        return new UpdateProfileResponse(
            profileImg.getId(),
            profileImg.getName(),
            profileImg.getImgUrl()
        );
    }
}
