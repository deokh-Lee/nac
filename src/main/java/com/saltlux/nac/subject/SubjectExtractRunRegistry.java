package com.saltlux.nac.subject;

import java.util.HashSet;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Component
public class SubjectExtractRunRegistry {

    public static final String ALL = "ALL";
    public static final String POLICY = "POLICY";
    public static final String EVENT = "EVENT";
    public static final String ACTIVITY = "ACTIVITY";

    private final Object monitor = new Object();
    private final Set<RunKey> activeRuns = new HashSet<>();

    public RunKey acquire(String processName, String subjectType, String transferYear, String prodYear) {
        RunKey requested = new RunKey(processName, subjectType, transferYear, prodYear);
        synchronized (monitor) {
            for (RunKey active : activeRuns) {
                if (active.overlaps(requested)) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            active.processName() + " already running for year=" + active.transferYear()
                                    + " prodYear=" + displayProdYear(active.prodYear())
                    );
                }
            }
            activeRuns.add(requested);
        }
        return requested;
    }

    public void release(RunKey runKey) {
        synchronized (monitor) {
            activeRuns.remove(runKey);
        }
    }

    private static String displayProdYear(String prodYear) {
        return StringUtils.hasText(prodYear) ? prodYear : "ALL";
    }

    public record RunKey(String processName, String subjectType, String transferYear, String prodYear) {
        boolean overlaps(RunKey other) {
            if (!transferYear.equals(other.transferYear())) {
                return false;
            }
            if (!sameSubjectRange(other)) {
                return false;
            }
            return !StringUtils.hasText(prodYear)
                    || !StringUtils.hasText(other.prodYear())
                    || prodYear.equals(other.prodYear());
        }

        private boolean sameSubjectRange(RunKey other) {
            return ALL.equals(subjectType)
                    || ALL.equals(other.subjectType())
                    || subjectType.equals(other.subjectType());
        }
    }
}
