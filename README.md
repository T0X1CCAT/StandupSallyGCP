This can be used to send a message to a Slack channel, telling the team members whose turn it is to run standup on the day. The slack handles are stored in a comma sep string within GCP secret manager. Another secret stores the last user who ran standup. ANother secret stores the slack channel id and a 4th secret stores the slack auth token. Bob's your relative.

--log into GCP locally
gcloud auth application-default login
