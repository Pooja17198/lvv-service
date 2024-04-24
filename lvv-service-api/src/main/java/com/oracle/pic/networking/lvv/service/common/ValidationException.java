package com.oracle.pic.networking.lvv.service.common;

import com.oracle.pic.commons.exceptions.server.ErrorCode;
import com.oracle.pic.commons.exceptions.server.RenderableException;
import java.util.Arrays;
import lombok.Getter;

/**
 * Class to represent exceptions caused by invalid values for an object.
 *
 * <p>ValidationException was adapted from {@link RenderableException}.
 */
@Getter
public class ValidationException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * @return the machine-readable code associated with this exception
     */
    private ErrorCode errorCode;

    public ValidationException(ErrorCode code, String formatString, Object... args) {
        super(computeMessage(formatString, args), computeCause(args));
        this.errorCode = code;
    }

    private static String computeMessage(String formatString, Object... args) {
        if (args.length == 0) {
            return formatString;
        }

        Object last = args[args.length - 1];
        if (last instanceof Throwable) {
            return String.format(formatString, Arrays.copyOf(args, args.length - 1));
        } else {
            return String.format(formatString, args);
        }
    }

    private static Throwable computeCause(Object... args) {
        if (args.length == 0) {
            return null;
        }

        Object last = args[args.length - 1];
        if (last instanceof Throwable) {
            return (Throwable) last;
        } else {
            return null;
        }
    }
}
