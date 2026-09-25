package io.github.hantsy.jdbc.tx.it.event;

public class TestEvent {

    private final String payload;

    public TestEvent(String payload) {
        this.payload = payload;
    }

    public String getPayload() {
        return payload;
    }
}
