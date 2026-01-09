package com.oracle.pic.networking.lvv.service.resources;

import com.oracle.pic.networking.lvv.service.utils.RenderableExceptionsGenerator;
import java.security.InvalidParameterException;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

@ToString
public class ResourceUtils {

    static void validateRequiredParameter(String parameterName, String parameterValue) {
        if (StringUtils.isBlank(parameterValue)) {
            throw RenderableExceptionsGenerator.generateInvalidParameterException(parameterName);
        }
    }

    static void validateOptionalParameter(String parameterName, String parameterValue) {
        if (parameterValue == null) {
            return;
        }
        if (StringUtils.isBlank(parameterValue)) {
            throw RenderableExceptionsGenerator.generateInvalidParameterException(parameterName);
        }
    }

    static void validateOptionalPositiveIntegerParameter(
            String parameterName, Integer parameterValue) {
        if (parameterValue != null && parameterValue <= 0) {
            throw new InvalidParameterException(parameterName);
        }
    }

    static String changeColonToHyphenInRackInfo(String rackInfo, boolean csvFriendly) {
        if (rackInfo == null) {
            return null;
        }
        return csvFriendly ? rackInfo.replace(":", " - ") : rackInfo;
    }
}
