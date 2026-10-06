package org.aiknow.server.admin.exception;

import lombok.Getter;
import org.aiknow.server.common.exception.AiknowException;

@Getter
public class AdminException extends AiknowException {

    public AdminException(AdminErrorCode adminErrorCode) {
        super(adminErrorCode);
    }
}
