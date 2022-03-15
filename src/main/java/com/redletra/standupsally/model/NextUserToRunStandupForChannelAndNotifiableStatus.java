package com.redletra.standupsally.model;

import java.util.List;

public class NextUserToRunStandupForChannelAndNotifiableStatus {

    // this models if the channel should receive a message. In some cases the channel
    // does not have a standup on the last day/first day of sprint
    private Boolean shouldSendUpdateToChannel;
    private String nextUserToRunStandup;

    public NextUserToRunStandupForChannelAndNotifiableStatus(Boolean shouldSendUpdateToChannel,
                                                             String nextUserToRunStandup) {
        this.shouldSendUpdateToChannel = shouldSendUpdateToChannel;
        this.nextUserToRunStandup = nextUserToRunStandup;
    }

    public Boolean getShouldSendUpdateToChannel() {
        return shouldSendUpdateToChannel;
    }

    public String getNextUserToRunStandup() {
        return nextUserToRunStandup;
    }
}
