package ru.spbau.voronchikhin;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.JSONParser;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by s on 26.01.15.
 */
public class InputProcessor {
    private JSONParser parser = new JSONParser();
    String data;
    private boolean status;

    public InputProcessor(String input) {
//        System.out.println("input= "+input);
        try {
            JSONObject json = (JSONObject) parser.parse(input);
            data = (String) json.get("data");
            status = true;
        } catch (Throwable e) {
            System.err.println("failed on parsing response "+input +" " +e.getMessage());
            data = null;
            status = false;
        }
    }

    public ByteBuffer getResponse() throws IOException {
        Map obj = new LinkedHashMap();
        if (data!=null) {
            String reversed = new StringBuilder(data).reverse().toString();
            obj.put("data", reversed);
        }
        obj.put("status", status);
        StringWriter out = new StringWriter();
        JSONValue.writeJSONString(obj, out);
        String jsonText = out.toString() + "\n";
//        System.out.println("send to output " + jsonText);
        jsonText.hashCode(); //just for delay
        return ByteBuffer.wrap(jsonText.getBytes());
    }


}
