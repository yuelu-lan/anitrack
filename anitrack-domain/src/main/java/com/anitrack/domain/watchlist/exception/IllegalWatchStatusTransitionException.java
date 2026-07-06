package com.anitrack.domain.watchlist.exception;

import com.anitrack.domain.common.AnitrackDomainException;
import com.anitrack.domain.watchlist.enums.WatchStatus;

public class IllegalWatchStatusTransitionException extends AnitrackDomainException {

    public IllegalWatchStatusTransitionException(WatchStatus from, WatchStatus to) {
        super("不允许从" + from + "状态转移至" + to + "状态");
    }
}
