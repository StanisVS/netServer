package ru.spbau.goncharova.task3;

import org.json.simple.JSONObject;

public class ProcessingResult {
    private final JSONObject response;
    final long responseTime;

    public boolean isOk() {
        return (Boolean) (response.get(MyClient.statusId));
    }

    public ProcessingResult(JSONObject response, long responseTime) {
        this.response = response;
        this.responseTime = responseTime;
    }
}