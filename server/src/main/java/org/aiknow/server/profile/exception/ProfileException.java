package org.aiknow.server.profile.exception;

import lombok.Getter;
import org.aiknow.server.common.exception.AiknowException;

@Getter
public class ProfileException extends AiknowException {

    public ProfileException(ProfileErrorCode profileErrorCode)
    {
        super(profileErrorCode);
    }
}
