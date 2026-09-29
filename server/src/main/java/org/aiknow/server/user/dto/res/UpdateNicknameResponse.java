package org.aiknow.server.user.dto.res;

public record UpdateNicknameResponse(
    String nickname
) {
    public static UpdateNicknameResponse from(String newNickname) {
        return new UpdateNicknameResponse(newNickname);
    }
}
