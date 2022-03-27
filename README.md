# Functionality
- GCP function to alert slack channels who the next standup runner should be
- GCP function which listens to Slack events
  - channel add user event
  - channel remove user event
  - app mention event to add user (eg @StandupSally add @jsmith)
  - app mention event to remove user (eg @StandupSally remove @jsmith)
  - app mention event to add all users in the channel to StandupSally (eg @StandupSally add us)

# Tech
- requires GCP secrets to store data (5 which keeps it in the free tier)
  - channelIdTOMemberSlackHandles
  - channelTolastUserToRunStandup
  - mondaySprintStartDate
  - slackOauthToken
  - slackSigningSecret