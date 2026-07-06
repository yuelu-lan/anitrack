package com.anitrack.starter.request;

import com.anitrack.domain.watchlist.enums.WatchStatus;
import lombok.Getter;

@Getter
public class WatchlistListReq {

    private WatchStatus status;
}
