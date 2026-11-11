# Broadcaster

The broadcaster subscribes to NATS messages published by the todo backend and forwards them to an external webhook.

How it works:

- todo-backend publishes event JSON to the `todo.events` subject
- broadcaster receives the message on a NATS queue group
- the broadcaster sends `{"user":"bot","message":"..."}` to `BROADCAST_URL`

Set `BROADCAST_URL` or `GENERIC_WEBHOOK_URL` to a generic webhook endpoint before deploying the app.
